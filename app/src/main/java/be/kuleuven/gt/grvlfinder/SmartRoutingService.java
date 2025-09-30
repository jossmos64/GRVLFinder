package be.kuleuven.gt.grvlfinder;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Production-ready routing service that scores roads based on bike type preferences
 * Uses A* pathfinding with road quality scores as weights
 */
public class SmartRoutingService {
    private static final String TAG = "SmartRoutingService";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final double MAX_DISTANCE_KM = 50.0; // Maximum route distance
    private static final int MAX_NODES = 10000; // Prevent infinite loops

    private Context context;
    private BikeTypeManager bikeTypeManager;
    private ScoreCalculator scoreCalculator;

    public SmartRoutingService(Context context, BikeTypeManager bikeTypeManager, ScoreCalculator scoreCalculator) {
        this.context = context.getApplicationContext();
        this.bikeTypeManager = bikeTypeManager;
        this.scoreCalculator = scoreCalculator;
    }

    public interface SmartRoutingCallback {
        void onRouteCalculated(List<GeoPoint> route, RouteMetrics metrics);
        void onError(String error);
    }

    public void calculateRoute(GeoPoint start, GeoPoint end, SmartRoutingCallback callback) {
        Handler mainHandler = new Handler(Looper.getMainLooper());

        executor.execute(() -> {
            try {
                //Fetch road network in bounding box
                BoundingBox bbox = createExpandedBBox(start, end);
                Log.d(TAG, "Fetching road network...");

                List<PolylineResult> roads = OverpassServiceSync.fetchDataSync(bbox, scoreCalculator);

                if (roads.isEmpty()) {
                    mainHandler.post(() -> callback.onError("No roads found in area"));
                    return;
                }

                Log.d(TAG, "Found " + roads.size() + " roads, building graph...");

                //Build road network graph
                RoadGraph graph = buildRoadGraph(roads);

                //Find nearest nodes to start and end
                RoadNode startNode = graph.findNearestNode(start);
                RoadNode endNode = graph.findNearestNode(end);

                if (startNode == null || endNode == null) {
                    mainHandler.post(() -> callback.onError("Cannot connect start/end to road network"));
                    return;
                }

                Log.d(TAG, "Running A* pathfinding...");

                //Run A* pathfinding with score-based weights
                List<GeoPoint> route = findBestPath(graph, startNode, endNode, start, end);

                if (route == null || route.isEmpty()) {
                    mainHandler.post(() -> callback.onError("No route found"));
                    return;
                }

                //Calculate route metrics
                RouteMetrics metrics = calculateRouteMetrics(route, roads);

                Log.d(TAG, "Route found: " + route.size() + " points, " +
                        String.format("%.1f km", metrics.totalDistanceKm));

                mainHandler.post(() -> callback.onRouteCalculated(route, metrics));

            } catch (Exception e) {
                Log.e(TAG, "Routing error", e);
                mainHandler.post(() -> callback.onError("Routing failed: " + e.getMessage()));
            }
        });
    }

    /**
     * Build road graph from OSM data
     */
    private RoadGraph buildRoadGraph(List<PolylineResult> roads) {
        RoadGraph graph = new RoadGraph();

        for (PolylineResult road : roads) {
            List<GeoPoint> points = road.getPoints();
            int score = road.getScore();

            // Add nodes and edges for this road
            for (int i = 0; i < points.size() - 1; i++) {
                GeoPoint p1 = points.get(i);
                GeoPoint p2 = points.get(i + 1);

                RoadNode n1 = graph.getOrCreateNode(p1);
                RoadNode n2 = graph.getOrCreateNode(p2);

                double distance = p1.distanceToAsDouble(p2);

                // Weight = distance / (score factor)
                // Higher score = lower weight = preferred path
                double scoreFactor = Math.max(1.0, (score + 30.0) / 30.0);
                double weight = distance / scoreFactor;

                graph.addEdge(n1, n2, weight, road);
            }
        }

        return graph;
    }

