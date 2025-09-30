package be.kuleuven.gt.grvlfinder;

import org.osmdroid.util.GeoPoint;
import java.util.List;
import java.util.Map;

public class PolylineResult {
    private List<GeoPoint> points;
    private int score;
    private Map<String, String> tags;
    private double maxSlopePercent = -1; // -1 means not calculated

    public PolylineResult(List<GeoPoint> points, int score, Map<String, String> tags) {
        this.points = points;
        this.score = score;
        this.tags = tags;
        this.maxSlopePercent = -1; // Will be calculated after elevation data is added
    }

    // Getters and setters
    public List<GeoPoint> getPoints() { return points; }

    public void setPoints(List<GeoPoint> points) {
        this.points = points;
        this.maxSlopePercent = ScoreCalculator.calculateMaxSlopePercent(points);
    }

    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }
    public Map<String, String> getTags() { return tags; }
    public void setTags(Map<String, String> tags) { this.tags = tags; }
    public double getMaxSlopePercent() { return maxSlopePercent; }

    public void setMaxSlope(double maxSlope) {

        this.maxSlopePercent = maxSlope;
    }
}