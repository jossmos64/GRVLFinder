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

    private static final String PREFS_NAME = "grvlPrefs";
    private static final String KEY_LEGAL_ACCEPTED = "legalAccepted";

    private ViewPager2 viewPager;
    private TabLayout tabLayout;
    private Button skipButton;
    private Button nextButton;
    private SurfaceTutorialAdapter adapter;
    private boolean isLegalAccepted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tutorial);

        // Check if legal terms have already been accepted
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        isLegalAccepted = prefs.getBoolean(KEY_LEGAL_ACCEPTED, false);

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

        // Skip button - go to appropriate destination
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
            // Change button text based on whether legal has been accepted
            if (isLegalAccepted) {
                nextButton.setText("Get Started!");
            } else {
                nextButton.setText("Continue to Terms");
            }
        } else {
            nextButton.setText("Next");
        }
    }

    private void finishTutorial() {
        // Route based on whether legal terms have been accepted
        if (isLegalAccepted) {
            // Already accepted - return to MainActivity
            Intent intent = new Intent(TutorialActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
        } else {
            // Not yet accepted - must go to Legal Acceptance
            Intent intent = new Intent(TutorialActivity.this, LegalAcceptanceActivity.class);
            startActivity(intent);
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        if (viewPager.getCurrentItem() == 0) {
            // If legal already accepted, allow going back to MainActivity
            if (isLegalAccepted) {
                finish(); // Return to MainActivity
            } else {
                // First-time user - show exit dialog
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Exit App?")
                        .setMessage("Are you sure you want to exit?")
                        .setPositiveButton("Exit", (dialog, which) -> finishAffinity())
                        .setNegativeButton("Stay", null)
                        .show();
            }
        } else {
            viewPager.setCurrentItem(viewPager.getCurrentItem() - 1);
        }
    }
}