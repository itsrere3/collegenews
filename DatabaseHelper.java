package com.example.news;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.mindrot.jbcrypt.BCrypt;
import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "news_db";
    private static final int DATABASE_VERSION = 2;
    private static DatabaseHelper instance;

    public static synchronized DatabaseHelper getInstance(Context context) {
        if (instance == null) instance = new DatabaseHelper(context.getApplicationContext());
        return instance;
    }

    private DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE users (user_id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE, password_hash TEXT, role TEXT)");
        db.execSQL("CREATE TABLE resources (resource_id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT, category TEXT, url TEXT, description TEXT, avg_star_rating REAL, added_date TEXT)");
        db.execSQL("CREATE TABLE comments (comment_id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER, resource_id INTEGER, comment_text TEXT, star_rating INTEGER, posted_at DATETIME DEFAULT CURRENT_TIMESTAMP)");

        // Seed data
        String hash = BCrypt.hashpw("admin123", BCrypt.gensalt());
        db.execSQL("INSERT INTO users (username, password_hash, role) VALUES ('admin', '" + hash + "', 'admin')");
        
        db.execSQL("INSERT INTO resources (title, category, url, description, avg_star_rating, added_date) VALUES ('FIFA Training Centre', 'Coaching', 'https://www.fifatrainingcentre.com', 'Official FIFA platform for coaching and performance analysis.', 5.0, '2026-01-10 10:00:00')");
        db.execSQL("INSERT INTO resources (title, category, url, description, avg_star_rating, added_date) VALUES ('MyFitnessPal Nutrition', 'Nutrition', 'https://www.myfitnesspal.com', 'Comprehensive nutrition and calorie tracking platform.', 4.7, '2026-01-12 11:30:00')");
        db.execSQL("INSERT INTO resources (title, category, url, description, avg_star_rating, added_date) VALUES ('NSCA Strength & Conditioning', 'Fitness', 'https://www.nsca.com', 'Evidence-based strength and conditioning research and education.', 4.1, '2026-01-14 9:00:00')");
        db.execSQL("INSERT INTO resources (title, category, url, description, avg_star_rating, added_date) VALUES ('Sports Injury Clinic', 'Sports Science', 'https://www.sportsinjuryclinic.net', 'Practical guides covering injury prevention and rehabilitation.', 1.0, '2026-01-16 14:00:00')");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        db.execSQL("DROP TABLE IF EXISTS users");
        db.execSQL("DROP TABLE IF EXISTS resources");
        db.execSQL("DROP TABLE IF EXISTS comments");
        onCreate(db);
    }

    public static class User {
        public int userId;
        public String username, passwordHash, role;
    }

    public static class LearningResource {
        public int resourceId;
        public String title, category, url, description;
        public double avgStarRating;
    }

    public static class Comment {
        public String username, commentText, postedAt;
        public int starRating;
    }

    public User findByUsername(String username) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT * FROM users WHERE username = ?", new String[]{username});
        if (c.moveToFirst()) {
            User u = new User();
            u.userId = c.getInt(0);
            u.username = c.getString(1);
            u.passwordHash = c.getString(2);
            u.role = c.getString(3);
            c.close();
            return u;
        }
        c.close();
        return null;
    }

    public List<LearningResource> getAllResources(String orderBy) {
        List<LearningResource> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        // COALESCE ensures we use the seeded avg_star_rating if no comments exist yet
        String sql = "SELECT r.resource_id, r.title, r.category, r.url, r.description, " +
                     "COALESCE((SELECT AVG(star_rating) FROM comments WHERE resource_id = r.resource_id), r.avg_star_rating) as avg_r " +
                     "FROM resources r";
        
        if ("highest".equals(orderBy)) sql += " ORDER BY avg_r DESC";
        else if ("lowest".equals(orderBy)) sql += " ORDER BY avg_r ASC";
        else if ("latest".equals(orderBy)) sql += " ORDER BY r.added_date DESC";
        
        Cursor c = db.rawQuery(sql, null);
        while (c.moveToNext()) {
            LearningResource r = new LearningResource();
            r.resourceId = c.getInt(0);
            r.title = c.getString(1);
            r.category = c.getString(2);
            r.url = c.getString(3);
            r.description = c.getString(4);
            r.avgStarRating = c.getDouble(5);
            list.add(r);
        }
        c.close();
        return list;
    }

    public boolean insertComment(int userId, int resId, String text, int stars) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("user_id", userId);
        cv.put("resource_id", resId);
        cv.put("comment_text", text);
        cv.put("star_rating", stars);
        return db.insert("comments", null, cv) != -1;
    }

    public List<Comment> getComments(int resId) {
        List<Comment> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT u.username, c.comment_text, c.star_rating, c.posted_at FROM comments c JOIN users u ON c.user_id = u.user_id WHERE c.resource_id = ?", new String[]{String.valueOf(resId)});
        while (c.moveToNext()) {
            Comment co = new Comment();
            co.username = c.getString(0);
            co.commentText = c.getString(1);
            co.starRating = c.getInt(2);
            co.postedAt = c.getString(3);
            list.add(co);
        }
        c.close();
        return list;
    }

    public int getCommentCount(int resId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM comments WHERE resource_id = ?", new String[]{String.valueOf(resId)});
        int count = 0;
        if (c.moveToFirst()) count = c.getInt(0);
        c.close();
        return count;
    }

    public double getAvgRating(int resId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT AVG(star_rating) FROM comments WHERE resource_id = ?", new String[]{String.valueOf(resId)});
        double avg = 0;
        if (c.moveToFirst()) avg = c.getDouble(0);
        c.close();
        return avg;
    }
}
