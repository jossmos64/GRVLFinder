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
 * Enhanced GPX Evaluator that uses the same scoring system as the main "Find Gravel" functionality
 */
public class ImprovedGpxEvaluator {
    private static final String TAG = "ImprovedGpxEvaluator";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final double SEGMENT_LENGTH_METERS = 100.0; // Same as original

    // Score thresholds - same as main app
    private static final int SCORE_GREEN_THRESHOLD = 20;  // Green: excellent roads
    private static final int SCORE_YELLOW_THRESHOLD = 10; // Yellow: decent roads
    // Red: < 10 (poor roads)

    private BikeTypeManager bikeTypeManager;

    public ImprovedGpxEvaluator(BikeTypeManager bikeTypeManager) {
        this.bikeTypeManager = bikeTypeManager;
    }

    public static class EnhancedRouteAnalysis {
        // Distance breakdowns in kilometers
        public double totalDistance;
        public double greenDistance;    // Excellent roads (score >= 20)
        public double yellowDistance;   // Decent roads (score 10-19)
        public double redDistance;      // Poor roads (score < 10)
        public double unknownDistance;  // No road data available

        // Percentages for easy display
        public double greenPercentage;
        public double yellowPercentage;
        public double redPercentage;
        public double unknownPercentage;

        // Elevation data
        public double maxSlope;
        public GeoPoint steepestPoint;
        public String steepestLocationDescription;
        public boolean hasElevationData;

        // Analysis metadata
        public BikeType analyzedForBikeType;
        public int totalSegmentsAnalyzed;
        public int segmentsWithRoadData;
        public double dataCoveragePercentage;

        // Detailed surface breakdown (same as original)
        public Map<String, Double> surfaceBreakdown;

        public EnhancedRouteAnalysis() {
            this.surfaceBreakdown = new HashMap<>();
            this.maxSlope = 0.0;
            this.steepestLocationDescription = "Unknown";
        }

        public void calculatePercentages() {
            if (totalDistance > 0) {
                greenPercentage = (greenDistance / totalDistance) * 100.0;
                yellowPercentage = (yellowDistance / totalDistance) * 100.0;
                redPercentage = (redDistance / totalDistance) * 100.0;
                unknownPercentage = (unknownDistance / totalDistance) * 100.0;
            }
        }

        /**
         * Get a quality assessment based on the bike type and road quality distribution
         */
        public String getQualityAssessment() {
            if (dataCoveragePercentage < 30) {
                return "Limited data available for assessment";
            }

            if (greenPercentage >= 60) {
                return "Excellent route for " + analyzedForBikeType.getDisplayName();
            } else if (greenPercentage + yellowPercentage >= 70) {
                return "Good route for " + analyzedForBikeType.getDisplayName();
            } else if (redPercentage >= 50) {
                return "Challenging route - many poor quality segments";
            } else {
                return "Mixed quality route";
            }
        }

        /**
         * Get elevation assessment
         */
        public String getElevationAssessment() {
            if (!hasElevationData || maxSlope < 0) {
                return "No elevation data available";
            }

            if (maxSlope >= 15.0) {
                return String.format("Very steep route (max %.1f%%)", maxSlope);
            } else if (maxSlope >= 12.0) {
                return String.format("Steep sections present (max %.1f%%)", maxSlope);
            } else if (maxSlope >= 8.0) {
                return String.format("Moderate hills (max %.1f%%)", maxSlope);
            } else {
                return String.format("Mostly flat route (max %.1f%%)", maxSlope);
            }
        }
    }

    public interface EnhancedRouteAnalysisCallback {
        void onAnalysisComplete(EnhancedRouteAnalysis analysis);
        void onAnalysisError(String error);
        void onProgress(int progress); // 0-100
    }

    // Internal representation of a segment
    private static class RouteSegment {
        GeoPoint startPoint;
        GeoPoint endPoint;
        double distance;
        double slope = -1; // -1 = unknown
        PolylineResult matchedRoad = null; // The OSM road this segment matches to
        int roadScore = -1; // Score from the matched road

        RouteSegment(GeoPoint start, GeoPoint end) {
            this.startPoint = start;
            this.endPoint = end;
            this.distance = start.distanceToAsDouble(end);
        }
    }

