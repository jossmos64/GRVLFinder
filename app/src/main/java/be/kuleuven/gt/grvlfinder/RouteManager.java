package be.kuleuven.gt.grvlfinder;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;
import android.widget.Toast;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;
import java.util.*;

public class RouteManager {
    private static final String TAG = "RouteManager";
    private List<GeoPoint> drawnRoute = new ArrayList<>();
    private Polyline drawnPolyline = null;
    private Marker startMarker = null;
    private Marker endMarker = null;
    private MapView mapView;
    private List<PolylineResult> lastResults;
    private BikeTypeManager bikeTypeManager;
    private ScoreCalculator scoreCalculator;
    private Context context;
    private RoadNetwork roadNetwork;

    public RouteManager(MapView mapView) {
        this.mapView = mapView;
        this.context = mapView.getContext();
    }

    public void setDependencies(BikeTypeManager bikeTypeManager, ScoreCalculator scoreCalculator) {
        this.bikeTypeManager = bikeTypeManager;
        this.scoreCalculator = scoreCalculator;
    }

    public void setLastResults(List<PolylineResult> results) {
        this.lastResults = results;
        // Build road network graph whenever roads are updated
        if (results != null && !results.isEmpty()) {
            roadNetwork = new RoadNetwork(results);
            Log.d(TAG, "Built road network with " + roadNetwork.nodes.size() + " nodes");
        }
    }

    public void addPointToRoute(GeoPoint tappedPoint) {
        GeoPoint snappedPoint = snapToNearestRoad(tappedPoint);
        // Add visual feedback marker at snap location
        showSnapMarker(snappedPoint);

        if (drawnRoute.isEmpty()) {
            drawnRoute.add(snappedPoint);
            updateRouteDisplay();
            return;
        }

        GeoPoint lastPoint = drawnRoute.get(drawnRoute.size() - 1);
        // Use A* pathfinding through road network
        List<GeoPoint> path = findPathAStar(lastPoint, snappedPoint);

        if (path != null && !path.isEmpty()) {
            Log.d(TAG, "Found path with " + path.size() + " points");
            drawnRoute.addAll(path);
        } else {
            // Fallback to straight line only if pathfinding completely fails
            Log.d(TAG, "Pathfinding failed, using straight line");
            addStraightLine(lastPoint, snappedPoint);
        }

        updateRouteDisplay();
    }

    private void showSnapMarker(GeoPoint point) {
        Marker snapMarker = new Marker(mapView);
        snapMarker.setPosition(point);
        snapMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        snapMarker.setIcon(context.getResources().getDrawable(android.R.drawable.presence_online));
        mapView.getOverlays().add(snapMarker);
        mapView.invalidate();

        // Remove marker after 1 second
        new android.os.Handler().postDelayed(() -> {
            mapView.getOverlays().remove(snapMarker);
            mapView.invalidate();
        }, 1000);
    }

    private List<GeoPoint> findPathAStar(GeoPoint start, GeoPoint end) {
        if (roadNetwork == null || roadNetwork.nodes.isEmpty()) {
            return null;
        }

        // Find nearest nodes to start and end
        RoadNode startNode = roadNetwork.findNearestNode(start);
        RoadNode endNode = roadNetwork.findNearestNode(end);

        if (startNode == null || endNode == null) {
            Log.d(TAG, "Could not find start/end nodes in network");
            return null;
        }

        // A* algorithm
        PriorityQueue<PathNode> openSet = new PriorityQueue<>((a, b) ->
                Double.compare(a.fScore, b.fScore));

        Map<RoadNode, PathNode> allNodes = new HashMap<>();
        Set<RoadNode> closedSet = new HashSet<>();

        PathNode startPath = new PathNode(startNode, null, 0,
                startNode.point.distanceToAsDouble(endNode.point));
        openSet.add(startPath);
        allNodes.put(startNode, startPath);

        int iterations = 0;
        int maxIterations = 5000;

        while (!openSet.isEmpty() && iterations < maxIterations) {
            iterations++;
            PathNode current = openSet.poll();

            if (current.node == endNode) {
                // Found path! Reconstruct it
                Log.d(TAG, "Path found in " + iterations + " iterations");
                return reconstructPath(current, start, end);
            }

            closedSet.add(current.node);

            // Check all neighbors
            for (RoadEdge edge : current.node.edges) {
                RoadNode neighbor = edge.to;

                if (closedSet.contains(neighbor)) continue;

                double tentativeG = current.gScore + edge.distance;

                PathNode neighborPath = allNodes.get(neighbor);

                if (neighborPath == null) {
                    double h = neighbor.point.distanceToAsDouble(endNode.point);
                    neighborPath = new PathNode(neighbor, current, tentativeG, tentativeG + h);
                    allNodes.put(neighbor, neighborPath);
                    openSet.add(neighborPath);
                } else if (tentativeG < neighborPath.gScore) {
                    neighborPath.gScore = tentativeG;
                    neighborPath.fScore = tentativeG + neighbor.point.distanceToAsDouble(endNode.point);
                    neighborPath.parent = current;
                    openSet.remove(neighborPath);
                    openSet.add(neighborPath);
                }
            }
        }

        Log.d(TAG, "No path found after " + iterations + " iterations");
        return null;
    }

