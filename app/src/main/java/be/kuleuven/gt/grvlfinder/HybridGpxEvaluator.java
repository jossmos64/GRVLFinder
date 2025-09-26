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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Hybrid GPX Evaluator that combines the speed of MemoryOptimizedGpxEvaluator
 * with the accuracy of PolylineBasedGpxEvaluator
 */
public class HybridGpxEvaluator {
    private static final String TAG = "HybridGpxEvaluator";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    // Optimized parameters for speed vs accuracy balance
    private static final double MAX_CHUNK_SIZE_KM = 3.0; // Slightly larger chunks than memory-optimized
    private static final double SEGMENT_LENGTH_METERS = 75.0; // Compromise between 50m and 100m
    private static final double BUFFER_DEGREES = 0.008; // Smaller buffer for less data
    private static final double MAX_ROAD_MATCH_DISTANCE = 100.0; // Max distance to consider road match

    // Score thresholds - same as main app
    private static final int SCORE_GREEN_THRESHOLD = 20;
    private static final int SCORE_YELLOW_THRESHOLD = 10;

    public static class HybridRouteAnalysis {
        // Distance breakdowns in kilometers
        public double totalDistance;
        public double greenDistance;
        public double yellowDistance;
        public double redDistance;
        public double unknownDistance;

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
        public int totalRoadsInArea;

        // Surface breakdown for detailed analysis
        public Map<String, Double> surfaceBreakdown;

