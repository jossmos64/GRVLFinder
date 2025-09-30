package be.kuleuven.gt.grvlfinder;

public enum BikeType {
    RACE_ROAD("Race Bike - Roads", "🚴‍♂️", "Fast rides on asphalt and paved roads"),
    GRAVEL_BIKE("Gravel Bike - Gravel", "🚵‍♂️", "Adventure rides on gravel and unpaved roads"),
    RACE_BIKEPACKING("Bikepacking - Race Bike", "🎒🚴‍♂️", "Long distance touring on paved roads"),
    GRAVEL_BIKEPACKING("Bikepacking - Gravel", "🎒🚵‍♂️", "Long distance touring on gravel roads"),
    CUSTOM("Custom Mode", "⚙️", "Configure your own criteria");

    private final String displayName;
    private final String emoji;
    private final String description;

    BikeType(String displayName, String emoji, String description) {
        this.displayName = displayName;
        this.emoji = emoji;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getEmoji() {
        return emoji;
    }

    public String getDescription() {
        return description;
    }

    public String getFullDisplayName() {
        return emoji + " " + displayName;
    }
}