package be.kuleuven.gt.grvlfinder;

import android.util.Log;
import org.osmdroid.util.GeoPoint;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class GpxParser {
    private static final String TAG = "GpxParser";

    public static class GpxRoute {
        private List<GeoPoint> points;
        private String name;
        private String description;

        public GpxRoute() {
            this.points = new ArrayList<>();
        }

        public List<GeoPoint> getPoints() { return points; }
        public void setPoints(List<GeoPoint> points) { this.points = points; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    public static GpxRoute parseGpxFile(InputStream inputStream) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(inputStream);

        GpxRoute route = new GpxRoute();
        List<GeoPoint> allPoints = new ArrayList<>();

        // Parse tracks (trk)
        NodeList tracks = document.getElementsByTagName("trk");
        for (int i = 0; i < tracks.getLength(); i++) {
            Element track = (Element) tracks.item(i);

            // Get track name if available
            NodeList nameNodes = track.getElementsByTagName("name");
            if (nameNodes.getLength() > 0 && route.getName() == null) {
                route.setName(nameNodes.item(0).getTextContent());
            }

            // Parse track segments
            NodeList segments = track.getElementsByTagName("trkseg");
            for (int j = 0; j < segments.getLength(); j++) {
                Element segment = (Element) segments.item(j);
                NodeList points = segment.getElementsByTagName("trkpt");

                for (int k = 0; k < points.getLength(); k++) {
                    Element point = (Element) points.item(k);
                    double lat = Double.parseDouble(point.getAttribute("lat"));
                    double lon = Double.parseDouble(point.getAttribute("lon"));

                    GeoPoint geoPoint = new GeoPoint(lat, lon);

                    // Try to get elevation if available
                    NodeList elevationNodes = point.getElementsByTagName("ele");
                    if (elevationNodes.getLength() > 0) {
                        try {
                            double elevation = Double.parseDouble(elevationNodes.item(0).getTextContent());
                            geoPoint.setAltitude(elevation);
                        } catch (NumberFormatException e) {
                            geoPoint.setAltitude(0.0);
                        }
                    } else {
                        geoPoint.setAltitude(0.0);
                    }

                    allPoints.add(geoPoint);
                }
            }
        }

        // Parse routes (rte) if no tracks found
        if (allPoints.isEmpty()) {
            NodeList routes = document.getElementsByTagName("rte");
            for (int i = 0; i < routes.getLength(); i++) {
                Element routeElement = (Element) routes.item(i);

                // Get route name if available
                NodeList nameNodes = routeElement.getElementsByTagName("name");
                if (nameNodes.getLength() > 0 && route.getName() == null) {
                    route.setName(nameNodes.item(0).getTextContent());
                }

                NodeList points = routeElement.getElementsByTagName("rtept");
                for (int j = 0; j < points.getLength(); j++) {
                    Element point = (Element) points.item(j);
                    double lat = Double.parseDouble(point.getAttribute("lat"));
                    double lon = Double.parseDouble(point.getAttribute("lon"));

                    GeoPoint geoPoint = new GeoPoint(lat, lon);

                    // Try to get elevation if available
                    NodeList elevationNodes = point.getElementsByTagName("ele");
                    if (elevationNodes.getLength() > 0) {
                        try {
                            double elevation = Double.parseDouble(elevationNodes.item(0).getTextContent());
                            geoPoint.setAltitude(elevation);
                        } catch (NumberFormatException e) {
                            geoPoint.setAltitude(0.0);
                        }
                    } else {
                        geoPoint.setAltitude(0.0);
                    }

                    allPoints.add(geoPoint);
                }
            }
        }

        // Parse waypoints (wpt) as last resort
        if (allPoints.isEmpty()) {
            NodeList waypoints = document.getElementsByTagName("wpt");
            for (int i = 0; i < waypoints.getLength(); i++) {
                Element point = (Element) waypoints.item(i);
                double lat = Double.parseDouble(point.getAttribute("lat"));
                double lon = Double.parseDouble(point.getAttribute("lon"));

                GeoPoint geoPoint = new GeoPoint(lat, lon);

                NodeList elevationNodes = point.getElementsByTagName("ele");
                if (elevationNodes.getLength() > 0) {
                    try {
                        double elevation = Double.parseDouble(elevationNodes.item(0).getTextContent());
                        geoPoint.setAltitude(elevation);
                    } catch (NumberFormatException e) {
                        geoPoint.setAltitude(0.0);
                    }
                } else {
                    geoPoint.setAltitude(0.0);
                }

                allPoints.add(geoPoint);
            }
        }

        route.setPoints(allPoints);

        Log.d(TAG, "Parsed GPX with " + allPoints.size() + " points");
        return route;
    }

    /**
     * Calculate total distance of route in meters
     */
    public static double calculateRouteDistance(List<GeoPoint> points) {
        if (points == null || points.size() < 2) return 0.0;

        double totalDistance = 0.0;
        for (int i = 1; i < points.size(); i++) {
            totalDistance += points.get(i-1).distanceToAsDouble(points.get(i));
        }
        return totalDistance;
    }

    /**
     * Check if route has elevation data
     */
    public static boolean hasElevationData(List<GeoPoint> points) {
        if (points == null || points.isEmpty()) return false;

        // Check if any point has non-zero elevation
        for (GeoPoint point : points) {
            if (point.getAltitude() != 0.0) return true;
        }
        return false;
    }
}