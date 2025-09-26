package be.kuleuven.gt.grvlfinder;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * GPX Evaluator that uses the same approach as "Find Gravel" -
 * gets all roads in the route area first, then matches route segments to existing roads
 */
public class PolylineBasedGpxEvaluator {
    private static final String TAG = "PolylineBasedGpxEvaluator";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final double SEGMENT_LENGTH_METERS = 50.0; // Smaller segments for better matching

    // Score thresholds - same as main app
    private static final int SCORE_GREEN_THRESHOLD = 20;
    private static final int SCORE_YELLOW_THRESHOLD = 10;

    public static class PolylineRouteAnalysis {
        // Distance breakdowns in kilometers
        public double totalDistance;
        public double greenDistance;
        public double yellowDistance;
        public double redDistance;
        public double unknownDistance;

        // Percentages
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
        public int totalRoadsInArea; // New: total roads found by "Find Gravel" approach

        public PolylineRouteAnalysis() {
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

    public interface PolylineRouteAnalysisCallback {
        void onAnalysisComplete(PolylineRouteAnalysis analysis);
        void onAnalysisError(String error);
        void onProgress(int progress, String message);
    }

    private static class RouteSegment {
        GeoPoint startPoint;
        GeoPoint endPoint;
        double distance;
        double slope = -1;
        PolylineResult matchedRoad = null;
        int roadScore = -1;

        RouteSegment(GeoPoint start, GeoPoint end) {
            this.startPoint = start;
            this.endPoint = end;
            this.distance = start.distanceToAsDouble(end);
        }
    }

    /**
     * Analyze GPX route using the same approach as "Find Gravel"
     */
    // Replace the entire analyzeGpxRouteWithPolylines method in PolylineBasedGpxEvaluator.java with this simpler version:

    /**
     * Analyze GPX route using the same approach as "Find Gravel" but with selective elevation fetching
     */
    public static void analyzeGpxRouteWithPolylines(List<GeoPoint> routePoints,
                                                    BikeTypeManager bikeTypeManager,
                                                    PolylineRouteAnalysisCallback callback) {

        if (routePoints == null || routePoints.isEmpty()) {
            if (callback != null) {
                new Handler(Looper.getMainLooper()).post(() ->
                        callback.onAnalysisError("No route points provided"));
            }
            return;
        }

        Log.d(TAG, "Starting optimized polyline-based GPX analysis for " + routePoints.size() + " points");

        executor.execute(() -> {
            try {
                PolylineRouteAnalysis analysis = new PolylineRouteAnalysis();
                analysis.analyzedForBikeType = bikeTypeManager.getCurrentBikeType();
                analysis.totalDistance = GpxParser.calculateRouteDistance(routePoints) / 1000.0;
                analysis.hasElevationData = GpxParser.hasElevationData(routePoints);

                // Step 1: Create route segments
                List<RouteSegment> segments = createRouteSegments(routePoints);
                analysis.totalSegmentsAnalyzed = segments.size();

                if (callback != null) postProgress(callback, 10, "Creating route segments...");

                // Step 2: Handle elevation if available
                if (analysis.hasElevationData) {
                    analyzeSlopeData(segments, analysis);
                }

                if (callback != null) postProgress(callback, 20, "Analyzing elevation data...");

                // Step 3: Get overall bounding box for the entire route
                BoundingBox routeBbox = calculateRouteBoundingBox(routePoints);
                Log.d(TAG, "Route bounding box: " + routeBbox.toString());

                if (callback != null) postProgress(callback, 30, "Fetching road data for route area...");

                // Step 4: Get ALL roads in the area using synchronous method (no elevation processing)
                ScoreCalculator scoreCalculator = new ScoreCalculator(bikeTypeManager.getCurrentWeights());
                scoreCalculator.setBikeTypeManager(bikeTypeManager);

                List<PolylineResult> allRoadsInArea;
                try {
                    allRoadsInArea = OverpassServiceSync.fetchDataSync(routeBbox, scoreCalculator);
                    Log.d(TAG, "Found " + allRoadsInArea.size() + " roads in route area (no elevation processing)");
                    analysis.totalRoadsInArea = allRoadsInArea.size();
                } catch (Exception e) {
                    Log.e(TAG, "Failed to fetch road data: " + e.getMessage());
                    if (callback != null) {
                        new Handler(Looper.getMainLooper()).post(() ->
                                callback.onAnalysisError("Failed to fetch road data: " + e.getMessage()));
                    }
                    return;
                }

                if (callback != null) postProgress(callback, 60, "Matching route to roads...");

                // Step 5: Match route segments to roads and identify which roads we actually use
                List<PolylineResult> matchedRoads = new ArrayList<>();
                int matchedSegments = matchRouteSegmentsToRoads(segments, allRoadsInArea, analysis, matchedRoads);

                analysis.segmentsWithRoadData = matchedSegments;
                analysis.dataCoveragePercentage = segments.size() > 0 ?
                        (matchedSegments / (double) segments.size()) * 100.0 : 0.0;

                Log.d(TAG, String.format("Matched %d segments to %d unique roads (%.1f%% coverage)",
                        matchedSegments, matchedRoads.size(), analysis.dataCoveragePercentage));

                // Step 6: Fetch elevation data ONLY for roads that we actually matched to
                if (bikeTypeManager.shouldFetchElevationData() && !matchedRoads.isEmpty()) {

                    if (callback != null) postProgress(callback, 75, "Fetching elevation for " + matchedRoads.size() + " matched roads...");

                    Log.d(TAG, "Fetching elevation data for " + matchedRoads.size() + " matched roads only (not all " + allRoadsInArea.size() + " roads)");

                    // Use a CountDownLatch to wait for elevation processing to complete
                    java.util.concurrent.CountDownLatch elevationLatch = new java.util.concurrent.CountDownLatch(1);
                    java.util.concurrent.atomic.AtomicBoolean elevationSuccess = new java.util.concurrent.atomic.AtomicBoolean(false);

                    ElevationService.addSlopeDataToRoads(matchedRoads, new ElevationService.RoadElevationCallback() {
                        @Override
                        public void onSuccess(List<PolylineResult> updatedRoads) {
                            try {
                                Log.d(TAG, "Recalculating scores with elevation data for " + updatedRoads.size() + " roads");

                                // Recalculate scores with slope data
                                for (PolylineResult road : updatedRoads) {
                                    double maxSlope = road.getMaxSlopePercent();
                                    if (maxSlope >= 0) {
                                        int newScore = scoreCalculator.calculateScoreWithSlope(
                                                road.getTags(), road.getPoints(), maxSlope);
                                        road.setScore(newScore);

                                        if (maxSlope > 12.0) {
                                            Log.d(TAG, String.format("Steep road: %.1f%% slope, score: %d", maxSlope, newScore));
                                        }
                                    }
                                }
                                elevationSuccess.set(true);
                            } finally {
                                elevationLatch.countDown();
                            }
                        }

                        @Override
                        public void onError(String error) {
                            Log.w(TAG, "Elevation fetch failed: " + error + ". Continuing without elevation data.");
                            elevationLatch.countDown();
                        }
                    });

                    // Wait for elevation processing to complete (with timeout)
                    try {
                        boolean finished = elevationLatch.await(30, java.util.concurrent.TimeUnit.SECONDS);
                        if (!finished) {
                            Log.w(TAG, "Elevation processing timed out");
                        }
                    } catch (InterruptedException e) {
                        Log.w(TAG, "Interrupted while waiting for elevation processing", e);
                        Thread.currentThread().interrupt();
                    }
                } else {
                    Log.d(TAG, "Skipping elevation fetch - not needed for current bike type or no matched roads");
                }

                // Step 7: Final classification of segments based on road scores
                classifySegmentsByRoadScores(segments, analysis);

                if (callback != null) postProgress(callback, 90, "Calculating final results...");

                // Step 8: Calculate final metrics
                calculateFinalMetrics(analysis);

                Log.d(TAG, String.format("Analysis complete: %.1f%% green, %.1f%% yellow, %.1f%% red, %.1f%% unknown",
                        analysis.greenPercentage, analysis.yellowPercentage, analysis.redPercentage, analysis.unknownPercentage));

                // Return results
                if (callback != null) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        callback.onProgress(100, "Analysis complete!");
                        callback.onAnalysisComplete(analysis);
                    });
                }

            } catch (Exception e) {
                Log.e(TAG, "Error in optimized polyline-based GPX analysis", e);
                if (callback != null) {
                    new Handler(Looper.getMainLooper()).post(() ->
                            callback.onAnalysisError("Analysis failed: " + e.getMessage()));
                }
            }
        });
    }

    /**
     * Match route segments to roads and return the list of roads that were actually matched
     */
    private static int matchRouteSegmentsToRoads(List<RouteSegment> segments,
                                                 List<PolylineResult> allRoads,
                                                 PolylineRouteAnalysis analysis,
                                                 List<PolylineResult> matchedRoadsOut) {
        int matchedCount = 0;

        for (RouteSegment segment : segments) {
            PolylineResult bestMatch = findBestMatchingRoad(segment, allRoads);

            if (bestMatch != null) {
                segment.matchedRoad = bestMatch;
                matchedCount++;

                // Add to list of matched roads (avoid duplicates)
                if (!matchedRoadsOut.contains(bestMatch)) {
                    matchedRoadsOut.add(bestMatch);
                }
            } else {
                analysis.unknownDistance += segment.distance;
            }
        }

        return matchedCount;
    }

    /**
     * Classify all segments based on their matched road scores
     */
    private static void classifySegmentsByRoadScores(List<RouteSegment> segments, PolylineRouteAnalysis analysis) {
        // Reset counters (unknownDistance was already set during matching)
        analysis.greenDistance = 0;
        analysis.yellowDistance = 0;
        analysis.redDistance = 0;

        for (RouteSegment segment : segments) {
            if (segment.matchedRoad != null) {
                double segmentDistance = segment.distance;
                int score = segment.matchedRoad.getScore();

                if (score >= SCORE_GREEN_THRESHOLD) {
                    analysis.greenDistance += segmentDistance;
                } else if (score >= SCORE_YELLOW_THRESHOLD) {
                    analysis.yellowDistance += segmentDistance;
                } else {
                    analysis.redDistance += segmentDistance;
                }
            }
        }

        Log.d(TAG, String.format("Classification: %.0fm green, %.0fm yellow, %.0fm red, %.0fm unknown",
                analysis.greenDistance, analysis.yellowDistance, analysis.redDistance, analysis.unknownDistance));
    }

    /**
     * Use the same OverpassService as the main app to get all roads in the route area
     */
    // Replace the fetchRoadsForRouteArea method in PolylineBasedGpxEvaluator.java

    /**
     * Use OverpassService to get road data, but WITHOUT elevation data initially
     * We'll fetch elevation only for roads that the route actually matches to
     */
    private static void fetchRoadsForRouteArea(BoundingBox routeBbox,
                                               ScoreCalculator scoreCalculator,
                                               BikeTypeManager bikeTypeManager,
                                               RouteAreaCallback callback) {

        // Use the synchronous version to avoid elevation processing on ALL roads
        try {
            List<PolylineResult> allRoadsInArea = OverpassServiceSync.fetchDataSync(routeBbox, scoreCalculator);
            Log.d(TAG, "Found " + allRoadsInArea.size() + " roads in route area (without elevation data)");

            // Don't fetch elevation for all roads - we'll do it selectively later
            callback.onSuccess(allRoadsInArea);

        } catch (Exception e) {
            Log.e(TAG, "Failed to fetch roads for route area: " + e.getMessage());
            callback.onError("Failed to fetch road data: " + e.getMessage());
        }
    }

    // Add this interface
    private interface RouteAreaCallback {
        void onSuccess(List<PolylineResult> allRoadsInArea);
        void onError(String error);
    }

