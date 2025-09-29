package be.kuleuven.gt.grvlfinder;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class TutorialActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private TabLayout tabLayout;
    private Button skipButton;
    private Button nextButton;
    private SurfaceTutorialAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tutorial);

        viewPager = findViewById(R.id.tutorialViewPager);
        tabLayout = findViewById(R.id.tabLayout);
        skipButton = findViewById(R.id.skipButton);
        nextButton = findViewById(R.id.nextButton);

        // Setup adapter with tutorial pages
        adapter = new SurfaceTutorialAdapter(this);
        viewPager.setAdapter(adapter);

        // Link tab layout with view pager
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            // Tab dots are automatically created
        }).attach();

        // Handle page changes
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                updateButtons(position);
            }
        });

        // Skip button - go to main activity
        skipButton.setOnClickListener(v -> finishTutorial());

        // Next button - go to next page or finish
        nextButton.setOnClickListener(v -> {
            if (viewPager.getCurrentItem() < adapter.getItemCount() - 1) {
                viewPager.setCurrentItem(viewPager.getCurrentItem() + 1);
            } else {
                finishTutorial();
            }
        });

        updateButtons(0);
    }

    private void updateButtons(int position) {
        if (position == adapter.getItemCount() - 1) {
            nextButton.setText("Get Started!");
        } else {
            nextButton.setText("Next");
        }
    }

    private void finishTutorial() {
        Intent intent = new Intent(TutorialActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    @Override
    public void onBackPressed() {
        if (viewPager.getCurrentItem() == 0) {
            super.onBackPressed();
        } else {
            viewPager.setCurrentItem(viewPager.getCurrentItem() - 1);
        }
    }
}