    /**
     * A* pathfinding algorithm with score-based weights
     */
    private List<GeoPoint> findBestPath(RoadGraph graph, RoadNode start, RoadNode end,
                                        GeoPoint actualStart, GeoPoint actualEnd) {

        PriorityQueue<PathNode> openSet = new PriorityQueue<>((a, b) ->
                Double.compare(a.fScore, b.fScore));

        Map<RoadNode, PathNode> allNodes = new HashMap<>();
        Set<RoadNode> closedSet = new HashSet<>();

        PathNode startPath = new PathNode(start, null, 0, start.point.distanceToAsDouble(end.point));
        openSet.add(startPath);
        allNodes.put(start, startPath);

        int iterations = 0;

        while (!openSet.isEmpty() && iterations < MAX_NODES) {
            iterations++;

            PathNode current = openSet.poll();

            if (current.node.equals(end)) {
                // Reconstruct path
                List<GeoPoint> path = new ArrayList<>();
                path.add(actualStart); // Add actual start point

                PathNode node = current;
                while (node != null) {
                    path.add(node.node.point);
                    node = node.parent;
                }

                Collections.reverse(path);
                path.add(actualEnd); // Add actual end point

                return path;
            }

            closedSet.add(current.node);

            // Explore neighbors
            for (RoadEdge edge : current.node.edges) {
                RoadNode neighbor = edge.to;

                if (closedSet.contains(neighbor)) continue;

                double tentativeG = current.gScore + edge.weight;

                PathNode neighborPath = allNodes.get(neighbor);

                if (neighborPath == null) {
                    double h = neighbor.point.distanceToAsDouble(end.point);
                    neighborPath = new PathNode(neighbor, current, tentativeG, tentativeG + h);
                    allNodes.put(neighbor, neighborPath);
                    openSet.add(neighborPath);
                } else if (tentativeG < neighborPath.gScore) {
                    neighborPath.gScore = tentativeG;
                    neighborPath.fScore = tentativeG + neighbor.point.distanceToAsDouble(end.point);
                    neighborPath.parent = current;

                    // Re-add to priority queue with updated priority
                    openSet.remove(neighborPath);
                    openSet.add(neighborPath);
                }
            }
        }

        Log.w(TAG, "No path found after " + iterations + " iterations");
        return null;
    }

    /**
     * Calculate detailed route metrics
     */
    private RouteMetrics calculateRouteMetrics(List<GeoPoint> route, List<PolylineResult> roads) {
        RouteMetrics metrics = new RouteMetrics();

        double totalDistance = 0;
        double gravelDistance = 0;
        double pavedDistance = 0;
        double maxSlope = 0;
        GeoPoint steepestPoint = null;

        for (int i = 0; i < route.size() - 1; i++) {
            GeoPoint p1 = route.get(i);
            GeoPoint p2 = route.get(i + 1);

            double segmentDist = p1.distanceToAsDouble(p2);
            totalDistance += segmentDist;

            // Find matching road for this segment
            PolylineResult matchingRoad = findMatchingRoad(p1, p2, roads);

            if (matchingRoad != null) {
                String surface = matchingRoad.getTags().get("surface");

                if (isGravelSurface(surface)) {
                    gravelDistance += segmentDist;
                } else if (isPavedSurface(surface)) {
                    pavedDistance += segmentDist;
                }

                // Check slope
                double slope = matchingRoad.getMaxSlopePercent();
                if (slope > maxSlope) {
                    maxSlope = slope;
                    steepestPoint = p1;
                }
            }
        }

        metrics.totalDistanceKm = totalDistance / 1000.0;
        metrics.gravelDistanceKm = gravelDistance / 1000.0;
        metrics.pavedDistanceKm = pavedDistance / 1000.0;
        metrics.maxSlopePercent = maxSlope;
        metrics.steepestPoint = steepestPoint;

        return metrics;
    }

    private PolylineResult findMatchingRoad(GeoPoint p1, GeoPoint p2, List<PolylineResult> roads) {
        double minDist = Double.MAX_VALUE;
        PolylineResult closest = null;

        for (PolylineResult road : roads) {
            List<GeoPoint> points = road.getPoints();

            for (int i = 0; i < points.size() - 1; i++) {
                GeoPoint a = points.get(i);
                GeoPoint b = points.get(i + 1);

                double dist1 = distanceToSegment(p1, a, b);
                double dist2 = distanceToSegment(p2, a, b);
                double avgDist = (dist1 + dist2) / 2.0;

                if (avgDist < minDist) {
                    minDist = avgDist;
                    closest = road;
                }
            }
        }

        return (minDist < 50) ? closest : null;
    }