// Replace the matchRouteToExistingRoads method with this optimized version:

    /**
     * Match route segments to existing roads, then fetch elevation only for matched roads
     */
    private static void matchRouteToExistingRoads(List<RouteSegment> segments,
                                                  List<PolylineResult> allRoads,
                                                  PolylineRouteAnalysis analysis,
                                                  BikeTypeManager bikeTypeManager,
                                                  PolylineRouteAnalysisCallback callback) {

        Log.d(TAG, "Phase 1: Matching route segments to roads (without elevation)");

        int matchedSegments = 0;
        List<PolylineResult> matchedRoads = new ArrayList<>();
        Map<PolylineResult, Double> roadDistances = new HashMap<>();

        // Phase 1: Match segments to roads without considering elevation
        for (RouteSegment segment : segments) {
            PolylineResult bestMatch = findBestMatchingRoad(segment, allRoads);

            double segmentDistance = segment.distance;

            if (bestMatch != null) {
                segment.matchedRoad = bestMatch;
                matchedSegments++;

                // Track which roads we actually use
                if (!matchedRoads.contains(bestMatch)) {
                    matchedRoads.add(bestMatch);
                }

                // Track total distance per road for accurate scoring
                roadDistances.put(bestMatch, roadDistances.getOrDefault(bestMatch, 0.0) + segmentDistance);

            } else {
                analysis.unknownDistance += segmentDistance;
            }
        }

        analysis.segmentsWithRoadData = matchedSegments;
        analysis.dataCoveragePercentage = segments.size() > 0 ?
                (matchedSegments / (double) segments.size()) * 100.0 : 0.0;

        Log.d(TAG, String.format("Phase 1 complete: %d segments matched to %d unique roads (%.1f%% coverage)",
                matchedSegments, matchedRoads.size(), analysis.dataCoveragePercentage));

        // Phase 2: Fetch elevation data ONLY for roads that were actually matched
        if (bikeTypeManager.shouldFetchElevationData() && !matchedRoads.isEmpty()) {

            if (callback != null) {
                new Handler(Looper.getMainLooper()).post(() ->
                        callback.onProgress(75, "Fetching elevation for " + matchedRoads.size() + " matched roads...")
                );
            }

            Log.d(TAG, "Phase 2: Fetching elevation data for " + matchedRoads.size() + " matched roads only");

            ElevationService.addSlopeDataToRoads(matchedRoads, new ElevationService.RoadElevationCallback() {
                @Override
                public void onSuccess(List<PolylineResult> updatedRoads) {
                    Log.d(TAG, "Phase 3: Recalculating scores with elevation data");

                    // Recalculate scores for roads that now have elevation data
                    ScoreCalculator scoreCalculator = new ScoreCalculator(bikeTypeManager.getCurrentWeights());
                    scoreCalculator.setBikeTypeManager(bikeTypeManager);

                    for (PolylineResult road : updatedRoads) {
                        double maxSlope = road.getMaxSlopePercent();
                        if (maxSlope >= 0) {
                            int newScore = scoreCalculator.calculateScoreWithSlope(
                                    road.getTags(), road.getPoints(), maxSlope);
                            road.setScore(newScore);

                            Log.d(TAG, String.format("Road with %.1f%% slope got score %d", maxSlope, newScore));
                        }
                    }

                    // Phase 4: Final classification with accurate scores
                    classifyMatchedSegments(segments, roadDistances, analysis);

                    // Continue with completion
                    completeAnalysis(analysis, callback);
                }

                @Override
                public void onError(String error) {
                    Log.w(TAG, "Elevation fetch failed: " + error + ". Continuing without elevation data.");

                    // Continue without elevation data
                    classifyMatchedSegments(segments, roadDistances, analysis);
                    completeAnalysis(analysis, callback);
                }
            });

        } else {
            Log.d(TAG, "Skipping elevation fetch - not needed for current bike type or no matched roads");

            // Classify without elevation data
            classifyMatchedSegments(segments, roadDistances, analysis);
            completeAnalysis(analysis, callback);
        }
    }

    /**
     * Classify segments based on their matched road scores
     */
    private static void classifyMatchedSegments(List<RouteSegment> segments,
                                                Map<PolylineResult, Double> roadDistances,
                                                PolylineRouteAnalysis analysis) {

        // Reset distance counters (they may have been set in Phase 1)
        analysis.greenDistance = 0;
        analysis.yellowDistance = 0;
        analysis.redDistance = 0;
        // unknownDistance was already set correctly

        for (RouteSegment segment : segments) {
            if (segment.matchedRoad != null) {
                double segmentDistance = segment.distance;
                int score = segment.matchedRoad.getScore();

                if (score >= SCORE_GREEN_THRESHOLD) {
                    analysis.greenDistance += segmentDistance;
                } else if (score >= SCORE_YELLOW_THRESHOLD) {
                    analysis.yellowDistance += segmentDistance;
                } else {
                    analysis.redDistance += segmentDistance;
                }
            }
        }

        Log.d(TAG, String.format("Final classification: %.1fm green, %.1fm yellow, %.1fm red, %.1fm unknown",
                analysis.greenDistance, analysis.yellowDistance, analysis.redDistance, analysis.unknownDistance));
    }

    /**
     * Complete the analysis and call the callback
     */
    private static void completeAnalysis(PolylineRouteAnalysis analysis, PolylineRouteAnalysisCallback callback) {
        calculateFinalMetrics(analysis);

        new Handler(Looper.getMainLooper()).post(() -> {
            if (callback != null) {
                callback.onProgress(100, "Analysis complete!");
                callback.onAnalysisComplete(analysis);
            }
        });
    }

    /**
     * Find the best matching road for a route segment
     * Uses more sophisticated matching than the previous approach
     */
    private static PolylineResult findBestMatchingRoad(RouteSegment segment, List<PolylineResult> allRoads) {
        if (allRoads.isEmpty()) return null;

        PolylineResult bestMatch = null;
        double bestScore = Double.MAX_VALUE; // Lower is better for distance-based scoring

        GeoPoint segmentMidpoint = new GeoPoint(
                (segment.startPoint.getLatitude() + segment.endPoint.getLatitude()) / 2.0,
                (segment.startPoint.getLongitude() + segment.endPoint.getLongitude()) / 2.0
        );

        for (PolylineResult road : allRoads) {
            // Check distance to the road
            double minDistanceToRoad = Double.MAX_VALUE;

            List<GeoPoint> roadPoints = road.getPoints();
            for (int i = 0; i < roadPoints.size() - 1; i++) {
                GeoPoint roadStart = roadPoints.get(i);
                GeoPoint roadEnd = roadPoints.get(i + 1);

                // Find closest point on this road segment to our route segment
                GeoPoint closestPoint = getClosestPointOnSegment(segmentMidpoint, roadStart, roadEnd);
                double distance = segmentMidpoint.distanceToAsDouble(closestPoint);

                if (distance < minDistanceToRoad) {
                    minDistanceToRoad = distance;
                }
            }

            // Consider this road if it's within reasonable distance
            if (minDistanceToRoad < 150.0 && minDistanceToRoad < bestScore) { // 150m max distance
                bestScore = minDistanceToRoad;
                bestMatch = road;
            }
        }

        return bestMatch;
    }

    /**
     * Get the closest point on a line segment to a given point
     */
    private static GeoPoint getClosestPointOnSegment(GeoPoint point, GeoPoint segmentStart, GeoPoint segmentEnd) {
        double lat1 = segmentStart.getLatitude();
        double lon1 = segmentStart.getLongitude();
        double lat2 = segmentEnd.getLatitude();
        double lon2 = segmentEnd.getLongitude();
        double lat = point.getLatitude();
        double lon = point.getLongitude();

        double dx = lon2 - lon1;
        double dy = lat2 - lat1;

        if (dx == 0 && dy == 0) {
            return segmentStart; // Degenerate segment
        }

        double t = ((lon - lon1) * dx + (lat - lat1) * dy) / (dx * dx + dy * dy);
        t = Math.max(0, Math.min(1, t)); // Clamp to [0, 1]

        return new GeoPoint(lat1 + t * dy, lon1 + t * dx);
    }

    // Helper methods (same as before but simpler)
    private static void postProgress(PolylineRouteAnalysisCallback callback, int progress, String message) {
        new Handler(Looper.getMainLooper()).post(() -> callback.onProgress(progress, message));
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

    private static BoundingBox calculateRouteBoundingBox(List<GeoPoint> routePoints) {
        double minLat = Double.MAX_VALUE, maxLat = Double.MIN_VALUE;
        double minLon = Double.MAX_VALUE, maxLon = Double.MIN_VALUE;

        for (GeoPoint point : routePoints) {
            double lat = point.getLatitude();
            double lon = point.getLongitude();

            minLat = Math.min(minLat, lat);
            maxLat = Math.max(maxLat, lat);
            minLon = Math.min(minLon, lon);
            maxLon = Math.max(maxLon, lon);
        }

        // Add buffer
        double buffer = 0.005; // ~500m buffer
        return new BoundingBox(maxLat + buffer, maxLon + buffer, minLat - buffer, minLon - buffer);
    }

    private static void analyzeSlopeData(List<RouteSegment> segments, PolylineRouteAnalysis analysis) {
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

    private static void calculateFinalMetrics(PolylineRouteAnalysis analysis) {
        // Convert from meters to kilometers
        analysis.greenDistance = analysis.greenDistance / 1000.0;
        analysis.yellowDistance = analysis.yellowDistance / 1000.0;
        analysis.redDistance = analysis.redDistance / 1000.0;
        analysis.unknownDistance = analysis.unknownDistance / 1000.0;

        // Calculate percentages
        analysis.calculatePercentages();
    }
}