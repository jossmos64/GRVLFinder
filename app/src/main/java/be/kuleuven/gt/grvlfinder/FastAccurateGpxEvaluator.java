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
 * Fast and Accurate GPX Evaluator that combines the best of all approaches:
 * - Memory efficient chunking like MemoryOptimizedGpxEvaluator
 * - Accurate road matching like PolylineBasedGpxEvaluator
 * - Smart optimizations like HybridGpxEvaluator
 * - Progressive elevation fetching only for matched roads
 */
public class FastAccurateGpxEvaluator {
    private static final String TAG = "FastAccurateGpxEvaluator";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    // Optimized parameters for memory vs accuracy balance
    private static final double MAX_CHUNK_SIZE_KM = 1.5; // Smaller chunks for memory safety
    private static final double SEGMENT_LENGTH_METERS = 75.0; // Good accuracy vs performance balance
    private static final double BUFFER_DEGREES = 0.006; // Smaller buffer = less data
    private static final double MAX_ROAD_MATCH_DISTANCE = 80.0; // Tighter matching for accuracy
    private static final int MAX_ROADS_PER_CHUNK = 150; // Memory limit per chunk

    // Score thresholds - same as main app
    private static final int SCORE_GREEN_THRESHOLD = 20;
    private static final int SCORE_YELLOW_THRESHOLD = 10;

    public static class FastAccurateRouteAnalysis {
        public double totalDistance;
        public double greenDistance;
        public double yellowDistance;
        public double redDistance;
        public double unknownDistance;

        public double greenPercentage;
        public double yellowPercentage;
        public double redPercentage;
        public double unknownPercentage;

        public double maxSlope;
        public GeoPoint steepestPoint;
        public String steepestLocationDescription;
        public boolean hasElevationData;

        public BikeType analyzedForBikeType;
        public int totalSegmentsAnalyzed;
        public int segmentsWithRoadData;
        public double dataCoveragePercentage;
        public int totalRoadsInArea;
        public int totalRoadsMatched; // New: tracks unique roads actually used

        public Map<String, Double> surfaceBreakdown;

        public FastAccurateRouteAnalysis() {
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

    public interface FastAccurateAnalysisCallback {
        void onAnalysisComplete(FastAccurateRouteAnalysis analysis);
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

    private static class SmartRouteChunk {
        List<RouteSegment> segments;
        BoundingBox boundingBox;
        int chunkIndex;
        List<PolylineResult> chunkRoads;
        List<PolylineResult> matchedRoads; // Only roads actually matched to segments

        SmartRouteChunk(List<RouteSegment> segments, BoundingBox boundingBox, int chunkIndex) {
            this.segments = segments;
            this.boundingBox = boundingBox;
            this.chunkIndex = chunkIndex;
            this.chunkRoads = new ArrayList<>();
            this.matchedRoads = new ArrayList<>();
        }
    }

    /**
     * Main analysis method - fast, accurate, and memory efficient
     */
    public static void analyzeGpxRouteFastAccurate(List<GeoPoint> routePoints,
                                                   BikeTypeManager bikeTypeManager,
                                                   FastAccurateAnalysisCallback callback) {

        if (routePoints == null || routePoints.isEmpty()) {
            if (callback != null) {
                new Handler(Looper.getMainLooper()).post(() ->
                        callback.onAnalysisError("No route points provided"));
            }
            return;
        }

        Log.d(TAG, "Starting fast accurate GPX analysis for " + routePoints.size() + " points");

        executor.execute(() -> {
            try {
                FastAccurateRouteAnalysis analysis = new FastAccurateRouteAnalysis();
                analysis.analyzedForBikeType = bikeTypeManager.getCurrentBikeType();
                analysis.totalDistance = GpxParser.calculateRouteDistance(routePoints) / 1000.0;
                analysis.hasElevationData = GpxParser.hasElevationData(routePoints);

                if (callback != null) postProgress(callback, 5, "Preparing fast accurate analysis...");

                // Step 1: Create segments
                List<RouteSegment> allSegments = createRouteSegments(routePoints);
                analysis.totalSegmentsAnalyzed = allSegments.size();

                if (callback != null) postProgress(callback, 10, "Creating smart route chunks...");

                // Step 2: Create smart overlapping chunks for better accuracy
                List<SmartRouteChunk> chunks = createSmartOverlappingChunks(allSegments);
                Log.d(TAG, "Created " + chunks.size() + " smart chunks");

                if (callback != null) postProgress(callback, 15, "Processing " + chunks.size() + " chunks with accurate matching...");

                // Step 3: Process chunks with accurate road matching and memory management
                ScoreCalculator scoreCalculator = new ScoreCalculator(bikeTypeManager.getCurrentWeights());
                scoreCalculator.setBikeTypeManager(bikeTypeManager);

                List<PolylineResult> allMatchedRoads = processChunksWithAccurateMatching(
                        chunks, analysis, scoreCalculator, callback);

                // Step 4: Handle elevation efficiently - only for matched roads
                if (bikeTypeManager.shouldFetchElevationData() && !allMatchedRoads.isEmpty()) {
                    if (callback != null) postProgress(callback, 80,
                            "Fetching elevation for " + allMatchedRoads.size() + " matched roads...");

                    fetchElevationForMatchedRoadsOnly(allMatchedRoads, chunks, analysis,
                            scoreCalculator, callback);
                } else if (analysis.hasElevationData) {
                    analyzeExistingElevationData(allSegments, analysis);
                }

                // Step 5: Calculate final metrics
                calculateFinalMetrics(analysis);

                Log.d(TAG, String.format("Fast accurate analysis complete: %.1f%% green, %.1f%% yellow, %.1f%% red, %.1f%% unknown (%.1f%% coverage)",
                        analysis.greenPercentage, analysis.yellowPercentage,
                        analysis.redPercentage, analysis.unknownPercentage, analysis.dataCoveragePercentage));

                if (callback != null) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        callback.onProgress(100, "Fast accurate analysis complete!");
                        callback.onAnalysisComplete(analysis);
                    });
                }

            } catch (Exception e) {
                Log.e(TAG, "Error in fast accurate GPX analysis", e);
                if (callback != null) {
                    new Handler(Looper.getMainLooper()).post(() ->
                            callback.onAnalysisError("Analysis failed: " + e.getMessage()));
                }
            }
        });
    }

