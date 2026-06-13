package com.example.itcnews; // The package name for this application

import android.content.Intent; // Used to switch between different screens
import android.net.Uri; // Used to handle web links (URLs)
import android.os.Bundle; // Used to pass data when a screen is created
import android.text.Editable; // Used to handle editable text
import android.text.TextWatcher; // Used to watch for changes in a text field
import android.view.LayoutInflater; // Used to load layout files
import android.view.Menu; // Used to handle the app menu
import android.view.MenuItem; // Used to handle menu items
import android.view.View; // The basic building block for UI components
import android.view.ViewGroup; // A container for other views
import android.widget.AdapterView; // Used to handle clicks on items in a list
import android.widget.ArrayAdapter; // Used to provide data for a spinner (dropdown)
import android.widget.Button;
import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.widget.ImageView;
import android.widget.EditText; // A text input field
import android.widget.RatingBar; // A bar for selecting or showing a star rating
import android.widget.Spinner; // A dropdown selection menu
import android.widget.TextView; // A component for showing text
import android.widget.Toast; // A small message that pops up on the screen
import androidx.annotation.NonNull; // Indicates that a value cannot be null
import androidx.appcompat.app.AppCompatActivity; // The base class for modern Android activities
import androidx.appcompat.widget.Toolbar; // A toolbar for the top of the screen
import androidx.recyclerview.widget.LinearLayoutManager; // Manages the layout of a RecyclerView
import androidx.recyclerview.widget.RecyclerView; // A component for showing a long list of items
import java.util.List; // A standard interface for lists

/**
 * This is the main screen of the application.
 * It displays a list of resources and allows users to sort them.
 */
public class MainActivity extends AppCompatActivity {

    private RecyclerView rvResources; // List to show all items
    private int currentUserId; // Current logged-in user