    /**
     * Main entry point for enhanced GPX analysis using the same logic as "Find Gravel"
     */
    public static void analyzeGpxRouteEnhanced(List<GeoPoint> routePoints,
                                               BikeTypeManager bikeTypeManager,
                                               EnhancedRouteAnalysisCallback callback) {

        if (routePoints == null || routePoints.isEmpty()) {
            if (callback != null) {
                new Handler(Looper.getMainLooper()).post(() ->
                        callback.onAnalysisError("No route points provided"));
            }
            return;
        }

        Log.d(TAG, "Starting enhanced GPX analysis for " + routePoints.size() + " points");

        executor.execute(() -> {
            try {
                EnhancedRouteAnalysis analysis = new EnhancedRouteAnalysis();
                analysis.analyzedForBikeType = bikeTypeManager.getCurrentBikeType();
                analysis.totalDistance = GpxParser.calculateRouteDistance(routePoints) / 1000.0; // Convert to km
                analysis.hasElevationData = GpxParser.hasElevationData(routePoints);

                // Create segments
                List<RouteSegment> segments = createRouteSegments(routePoints);
                analysis.totalSegmentsAnalyzed = segments.size();

                Log.d(TAG, "Created " + segments.size() + " segments for enhanced analysis");

                if (callback != null) postProgress(callback, 10);

                // Prepare ScoreCalculator with current bike type
                ScoreCalculator scoreCalculator = new ScoreCalculator(bikeTypeManager.getCurrentWeights());
                scoreCalculator.setBikeTypeManager(bikeTypeManager);

                // Handle elevation analysis
                if (analysis.hasElevationData) {
                    analyzeSlopeData(segments, analysis);
                    if (callback != null) postProgress(callback, 25);
                } else if (bikeTypeManager.shouldFetchElevationData()) {
                    fetchElevationForSegmentsSync(segments, analysis, callback);
                    if (callback != null) postProgress(callback, 25);
                }

                // This is the key improvement: analyze surface data using the same system as "Find Gravel"
                analyzeRoadQuality(segments, analysis, bikeTypeManager, scoreCalculator, callback);

                // Calculate final metrics and percentages
                calculateFinalMetrics(analysis);

                if (callback != null) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        callback.onProgress(100);
                        callback.onAnalysisComplete(analysis);
                    });
                }

            } catch (Exception e) {
                Log.e(TAG, "Error in enhanced GPX route analysis", e);
                if (callback != null) {
                    new Handler(Looper.getMainLooper()).post(() ->
                            callback.onAnalysisError("Enhanced analysis failed: " + e.getMessage()));
                }
            }
        });
    }

    /**
     * The key method: analyze road quality using the same scoring system as "Find Gravel"
     */
    private static void analyzeRoadQuality(List<RouteSegment> segments,
                                           EnhancedRouteAnalysis analysis,
                                           BikeTypeManager bikeTypeManager,
                                           ScoreCalculator scoreCalculator,
                                           EnhancedRouteAnalysisCallback callback) {

        Map<BoundingBox, List<RouteSegment>> bboxGroups = groupSegmentsByBounds(segments);
        Map<String, BoundingBox> bboxKeys = new LinkedHashMap<>();
        for (BoundingBox b : bboxGroups.keySet()) {
            bboxKeys.put(bboxKey(b), b);
        }

        Log.d(TAG, "Querying " + bboxGroups.size() + " bounding boxes for road quality analysis");

        Map<String, List<PolylineResult>> osmCache = new HashMap<>();
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

            // Fetch OSM data for this bounding box - same as "Find Gravel"
            List<PolylineResult> osmRoads = osmCache.get(key);
            if (osmRoads == null) {
                try {
                    // Use the same OverpassService as the main app
                    osmRoads = OverpassServiceSync.fetchDataSync(bbox, scoreCalculator);

                    // Apply slope data if needed (same as main app)
                    if (bikeTypeManager.shouldFetchElevationData() && !osmRoads.isEmpty()) {
                        CountDownLatch slopeLatch = new CountDownLatch(1);
                        AtomicBoolean slopeSuccess = new AtomicBoolean(false);

                        ElevationService.addSlopeDataToRoads(osmRoads, new ElevationService.RoadElevationCallback() {
                            @Override
                            public void onSuccess(List<PolylineResult> updatedResults) {
                                // Recalculate scores with slope data - same as main app
                                for (PolylineResult road : updatedResults) {
                                    double maxSlope = road.getMaxSlopePercent();
                                    if (maxSlope >= 0) {
                                        int newScore = scoreCalculator.calculateScoreWithSlope(
                                                road.getTags(), road.getPoints(), maxSlope);
                                        road.setScore(newScore);
                                    }
                                }
                                slopeSuccess.set(true);
                                slopeLatch.countDown();
                            }

                            @Override
                            public void onError(String error) {
                                Log.w(TAG, "Slope data fetch failed: " + error);
                                slopeLatch.countDown();
                            }
                        });

                        try {
                            slopeLatch.await(15, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }

                } catch (Exception e) {
                    Log.w(TAG, "Failed to fetch OSM data: " + e.getMessage());
                    osmRoads = new ArrayList<>();
                }
                osmCache.put(key, osmRoads);
            }

            // Match segments to roads and apply scoring - this is the core improvement
            matchSegmentsToRoadsWithScoring(bboxSegments, osmRoads, analysis, scoreCalculator);

            processedBoxes++;
            final int progress = 30 + (processedBoxes * 60 / totalBoxes);
            if (callback != null) postProgress(callback, progress);
        }
    }

    /**
     * Match segments to OSM roads and classify them using the same scoring system as main app
     */
    private static void matchSegmentsToRoadsWithScoring(List<RouteSegment> segments,
                                                        List<PolylineResult> osmRoads,
                                                        EnhancedRouteAnalysis analysis,
                                                        ScoreCalculator scoreCalculator) {

        for (RouteSegment segment : segments) {
            PolylineResult bestMatch = findClosestRoad(segment, osmRoads);

            double segmentDistance = segment.distance;

            if (bestMatch != null) {
                segment.matchedRoad = bestMatch;
                analysis.segmentsWithRoadData++;

                // Get the road's score (same logic as main app)
                int roadScore = bestMatch.getScore();
                segment.roadScore = roadScore;

                // Classify the segment based on score thresholds (same as main app)
                if (roadScore >= SCORE_GREEN_THRESHOLD) {
                    analysis.greenDistance += segmentDistance;
                    addSurfaceBreakdown(analysis, "excellent_roads", segmentDistance);
                } else if (roadScore >= SCORE_YELLOW_THRESHOLD) {
                    analysis.yellowDistance += segmentDistance;
                    addSurfaceBreakdown(analysis, "decent_roads", segmentDistance);
                } else {
                    analysis.redDistance += segmentDistance;
                    addSurfaceBreakdown(analysis, "poor_roads", segmentDistance);
                }

                // Also add specific surface info if available
                String surface = bestMatch.getTags().get("surface");
                if (surface != null && !surface.trim().isEmpty()) {
                    addSurfaceBreakdown(analysis, surface.toLowerCase(), segmentDistance);
                }

                Log.d(TAG, String.format("Segment matched to road with score %d (%s category)",
                        roadScore,
                        roadScore >= SCORE_GREEN_THRESHOLD ? "green" :
                                roadScore >= SCORE_YELLOW_THRESHOLD ? "yellow" : "red"));

            } else {
                // No matching road found
                analysis.unknownDistance += segmentDistance;
                addSurfaceBreakdown(analysis, "no_road_data", segmentDistance);
            }
        }

        // Calculate data coverage percentage
        analysis.dataCoveragePercentage = analysis.totalSegmentsAnalyzed > 0 ?
                (analysis.segmentsWithRoadData / (double) analysis.totalSegmentsAnalyzed) * 100.0 : 0.0;

        Log.d(TAG, String.format("Road quality analysis: %.1f%% green, %.1f%% yellow, %.1f%% red, %.1f%% unknown (%.1f%% coverage)",
                (analysis.greenDistance / (analysis.totalDistance * 1000)) * 100,
                (analysis.yellowDistance / (analysis.totalDistance * 1000)) * 100,
                (analysis.redDistance / (analysis.totalDistance * 1000)) * 100,
                (analysis.unknownDistance / (analysis.totalDistance * 1000)) * 100,
                analysis.dataCoveragePercentage));
    }

    private static void addSurfaceBreakdown(EnhancedRouteAnalysis analysis, String key, double meters) {
        analysis.surfaceBreakdown.put(key, analysis.surfaceBreakdown.getOrDefault(key, 0.0) + meters);
    }

    private static PolylineResult findClosestRoad(RouteSegment segment, List<PolylineResult> osmRoads) {
        if (osmRoads.isEmpty()) return null;

        PolylineResult bestMatch = null;
        double minDistance = Double.MAX_VALUE;

        GeoPoint midpoint = new GeoPoint(
                (segment.startPoint.getLatitude() + segment.endPoint.getLatitude()) / 2.0,
                (segment.startPoint.getLongitude() + segment.endPoint.getLongitude()) / 2.0
        );

        for (PolylineResult road : osmRoads) {
            for (GeoPoint roadPoint : road.getPoints()) {
                double distance = midpoint.distanceToAsDouble(roadPoint);
                if (distance < minDistance && distance < 200.0) { // within 100m
                    minDistance = distance;
                    bestMatch = road;
                }
            }
        }

        return bestMatch;
    }

    // Helper methods (mostly same as original GpxEvaluator)
    private static void postProgress(EnhancedRouteAnalysisCallback callback, int progress) {
        new Handler(Looper.getMainLooper()).post(() -> callback.onProgress(progress));
    }

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

            if (accumulatedDistance >= SEGMENT_LENGTH_METERS || i == routePoints.size() - 1) {
                segments.add(new RouteSegment(segmentStart, current));
                segmentStart = current;
                accumulatedDistance = 0.0;
            }
        }
        return segments;
    }

    private static void analyzeSlopeData(List<RouteSegment> segments, EnhancedRouteAnalysis analysis) {
        for (RouteSegment segment : segments) {
            double a1 = segment.startPoint.getAltitude();
            double a2 = segment.endPoint.getAltitude();

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

    private static boolean fetchElevationForSegmentsSync(List<RouteSegment> segments,
                                                         EnhancedRouteAnalysis analysis,
                                                         EnhancedRouteAnalysisCallback callback) {
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

        List<PolylineResult> dummyRoads = createDummyRoadsForElevation(elevationPoints);

        ElevationService.addSlopeDataToRoads(dummyRoads, new ElevationService.RoadElevationCallback() {
            @Override
            public void onSuccess(List<PolylineResult> updatedResults) {
                try {
                    updateSegmentsWithElevation(segments, elevationPoints, updatedResults);
                    analyzeSlopeData(segments, analysis);
                    analysis.hasElevationData = true;
                    success.set(true);
                    if (callback != null) postProgress(callback, 50);
                } finally {
                    latch.countDown();
                }
            }

            @Override
            public void onError(String error) {
                Log.w(TAG, "Failed to fetch elevation data: " + error);
                if (callback != null) postProgress(callback, 50);
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

    private static Map<BoundingBox, List<RouteSegment>> groupSegmentsByBounds(List<RouteSegment> segments) {
        Map<BoundingBox, List<RouteSegment>> groups = new LinkedHashMap<>();
        double bufferDegrees = 0.02;
        double mergeThreshold = 0.05;

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

    private static void calculateFinalMetrics(EnhancedRouteAnalysis analysis) {
        // Convert all distances from meters to kilometers
        analysis.greenDistance = analysis.greenDistance / 1000.0;
        analysis.yellowDistance = analysis.yellowDistance / 1000.0;
        analysis.redDistance = analysis.redDistance / 1000.0;
        analysis.unknownDistance = analysis.unknownDistance / 1000.0;

        // Calculate percentages
        analysis.calculatePercentages();

        // Convert surface breakdown to kilometers
        Map<String, Double> kmBreakdown = new HashMap<>();
        for (Map.Entry<String, Double> e : analysis.surfaceBreakdown.entrySet()) {
            kmBreakdown.put(e.getKey(), e.getValue() / 1000.0);
        }
        analysis.surfaceBreakdown = kmBreakdown;
    }
}