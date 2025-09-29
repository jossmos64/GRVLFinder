package be.kuleuven.gt.grvlfinder;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class SurfaceTutorialAdapter extends RecyclerView.Adapter<SurfaceTutorialAdapter.TutorialViewHolder> {

    private Context context;
    private TutorialPage[] pages;

    public SurfaceTutorialAdapter(Context context) {
        this.context = context;
        this.pages = createTutorialPages();
    }

    private TutorialPage[] createTutorialPages() {
        return new TutorialPage[]{
                new TutorialPage(
                        "Welcome to GRVLFinder! 🚴‍♂️",
                        "Discover the best cycling routes based on surface quality and your bike type.",
                        R.drawable.grvlfinder_logo
                ),
                new TutorialPage(
                        "Asphalt & Paved Roads 🛣️",
                        "Smooth, hard surfaces perfect for road bikes. High speed, low rolling resistance. Ideal for racing and long distance rides on tarmac.",
                        R.drawable.tutorial_concrete
                ),
                new TutorialPage(
                        "Gravel Roads 🪨",
                        "Loose stone surface, perfect for gravel bikes! Moderate rolling resistance. Great for adventure rides and exploring unpaved routes.",
                        R.drawable.tutorial_gravel
                ),
                new TutorialPage(
                        "Compacted Surfaces ⚡",
                        "Firm dirt or fine gravel, well-maintained. Good for both gravel and road bikes. Smooth enough for speed, adventurous enough for fun!",
                        R.drawable.tutorial_compacted
                ),
                new TutorialPage(
                        "Dirt & Earth Tracks 🌍",
                        "Natural unpaved surfaces. Best for mountain bikes or sturdy gravel bikes. Can be challenging after rain but offer true off-road experience.",
                        R.drawable.tutorial_dirt
                ),
                new TutorialPage(
                        "Understanding Road Scores 📊",
                        "Roads are color-coded:\n\n🟢 Green (20+ points): Excellent for your bike type\n🟡 Orange (10-19): Acceptable quality\n🔴 Red (<10): Challenging terrain",
                        R.drawable.tutorial_scoring
                ),
                new TutorialPage(
                        "Choose Your Bike Type 🚵‍♂️",
                        "Select your bike type to get personalized route recommendations:\n\n• Race Bike 🚴‍♂️\n• Gravel Bike 🚵‍♂️\n• Bikepacking modes 🎒\n\nEach mode prioritizes different surface types!",
                        R.drawable.tutorial_bike_types
                ),
                new TutorialPage(
                        "Ready to Explore! 🗺️",
                        "Tap 'Find Gravel' to discover routes in your area. Draw custom routes with the pencil tool. Export to GPX for your GPS device!",
                        R.drawable.grvlfinder_logo
                )
        };
    }

    @NonNull
    @Override
    public TutorialViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_tutorial_page, parent, false);
        return new TutorialViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TutorialViewHolder holder, int position) {
        TutorialPage page = pages[position];
        holder.titleText.setText(page.title);
        holder.descriptionText.setText(page.description);
        holder.imageView.setImageResource(page.imageResId);
    }

    @Override
    public int getItemCount() {
        return pages.length;
    }

    static class TutorialViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        TextView titleText;
        TextView descriptionText;

        public TutorialViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.tutorialImage);
            titleText = itemView.findViewById(R.id.tutorialTitle);
            descriptionText = itemView.findViewById(R.id.tutorialDescription);
        }
    }

    private static class TutorialPage {
        String title;
        String description;
        int imageResId;

        TutorialPage(String title, String description, int imageResId) {
            this.title = title;
            this.description = description;
            this.imageResId = imageResId;
        }
    }
}