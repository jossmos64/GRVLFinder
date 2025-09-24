package be.kuleuven.gt.grvlfinder;

import android.widget.CompoundButton;
import android.widget.ToggleButton;
import java.util.ArrayList;
import java.util.List;

public class FilterManager {

    private boolean showGreen = true;
    private boolean showYellow = true;
    private boolean showRed = true;

    private ToggleButton btnGreen, btnYellow, btnRed;
    private FilterCallback callback;

    public interface FilterCallback {
        void onFilterChanged();
    }

    public void setFilterCallback(FilterCallback callback) {
        this.callback = callback;
    }

    // Koppel de ToggleButtons uit de legend
    public void setButtons(ToggleButton green, ToggleButton yellow, ToggleButton red) {
        this.btnGreen = green;
        this.btnYellow = yellow;
        this.btnRed = red;

        // Initialiseer status
        this.showGreen = green.isChecked();
        this.showYellow = yellow.isChecked();
        this.showRed = red.isChecked();

        // Luister naar toggles
        CompoundButton.OnCheckedChangeListener listener = (buttonView, isChecked) -> {
            updateFromButtons();
            if (callback != null) callback.onFilterChanged();
        };

        btnGreen.setOnCheckedChangeListener(listener);
        btnYellow.setOnCheckedChangeListener(listener);
        btnRed.setOnCheckedChangeListener(listener);
    }

    private void updateFromButtons() {
        if (btnGreen != null) showGreen = btnGreen.isChecked();
        if (btnYellow != null) showYellow = btnYellow.isChecked();
        if (btnRed != null) showRed = btnRed.isChecked();
    }

    public List<PolylineResult> applyFilter(List<PolylineResult> results) {
        if (results == null) return new ArrayList<>();
        updateFromButtons();

        List<PolylineResult> filtered = new ArrayList<>();
        for (PolylineResult pr : results) {
            if (pr == null) continue;

            int score = pr.getScore();
            if ((score >= 20 && showGreen) ||
                    (score >= 10 && score < 20 && showYellow) ||
                    (score < 10 && showRed)) {
                filtered.add(pr);
            }
        }
        return filtered;
    }

    // Getters
    public boolean isShowGreen() { return showGreen; }
    public boolean isShowYellow() { return showYellow; }
    public boolean isShowRed() { return showRed; }

    // Setters
    public void setShowGreen(boolean show) { this.showGreen = show; if(btnGreen!=null) btnGreen.setChecked(show);}
    public void setShowYellow(boolean show) { this.showYellow = show; if(btnYellow!=null) btnYellow.setChecked(show);}
    public void setShowRed(boolean show) { this.showRed = show; if(btnRed!=null) btnRed.setChecked(show); }

    // Convenience methods
    public void setAllFilters(boolean green, boolean yellow, boolean red) {
        setShowGreen(green);
        setShowYellow(yellow);
        setShowRed(red);
    }

    public boolean hasAnyFiltersEnabled() {
        return showGreen || showYellow || showRed;
    }
}
