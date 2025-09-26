package be.kuleuven.gt.grvlfinder;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Improved GpxEvaluator for analyzing GPX routes with proper surface and elevation analysis
 */
public class GpxEvaluator {
    private static final String TAG = "GpxEvaluator";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final double SEGMENT_LENGTH_METERS = 100.0; // Analyze in ~100m segments
    private static final int SCORE_ASPHALT_THRESHOLD = 5; // score >= => asphalt if no tag
    private static final int SCORE_GRAVEL_THRESHOLD = 0;  // score <= => gravel if no tag

    private BikeTypeManager bikeTypeManager;

    public GpxEvaluator(BikeTypeManager bikeTypeManager) {
        this.bikeTypeManager = bikeTypeManager;
    }

    public static class RouteAnalysis {
        public double totalDistance; // in km
        public double gravelDistance; // in km
        public double asphaltDistance; // in km
        public double unknownSurfaceDistance; // in km
        public double maxSlope;
        public GeoPoint steepestPoint;
        public String steepestLocationDescription;
        public Map<String, Double> surfaceBreakdown; // in km
        public BikeType analyzedForBikeType;
        public boolean hasElevationData;
        public int totalSegmentsAnalyzed;
        public int segmentsWithRoadData;

        public RouteAnalysis() {
            this.surfaceBreakdown = new HashMap<>();
            this.maxSlope = 0.0;
            this.steepestLocationDescription = "Unknown";
        }

        public double getGravelPercentage() {
            return totalDistance > 0 ? (gravelDistance / totalDistance) * 100.0 : 0.0;
        }

        public double getAsphaltPercentage() {
            return totalDistance > 0 ? (asphaltDistance / totalDistance) * 100.0 : 0.0;
        }

        public double getDataCoveragePercentage() {
            return totalSegmentsAnalyzed > 0 ?
                    (segmentsWithRoadData / (double) totalSegmentsAnalyzed) * 100.0 : 0.0;
        }
    }

    public interface RouteAnalysisCallback {
        void onAnalysisComplete(RouteAnalysis analysis);
        void onAnalysisError(String error);
        void onProgress(int progress); // 0-100
    }

    // Internal representation of a segment
    private static class RouteSegment {
        GeoPoint startPoint;
        GeoPoint endPoint;
        double distance;
        double slope = -1; // -1 = unknown
        Map<String, String> roadTags;

        RouteSegment(GeoPoint start, GeoPoint end) {
            this.startPoint = start;
            this.endPoint = end;
            this.distance = start.distanceToAsDouble(end);
            this.roadTags = new HashMap<>();
        }
    }

    /**
     * Analyze route points using given BikeTypeManager
     */
    public RouteAnalysis analyze(GpxParser.GpxRoute route) {
        RouteAnalysis analysis = new RouteAnalysis();
        analysis.analyzedForBikeType = bikeTypeManager.getCurrentBikeType();

        List<GeoPoint> points = route.getPoints();
        if (points == null || points.isEmpty()) {
            return analysis;
        }

        analysis.totalDistance = GpxParser.calculateRouteDistance(points);
        analysis.hasElevationData = GpxParser.hasElevationData(points);

        // Create segments for analysis
        List<RouteSegment> segments = createRouteSegments(points);
        analysis.totalSegmentsAnalyzed = segments.size();

        // Analyze elevation/slope data
        if (analysis.hasElevationData) {
            analyzeSlopeData(segments, analysis);
        }

        // Analyze surface data (simplified for sync analysis)
        analyzeSurfaceDataSync(segments, analysis);

        // Convert from meters to kilometers
        analysis.totalDistance = analysis.totalDistance / 1000.0;
        analysis.gravelDistance = analysis.gravelDistance / 1000.0;
        analysis.asphaltDistance = analysis.asphaltDistance / 1000.0;
        analysis.unknownSurfaceDistance = analysis.unknownSurfaceDistance / 1000.0;

        Map<String, Double> kmBreakdown = new HashMap<>();
        for (Map.Entry<String, Double> e : analysis.surfaceBreakdown.entrySet()) {
            kmBreakdown.put(e.getKey(), e.getValue() / 1000.0);
        }
        analysis.surfaceBreakdown = kmBreakdown;

        return analysis;
    }