    private List<GeoPoint> reconstructPath(PathNode endPath, GeoPoint actualStart, GeoPoint actualEnd) {
        List<RoadNode> nodePath = new ArrayList<>();
        PathNode current = endPath;

        while (current != null) {
            nodePath.add(current.node);
            current = current.parent;
        }

        Collections.reverse(nodePath);

        // Now convert node path to full point path following road geometry
        List<GeoPoint> fullPath = new ArrayList<>();

        for (int i = 0; i < nodePath.size() - 1; i++) {
            RoadNode from = nodePath.get(i);
            RoadNode to = nodePath.get(i + 1);

            // Find the edge connecting these nodes
            RoadEdge connectingEdge = null;
            for (RoadEdge edge : from.edges) {
                if (edge.to == to) {
                    connectingEdge = edge;
                    break;
                }
            }

            if (connectingEdge != null && connectingEdge.road != null) {
                // Add the actual road geometry points
                List<GeoPoint> roadPoints = connectingEdge.road.getPoints();

                // Find closest indices to from and to points
                int startIdx = findClosestIndex(roadPoints, from.point);
                int endIdx = findClosestIndex(roadPoints, to.point);

                // Add points in correct order
                if (startIdx < endIdx) {
                    for (int j = startIdx; j <= endIdx && j < roadPoints.size(); j++) {
                        fullPath.add(roadPoints.get(j));
                    }
                } else {
                    for (int j = startIdx; j >= endIdx && j >= 0; j--) {
                        fullPath.add(roadPoints.get(j));
                    }
                }
            }
        }

        return fullPath.isEmpty() ? null : fullPath;
    }

    private int findClosestIndex(List<GeoPoint> points, GeoPoint target) {
        int closestIdx = 0;
        double minDist = Double.MAX_VALUE;

        for (int i = 0; i < points.size(); i++) {
            double dist = points.get(i).distanceToAsDouble(target);
            if (dist < minDist) {
                minDist = dist;
                closestIdx = i;
            }
        }

        return closestIdx;
    }

    private void addStraightLine(GeoPoint from, GeoPoint to) {
        double distance = from.distanceToAsDouble(to);
        int numSegments = Math.max(1, (int)(distance / 30));

        for (int i = 1; i <= numSegments; i++) {
            double ratio = (double) i / numSegments;
            double lat = from.getLatitude() + ratio * (to.getLatitude() - from.getLatitude());
            double lon = from.getLongitude() + ratio * (to.getLongitude() - from.getLongitude());
            drawnRoute.add(new GeoPoint(lat, lon));
        }
    }

    private GeoPoint snapToNearestRoad(GeoPoint clicked) {
        if (lastResults == null || lastResults.isEmpty()) {
            return clicked;
        }

        GeoPoint bestPoint = null;
        double minDist = Double.MAX_VALUE;

        // Check all road segments
        for (PolylineResult pr : lastResults) {
            List<GeoPoint> pts = pr.getPoints();
            for (int i = 0; i < pts.size() - 1; i++) {
                GeoPoint a = pts.get(i);
                GeoPoint b = pts.get(i + 1);

                GeoPoint proj = projectOntoSegment(clicked, a, b);
                double dist = clicked.distanceToAsDouble(proj);

                if (dist < minDist) {
                    minDist = dist;
                    bestPoint = proj;
                }
            }
        }

        if (minDist <= 500.0 && bestPoint != null) {
            Log.d(TAG, "Snapped to road " + minDist + "m away");
            return bestPoint;
        }

        Log.d(TAG, "No road within 300m, using clicked point");
        return clicked;
    }

