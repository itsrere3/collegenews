package com.example.news;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RatingBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private RecyclerView    rvResources;
    private int             currentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        currentUserId = getSharedPreferences("cc_session", MODE_PRIVATE)
                .getInt("user_id", -1);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        Spinner spinnerSort = findViewById(R.id.spinner_sort);
        String[] sortLabels = {"Highest Rating", "Lowest Rating", "Latest"};
        ArrayAdapter<String> sa = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, sortLabels);
        sa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSort.setAdapter(sa);

        rvResources = findViewById(R.id.recycler_resources);
        rvResources.setLayoutManager(new LinearLayoutManager(this));

        spinnerSort.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                String[] keys = {"highest", "lowest", "latest"};
                loadResources(keys[pos]);
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        loadResources("highest");
    }

    private void loadResources(String orderBy) {
        new Thread(() -> {
            List<DatabaseHelper.LearningResource> list =
                    DatabaseHelper.getInstance(this).getAllResources(orderBy);
            runOnUiThread(() ->
                    rvResources.setAdapter(
                            new ResourceAdapter(list, currentUserId,
                                    DatabaseHelper.getInstance(this)))
            );
        }).start();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_logout) {
            getSharedPreferences("cc_session", MODE_PRIVATE).edit().clear().apply();
            Intent i = new Intent(this, LoginActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}

class ResourceAdapter extends RecyclerView.Adapter<ResourceAdapter.VH> {
    private final List<DatabaseHelper.LearningResource> items;
    private final int userId;
    private final DatabaseHelper db;

    ResourceAdapter(List<DatabaseHelper.LearningResource> items,
                    int userId, DatabaseHelper db) {
        this.items  = items;
        this.userId = userId;
        this.db     = db;
    }

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
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_resource, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        DatabaseHelper.LearningResource r = items.get(pos);

        h.tvTitle.setText(r.title);
        h.tvCategory.setText(r.category);
        h.tvUrl.setText(r.url);
        h.tvDescription.setText(r.description);
        h.tvAvgRating.setText(h.itemView.getContext().getString(R.string.avg_rating_format, r.avgStarRating));
        h.rbDisplay.setRating((float) r.avgStarRating);

        h.tvUrl.setOnClickListener(v ->
                v.getContext().startActivity(
                        new Intent(Intent.ACTION_VIEW, Uri.parse(r.url)))
        );

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

        h.rvComments.setLayoutManager(new LinearLayoutManager(h.itemView.getContext()));
        h.rvComments.setNestedScrollingEnabled(false);
        h.rvComments.setVisibility(View.GONE);

        refreshExpandLabel(h, r.resourceId);

        h.btnExpand.setOnClickListener(v -> {
            if (h.rvComments.getVisibility() == View.GONE) {
                loadComments(h, r.resourceId);
                h.rvComments.setVisibility(View.VISIBLE);
                h.btnExpand.setText(R.string.hide_comments);
            } else {
                h.rvComments.setVisibility(View.GONE);
                refreshExpandLabel(h, r.resourceId);
            }
        });

        h.btnSubmit.setOnClickListener(v -> {
            String text  = h.etComment.getText().toString().trim();
            int    stars = (int) h.rbInput.getRating();

            if (text.isEmpty()) {
                Toast.makeText(v.getContext(), R.string.please_write_comment, Toast.LENGTH_SHORT).show();
                return;
            }
            if (stars == 0) {
                Toast.makeText(v.getContext(), R.string.please_select_rating, Toast.LENGTH_SHORT).show();
                return;
            }

            h.btnSubmit.setEnabled(false);
            new Thread(() -> {
                boolean ok    = db.insertComment(userId, r.resourceId, text, stars);
                double newAvg = db.getAvgRating(r.resourceId);
                r.avgStarRating = newAvg;

                ((android.app.Activity) v.getContext()).runOnUiThread(() -> {
                    h.btnSubmit.setEnabled(true);
                    if (ok) {
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

    private void loadComments(VH h, int resourceId) {
        new Thread(() -> {
            List<DatabaseHelper.Comment> comments = db.getComments(resourceId);
            ((android.app.Activity) h.itemView.getContext()).runOnUiThread(() -> {
                h.rvComments.setAdapter(new CommentAdapter(comments));
                refreshExpandLabel(h, resourceId);
            });
        }).start();
    }

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

class CommentAdapter extends RecyclerView.Adapter<CommentAdapter.CVH> {
    private final List<DatabaseHelper.Comment> comments;

    CommentAdapter(List<DatabaseHelper.Comment> comments) {
        this.comments = comments;
    }

    static class CVH extends RecyclerView.ViewHolder {
        TextView tvCommentUser, tvCommentStars, tvCommentText;
        CVH(View v) {
            super(v);
            tvCommentUser  = v.findViewById(R.id.tv_comment_user);
            tvCommentStars = v.findViewById(R.id.tv_comment_stars);
            tvCommentText  = v.findViewById(R.id.tv_comment_text);
        }
    }

    @NonNull
    @Override
    public CVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_comment, parent, false);
        return new CVH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull CVH h, int pos) {
        DatabaseHelper.Comment c = comments.get(pos);
        h.tvCommentUser.setText(h.itemView.getContext().getString(R.string.comment_user_format, c.username, c.postedAt));
        h.tvCommentText.setText(c.commentText);

        String filled = h.itemView.getContext().getString(R.string.star_filled);
        String empty = h.itemView.getContext().getString(R.string.star_empty);
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 5; i++)
            sb.append(i <= c.starRating ? filled : empty);
        h.tvCommentStars.setText(sb.toString());
    }

    @Override public int getItemCount() { return comments.size(); }
}