    private double distanceToSegment(GeoPoint p, GeoPoint a, GeoPoint b) {
        double ax = a.getLongitude(), ay = a.getLatitude();
        double bx = b.getLongitude(), by = b.getLatitude();
        double px = p.getLongitude(), py = p.getLatitude();

        double dx = bx - ax, dy = by - ay;
        if (dx == 0 && dy == 0) return p.distanceToAsDouble(a);

        double t = Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)));

        GeoPoint projected = new GeoPoint(ay + t * dy, ax + t * dx);
        return p.distanceToAsDouble(projected);
    }

    private boolean isGravelSurface(String surface) {
        if (surface == null) return false;
        surface = surface.toLowerCase();
        return surface.contains("gravel") || surface.contains("dirt") ||
                surface.contains("ground") || surface.contains("earth") ||
                surface.contains("unpaved") || surface.contains("compacted");
    }

    private boolean isPavedSurface(String surface) {
        if (surface == null) return false;
        surface = surface.toLowerCase();
        return surface.contains("asphalt") || surface.contains("paved") ||
                surface.contains("concrete");
    }

    private BoundingBox createExpandedBBox(GeoPoint start, GeoPoint end) {
        double minLat = Math.min(start.getLatitude(), end.getLatitude());
        double maxLat = Math.max(start.getLatitude(), end.getLatitude());
        double minLon = Math.min(start.getLongitude(), end.getLongitude());
        double maxLon = Math.max(start.getLongitude(), end.getLongitude());

        // Expand by 20%
        double latMargin = (maxLat - minLat) * 0.2;
        double lonMargin = (maxLon - minLon) * 0.2;

        return new BoundingBox(maxLat + latMargin, maxLon + lonMargin,
                minLat - latMargin, minLon - lonMargin);
    }

    // Inner classes
    private static class RoadGraph {
        private Map<String, RoadNode> nodes = new HashMap<>();

        RoadNode getOrCreateNode(GeoPoint point) {
            String key = pointKey(point);
            return nodes.computeIfAbsent(key, k -> new RoadNode(point));
        }

        void addEdge(RoadNode from, RoadNode to, double weight, PolylineResult road) {
            from.edges.add(new RoadEdge(to, weight, road));
            to.edges.add(new RoadEdge(from, weight, road)); // Bidirectional
        }

        RoadNode findNearestNode(GeoPoint point) {
            RoadNode nearest = null;
            double minDist = Double.MAX_VALUE;

            for (RoadNode node : nodes.values()) {
                double dist = point.distanceToAsDouble(node.point);
                if (dist < minDist) {
                    minDist = dist;
                    nearest = node;
                }
            }

            return (minDist < 100) ? nearest : null; // Max 100m snap distance
        }

        private String pointKey(GeoPoint p) {
            return String.format("%.6f,%.6f", p.getLatitude(), p.getLongitude());
        }
    }

    private static class RoadNode {
        GeoPoint point;
        List<RoadEdge> edges = new ArrayList<>();

        RoadNode(GeoPoint point) {
            this.point = point;
        }
    }

    private static class RoadEdge {
        RoadNode to;
        double weight;
        PolylineResult road;

        RoadEdge(RoadNode to, double weight, PolylineResult road) {
            this.to = to;
            this.weight = weight;
            this.road = road;
        }
    }

    private static class PathNode {
        RoadNode node;
        PathNode parent;
        double gScore;
        double fScore;

        PathNode(RoadNode node, PathNode parent, double gScore, double fScore) {
            this.node = node;
            this.parent = parent;
            this.gScore = gScore;
            this.fScore = fScore;
        }
    }

    public static class RouteMetrics {
        public double totalDistanceKm;
        public double gravelDistanceKm;
        public double pavedDistanceKm;
        public double maxSlopePercent;
        public GeoPoint steepestPoint;

        public String getSummary() {
            return String.format("Total: %.1f km\nGravel: %.1f km\nPaved: %.1f km\nMax slope: %.1f%%",
                    totalDistanceKm, gravelDistanceKm, pavedDistanceKm, maxSlopePercent);
        }
    }
}