    private GeoPoint projectOntoSegment(GeoPoint p, GeoPoint a, GeoPoint b) {
        double ax = a.getLongitude(), ay = a.getLatitude();
        double bx = b.getLongitude(), by = b.getLatitude();
        double px = p.getLongitude(), py = p.getLatitude();

        double dx = bx - ax, dy = by - ay;
        if (dx == 0 && dy == 0) return a;

        double t = ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy);
        t = Math.max(0, Math.min(1, t));

        return new GeoPoint(ay + t * dy, ax + t * dx);
    }

    public void undoLastSegment() {
        if (drawnRoute.isEmpty()) return;

        int startSize = drawnRoute.size();

        if (startSize == 1) {
            drawnRoute.clear();
        } else {
            GeoPoint lastPoint = drawnRoute.get(startSize - 1);
            int removeCount = 0;

            for (int i = startSize - 2; i >= 0 && removeCount < 100; i--) {
                double dist = drawnRoute.get(i).distanceToAsDouble(lastPoint);
                removeCount++;
                if (dist > 300) break;
            }

            for (int i = 0; i < removeCount && !drawnRoute.isEmpty(); i++) {
                drawnRoute.remove(drawnRoute.size() - 1);
            }
        }

        updateRouteDisplay();
        Toast.makeText(context, "Segment removed", Toast.LENGTH_SHORT).show();
    }

    private void updateRouteDisplay() {
        if (drawnPolyline != null) {
            mapView.getOverlays().remove(drawnPolyline);
        }
        if (startMarker != null) {
            mapView.getOverlays().remove(startMarker);
        }
        if (endMarker != null) {
            mapView.getOverlays().remove(endMarker);
        }

        if (!drawnRoute.isEmpty()) {
            drawnPolyline = new Polyline();
            drawnPolyline.setPoints(new ArrayList<>(drawnRoute));
            drawnPolyline.setColor(Color.BLUE);
            drawnPolyline.setWidth(5.0f);
            mapView.getOverlays().add(drawnPolyline);

            startMarker = new Marker(mapView);
            startMarker.setPosition(drawnRoute.get(0));
            startMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            startMarker.setTitle("Start");
            startMarker.setIcon(context.getResources().getDrawable(android.R.drawable.ic_menu_mylocation));
            mapView.getOverlays().add(startMarker);

            if (drawnRoute.size() > 1) {
                endMarker = new Marker(mapView);
                endMarker.setPosition(drawnRoute.get(drawnRoute.size() - 1));
                endMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                endMarker.setTitle("End");
                endMarker.setIcon(context.getResources().getDrawable(android.R.drawable.ic_menu_compass));
                mapView.getOverlays().add(endMarker);
            }
        }

        mapView.invalidate();
    }

    public List<GeoPoint> getDrawnRoute() {
        return new ArrayList<>(drawnRoute);
    }

    public boolean hasRoute() {
        return !drawnRoute.isEmpty();
    }

    public void clearRoute() {
        drawnRoute.clear();
        updateRouteDisplay();
    }

    // Road network classes
    private static class RoadNetwork {
        Map<String, RoadNode> nodes = new HashMap<>();

        RoadNetwork(List<PolylineResult> roads) {

            buildNetwork(roads);
        }

        private void buildNetwork(List<PolylineResult> roads) {
            for (PolylineResult road : roads) {
                List<GeoPoint> points = road.getPoints();

                // Add edges for each segment
                for (int i = 0; i < points.size() - 1; i++) {
                    GeoPoint p1 = points.get(i);
                    GeoPoint p2 = points.get(i + 1);

                    RoadNode n1 = getOrCreateNode(p1);
                    RoadNode n2 = getOrCreateNode(p2);

                    double distance = p1.distanceToAsDouble(p2);

                    // Add bidirectional edges
                    n1.edges.add(new RoadEdge(n2, distance, road));
                    n2.edges.add(new RoadEdge(n1, distance, road));
                }
            }
        }

        private RoadNode getOrCreateNode(GeoPoint point) {
            String key = String.format("%.6f,%.6f", point.getLatitude(), point.getLongitude());
            return nodes.computeIfAbsent(key, k -> new RoadNode(point));
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

            return (minDist < 150) ? nearest : null;
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
        double distance;
        PolylineResult road;

        RoadEdge(RoadNode to, double distance, PolylineResult road) {
            this.to = to;
            this.distance = distance;
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
}