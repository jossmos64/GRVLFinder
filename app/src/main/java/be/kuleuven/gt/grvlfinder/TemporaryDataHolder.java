package be.kuleuven.gt.grvlfinder;

/**
 * Singleton class to temporarily hold GPX route data between activities
 * This is a simple solution for passing complex route data without using Parcelable
 */
public class TemporaryDataHolder {
    private static TemporaryDataHolder instance;
    private GpxParser.GpxRoute route;
    private String routeName;

    private TemporaryDataHolder() {}

    public static TemporaryDataHolder getInstance() {
        if (instance == null) {
            instance = new TemporaryDataHolder();
        }
        return instance;
    }

    public void setRoute(GpxParser.GpxRoute route, String routeName) {
        this.route = route;
        this.routeName = routeName;
    }

    public GpxParser.GpxRoute getRoute() {
        return route;
    }

    public String getRouteName() {
        return routeName;
    }

    public boolean hasRoute() {
        return route != null;
    }

    public void clear() {
        route = null;
        routeName = null;
    }
}