    /**
     * Create overlapping chunks for better road matching accuracy at boundaries
     */
    private static List<SmartRouteChunk> createSmartOverlappingChunks(List<RouteSegment> segments) {
        List<SmartRouteChunk> chunks = new ArrayList<>();

        double maxChunkDistanceMeters = MAX_CHUNK_SIZE_KM * 1000;
        double currentChunkDistance = 0.0;
        List<RouteSegment> currentChunkSegments = new ArrayList<>();
        int chunkIndex = 0;

        for (int i = 0; i < segments.size(); i++) {
            RouteSegment segment = segments.get(i);
            currentChunkSegments.add(segment);
            currentChunkDistance += segment.distance;

            // Create chunk when size limit reached
            if (currentChunkDistance >= maxChunkDistanceMeters ||
                    currentChunkSegments.size() >= 15 || // Limit segments per chunk
                    i == segments.size() - 1) {

                BoundingBox chunkBbox = calculateSmartChunkBoundingBox(currentChunkSegments);
                chunks.add(new SmartRouteChunk(new ArrayList<>(currentChunkSegments), chunkBbox, chunkIndex++));

                // Smart overlap: keep last 3 segments for next chunk (improves boundary matching)
                if (i < segments.size() - 1 && currentChunkSegments.size() > 3) {
                    List<RouteSegment> overlapSegments = currentChunkSegments.subList(
                            Math.max(0, currentChunkSegments.size() - 3),
                            currentChunkSegments.size());
                    currentChunkSegments = new ArrayList<>(overlapSegments);

                    // Recalculate overlap distance
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
     * Process chunks with accurate matching while managing memory
     */
    private static List<PolylineResult> processChunksWithAccurateMatching(List<SmartRouteChunk> chunks,
                                                                          FastAccurateRouteAnalysis analysis,
                                                                          ScoreCalculator scoreCalculator,
                                                                          FastAccurateAnalysisCallback callback) throws Exception {

        List<PolylineResult> allMatchedRoads = new ArrayList<>();
        Map<String, PolylineResult> uniqueMatchedRoads = new HashMap<>(); // Prevent duplicates
        int processedChunks = 0;

        for (SmartRouteChunk chunk : chunks) {
            try {
                if (callback != null) {
                    int progress = 20 + (processedChunks * 55 / chunks.size());
                    postProgress(callback, progress,
                            "Processing chunk " + (processedChunks + 1) + "/" + chunks.size());
                }

                // Fetch roads for this chunk with memory limits
                chunk.chunkRoads = fetchRoadsForChunkWithLimits(chunk.boundingBox, scoreCalculator);
                analysis.totalRoadsInArea += chunk.chunkRoads.size();

                Log.d(TAG, "Chunk " + chunk.chunkIndex + ": found " + chunk.chunkRoads.size() + " roads");

                // Accurate road matching for this chunk
                matchSegmentsWithHighAccuracy(chunk, analysis, uniqueMatchedRoads);

                // Clear chunk roads immediately to free memory
                chunk.chunkRoads.clear();
                chunk.chunkRoads = null;

                processedChunks++;

                // Force garbage collection between chunks
                if (processedChunks % 3 == 0) {
                    System.gc();
                }

                // Rate limiting
                Thread.sleep(100);

            } catch (OutOfMemoryError e) {
                Log.w(TAG, "Memory error in chunk " + chunk.chunkIndex + ", using fallback");
                // Mark segments as unknown and continue
                for (RouteSegment segment : chunk.segments) {
                    analysis.unknownDistance += segment.distance;
                    addSurfaceBreakdown(analysis, "memory_error", segment.distance);
                }
            } catch (Exception e) {
                Log.w(TAG, "Error processing chunk " + chunk.chunkIndex + ": " + e.getMessage());
                // Mark segments as unknown and continue
                for (RouteSegment segment : chunk.segments) {
                    analysis.unknownDistance += segment.distance;
                    addSurfaceBreakdown(analysis, "processing_error", segment.distance);
                }
            }
        }

        // Convert unique matched roads to list
        allMatchedRoads.addAll(uniqueMatchedRoads.values());
        analysis.totalRoadsMatched = allMatchedRoads.size();

        Log.d(TAG, String.format("Matched to %d unique roads out of %d total roads found",
                allMatchedRoads.size(), analysis.totalRoadsInArea));

        return allMatchedRoads;
    }

    /**
     * Fetch roads for chunk with strict memory limits
     */
    private static List<PolylineResult> fetchRoadsForChunkWithLimits(BoundingBox bbox,
                                                                     ScoreCalculator scoreCalculator) throws Exception {
        List<PolylineResult> roads = OverpassServiceSync.fetchDataSync(bbox, scoreCalculator);

        // If too many roads, filter to most relevant ones to prevent memory issues
        if (roads.size() > MAX_ROADS_PER_CHUNK) {
            Log.d(TAG, "Filtering " + roads.size() + " roads to " + MAX_ROADS_PER_CHUNK + " for memory safety");

            // Sort by length (longer roads more likely to be matched) and take top N
            roads.sort((a, b) -> {
                double lengthA = calculateRoadLength(a.getPoints());
                double lengthB = calculateRoadLength(b.getPoints());
                return Double.compare(lengthB, lengthA);
            });

            roads = roads.subList(0, MAX_ROADS_PER_CHUNK);
        }

        return roads;
    }

    /**
     * High-accuracy segment matching using improved algorithms
     */
    private static void matchSegmentsWithHighAccuracy(SmartRouteChunk chunk,
                                                      FastAccurateRouteAnalysis analysis,
                                                      Map<String, PolylineResult> uniqueMatchedRoads) {

        for (RouteSegment segment : chunk.segments) {
            PolylineResult bestMatch = findBestRoadMatchAccurate(segment, chunk.chunkRoads);
            double segmentDistance = segment.distance;

            if (bestMatch != null) {
                segment.matchedRoad = bestMatch;
                analysis.segmentsWithRoadData++;

                // Add to unique matched roads (avoid duplicates across chunks)
                String roadKey = generateRoadKey(bestMatch);
                uniqueMatchedRoads.put(roadKey, bestMatch);

                // Classify by current score (will be updated after elevation)
                classifySegmentByScore(bestMatch.getScore(), segmentDistance, analysis);

            } else {
                analysis.unknownDistance += segmentDistance;
                addSurfaceBreakdown(analysis, "no_road_match", segmentDistance);
            }
        }
    }

    /**
     * Improved road matching algorithm that balances accuracy with performance
     */
    private static PolylineResult findBestRoadMatchAccurate(RouteSegment segment, List<PolylineResult> roads) {
        if (roads.isEmpty()) return null;

        PolylineResult bestMatch = null;
        double bestScore = Double.MAX_VALUE;

        // Calculate segment midpoint and direction
        GeoPoint segmentMidpoint = new GeoPoint(
                (segment.startPoint.getLatitude() + segment.endPoint.getLatitude()) / 2.0,
                (segment.startPoint.getLongitude() + segment.endPoint.getLongitude()) / 2.0
        );

        double segmentBearing = calculateBearing(segment.startPoint, segment.endPoint);

        // Pre-filter roads by approximate distance
        List<PolylineResult> nearbyRoads = new ArrayList<>();
        for (PolylineResult road : roads) {
            if (!road.getPoints().isEmpty()) {
                double approxDistance = segmentMidpoint.distanceToAsDouble(road.getPoints().get(0));
                if (approxDistance < MAX_ROAD_MATCH_DISTANCE * 3) {
                    nearbyRoads.add(road);
                }
            }
        }

        // Accurate matching on nearby roads
        for (PolylineResult road : nearbyRoads) {
            double roadMatchScore = calculateRoadMatchScore(segment, segmentMidpoint,
                    segmentBearing, road);

            if (roadMatchScore < bestScore) {
                bestScore = roadMatchScore;
                bestMatch = road;
            }
        }

        // Only return match if within reasonable distance
        return (bestScore < MAX_ROAD_MATCH_DISTANCE) ? bestMatch : null;
    }

    /**
     * Calculate comprehensive road match score considering distance and direction
     */
    private static double calculateRoadMatchScore(RouteSegment segment, GeoPoint segmentMidpoint,
                                                  double segmentBearing, PolylineResult road) {

        List<GeoPoint> roadPoints = road.getPoints();
        double minDistance = Double.MAX_VALUE;
        double bestBearingDiff = Double.MAX_VALUE;

        // Find closest point on road and check bearing alignment
        for (int i = 0; i < roadPoints.size() - 1; i++) {
            GeoPoint roadStart = roadPoints.get(i);
            GeoPoint roadEnd = roadPoints.get(i + 1);

            // Distance to road segment
            double distance = distanceToLineSegment(segmentMidpoint, roadStart, roadEnd);

            if (distance < minDistance) {
                minDistance = distance;

                // Calculate bearing difference for direction matching
                double roadBearing = calculateBearing(roadStart, roadEnd);
                double bearingDiff = Math.abs(normalizeAngleDiff(segmentBearing - roadBearing));
                bestBearingDiff = Math.min(bestBearingDiff, bearingDiff);
            }
        }

        // Combine distance and direction for more accurate matching
        // Penalize roads that go in very different directions
        double bearingPenalty = (bestBearingDiff > 90) ? minDistance * 2 : 0;

        return minDistance + bearingPenalty;
    }

    /**
     * Fetch elevation only for roads that were actually matched
     */
    private static void fetchElevationForMatchedRoadsOnly(List<PolylineResult> matchedRoads,
                                                          List<SmartRouteChunk> chunks,
                                                          FastAccurateRouteAnalysis analysis,
                                                          ScoreCalculator scoreCalculator,
                                                          FastAccurateAnalysisCallback callback) {

        Log.d(TAG, "Fetching elevation for " + matchedRoads.size() + " matched roads only");

        CountDownLatch elevationLatch = new CountDownLatch(1);
        AtomicBoolean success = new AtomicBoolean(false);

        ElevationService.addSlopeDataToRoads(matchedRoads, new ElevationService.RoadElevationCallback() {
            @Override
            public void onSuccess(List<PolylineResult> updatedRoads) {
                try {
                    Log.d(TAG, "Recalculating scores with elevation data");

                    // Recalculate scores with slope data
                    for (PolylineResult road : updatedRoads) {
                        double maxSlope = road.getMaxSlopePercent();
                        if (maxSlope >= 0) {
                            int newScore = scoreCalculator.calculateScoreWithSlope(
                                    road.getTags(), road.getPoints(), maxSlope);
                            road.setScore(newScore);
                        }
                    }

                    // Re-classify all segments with updated scores
                    reclassifyAllSegmentsWithNewScores(chunks, analysis);

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
            elevationLatch.await(25, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Re-classify all segments after elevation data has been processed
     */
    private static void reclassifyAllSegmentsWithNewScores(List<SmartRouteChunk> chunks,
                                                           FastAccurateRouteAnalysis analysis) {
        // Reset distance counters (keep unknownDistance as is)
        analysis.greenDistance = 0;
        analysis.yellowDistance = 0;
        analysis.redDistance = 0;

        // Clear and rebuild surface breakdown
        analysis.surfaceBreakdown.clear();

        for (SmartRouteChunk chunk : chunks) {
            for (RouteSegment segment : chunk.segments) {
                double segmentDistance = segment.distance;

                if (segment.matchedRoad != null) {
                    classifySegmentByScore(segment.matchedRoad.getScore(), segmentDistance, analysis);

                    String surface = segment.matchedRoad.getTags().get("surface");
                    if (surface != null && !surface.trim().isEmpty()) {
                        addSurfaceBreakdown(analysis, surface.toLowerCase(), segmentDistance);
                    }
                } else {
                    addSurfaceBreakdown(analysis, "no_road_match", segmentDistance);
                }
            }
        }
    }

    // Helper methods

    private static void postProgress(FastAccurateAnalysisCallback callback, int progress, String message) {
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

    private static void analyzeExistingElevationData(List<RouteSegment> segments, FastAccurateRouteAnalysis analysis) {
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

    private static void classifySegmentByScore(int score, double distance, FastAccurateRouteAnalysis analysis) {
        if (score >= SCORE_GREEN_THRESHOLD) {
            analysis.greenDistance += distance;
            addSurfaceBreakdown(analysis, "excellent_roads", distance);
        } else if (score >= SCORE_YELLOW_THRESHOLD) {
            analysis.yellowDistance += distance;
            addSurfaceBreakdown(analysis, "decent_roads", distance);
        } else {
            analysis.redDistance += distance;
            addSurfaceBreakdown(analysis, "poor_roads", distance);
        }
    }

    private static void addSurfaceBreakdown(FastAccurateRouteAnalysis analysis, String key, double meters) {
        analysis.surfaceBreakdown.put(key, analysis.surfaceBreakdown.getOrDefault(key, 0.0) + meters);
    }

    private static void calculateFinalMetrics(FastAccurateRouteAnalysis analysis) {
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

    // Geometric helper methods

    private static double calculateRoadLength(List<GeoPoint> points) {
        double length = 0.0;
        for (int i = 1; i < points.size(); i++) {
            length += points.get(i-1).distanceToAsDouble(points.get(i));
        }
        return length;
    }

    private static double distanceToLineSegment(GeoPoint point, GeoPoint lineStart, GeoPoint lineEnd) {
        double A = point.getLatitude() - lineStart.getLatitude();
        double B = point.getLongitude() - lineStart.getLongitude();
        double C = lineEnd.getLatitude() - lineStart.getLatitude();
        double D = lineEnd.getLongitude() - lineStart.getLongitude();

        double dot = A * C + B * D;
        double lenSq = C * C + D * D;

        if (lenSq == 0) {
            return point.distanceToAsDouble(lineStart);
        }

        double param = dot / lenSq;
        GeoPoint closestPoint;

        if (param < 0) {
            closestPoint = lineStart;
        } else if (param > 1) {
            closestPoint = lineEnd;
        } else {
            closestPoint = new GeoPoint(
                    lineStart.getLatitude() + param * C,
                    lineStart.getLongitude() + param * D
            );
        }

        return point.distanceToAsDouble(closestPoint);
    }

    private static double calculateBearing(GeoPoint from, GeoPoint to) {
        double lat1 = Math.toRadians(from.getLatitude());
        double lat2 = Math.toRadians(to.getLatitude());
        double deltaLon = Math.toRadians(to.getLongitude() - from.getLongitude());

        double y = Math.sin(deltaLon) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(deltaLon);

        return Math.toDegrees(Math.atan2(y, x));
    }

    private static double normalizeAngleDiff(double angleDiff) {
        while (angleDiff > 180) angleDiff -= 360;
        while (angleDiff < -180) angleDiff += 360;
        return Math.abs(angleDiff);
    }

    private static String generateRoadKey(PolylineResult road) {
        // Generate unique key based on first and last points
        List<GeoPoint> points = road.getPoints();
        if (points.size() < 2) return "road_" + System.identityHashCode(road);

        GeoPoint first = points.get(0);
        GeoPoint last = points.get(points.size() - 1);

        return String.format(Locale.US, "%.6f_%.6f_%.6f_%.6f",
                first.getLatitude(), first.getLongitude(),
                last.getLatitude(), last.getLongitude());
    }
}