    // UI elements for adding a new resource
    private EditText etNewTitle, etNewCategory, etNewUrl, etNewDescription, etNewComment;
    private RatingBar rbNewRating;
    private Button btnAddResource;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); // Call the parent onCreate method
        setContentView(R.layout.activity_main); // Set the layout for this screen

        // Initialize UI elements for adding new resource
        etNewTitle = findViewById(R.id.et_new_title);
        etNewCategory = findViewById(R.id.et_new_category);
        etNewUrl = findViewById(R.id.et_new_url);
        etNewDescription = findViewById(R.id.et_new_description);
        etNewComment = findViewById(R.id.et_new_comment);
        rbNewRating = findViewById(R.id.rb_new_rating);
        btnAddResource = findViewById(R.id.btn_add_resource);

        // Get the logged-in user's ID from the app's saved data
        currentUserId = getSharedPreferences("cc_session", MODE_PRIVATE)
                .getInt("user_id", -1);

        // Set up the header menu (logout)
        ImageView btnMenu = findViewById(R.id.btn_menu);
        btnMenu.setOnClickListener(v -> {
            // Show a simple popup or just logout for now
            getSharedPreferences("cc_session", MODE_PRIVATE).edit().clear().apply();
            Intent i = new Intent(this, LoginActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
        });

        // Set up the spinner for sorting (styled as a chip)
        Spinner spinnerSort = findViewById(R.id.spinner_sort);
        String[] sortLabels = {"Newest First", "Highest Rating", "Lowest Rating"};
        ArrayAdapter<String> sa = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, sortLabels);
        sa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSort.setAdapter(sa);

        // Set up the RecyclerView to show the list of resources
        rvResources = findViewById(R.id.recycler_resources);
        rvResources.setLayoutManager(new LinearLayoutManager(this));

        // When the user selects a sorting option, reload the resources
        spinnerSort.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                // Pos 0: Newest First -> "latest"
                // Pos 1: Highest Rating -> "highest"
                // Pos 2: Lowest Rating -> "lowest"
                String[] keys = {"latest", "highest", "lowest"};
                loadResources(keys[pos]); // Load data based on selection
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        // Logic for adding a new resource
        btnAddResource.setOnClickListener(v -> {
            String title = etNewTitle.getText().toString().trim();
            String cat = etNewCategory.getText().toString().trim();
            String url = etNewUrl.getText().toString().trim();
            String desc = etNewDescription.getText().toString().trim();
            String comment = etNewComment.getText().toString().trim();
            int stars = (int) rbNewRating.getRating();

            if (title.isEmpty() || cat.isEmpty() || url.isEmpty() || desc.isEmpty()) {
                Toast.makeText(this, "Please fill all required fields", Toast.LENGTH_SHORT).show();
                return;
            }

            DatabaseHelper db = DatabaseHelper.getInstance(this);
            // Insert resource and get its ID
            long resId = db.insertResource(title, cat, url, desc, (double) stars);
            if (resId != -1) {
                // If there's an initial comment, insert it too
                if (!comment.isEmpty() || stars > 0) {
                    db.insertComment(currentUserId, (int) resId, comment, stars);
                }
                Toast.makeText(this, "Resource added successfully!", Toast.LENGTH_SHORT).show();
                // Clear fields
                etNewTitle.setText("");
                etNewCategory.setText("");
                etNewUrl.setText("");
                etNewDescription.setText("");
                etNewComment.setText("");
                rbNewRating.setRating(0);
                // Reload list
                loadResources("latest");
            } else {
                Toast.makeText(this, "Error adding resource", Toast.LENGTH_SHORT).show();
            }
        });

        // Load the resources from the database when the activity starts
        loadResources("latest");
    }

    /**
     * Loads the resources in a background thread to keep the UI smooth.
     */
    private void loadResources(String orderBy) {
        new Thread(() -> {
            // Get the list of resources from the database
            List<DatabaseHelper.LearningResource> list =
                    DatabaseHelper.getInstance(this).getAllResources(orderBy);
            // Update the UI on the main thread
            runOnUiThread(() ->
                    rvResources.setAdapter(
                            new ResourceAdapter(list, currentUserId,
                                    DatabaseHelper.getInstance(this)))
            );
        }).start();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Load the menu layout into the toolbar
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle menu item clicks
        if (item.getItemId() == R.id.action_logout) {
            // Clear user session and go back to the login screen
            getSharedPreferences("cc_session", MODE_PRIVATE).edit().clear().apply();
            Intent i = new Intent(this, LoginActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}

/**
 * This adapter manages the list of resources and handles user interactions like submitting comments.
 */
class ResourceAdapter extends RecyclerView.Adapter<ResourceAdapter.VH> {
    private final List<DatabaseHelper.LearningResource> items; // The list of resource items
    private final int userId; // The ID of the current user
    private final DatabaseHelper db; // The database helper instance

    ResourceAdapter(List<DatabaseHelper.LearningResource> items,
                    int userId, DatabaseHelper db) {
        this.items  = items;
        this.userId = userId;
        this.db     = db;
    }

    // This class holds the views for each item in the list
    static class VH extends RecyclerView.ViewHolder {
        TextView     tvTitle, tvCategory, tvUrl, tvDescription,
                tvAvgRating, tvCharCount, btnExpand;
        RatingBar    rbDisplay, rbInput;
        EditText     etComment;
        Button       btnSubmit;
        RecyclerView rvComments;
        TextWatcher  commentTextWatcher;

        VH(View v) {
            super(v);
            // Find all the views in the item layout
            tvTitle       = v.findViewById(R.id.tv_title);
            tvCategory    = v.findViewById(R.id.tv_category);
            tvUrl         = v.findViewById(R.id.tv_url);
            tvDescription = v.findViewById(R.id.tv_description);
            tvAvgRating   = v.findViewById(R.id.tv_avg_rating);
            tvCharCount   = v.findViewById(R.id.tv_char_count);
            rbDisplay     = v.findViewById(R.id.rating_bar_display);
            rbInput       = v.findViewById(R.id.rating_bar_input);
            etComment     = v.findViewById(R.id.et_comment);
            btnSubmit     = v.findViewById(R.id.btn_submit);
            btnExpand     = v.findViewById(R.id.btn_expand);
            rvComments    = v.findViewById(R.id.rv_comments);
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Create a new view for a list item
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_resource, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        // Get the data for the current item
        DatabaseHelper.LearningResource r = items.get(pos);

        // Set the text and ratings for the item views
        h.tvTitle.setText(r.title);
        h.tvCategory.setText(r.category);
        h.tvUrl.setText(r.url);
        h.tvDescription.setText(r.description);
        h.tvAvgRating.setText(h.itemView.getContext().getString(R.string.avg_rating_format, r.avgStarRating));
        h.rbDisplay.setRating((float) r.avgStarRating);

        // Open the URL in a browser when the link is clicked
        h.tvUrl.setOnClickListener(v ->
                v.getContext().startActivity(
                        new Intent(Intent.ACTION_VIEW, Uri.parse(r.url)))
        );

        // Watch the comment input field and update the character count
        if (h.commentTextWatcher != null) {
            h.etComment.removeTextChangedListener(h.commentTextWatcher);
        }
        h.commentTextWatcher = new TextWatcher() {
            public void afterTextChanged(Editable s) {
                h.tvCharCount.setText(h.itemView.getContext().getString(R.string.char_count_format, s.length(), 300));
            }
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
        };
        h.etComment.addTextChangedListener(h.commentTextWatcher);

        // Set up the comments list for this resource
        h.rvComments.setLayoutManager(new LinearLayoutManager(h.itemView.getContext()));
        h.rvComments.setNestedScrollingEnabled(false);
        h.rvComments.setVisibility(View.GONE);

        // Refresh the text for the "Show Comments" button
        refreshExpandLabel(h, r.resourceId);

        // Show or hide comments when the button is clicked
        h.btnExpand.setOnClickListener(v -> {
            if (h.rvComments.getVisibility() == View.GONE) {
                loadComments(h, r.resourceId); // Load and show comments
                h.rvComments.setVisibility(View.VISIBLE);
                h.btnExpand.setText(R.string.hide_comments);
            } else {
                h.rvComments.setVisibility(View.GONE); // Hide comments
                refreshExpandLabel(h, r.resourceId);
            }
        });

        // Handle comment submission
        h.btnSubmit.setOnClickListener(v -> {
            String text  = h.etComment.getText().toString().trim();
            int    stars = (int) h.rbInput.getRating();

            // Check if the comment or rating is empty
            if (text.isEmpty()) {
                Toast.makeText(v.getContext(), R.string.please_write_comment, Toast.LENGTH_SHORT).show();
                return;
            }
            if (stars == 0) {
                Toast.makeText(v.getContext(), R.string.please_select_rating, Toast.LENGTH_SHORT).show();
                return;
            }

            h.btnSubmit.setEnabled(false); // Disable button while saving
            new Thread(() -> {
                // Save the comment to the database
                boolean ok    = db.insertComment(userId, r.resourceId, text, stars);
                double newAvg = db.getAvgRating(r.resourceId); // Calculate new average rating
                r.avgStarRating = newAvg;

                // Update the UI after saving
                ((android.app.Activity) v.getContext()).runOnUiThread(() -> {
                    h.btnSubmit.setEnabled(true);
                    if (ok) {
                        // Clear input fields and refresh UI
                        h.etComment.setText("");
                        h.rbInput.setRating(0);
                        h.tvCharCount.setText(v.getContext().getString(R.string.char_count_format, 0, 300));
                        h.tvAvgRating.setText(v.getContext().getString(R.string.avg_rating_format, newAvg));
                        h.rbDisplay.setRating((float) newAvg);
                        loadComments(h, r.resourceId);
                        h.rvComments.setVisibility(View.VISIBLE);
                        Toast.makeText(v.getContext(), R.string.comment_submitted, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(v.getContext(), R.string.failed_to_submit, Toast.LENGTH_SHORT).show();
                    }
                });
            }).start();
        });
    }

    // Loads comments for a specific resource from the database
    private void loadComments(VH h, int resourceId) {
        new Thread(() -> {
            List<DatabaseHelper.Comment> comments = db.getComments(resourceId);
            ((android.app.Activity) h.itemView.getContext()).runOnUiThread(() -> {
                h.rvComments.setAdapter(new CommentAdapter(comments));
                refreshExpandLabel(h, resourceId);
            });
        }).start();
    }

    // Updates the label for the expand button with the current number of comments
    private void refreshExpandLabel(VH h, int resourceId) {
        new Thread(() -> {
            int n = db.getCommentCount(resourceId);
            ((android.app.Activity) h.itemView.getContext()).runOnUiThread(() -> {
                String label = h.itemView.getContext().getString(
                        n == 1 ? R.string.show_comments_singular : R.string.show_comments_plural, n);
                h.btnExpand.setText(label);
            });
        }).start();
    }

    @Override public int getItemCount() { return items.size(); }
}

/**
 * This adapter manages the display of individual comments.
 */
class CommentAdapter extends RecyclerView.Adapter<CommentAdapter.CVH> {
    private final List<DatabaseHelper.Comment> comments; // The list of comments to show

    CommentAdapter(List<DatabaseHelper.Comment> comments) {
        this.comments = comments;
    }

    // Holds the views for each comment item
    static class CVH extends RecyclerView.ViewHolder {
        TextView tvCommentUser, tvCommentText;
        RatingBar rbComment;
        CVH(View v) {
            super(v);
            tvCommentUser  = v.findViewById(R.id.tv_comment_user);
            rbComment      = v.findViewById(R.id.rating_bar_comment);
            tvCommentText  = v.findViewById(R.id.tv_comment_text);
        }
    }

    @NonNull
    @Override
    public CVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Load the comment item layout
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_comment, parent, false);
        return new CVH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull CVH h, int pos) {
        // Get the data for the current comment
        DatabaseHelper.Comment c = comments.get(pos);
        // Show the username, date, and comment text
        h.tvCommentUser.setText(h.itemView.getContext().getString(R.string.comment_user_format, c.username, c.postedAt));
        h.tvCommentText.setText(c.commentText);

        // Show the star rating using RatingBar
        h.rbComment.setRating(c.starRating);
    }

    @Override public int getItemCount() { return comments.size(); }
}
