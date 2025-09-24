package be.kuleuven.gt.grvlfinder;

import android.graphics.Color;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Polyline;
import java.util.ArrayList;
import java.util.List;

public class RouteManager {
    private List<GeoPoint> drawnRoute = new ArrayList<>();
    private Polyline drawnPolyline = null;
    private MapView mapView;
    private List<PolylineResult> lastResults;

    public RouteManager(MapView mapView) {
        this.mapView = mapView;
    }

    public void setLastResults(List<PolylineResult> results) {
        this.lastResults = results;
    }

    public void addPointToRoute(GeoPoint tappedPoint) {
        GeoPoint snapped = snapToNearestRoad(tappedPoint);

        if (!drawnRoute.isEmpty()) {
            GeoPoint lastPoint = drawnRoute.get(drawnRoute.size() - 1);

            new OSRMRoutingService.RouteTask(mapView.getContext(), routedPoints -> {
                drawnRoute.addAll(routedPoints);
                updateRouteDisplay();
            }).execute(lastPoint, snapped);
        } else {
            drawnRoute.add(snapped);
            updateRouteDisplay();
        }
    }

    public void undoLastSegment() {
        if (!drawnRoute.isEmpty()) {
            int removeCount = 1;
            if (drawnRoute.size() > 1) {
                removeCount = drawnRoute.size() - drawnRoute.lastIndexOf(drawnRoute.get(drawnRoute.size() - 2));
            }

            for (int i = 0; i < removeCount; i++) {
                drawnRoute.remove(drawnRoute.size() - 1);
            }

            updateRouteDisplay();
        }
    }

    private void updateRouteDisplay() {
        if (drawnPolyline != null) {
            mapView.getOverlays().remove(drawnPolyline);
        }

        if (!drawnRoute.isEmpty()) {
            drawnPolyline = new Polyline();
            drawnPolyline.setPoints(drawnRoute);
            drawnPolyline.setColor(Color.BLUE);
            drawnPolyline.setWidth(5.0f);
            mapView.getOverlays().add(drawnPolyline);
        } else {
            drawnPolyline = null;
        }

        mapView.invalidate();
    }

    private GeoPoint snapToNearestRoad(GeoPoint clicked) {
        if (lastResults == null) return clicked;

        GeoPoint bestPoint = clicked;
        double minDist = Double.MAX_VALUE;

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

        // Als te ver van een weg, laat gewoon het klikpunt
        if (minDist > 50) return clicked;

        return bestPoint;
    }

    // Projecteer punt P op lijnsegment AB
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
}