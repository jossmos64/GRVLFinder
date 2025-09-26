// Create a new file: MemoryOptimizedGpxEvaluator.java
// This version processes smaller chunks to avoid memory issues

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
 * Memory-optimized GPX Evaluator that processes route in smaller chunks to avoid OOM errors
 */
public class MemoryOptimizedGpxEvaluator {
    private static final String TAG = "MemoryOptimizedGpxEvaluator";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    // Reduced chunk size to prevent memory issues
    private static final double MAX_CHUNK_SIZE_KM = 2.0; // Process 2km chunks instead of entire route
    private static final double SEGMENT_LENGTH_METERS = 100.0;
    private static final double BUFFER_DEGREES = 0.01; // Smaller buffer = less data

    // Score thresholds - same as main app
    private static final int SCORE_GREEN_THRESHOLD = 20;
    private static final int SCORE_YELLOW_THRESHOLD = 10;

    public static class OptimizedRouteAnalysis {
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

        public OptimizedRouteAnalysis() {
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

    public interface OptimizedRouteAnalysisCallback {
        void onAnalysisComplete(OptimizedRouteAnalysis analysis);
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

    private static class RouteChunk {
        List<RouteSegment> segments;
        BoundingBox boundingBox;
        int chunkIndex;

        RouteChunk(List<RouteSegment> segments, BoundingBox boundingBox, int chunkIndex) {
            this.segments = segments;
            this.boundingBox = boundingBox;
            this.chunkIndex = chunkIndex;
        }
    }

    /**
     * Memory-optimized GPX analysis that processes route in chunks
     */
    public static void analyzeGpxRouteOptimized(List<GeoPoint> routePoints,
                                                BikeTypeManager bikeTypeManager,
                                                OptimizedRouteAnalysisCallback callback) {

        if (routePoints == null || routePoints.isEmpty()) {
            if (callback != null) {
                new Handler(Looper.getMainLooper()).post(() ->
                        callback.onAnalysisError("No route points provided"));
            }
            return;
        }

        Log.d(TAG, "Starting memory-optimized GPX analysis for " + routePoints.size() + " points");

        executor.execute(() -> {
            try {
                OptimizedRouteAnalysis analysis = new OptimizedRouteAnalysis();
                analysis.analyzedForBikeType = bikeTypeManager.getCurrentBikeType();
                analysis.totalDistance = GpxParser.calculateRouteDistance(routePoints) / 1000.0;
                analysis.hasElevationData = GpxParser.hasElevationData(routePoints);

                if (callback != null) postProgress(callback, 5, "Preparing route analysis...");

                // Step 1: Create segments
                List<RouteSegment> allSegments = createRouteSegments(routePoints);
                analysis.totalSegmentsAnalyzed = allSegments.size();

                if (callback != null) postProgress(callback, 10, "Creating route chunks...");

                // Step 2: Break route into manageable chunks
                List<RouteChunk> chunks = createRouteChunks(allSegments);
                Log.d(TAG, "Created " + chunks.size() + " chunks for analysis");

                if (callback != null) postProgress(callback, 15, "Processing " + chunks.size() + " route chunks...");

                // Step 3: Process each chunk separately to avoid memory issues
                ScoreCalculator scoreCalculator = new ScoreCalculator(bikeTypeManager.getCurrentWeights());
                scoreCalculator.setBikeTypeManager(bikeTypeManager);

                int processedChunks = 0;
                for (RouteChunk chunk : chunks) {
                    try {
                        processRouteChunk(chunk, analysis, scoreCalculator, bikeTypeManager);
                        processedChunks++;

                        // Force garbage collection between chunks
                        System.gc();

                        if (callback != null) {
                            int progress = 20 + (processedChunks * 70 / chunks.size());
                            postProgress(callback, progress,
                                    "Processed chunk " + processedChunks + "/" + chunks.size());
                        }

                        // Small delay to prevent overwhelming the API
                        Thread.sleep(200);

                    } catch (OutOfMemoryError e) {
                        Log.e(TAG, "Memory error processing chunk " + chunk.chunkIndex + ", skipping");
                        // Mark segments as unknown and continue
                        for (RouteSegment segment : chunk.segments) {
                            analysis.unknownDistance += segment.distance;
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Error processing chunk " + chunk.chunkIndex + ": " + e.getMessage());
                        // Mark segments as unknown and continue
                        for (RouteSegment segment : chunk.segments) {
                            analysis.unknownDistance += segment.distance;
                        }
                    }
                }

                // Step 4: Handle elevation if needed (simplified)
                if (analysis.hasElevationData) {
                    analyzeExistingElevationData(allSegments, analysis);
                }

                // Step 5: Calculate final metrics
                calculateFinalMetrics(analysis);

                if (callback != null) postProgress(callback, 95, "Finalizing analysis...");

                Log.d(TAG, String.format("Analysis complete: %.1f%% green, %.1f%% yellow, %.1f%% red, %.1f%% unknown",
                        analysis.greenPercentage, analysis.yellowPercentage,
                        analysis.redPercentage, analysis.unknownPercentage));

                if (callback != null) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        callback.onProgress(100, "Analysis complete!");
                        callback.onAnalysisComplete(analysis);
                    });
                }

            } catch (Exception e) {
                Log.e(TAG, "Error in memory-optimized GPX analysis", e);
                if (callback != null) {
                    new Handler(Looper.getMainLooper()).post(() ->
                            callback.onAnalysisError("Analysis failed: " + e.getMessage()));
                }
            }
        });
    }

