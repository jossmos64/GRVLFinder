package be.kuleuven.gt.grvlfinder;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;

public class LegendView {
    public static View create(Context context) {
        return LayoutInflater.from(context).inflate(R.layout.view_legend, null, false);
    }
}