    /**
     * Main entry point for async analysis
     */
    public static void analyzeGpxRoute(List<GeoPoint> routePoints,
                                       BikeTypeManager bikeTypeManager,
                                       RouteAnalysisCallback callback) {

        if (routePoints == null || routePoints.isEmpty()) {
            if (callback != null) {
                new Handler(Looper.getMainLooper()).post(() ->
                        callback.onAnalysisError("No route points provided"));
            }
            return;
        }

        Log.d(TAG, "Starting GPX analysis for " + routePoints.size() + " points");

        executor.execute(() -> {
            try {
                RouteAnalysis analysis = new RouteAnalysis();
                analysis.analyzedForBikeType = bikeTypeManager.getCurrentBikeType();
                analysis.totalDistance = GpxParser.calculateRouteDistance(routePoints);
                analysis.hasElevationData = GpxParser.hasElevationData(routePoints);

                // Create segments
                List<RouteSegment> segments = createRouteSegments(routePoints);
                analysis.totalSegmentsAnalyzed = segments.size();

                Log.d(TAG, "Created " + segments.size() + " segments for analysis");

                if (callback != null) postProgress(callback, 10);

                // Prepare ScoreCalculator
                ScoreCalculator scoreCalculator = new ScoreCalculator(bikeTypeManager.getCurrentWeights());
                scoreCalculator.setBikeTypeManager(bikeTypeManager);

                // Handle elevation
                if (analysis.hasElevationData) {
                    analyzeSlopeData(segments, analysis);
                    if (callback != null) postProgress(callback, 30);
                } else if (bikeTypeManager.shouldFetchElevationData()) {
                    boolean fetched = fetchElevationForSegmentsSync(segments, analysis, callback);
                    if (callback != null) postProgress(callback, 30);
                }

                // Analyze surface data
                Map<String, List<PolylineResult>> osmCache = new HashMap<>();
                analyzeSurfaceData(segments, analysis, bikeTypeManager, scoreCalculator, osmCache, callback);

                // Convert to kilometers
                calculateFinalMetrics(analysis);

                if (callback != null) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        callback.onProgress(100);
                        callback.onAnalysisComplete(analysis);
                    });
                }

            } catch (Exception e) {
                Log.e(TAG, "Error analyzing GPX route", e);
                if (callback != null) {
                    new Handler(Looper.getMainLooper()).post(() ->
                            callback.onAnalysisError("Analysis failed: " + e.getMessage()));
                }
            }
        });
    }

    /**
     * Simplified sync surface analysis for when OSM data isn't available
     */
    private void analyzeSurfaceDataSync(List<RouteSegment> segments, RouteAnalysis analysis) {
        ScoreCalculator scoreCalculator = new ScoreCalculator(bikeTypeManager.getCurrentWeights());
        scoreCalculator.setBikeTypeManager(bikeTypeManager);

        // Simulate analysis based on bike type preferences
        for (RouteSegment segment : segments) {
            double dist = segment.distance;

            // Default assumption based on bike type
            if (bikeTypeManager.prefersPavedRoads()) {
                // Assume 70% asphalt, 30% unknown for road bikes
                analysis.asphaltDistance += dist * 0.7;
                analysis.unknownSurfaceDistance += dist * 0.3;
                addBreakdown(analysis, "assumed_asphalt", dist * 0.7);
                addBreakdown(analysis, "unknown", dist * 0.3);
            } else {
                // Assume 60% gravel, 40% unknown for gravel bikes
                analysis.gravelDistance += dist * 0.6;
                analysis.unknownSurfaceDistance += dist * 0.4;
                addBreakdown(analysis, "assumed_gravel", dist * 0.6);
                addBreakdown(analysis, "unknown", dist * 0.4);
            }
        }

        // Set low coverage since this is estimation
        analysis.segmentsWithRoadData = segments.size() / 3;
    }

    private static void postProgress(RouteAnalysisCallback callback, int progress) {
        new Handler(Looper.getMainLooper()).post(() -> callback.onProgress(progress));
    }

    /**
     * Split route into segments
     */
    private static List<RouteSegment> createRouteSegments(List<GeoPoint> routePoints) {
        List<RouteSegment> segments = new ArrayList<>();
        if (routePoints.size() < 2) return segments;

        double accumulatedDistance = 0.0;
        GeoPoint segmentStart = routePoints.get(0);

        for (int i = 1; i < routePoints.size(); i++) {
            GeoPoint prev = routePoints.get(i - 1);
            GeoPoint current = routePoints.get(i);
            double d = prev.distanceToAsDouble(current);
            accumulatedDistance += d;

            // Create segment when threshold reached or at end
            if (accumulatedDistance >= SEGMENT_LENGTH_METERS || i == routePoints.size() - 1) {
                segments.add(new RouteSegment(segmentStart, current));
                segmentStart = current;
                accumulatedDistance = 0.0;
            }
        }
        return segments;
    }

    /**
     * Analyze slope from existing altitude values
     */
    private static void analyzeSlopeData(List<RouteSegment> segments, RouteAnalysis analysis) {
        for (RouteSegment segment : segments) {
            double a1 = segment.startPoint.getAltitude();
            double a2 = segment.endPoint.getAltitude();

            // Skip if both altitudes are 0 (likely no data)
            if (Double.compare(a1, 0.0) == 0 && Double.compare(a2, 0.0) == 0) {
                continue;
            }

            double elevationDiff = Math.abs(a2 - a1);
            if (segment.distance > 0) {
                double slope = (elevationDiff / segment.distance) * 100.0;
                segment.slope = slope;
                if (slope > analysis.maxSlope) {
                    analysis.maxSlope = slope;
                    analysis.steepestPoint = segment.startPoint;
                    analysis.steepestLocationDescription = String.format(Locale.US,
                            "%.6f, %.6f", segment.startPoint.getLatitude(), segment.startPoint.getLongitude());
                }
            }
        }
    }

    /**
     * Synchronous elevation fetch
     */
    private static boolean fetchElevationForSegmentsSync(List<RouteSegment> segments,
                                                         RouteAnalysis analysis,
                                                         RouteAnalysisCallback callback) {
        List<GeoPoint> elevationPoints = new ArrayList<>();
        for (RouteSegment segment : segments) {
            if (Double.compare(segment.startPoint.getAltitude(), 0.0) == 0) {
                elevationPoints.add(segment.startPoint);
            }
            if (Double.compare(segment.endPoint.getAltitude(), 0.0) == 0) {
                elevationPoints.add(segment.endPoint);
            }
        }

        if (elevationPoints.isEmpty()) return false;

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean success = new AtomicBoolean(false);

        ElevationService.addSlopeDataToRoads(
                createDummyRoadsForElevation(elevationPoints),
                new ElevationService.RoadElevationCallback() {
                    @Override
                    public void onSuccess(List<PolylineResult> updatedResults) {
                        try {
                            updateSegmentsWithElevation(segments, elevationPoints, updatedResults);
                            analyzeSlopeData(segments, analysis);
                            analysis.hasElevationData = true;
                            success.set(true);
                            if (callback != null) postProgress(callback, 60);
                        } finally {
                            latch.countDown();
                        }
                    }

                    @Override
                    public void onError(String error) {
                        Log.w(TAG, "Failed to fetch elevation data: " + error);
                        if (callback != null) postProgress(callback, 60);
                        latch.countDown();
                    }
                });

        try {
            boolean finished = latch.await(30, TimeUnit.SECONDS);
            if (!finished) {
                Log.w(TAG, "Elevation fetch timed out");
            }
        } catch (InterruptedException e) {
            Log.w(TAG, "Interrupted while waiting for elevation fetch", e);
            Thread.currentThread().interrupt();
        }

        return success.get();
    }

    /**
     * Full surface analysis with OSM data
     */
    private static void analyzeSurfaceData(List<RouteSegment> segments,
                                           RouteAnalysis analysis,
                                           BikeTypeManager bikeTypeManager,
                                           ScoreCalculator scoreCalculator,
                                           Map<String, List<PolylineResult>> osmCache,
                                           RouteAnalysisCallback callback) {

        Map<BoundingBox, List<RouteSegment>> bboxGroups = groupSegmentsByBounds(segments);
        Map<String, BoundingBox> bboxKeys = new LinkedHashMap<>();
        for (BoundingBox b : bboxGroups.keySet()) {
            bboxKeys.put(bboxKey(b), b);
        }

        Log.d(TAG, "Querying " + bboxGroups.size() + " bounding boxes for surface data");

        int processedBoxes = 0;
        int totalBoxes = bboxGroups.size();
        if (totalBoxes == 0) {
            if (callback != null) postProgress(callback, 90);
            return;
        }

        for (Map.Entry<String, BoundingBox> keyed : bboxKeys.entrySet()) {
            String key = keyed.getKey();
            BoundingBox bbox = keyed.getValue();
            List<RouteSegment> bboxSegments = bboxGroups.get(bbox);

            List<PolylineResult> osmRoads = osmCache.get(key);
            if (osmRoads == null) {
                try {
                    osmRoads = OverpassServiceSync.fetchDataSync(bbox, scoreCalculator);
                } catch (Exception e) {
                    Log.w(TAG, "Failed to fetch OSM data: " + e.getMessage());
                    osmRoads = new ArrayList<>();
                }
                osmCache.put(key, osmRoads);
            }

            matchSegmentsToRoads(bboxSegments, osmRoads, analysis, scoreCalculator, bikeTypeManager);

            processedBoxes++;
            final int progress = 70 + (processedBoxes * 20 / totalBoxes);
            if (callback != null) postProgress(callback, progress);
        }
    }

    /**
     * Match segments to OSM roads
     */
    private static void matchSegmentsToRoads(List<RouteSegment> segments,
                                             List<PolylineResult> osmRoads,
                                             RouteAnalysis analysis,
                                             ScoreCalculator scoreCalculator,
                                             BikeTypeManager bikeTypeManager) {

        for (RouteSegment segment : segments) {
            PolylineResult bestMatch = findClosestRoad(segment, osmRoads);

            if (bestMatch != null) {
                Map<String, String> tags = bestMatch.getTags();
                segment.roadTags = tags;
                analysis.segmentsWithRoadData++;

                String surfaceTag = tags.get("surface");
                double dist = segment.distance;

                if (surfaceTag != null && !surfaceTag.trim().isEmpty()) {
                    categorizeSurface(surfaceTag, dist, analysis);
                } else {
                    // Use score fallback
                    int score;
                    if (segment.slope >= 0) {
                        score = scoreCalculator.calculateScoreWithSlope(tags, bestMatch.getPoints(), segment.slope);
                    } else {
                        score = scoreCalculator.calculateScore(tags, bestMatch.getPoints());
                    }

                    if (score >= SCORE_ASPHALT_THRESHOLD) {
                        analysis.asphaltDistance += dist;
                        addBreakdown(analysis, "scored_asphalt", dist);
                    } else if (score <= SCORE_GRAVEL_THRESHOLD) {
                        analysis.gravelDistance += dist;
                        addBreakdown(analysis, "scored_gravel", dist);
                    } else {
                        analysis.unknownSurfaceDistance += dist;
                        addBreakdown(analysis, "scored_unknown", dist);
                    }
                }
            } else {
                analysis.unknownSurfaceDistance += segment.distance;
            }
        }
    }

    private static void addBreakdown(RouteAnalysis analysis, String key, double meters) {
        analysis.surfaceBreakdown.put(key, analysis.surfaceBreakdown.getOrDefault(key, 0.0) + meters);
    }

    private static PolylineResult findClosestRoad(RouteSegment segment, List<PolylineResult> osmRoads) {
        PolylineResult bestMatch = null;
        double minDistance = Double.MAX_VALUE;

        GeoPoint midpoint = new GeoPoint(
                (segment.startPoint.getLatitude() + segment.endPoint.getLatitude()) / 2.0,
                (segment.startPoint.getLongitude() + segment.endPoint.getLongitude()) / 2.0
        );

        for (PolylineResult road : osmRoads) {
            for (GeoPoint rp : road.getPoints()) {
                double d = midpoint.distanceToAsDouble(rp);
                if (d < minDistance && d < 50.0) { // within 50m
                    minDistance = d;
                    bestMatch = road;
                }
            }
        }

        return bestMatch;
    }

    private static void categorizeSurface(String surface, double distance, RouteAnalysis analysis) {
        if (surface == null) {
            analysis.unknownSurfaceDistance += distance;
            return;
        }

        String key = surface.toLowerCase(Locale.ROOT).trim();
        analysis.surfaceBreakdown.put(key, analysis.surfaceBreakdown.getOrDefault(key, 0.0) + distance);

        switch (key) {
            case "gravel":
            case "fine_gravel":
            case "pebblestone":
            case "compacted":
            case "ground":
            case "earth":
            case "dirt":
            case "unpaved":
                analysis.gravelDistance += distance;
                break;

            case "asphalt":
            case "paved":
            case "concrete":
            case "concrete:plates":
                analysis.asphaltDistance += distance;
                break;

            default:
                analysis.unknownSurfaceDistance += distance;
                break;
        }
    }

    private static Map<BoundingBox, List<RouteSegment>> groupSegmentsByBounds(List<RouteSegment> segments) {
        Map<BoundingBox, List<RouteSegment>> groups = new LinkedHashMap<>();
        double bufferDegrees = 0.01;

        for (RouteSegment segment : segments) {
            double minLat = Math.min(segment.startPoint.getLatitude(), segment.endPoint.getLatitude()) - bufferDegrees;
            double maxLat = Math.max(segment.startPoint.getLatitude(), segment.endPoint.getLatitude()) + bufferDegrees;
            double minLon = Math.min(segment.startPoint.getLongitude(), segment.endPoint.getLongitude()) - bufferDegrees;
            double maxLon = Math.max(segment.startPoint.getLongitude(), segment.endPoint.getLongitude()) + bufferDegrees;

            BoundingBox bbox = new BoundingBox(maxLat, maxLon, minLat, minLon);

            BoundingBox existing = null;
            for (BoundingBox b : groups.keySet()) {
                if (bboxOverlaps(b, bbox)) {
                    existing = b;
                    break;
                }
            }

            if (existing != null) {
                groups.get(existing).add(segment);
            } else {
                List<RouteSegment> list = new ArrayList<>();
                list.add(segment);
                groups.put(bbox, list);
            }
        }

        return groups;
    }

    private static boolean bboxOverlaps(BoundingBox a, BoundingBox b) {
        return !(a.getLatNorth() < b.getLatSouth() ||
                b.getLatNorth() < a.getLatSouth() ||
                a.getLonEast() < b.getLonWest() ||
                b.getLonEast() < a.getLonWest());
    }

    private static String bboxKey(BoundingBox b) {
        return String.format(Locale.US, "%.6f_%.6f_%.6f_%.6f",
                b.getLatSouth(), b.getLonWest(), b.getLatNorth(), b.getLonEast());
    }

    private static List<PolylineResult> createDummyRoadsForElevation(List<GeoPoint> points) {
        List<PolylineResult> dummy = new ArrayList<>();
        Map<String, String> tags = new HashMap<>();
        PolylineResult p = new PolylineResult(points, 0, tags);
        dummy.add(p);
        return dummy;
    }

    private static void updateSegmentsWithElevation(List<RouteSegment> segments,
                                                    List<GeoPoint> elevationPoints,
                                                    List<PolylineResult> elevationResults) {
        if (elevationResults.isEmpty()) return;
        List<GeoPoint> updated = elevationResults.get(0).getPoints();

        for (int i = 0; i < elevationPoints.size() && i < updated.size(); i++) {
            GeoPoint original = elevationPoints.get(i);
            GeoPoint upd = updated.get(i);

            for (RouteSegment segment : segments) {
                if (pointsEqual(segment.startPoint, original)) {
                    segment.startPoint.setAltitude(upd.getAltitude());
                }
                if (pointsEqual(segment.endPoint, original)) {
                    segment.endPoint.setAltitude(upd.getAltitude());
                }
            }
        }
    }

    private static boolean pointsEqual(GeoPoint a, GeoPoint b) {
        return Math.abs(a.getLatitude() - b.getLatitude()) < 0.000001 &&
                Math.abs(a.getLongitude() - b.getLongitude()) < 0.000001;
    }

    private static void calculateFinalMetrics(RouteAnalysis analysis) {
        analysis.totalDistance = analysis.totalDistance / 1000.0;
        analysis.gravelDistance = analysis.gravelDistance / 1000.0;
        analysis.asphaltDistance = analysis.asphaltDistance / 1000.0;
        analysis.unknownSurfaceDistance = analysis.unknownSurfaceDistance / 1000.0;

        Map<String, Double> kmBreakdown = new HashMap<>();
        for (Map.Entry<String, Double> e : analysis.surfaceBreakdown.entrySet()) {
            kmBreakdown.put(e.getKey(), e.getValue() / 1000.0);
        }
        analysis.surfaceBreakdown = kmBreakdown;
    }
}