    /**
     * Create smaller route chunks to process separately
     */
    private static List<RouteChunk> createRouteChunks(List<RouteSegment> segments) {
        List<RouteChunk> chunks = new ArrayList<>();

        double maxChunkDistanceMeters = MAX_CHUNK_SIZE_KM * 1000;
        double currentChunkDistance = 0.0;
        List<RouteSegment> currentChunkSegments = new ArrayList<>();
        int chunkIndex = 0;

        for (RouteSegment segment : segments) {
            currentChunkSegments.add(segment);
            currentChunkDistance += segment.distance;

            if (currentChunkDistance >= maxChunkDistanceMeters ||
                    currentChunkSegments.size() >= 20) { // Also limit by segment count

                BoundingBox chunkBbox = calculateChunkBoundingBox(currentChunkSegments);
                chunks.add(new RouteChunk(new ArrayList<>(currentChunkSegments), chunkBbox, chunkIndex++));

                currentChunkSegments.clear();
                currentChunkDistance = 0.0;
            }
        }

        // Add remaining segments as final chunk
        if (!currentChunkSegments.isEmpty()) {
            BoundingBox chunkBbox = calculateChunkBoundingBox(currentChunkSegments);
            chunks.add(new RouteChunk(currentChunkSegments, chunkBbox, chunkIndex));
        }

        return chunks;
    }

    /**
     * Process a single route chunk
     */
    private static void processRouteChunk(RouteChunk chunk,
                                          OptimizedRouteAnalysis analysis,
                                          ScoreCalculator scoreCalculator,
                                          BikeTypeManager bikeTypeManager) throws Exception {

        Log.d(TAG, "Processing chunk " + chunk.chunkIndex + " with " + chunk.segments.size() + " segments");

        // Fetch roads for this chunk only
        List<PolylineResult> chunkRoads = null;
        try {
            chunkRoads = OverpassServiceSync.fetchDataSync(chunk.boundingBox, scoreCalculator);
            analysis.totalRoadsInArea += chunkRoads.size();
            Log.d(TAG, "Found " + chunkRoads.size() + " roads in chunk " + chunk.chunkIndex);
        } catch (Exception e) {
            Log.w(TAG, "Failed to fetch roads for chunk " + chunk.chunkIndex + ": " + e.getMessage());
            chunkRoads = new ArrayList<>();
        }

        // Match segments to roads
        for (RouteSegment segment : chunk.segments) {
            PolylineResult bestMatch = findClosestRoad(segment, chunkRoads);

            double segmentDistance = segment.distance;

            if (bestMatch != null) {
                segment.matchedRoad = bestMatch;
                analysis.segmentsWithRoadData++;

                int score = bestMatch.getScore();
                if (score >= SCORE_GREEN_THRESHOLD) {
                    analysis.greenDistance += segmentDistance;
                } else if (score >= SCORE_YELLOW_THRESHOLD) {
                    analysis.yellowDistance += segmentDistance;
                } else {
                    analysis.redDistance += segmentDistance;
                }
            } else {
                analysis.unknownDistance += segmentDistance;
            }
        }

        // Clear chunk roads to free memory
        chunkRoads.clear();
        chunkRoads = null;
    }

    /**
     * Calculate bounding box for a chunk of segments
     */
    private static BoundingBox calculateChunkBoundingBox(List<RouteSegment> segments) {
        double minLat = Double.MAX_VALUE, maxLat = Double.MIN_VALUE;
        double minLon = Double.MAX_VALUE, maxLon = Double.MIN_VALUE;

        for (RouteSegment segment : segments) {
            // Check start point
            double lat = segment.startPoint.getLatitude();
            double lon = segment.startPoint.getLongitude();
            minLat = Math.min(minLat, lat);
            maxLat = Math.max(maxLat, lat);
            minLon = Math.min(minLon, lon);
            maxLon = Math.max(maxLon, lon);

            // Check end point
            lat = segment.endPoint.getLatitude();
            lon = segment.endPoint.getLongitude();
            minLat = Math.min(minLat, lat);
            maxLat = Math.max(maxLat, lat);
            minLon = Math.min(minLon, lon);
            maxLon = Math.max(maxLon, lon);
        }

        // Add smaller buffer to reduce data size
        return new BoundingBox(maxLat + BUFFER_DEGREES, maxLon + BUFFER_DEGREES,
                minLat - BUFFER_DEGREES, minLon - BUFFER_DEGREES);
    }

    /**
     * Analyze elevation data that already exists in the GPX
     */
    private static void analyzeExistingElevationData(List<RouteSegment> segments, OptimizedRouteAnalysis analysis) {
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

    // Helper methods (simplified versions)
    private static void postProgress(OptimizedRouteAnalysisCallback callback, int progress, String message) {
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

    private static PolylineResult findClosestRoad(RouteSegment segment, List<PolylineResult> roads) {
        if (roads.isEmpty()) return null;

        PolylineResult bestMatch = null;
        double minDistance = Double.MAX_VALUE;

        GeoPoint midpoint = new GeoPoint(
                (segment.startPoint.getLatitude() + segment.endPoint.getLatitude()) / 2.0,
                (segment.startPoint.getLongitude() + segment.endPoint.getLongitude()) / 2.0
        );

        for (PolylineResult road : roads) {
            for (GeoPoint roadPoint : road.getPoints()) {
                double distance = midpoint.distanceToAsDouble(roadPoint);
                if (distance < minDistance && distance < 100.0) { // Reduced match distance
                    minDistance = distance;
                    bestMatch = road;
                }
            }
        }

        return bestMatch;
    }

    private static void calculateFinalMetrics(OptimizedRouteAnalysis analysis) {
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
    }
}