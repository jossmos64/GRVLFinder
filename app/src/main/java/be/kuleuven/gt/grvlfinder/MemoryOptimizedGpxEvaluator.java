// Updated MemoryOptimizedGpxEvaluator.java with data structures for maps
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
 * Memory-optimized GPX Evaluator with map visualization support
 */
public class MemoryOptimizedGpxEvaluator {
    private static final String TAG = "MemoryOptimizedGpxEvaluator";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    private static final double MAX_CHUNK_SIZE_KM = 2.0;
    private static final double SEGMENT_LENGTH_METERS = 100.0;
    private static final double BUFFER_DEGREES = 0.01;
    private static final int SCORE_GREEN_THRESHOLD = 20;
    private static final int SCORE_YELLOW_THRESHOLD = 10;

    /**
     * Data structure for route segments with quality scores for map visualization
     */
    public static class RouteSegmentResult {
        public List<GeoPoint> points;
        public int qualityScore;
        public double distance;
        public double slope;
        public String surfaceType;
        public String qualityDescription;

        public RouteSegmentResult(List<GeoPoint> points, int qualityScore) {
            this.points = new ArrayList<>(points);
            this.qualityScore = qualityScore;
            this.distance = calculateSegmentDistance(points);
            this.slope = -1; // Will be set if elevation data available
            this.surfaceType = "unknown";
            this.qualityDescription = getQualityDescription(qualityScore);
        }

        private double calculateSegmentDistance(List<GeoPoint> points) {
            if (points == null || points.size() < 2) return 0;
            double total = 0;
            for (int i = 1; i < points.size(); i++) {
                total += points.get(i-1).distanceToAsDouble(points.get(i));
            }
            return total;
        }

        private String getQualityDescription(int score) {
            if (score >= SCORE_GREEN_THRESHOLD) return "Excellent";
            else if (score >= SCORE_YELLOW_THRESHOLD) return "Decent";
            else if (score >= 0) return "Poor";
            else return "Unknown";
        }
    }

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

        // NEW: Route segments for map visualization
        public List<RouteSegmentResult> routeSegments;

