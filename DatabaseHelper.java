package com.example.itcnews; // The package name must match the folder structure

import android.content.ContentValues; // Used to put data into database tables
import android.content.Context; // Used to know about the application environment
import android.database.Cursor; // Used to read data from a database query
import android.database.sqlite.SQLiteDatabase; // The class for SQLite database
import android.database.sqlite.SQLiteOpenHelper; // A helper to manage database creation and versions
import org.mindrot.jbcrypt.BCrypt; // A library for password security (hashing)
import java.util.ArrayList; // A list that can grow in size
import java.util.List; // A standard interface for lists

/**
 * This class helps manage the local SQLite database.
 * It handles creating tables and performing CRUD operations for users, resources, and comments.
 */
public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "news_db"; // The name of the database file
    private static final int DATABASE_VERSION = 2; // The version of the database schema
    private static DatabaseHelper instance; // A static instance for the Singleton pattern

    // Returns the single instance of this class
    public static synchronized DatabaseHelper getInstance(Context context) {
        if (instance == null) { // Create the instance if it does not exist
            instance = new DatabaseHelper(context.getApplicationContext());
        }
        return instance; // Return the instance
    }

    // Constructor for the database helper
    private DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    /**
     * Called when the database is created for the first time.
     * This is where we create the tables and add some initial data.
     */
    @Override
    public void onCreate(SQLiteDatabase db) {
        // Create the users table
        db.execSQL("CREATE TABLE users (user_id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE, password_hash TEXT, role TEXT)");
        // Create the resources table
        db.execSQL("CREATE TABLE resources (resource_id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT, category TEXT, url TEXT, description TEXT, avg_star_rating REAL, added_date TEXT)");
        // Create the comments table
        db.execSQL("CREATE TABLE comments (comment_id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER, resource_id INTEGER, comment_text TEXT, star_rating INTEGER, posted_at DATETIME DEFAULT CURRENT_TIMESTAMP)");

        // Hash the admin password for security
        String hash = BCrypt.hashpw("admin123", BCrypt.gensalt());
        // Insert a default admin user
        db.execSQL("INSERT INTO users (username, password_hash, role) VALUES ('admin', '" + hash + "', 'admin')");

        // Insert some initial data into the resources table
        db.execSQL("INSERT INTO resources (title, category, url, description, avg_star_rating, added_date) VALUES ('FIFA Training Centre', 'Coaching', 'https://www.fifatrainingcentre.com', 'Official FIFA platform for coaching and performance analysis.', 5.0, '2026-01-10 10:00:00')");
        db.execSQL("INSERT INTO resources (title, category, url, description, avg_star_rating, added_date) VALUES ('MyFitnessPal Nutrition', 'Nutrition', 'https://www.myfitnesspal.com', 'Comprehensive nutrition and calorie tracking platform.', 4.7, '2026-01-12 11:30:00')");
        db.execSQL("INSERT INTO resources (title, category, url, description, avg_star_rating, added_date) VALUES ('NSCA Strength & Conditioning', 'Fitness', 'https://www.nsca.com', 'Evidence-based strength and conditioning research and education.', 4.1, '2026-01-14 9:00:00')");
        db.execSQL("INSERT INTO resources (title, category, url, description, avg_star_rating, added_date) VALUES ('Sports Injury Clinic', 'Sports Science', 'https://www.sportsinjuryclinic.net', 'Practical guides covering injury prevention and rehabilitation.', 1.0, '2026-01-16 14:00:00')");
    }

    // Called when the database is upgraded
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        db.execSQL("DROP TABLE IF EXISTS users"); // Remove old users table
        db.execSQL("DROP TABLE IF EXISTS resources"); // Remove old resources table
        db.execSQL("DROP TABLE IF EXISTS comments"); // Remove old comments table
        onCreate(db); // Create tables again
    }

    // A class to represent a user
    public static class User {
        public int userId; // The user's ID
        public String username, passwordHash, role; // The user's details
    }

    // A class to represent a resource item
    public static class LearningResource {
        public int resourceId; // The resource ID
        public String title, category, url, description; // Details about the resource
        public double avgStarRating; // The average rating for this resource
    }

    // A class to represent a comment
    public static class Comment {
        public String username, commentText, postedAt; // Details about the comment
        public int starRating; // The rating given in the comment
    }

    /**
     * Adds a new resource to the database.
     * @return the row ID of the newly inserted row, or -1 if an error occurred.
     */
    public long insertResource(String title, String category, String url, String desc, double initialRating) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("title", title);
        cv.put("category", category);
        cv.put("url", url);
        cv.put("description", desc);
        cv.put("avg_star_rating", initialRating);
        cv.put("added_date", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date()));
        return db.insert("resources", null, cv);
    }

    /**
     * Finds a user in the database by their username.
     */
    public User findByUsername(String username) {
        SQLiteDatabase db = getReadableDatabase(); // Open database for reading
        // Query the users table for the given username
        Cursor c = db.rawQuery("SELECT * FROM users WHERE username = ?", new String[]{username});
        if (c.moveToFirst()) { // If a result is found
            User u = new User(); // Create a new User object
            u.userId = c.getInt(0); // Set the ID
            u.username = c.getString(1); // Set the username
            u.passwordHash = c.getString(2); // Set the password hash
            u.role = c.getString(3); // Set the role
            c.close(); // Close the cursor
            return u; // Return the user
        }
        c.close(); // Close the cursor if no user is found
        return null; // Return null
    }

    /**
     * This method returns a list of all resources from the database.
     * You can choose the order (highest rating, lowest rating, or latest).
     */
    public List<LearningResource> getAllResources(String orderBy) {
        List<LearningResource> list = new ArrayList<>(); // Create a new list
        SQLiteDatabase db = getReadableDatabase(); // Open database for reading
        // SQL query to get resources and calculate average rating from comments
        String sql = "SELECT r.resource_id, r.title, r.category, r.url, r.description, " +
                "COALESCE((SELECT AVG(star_rating) FROM comments WHERE resource_id = r.resource_id), r.avg_star_rating) as avg_r " +
                "FROM resources r";

        // Add sorting to the SQL query
        if ("highest".equals(orderBy)) sql += " ORDER BY avg_r DESC";
        else if ("lowest".equals(orderBy)) sql += " ORDER BY avg_r ASC";
        else if ("latest".equals(orderBy)) sql += " ORDER BY r.added_date DESC";

        Cursor c = db.rawQuery(sql, null); // Execute the query
        while (c.moveToNext()) { // Loop through the results
            LearningResource r = new LearningResource(); // Create a new resource object
            r.resourceId = c.getInt(0); // Set the ID
            r.title = c.getString(1); // Set the title
            r.category = c.getString(2); // Set the category
            r.url = c.getString(3); // Set the URL
            r.description = c.getString(4); // Set the description
            r.avgStarRating = c.getDouble(5); // Set the average rating
            list.add(r); // Add the resource to the list
        }
        c.close(); // Close the cursor
        return list; // Return the list
    }

    /**
     * Saves a new comment and a rating for a specific resource.
     * @return true if it was saved successfully, false otherwise.
     */
    public boolean insertComment(int userId, int resId, String text, int stars) {
        SQLiteDatabase db = getWritableDatabase(); // Open database for writing
        ContentValues cv = new ContentValues(); // Create a container for the data
        cv.put("user_id", userId); // Add user ID
        cv.put("resource_id", resId); // Add resource ID
        cv.put("comment_text", text); // Add comment text
        cv.put("star_rating", stars); // Add star rating
        return db.insert("comments", null, cv) != -1; // Insert data and return success status
    }

    /**
     * Gets all the comments for one resource so we can show them in the list.
     */
    public List<Comment> getComments(int resId) {
        List<Comment> list = new ArrayList<>(); // Create a new list
        SQLiteDatabase db = getReadableDatabase(); // Open database for reading
        // Query to get comments and the username of the person who wrote them
        Cursor c = db.rawQuery("SELECT u.username, c.comment_text, c.star_rating, c.posted_at FROM comments c JOIN users u ON c.user_id = u.user_id WHERE c.resource_id = ?", new String[]{String.valueOf(resId)});
        while (c.moveToNext()) { // Loop through results
            Comment co = new Comment(); // Create a new comment object
            co.username = c.getString(0); // Set username
            co.commentText = c.getString(1); // Set comment text
            co.starRating = c.getInt(2); // Set star rating
            co.postedAt = c.getString(3); // Set the time it was posted
            list.add(co); // Add comment to the list
        }
        c.close(); // Close the cursor
        return list; // Return the list
    }

    // Gets the number of comments for a resource
    public int getCommentCount(int resId) {
        SQLiteDatabase db = getReadableDatabase(); // Open database for reading
        // Query to count comments for a specific resource
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM comments WHERE resource_id = ?", new String[]{String.valueOf(resId)});
        int count = 0; // Initialize count to 0
        if (c.moveToFirst()) count = c.getInt(0); // Get count from result
        c.close(); // Close the cursor
        return count; // Return the count
    }

    // Gets the average rating for a resource
    public double getAvgRating(int resId) {
        SQLiteDatabase db = getReadableDatabase(); // Open database for reading
        // Query to calculate average star rating for a specific resource
        Cursor c = db.rawQuery("SELECT AVG(star_rating) FROM comments WHERE resource_id = ?", new String[]{String.valueOf(resId)});
        double avg = 0; // Initialize average to 0
        if (c.moveToFirst()) avg = c.getDouble(0); // Get average from result
        c.close(); // Close the cursor
        return avg; // Return the average
    }
}