        public HybridRouteAnalysis() {
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

        public double getGravelPercentage() {
            return greenPercentage + yellowPercentage; // Good + decent roads for gravel
        }

        public double getAsphaltPercentage() {
            // For asphalt, we consider only the best roads (green)
            return greenPercentage;
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

        public double getDataCoveragePercentage() {
            return dataCoveragePercentage;
        }
    }

    public interface HybridRouteAnalysisCallback {
        void onAnalysisComplete(HybridRouteAnalysis analysis);
        void onAnalysisError(String error);
        void onProgress(int progress, String message);
    }

    private static class RouteSegment {
        GeoPoint startPoint;
        GeoPoint endPoint;
        double distance;
        double slope = -1;
        PolylineResult matchedRoad = null;

        RouteSegment(GeoPoint start, GeoPoint end) {
            this.startPoint = start;
            this.endPoint = end;
            this.distance = start.distanceToAsDouble(end);
        }
    }

    private static class OptimizedRouteChunk {
        List<RouteSegment> segments;
        BoundingBox boundingBox;
        int chunkIndex;
        List<PolylineResult> chunkRoads; // Cache roads for this chunk

        OptimizedRouteChunk(List<RouteSegment> segments, BoundingBox boundingBox, int chunkIndex) {
            this.segments = segments;
            this.boundingBox = boundingBox;
            this.chunkIndex = chunkIndex;
            this.chunkRoads = new ArrayList<>();
        }
    }

    /**
     * Main analysis method - hybrid approach for speed and accuracy
     */
    public static void analyzeGpxRouteHybrid(List<GeoPoint> routePoints,
                                             BikeTypeManager bikeTypeManager,
                                             HybridRouteAnalysisCallback callback) {

        if (routePoints == null || routePoints.isEmpty()) {
            if (callback != null) {
                new Handler(Looper.getMainLooper()).post(() ->
                        callback.onAnalysisError("No route points provided"));
            }
            return;
        }

        Log.d(TAG, "Starting hybrid GPX analysis for " + routePoints.size() + " points");

        executor.execute(() -> {
            try {
                HybridRouteAnalysis analysis = new HybridRouteAnalysis();
                analysis.analyzedForBikeType = bikeTypeManager.getCurrentBikeType();
                analysis.totalDistance = GpxParser.calculateRouteDistance(routePoints) / 1000.0;
                analysis.hasElevationData = GpxParser.hasElevationData(routePoints);

                if (callback != null) postProgress(callback, 5, "Preparing hybrid analysis...");

                // Step 1: Create segments (optimized size)
                List<RouteSegment> allSegments = createRouteSegments(routePoints);
                analysis.totalSegmentsAnalyzed = allSegments.size();

                if (callback != null) postProgress(callback, 10, "Creating optimized route chunks...");

                // Step 2: Create smart chunks with overlapping boundaries for better accuracy
                List<OptimizedRouteChunk> chunks = createSmartRouteChunks(allSegments);
                Log.d(TAG, "Created " + chunks.size() + " smart chunks for analysis");

                if (callback != null) postProgress(callback, 15, "Processing " + chunks.size() + " route chunks...");

                // Step 3: Process chunks with intelligent road matching
                ScoreCalculator scoreCalculator = new ScoreCalculator(bikeTypeManager.getCurrentWeights());
                scoreCalculator.setBikeTypeManager(bikeTypeManager);

                processChunksWithIntelligentMatching(chunks, analysis, scoreCalculator, bikeTypeManager, callback);

                // Step 4: Handle elevation efficiently
                if (analysis.hasElevationData) {
                    analyzeExistingElevationData(allSegments, analysis);
                } else if (bikeTypeManager.shouldFetchElevationData()) {
                    fetchElevationForMatchedRoadsOnly(chunks, analysis, callback);
                }

                // Step 5: Calculate final metrics
                calculateFinalMetrics(analysis);

                Log.d(TAG, String.format("Hybrid analysis complete: %.1f%% green, %.1f%% yellow, %.1f%% red, %.1f%% unknown",
                        analysis.greenPercentage, analysis.yellowPercentage,
                        analysis.redPercentage, analysis.unknownPercentage));

                if (callback != null) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        callback.onProgress(100, "Hybrid analysis complete!");
                        callback.onAnalysisComplete(analysis);
                    });
                }

            } catch (Exception e) {
                Log.e(TAG, "Error in hybrid GPX analysis", e);
                if (callback != null) {
                    new Handler(Looper.getMainLooper()).post(() ->
                            callback.onAnalysisError("Hybrid analysis failed: " + e.getMessage()));
                }
            }
        });
    }

    /**
     * Create smart chunks with overlapping boundaries for better road matching accuracy
     */
    private static List<OptimizedRouteChunk> createSmartRouteChunks(List<RouteSegment> segments) {
        List<OptimizedRouteChunk> chunks = new ArrayList<>();

        double maxChunkDistanceMeters = MAX_CHUNK_SIZE_KM * 1000;
        double currentChunkDistance = 0.0;
        List<RouteSegment> currentChunkSegments = new ArrayList<>();
        int chunkIndex = 0;

        for (int i = 0; i < segments.size(); i++) {
            RouteSegment segment = segments.get(i);
            currentChunkSegments.add(segment);
            currentChunkDistance += segment.distance;

            // Create chunk when size limit reached or at end
            if (currentChunkDistance >= maxChunkDistanceMeters ||
                    i == segments.size() - 1 ||
                    currentChunkSegments.size() >= 25) { // Limit segment count per chunk

                BoundingBox chunkBbox = calculateSmartChunkBoundingBox(currentChunkSegments);
                chunks.add(new OptimizedRouteChunk(new ArrayList<>(currentChunkSegments), chunkBbox, chunkIndex++));

                // Smart overlap: keep last 2 segments for next chunk to improve continuity
                if (i < segments.size() - 1 && currentChunkSegments.size() > 2) {
                    List<RouteSegment> overlapSegments = currentChunkSegments.subList(
                            Math.max(0, currentChunkSegments.size() - 2),
                            currentChunkSegments.size());
                    currentChunkSegments = new ArrayList<>(overlapSegments);
                    currentChunkDistance = 0.0;
                    for (RouteSegment seg : overlapSegments) {
                        currentChunkDistance += seg.distance;
                    }
                } else {
                    currentChunkSegments.clear();
                    currentChunkDistance = 0.0;
                }
            }
        }

        return chunks;
    }

    /**
     * Process chunks with intelligent matching to balance speed and accuracy
     */
    private static void processChunksWithIntelligentMatching(List<OptimizedRouteChunk> chunks,
                                                             HybridRouteAnalysis analysis,
                                                             ScoreCalculator scoreCalculator,
                                                             BikeTypeManager bikeTypeManager,
                                                             HybridRouteAnalysisCallback callback) throws Exception {

        int processedChunks = 0;
        Map<String, List<PolylineResult>> roadCache = new HashMap<>(); // Cache roads between nearby chunks

        for (OptimizedRouteChunk chunk : chunks) {
            try {
                // Check if we can reuse roads from cache (for nearby chunks)
                String bboxKey = getBboxKey(chunk.boundingBox);
                List<PolylineResult> cachedRoads = findNearbyRoadsInCache(roadCache, chunk.boundingBox);

                if (cachedRoads != null && !cachedRoads.isEmpty()) {
                    Log.d(TAG, "Reusing " + cachedRoads.size() + " roads from cache for chunk " + chunk.chunkIndex);
                    chunk.chunkRoads = cachedRoads;
                } else {
                    // Fetch roads for this chunk
                    chunk.chunkRoads = OverpassServiceSync.fetchDataSync(chunk.boundingBox, scoreCalculator);
                    roadCache.put(bboxKey, new ArrayList<>(chunk.chunkRoads));
                    Log.d(TAG, "Fetched " + chunk.chunkRoads.size() + " roads for chunk " + chunk.chunkIndex);
                }

                analysis.totalRoadsInArea += chunk.chunkRoads.size();

                // Match segments to roads with improved algorithm
                matchSegmentsWithImprovedAccuracy(chunk.segments, chunk.chunkRoads, analysis);

                processedChunks++;

                // Memory cleanup
                if (roadCache.size() > 5) {
                    roadCache.clear(); // Clear cache if it gets too large
                }
                System.gc();

                if (callback != null) {
                    int progress = 20 + (processedChunks * 60 / chunks.size());
                    postProgress(callback, progress,
                            "Processed chunk " + processedChunks + "/" + chunks.size());
                }

                // Small delay to prevent API overload
                Thread.sleep(150);

            } catch (Exception e) {
                Log.w(TAG, "Error processing chunk " + chunk.chunkIndex + ": " + e.getMessage());
                // Mark segments as unknown and continue
                for (RouteSegment segment : chunk.segments) {
                    analysis.unknownDistance += segment.distance;
                    addSurfaceBreakdown(analysis, "processing_error", segment.distance);
                }
            }
        }
    }

    /**
     * Improved segment matching that balances accuracy with performance
     */
    private static void matchSegmentsWithImprovedAccuracy(List<RouteSegment> segments,
                                                          List<PolylineResult> roads,
                                                          HybridRouteAnalysis analysis) {

        for (RouteSegment segment : segments) {
            PolylineResult bestMatch = findBestRoadMatchHybrid(segment, roads);
            double segmentDistance = segment.distance;

            if (bestMatch != null) {
                segment.matchedRoad = bestMatch;
                analysis.segmentsWithRoadData++;

                int score = bestMatch.getScore();
                String surface = bestMatch.getTags().get("surface");

                // Classify by score
                if (score >= SCORE_GREEN_THRESHOLD) {
                    analysis.greenDistance += segmentDistance;
                    addSurfaceBreakdown(analysis, "excellent_roads", segmentDistance);
                } else if (score >= SCORE_YELLOW_THRESHOLD) {
                    analysis.yellowDistance += segmentDistance;
                    addSurfaceBreakdown(analysis, "decent_roads", segmentDistance);
                } else {
                    analysis.redDistance += segmentDistance;
                    addSurfaceBreakdown(analysis, "poor_roads", segmentDistance);
                }

                // Also track specific surface types
                if (surface != null && !surface.trim().isEmpty()) {
                    addSurfaceBreakdown(analysis, surface.toLowerCase(), segmentDistance);
                }

            } else {
                analysis.unknownDistance += segmentDistance;
                addSurfaceBreakdown(analysis, "no_road_data", segmentDistance);
            }
        }
    }

    /**
     * Hybrid road matching algorithm - more accurate than simple distance, faster than full geometric analysis
     */
    private static PolylineResult findBestRoadMatchHybrid(RouteSegment segment, List<PolylineResult> roads) {
        if (roads.isEmpty()) return null;

        PolylineResult bestMatch = null;
        double bestScore = Double.MAX_VALUE;

        // Use segment midpoint for initial filtering
        GeoPoint segmentMidpoint = new GeoPoint(
                (segment.startPoint.getLatitude() + segment.endPoint.getLatitude()) / 2.0,
                (segment.startPoint.getLongitude() + segment.endPoint.getLongitude()) / 2.0
        );

        // Pre-filter roads by approximate distance for performance
        List<PolylineResult> nearbyRoads = new ArrayList<>();
        for (PolylineResult road : roads) {
            if (!road.getPoints().isEmpty()) {
                double approxDistance = segmentMidpoint.distanceToAsDouble(road.getPoints().get(0));
                if (approxDistance < MAX_ROAD_MATCH_DISTANCE * 2) { // Pre-filter with larger radius
                    nearbyRoads.add(road);
                }
            }
        }

        // More accurate matching on nearby roads only
        for (PolylineResult road : nearbyRoads) {
            double minDistanceToRoad = findMinDistanceToRoad(segmentMidpoint, road.getPoints());

            if (minDistanceToRoad < MAX_ROAD_MATCH_DISTANCE && minDistanceToRoad < bestScore) {
                bestScore = minDistanceToRoad;
                bestMatch = road;
            }
        }

        return bestMatch;
    }

    /**
     * Efficiently find minimum distance from point to road polyline
     */
    private static double findMinDistanceToRoad(GeoPoint point, List<GeoPoint> roadPoints) {
        double minDistance = Double.MAX_VALUE;

        for (int i = 0; i < roadPoints.size() - 1; i++) {
            GeoPoint roadStart = roadPoints.get(i);
            GeoPoint roadEnd = roadPoints.get(i + 1);

            // Use simple point-to-segment distance calculation
            double distance = distanceToSegment(point, roadStart, roadEnd);
            if (distance < minDistance) {
                minDistance = distance;
            }

            // Early termination if very close
            if (minDistance < 20.0) break;
        }

        return minDistance;
    }

    /**
     * Fast point-to-line-segment distance calculation
     */
    private static double distanceToSegment(GeoPoint point, GeoPoint segStart, GeoPoint segEnd) {
        double A = point.getLatitude() - segStart.getLatitude();
        double B = point.getLongitude() - segStart.getLongitude();
        double C = segEnd.getLatitude() - segStart.getLatitude();
        double D = segEnd.getLongitude() - segStart.getLongitude();

        double dot = A * C + B * D;
        double lenSq = C * C + D * D;

        if (lenSq == 0) {
            // Degenerate segment, return distance to start point
            return point.distanceToAsDouble(segStart);
        }

        double param = dot / lenSq;

        GeoPoint closestPoint;
        if (param < 0) {
            closestPoint = segStart;
        } else if (param > 1) {
            closestPoint = segEnd;
        } else {
            closestPoint = new GeoPoint(
                    segStart.getLatitude() + param * C,
                    segStart.getLongitude() + param * D
            );
        }

        return point.distanceToAsDouble(closestPoint);
    }

    /**
     * Fetch elevation only for roads that were actually matched (like PolylineBasedGpxEvaluator)
     */
    private static void fetchElevationForMatchedRoadsOnly(List<OptimizedRouteChunk> chunks,
                                                          HybridRouteAnalysis analysis,
                                                          HybridRouteAnalysisCallback callback) {

        // Collect all unique matched roads
        List<PolylineResult> matchedRoads = new ArrayList<>();
        for (OptimizedRouteChunk chunk : chunks) {
            for (RouteSegment segment : chunk.segments) {
                if (segment.matchedRoad != null && !matchedRoads.contains(segment.matchedRoad)) {
                    matchedRoads.add(segment.matchedRoad);
                }
            }
        }

        if (matchedRoads.isEmpty()) {
            Log.d(TAG, "No matched roads for elevation processing");
            return;
        }

        Log.d(TAG, "Fetching elevation for " + matchedRoads.size() + " matched roads");

        if (callback != null) postProgress(callback, 80, "Fetching elevation for " + matchedRoads.size() + " matched roads...");

        CountDownLatch elevationLatch = new CountDownLatch(1);
        AtomicBoolean success = new AtomicBoolean(false);

        ElevationService.addSlopeDataToRoads(matchedRoads, new ElevationService.RoadElevationCallback() {
            @Override
            public void onSuccess(List<PolylineResult> updatedRoads) {
                try {
                    // Recalculate scores with elevation data
                    ScoreCalculator scoreCalculator = new ScoreCalculator(new HashMap<>());
                    for (PolylineResult road : updatedRoads) {
                        double maxSlope = road.getMaxSlopePercent();
                        if (maxSlope >= 0) {
                            int newScore = scoreCalculator.calculateScoreWithSlope(
                                    road.getTags(), road.getPoints(), maxSlope);
                            road.setScore(newScore);
                        }
                    }

                    // Re-classify all segments with new scores
                    reclassifySegmentsWithNewScores(chunks, analysis);

                    success.set(true);
                } finally {
                    elevationLatch.countDown();
                }
            }

            @Override
            public void onError(String error) {
                Log.w(TAG, "Elevation fetch failed: " + error);
                elevationLatch.countDown();
            }
        });

        try {
            elevationLatch.await(20, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Re-classify segments after elevation data has been processed
     */
    private static void reclassifySegmentsWithNewScores(List<OptimizedRouteChunk> chunks, HybridRouteAnalysis analysis) {
        // Reset distance counters
        analysis.greenDistance = 0;
        analysis.yellowDistance = 0;
        analysis.redDistance = 0;
        // Keep unknownDistance as is

        // Clear and rebuild surface breakdown
        analysis.surfaceBreakdown.clear();

        for (OptimizedRouteChunk chunk : chunks) {
            for (RouteSegment segment : chunk.segments) {
                double segmentDistance = segment.distance;

                if (segment.matchedRoad != null) {
                    int score = segment.matchedRoad.getScore();
                    String surface = segment.matchedRoad.getTags().get("surface");

                    if (score >= SCORE_GREEN_THRESHOLD) {
                        analysis.greenDistance += segmentDistance;
                        addSurfaceBreakdown(analysis, "excellent_roads", segmentDistance);
                    } else if (score >= SCORE_YELLOW_THRESHOLD) {
                        analysis.yellowDistance += segmentDistance;
                        addSurfaceBreakdown(analysis, "decent_roads", segmentDistance);
                    } else {
                        analysis.redDistance += segmentDistance;
                        addSurfaceBreakdown(analysis, "poor_roads", segmentDistance);
                    }

                    if (surface != null && !surface.trim().isEmpty()) {
                        addSurfaceBreakdown(analysis, surface.toLowerCase(), segmentDistance);
                    }
                } else {
                    addSurfaceBreakdown(analysis, "no_road_data", segmentDistance);
                }
            }
        }
    }

    // Helper methods
    private static void postProgress(HybridRouteAnalysisCallback callback, int progress, String message) {
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

    private static BoundingBox calculateSmartChunkBoundingBox(List<RouteSegment> segments) {
        double minLat = Double.MAX_VALUE, maxLat = Double.MIN_VALUE;
        double minLon = Double.MAX_VALUE, maxLon = Double.MIN_VALUE;

        for (RouteSegment segment : segments) {
            // Check both start and end points
            double[] lats = {segment.startPoint.getLatitude(), segment.endPoint.getLatitude()};
            double[] lons = {segment.startPoint.getLongitude(), segment.endPoint.getLongitude()};

            for (double lat : lats) {
                minLat = Math.min(minLat, lat);
                maxLat = Math.max(maxLat, lat);
            }
            for (double lon : lons) {
                minLon = Math.min(minLon, lon);
                maxLon = Math.max(maxLon, lon);
            }
        }

        return new BoundingBox(maxLat + BUFFER_DEGREES, maxLon + BUFFER_DEGREES,
                minLat - BUFFER_DEGREES, minLon - BUFFER_DEGREES);
    }

    private static void analyzeExistingElevationData(List<RouteSegment> segments, HybridRouteAnalysis analysis) {
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

    private static void addSurfaceBreakdown(HybridRouteAnalysis analysis, String key, double meters) {
        analysis.surfaceBreakdown.put(key, analysis.surfaceBreakdown.getOrDefault(key, 0.0) + meters);
    }

    private static void calculateFinalMetrics(HybridRouteAnalysis analysis) {
        // Convert from meters to kilometers
        analysis.greenDistance = analysis.greenDistance / 1000.0;
        analysis.yellowDistance = analysis.yellowDistance / 1000.0;
        analysis.redDistance = analysis.redDistance / 1000.0;
        analysis.unknownDistance = analysis.unknownDistance / 1000.0;

        // Calculate percentages
        analysis.calculatePercentages();

        // Calculate data coverage
        analysis.dataCoveragePercentage = analysis.totalSegmentsAnalyzed > 0 ?
                (analysis.segmentsWithRoadData / (double) analysis.totalSegmentsAnalyzed) * 100.0 : 0.0;

        // Convert surface breakdown to kilometers
        Map<String, Double> kmBreakdown = new HashMap<>();
        for (Map.Entry<String, Double> e : analysis.surfaceBreakdown.entrySet()) {
            kmBreakdown.put(e.getKey(), e.getValue() / 1000.0);
        }
        analysis.surfaceBreakdown = kmBreakdown;
    }

    // Cache-related helper methods
    private static String getBboxKey(BoundingBox bbox) {
        return String.format(Locale.US, "%.4f_%.4f_%.4f_%.4f",
                bbox.getLatSouth(), bbox.getLonWest(), bbox.getLatNorth(), bbox.getLonEast());
    }

    private static List<PolylineResult> findNearbyRoadsInCache(Map<String, List<PolylineResult>> roadCache, BoundingBox bbox) {
        // Simple cache lookup - could be made more sophisticated
        for (Map.Entry<String, List<PolylineResult>> entry : roadCache.entrySet()) {
            // If we have any cached roads, we can potentially reuse them
            // This is a simplified version - in practice you'd want to check bbox overlap
            if (!entry.getValue().isEmpty()) {
                return entry.getValue();
            }
        }
        return null;
    }
}