        public OptimizedRouteAnalysis() {
            this.maxSlope = 0.0;
            this.steepestLocationDescription = "Unknown";
            this.routeSegments = new ArrayList<>();
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
        List<GeoPoint> segmentPoints;

        RouteSegment(GeoPoint start, GeoPoint end, List<GeoPoint> points) {
            this.startPoint = start;
            this.endPoint = end;
            this.segmentPoints = new ArrayList<>(points);
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
     * Memory-optimized GPX analysis with map visualization data
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

                // Step 1: Create segments with point data for visualization
                List<RouteSegment> allSegments = createRouteSegmentsWithPoints(routePoints);
                analysis.totalSegmentsAnalyzed = allSegments.size();

                if (callback != null) postProgress(callback, 10, "Creating route chunks...");

                // Step 2: Break route into manageable chunks
                List<RouteChunk> chunks = createRouteChunks(allSegments);
                Log.d(TAG, "Created " + chunks.size() + " chunks for analysis");

                if (callback != null) postProgress(callback, 15, "Processing " + chunks.size() + " route chunks...");

                // Step 3: Process each chunk
                ScoreCalculator scoreCalculator = new ScoreCalculator(bikeTypeManager.getCurrentWeights());
                scoreCalculator.setBikeTypeManager(bikeTypeManager);

                int processedChunks = 0;
                for (RouteChunk chunk : chunks) {
                    try {
                        processRouteChunkWithVisualization(chunk, analysis, scoreCalculator, bikeTypeManager);
                        processedChunks++;

                        System.gc();

                        if (callback != null) {
                            int progress = 20 + (processedChunks * 60 / chunks.size());
                            postProgress(callback, progress,
                                    "Processed chunk " + processedChunks + "/" + chunks.size());
                        }

                        Thread.sleep(200);

                    } catch (OutOfMemoryError e) {
                        Log.e(TAG, "Memory error processing chunk " + chunk.chunkIndex + ", skipping");
                        for (RouteSegment segment : chunk.segments) {
                            analysis.unknownDistance += segment.distance;
                            // Add unknown segment to visualization
                            analysis.routeSegments.add(new RouteSegmentResult(segment.segmentPoints, -1));
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Error processing chunk " + chunk.chunkIndex + ": " + e.getMessage());
                        for (RouteSegment segment : chunk.segments) {
                            analysis.unknownDistance += segment.distance;
                            analysis.routeSegments.add(new RouteSegmentResult(segment.segmentPoints, -1));
                        }
                    }
                }

                if (callback != null) postProgress(callback, 85, "Analyzing elevation data...");

                // Step 4: Handle elevation
                if (analysis.hasElevationData) {
                    analyzeExistingElevationDataWithVisualization(allSegments, analysis);
                }

                if (callback != null) postProgress(callback, 95, "Finalizing analysis...");

                // Step 5: Calculate final metrics
                calculateFinalMetrics(analysis);

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
     * Create route segments with point data for map visualization
     */
    private static List<RouteSegment> createRouteSegmentsWithPoints(List<GeoPoint> routePoints) {
        List<RouteSegment> segments = new ArrayList<>();
        if (routePoints.size() < 2) return segments;

        double accumulatedDistance = 0.0;
        int segmentStartIndex = 0;

        for (int i = 1; i < routePoints.size(); i++) {
            GeoPoint prev = routePoints.get(i - 1);
            GeoPoint current = routePoints.get(i);
            double d = prev.distanceToAsDouble(current);
            accumulatedDistance += d;

            if (accumulatedDistance >= SEGMENT_LENGTH_METERS || i == routePoints.size() - 1) {
                // Create segment with all points in this section
                List<GeoPoint> segmentPoints = new ArrayList<>();
                for (int j = segmentStartIndex; j <= i; j++) {
                    segmentPoints.add(routePoints.get(j));
                }

                RouteSegment segment = new RouteSegment(
                        routePoints.get(segmentStartIndex),
                        routePoints.get(i),
                        segmentPoints
                );
                segments.add(segment);

                segmentStartIndex = i;
                accumulatedDistance = 0.0;
            }
        }
        return segments;
    }

    /**
     * Process route chunk and create visualization data
     */
    private static void processRouteChunkWithVisualization(RouteChunk chunk,
                                                           OptimizedRouteAnalysis analysis,
                                                           ScoreCalculator scoreCalculator,
                                                           BikeTypeManager bikeTypeManager) throws Exception {

        Log.d(TAG, "Processing chunk " + chunk.chunkIndex + " with " + chunk.segments.size() + " segments");

        List<PolylineResult> chunkRoads = null;
        try {
            chunkRoads = OverpassServiceSync.fetchDataSync(chunk.boundingBox, scoreCalculator);
            analysis.totalRoadsInArea += chunkRoads.size();
            Log.d(TAG, "Found " + chunkRoads.size() + " roads in chunk " + chunk.chunkIndex);
        } catch (Exception e) {
            Log.w(TAG, "Failed to fetch roads for chunk " + chunk.chunkIndex + ": " + e.getMessage());
            chunkRoads = new ArrayList<>();
        }

        // Match segments to roads and create visualization data
        for (RouteSegment segment : chunk.segments) {
            PolylineResult bestMatch = findClosestRoad(segment, chunkRoads);

            double segmentDistance = segment.distance;
            int qualityScore = -1; // Unknown by default
            String surfaceType = "unknown";

            if (bestMatch != null) {
                segment.matchedRoad = bestMatch;
                analysis.segmentsWithRoadData++;

                qualityScore = bestMatch.getScore();
                surfaceType = bestMatch.getTags().getOrDefault("surface", "unknown");

                if (qualityScore >= SCORE_GREEN_THRESHOLD) {
                    analysis.greenDistance += segmentDistance;
                } else if (qualityScore >= SCORE_YELLOW_THRESHOLD) {
                    analysis.yellowDistance += segmentDistance;
                } else {
                    analysis.redDistance += segmentDistance;
                }
            } else {
                analysis.unknownDistance += segmentDistance;
            }

            // Create visualization segment
            RouteSegmentResult segmentResult = new RouteSegmentResult(segment.segmentPoints, qualityScore);
            segmentResult.surfaceType = surfaceType;
            analysis.routeSegments.add(segmentResult);
        }

        // Clear memory
        chunkRoads.clear();
        chunkRoads = null;
    }

    private static void analyzeExistingElevationDataWithVisualization(List<RouteSegment> segments,
                                                                      OptimizedRouteAnalysis analysis) {
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

    // Helper methods (unchanged from original)
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
                    currentChunkSegments.size() >= 20) {

                BoundingBox chunkBbox = calculateChunkBoundingBox(currentChunkSegments);
                chunks.add(new RouteChunk(new ArrayList<>(currentChunkSegments), chunkBbox, chunkIndex++));

                currentChunkSegments.clear();
                currentChunkDistance = 0.0;
            }
        }

        if (!currentChunkSegments.isEmpty()) {
            BoundingBox chunkBbox = calculateChunkBoundingBox(currentChunkSegments);
            chunks.add(new RouteChunk(currentChunkSegments, chunkBbox, chunkIndex));
        }

        return chunks;
    }

    private static BoundingBox calculateChunkBoundingBox(List<RouteSegment> segments) {
        double minLat = Double.MAX_VALUE, maxLat = Double.MIN_VALUE;
        double minLon = Double.MAX_VALUE, maxLon = Double.MIN_VALUE;

        for (RouteSegment segment : segments) {
            for (GeoPoint point : segment.segmentPoints) {
                double lat = point.getLatitude();
                double lon = point.getLongitude();
                minLat = Math.min(minLat, lat);
                maxLat = Math.max(maxLat, lat);
                minLon = Math.min(minLon, lon);
                maxLon = Math.max(maxLon, lon);
            }
        }

        return new BoundingBox(maxLat + BUFFER_DEGREES, maxLon + BUFFER_DEGREES,
                minLat - BUFFER_DEGREES, minLon - BUFFER_DEGREES);
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
                if (distance < minDistance && distance < 100.0) {
                    minDistance = distance;
                    bestMatch = road;
                }
            }
        }

        return bestMatch;
    }

    private static void calculateFinalMetrics(OptimizedRouteAnalysis analysis) {
        analysis.greenDistance = analysis.greenDistance / 1000.0;
        analysis.yellowDistance = analysis.yellowDistance / 1000.0;
        analysis.redDistance = analysis.redDistance / 1000.0;
        analysis.unknownDistance = analysis.unknownDistance / 1000.0;

        analysis.calculatePercentages();

        analysis.dataCoveragePercentage = analysis.totalSegmentsAnalyzed > 0 ?
                (analysis.segmentsWithRoadData / (double) analysis.totalSegmentsAnalyzed) * 100.0 : 0.0;
    }

    private static void postProgress(OptimizedRouteAnalysisCallback callback, int progress, String message) {
        new Handler(Looper.getMainLooper()).post(() -> callback.onProgress(progress, message));
    }
}