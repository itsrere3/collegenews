package com.example.itcnews; // The package name for this application

import android.content.Intent; // Used to move from one screen to another
import android.content.SharedPreferences; // Used to save small pieces of data like login status
import android.os.Bundle; // Used to pass data when a screen starts
import android.os.CountDownTimer; // Used to create a timer for locking the login button
import android.text.InputType; // Used to change the type of input (like showing or hiding a password)
import android.view.View; // The base class for all UI elements
import android.widget.Button; // A button that the user can click
import android.widget.EditText; // A field where the user can type text
import android.widget.ImageButton; // A button with an image on it
import android.widget.TextView; // A field that displays text to the user
import androidx.appcompat.app.AppCompatActivity; // The base class for the activity
import org.mindrot.jbcrypt.BCrypt; // A library for securely checking passwords

/**
 * This class handles the login process for users.
 * It checks credentials and manages the login attempts.
 */
public class LoginActivity extends AppCompatActivity {
    private EditText etUsername, etPassword; // Text fields for username and password
    private ImageButton ibEye; // Button to show or hide the password
    private TextView tvError; // Text field to show error messages
    private Button btnLogin; // The login button
    private int failCount = 0; // Counts how many times login failed
    private CountDownTimer lockTimer = null; // A timer to lock the login button
    private boolean locked = false; // Remembers if the login is currently locked
    private boolean pwVisible = false; // Remembers if the password is currently visible

    /**
     * Called when the activity is first created.
     * We initialize the UI components and setup listeners here.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); // Call the parent onCreate method
        setContentView(R.layout.activity_login); // Load the login screen layout

        // Find the UI elements by their IDs
        etUsername = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);
        ibEye = findViewById(R.id.ib_eye);
        tvError = findViewById(R.id.tv_error);
        btnLogin = findViewById(R.id.btn_login);

        // Check if the user is already logged in
        SharedPreferences prefs = getSharedPreferences("cc_session", MODE_PRIVATE);
        if (prefs.getBoolean("logged_in", false)) {
            goToMain(); // If logged in, go directly to the main screen
            return;
        }

        // Toggle the password visibility when the eye button is clicked
        ibEye.setOnClickListener(v -> {
            if (!pwVisible) {
                // Show the password as plain text
                etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                ibEye.setImageResource(android.R.drawable.ic_menu_view); // Change icon to visible
                pwVisible = true;
            } else {
                // Hide the password with dots
                etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                ibEye.setImageResource(android.R.drawable.ic_secure); // Change icon to secure
                pwVisible = false;
            }
            // Keep the cursor at the end of the text
            etPassword.setSelection(etPassword.getText().length());
        });

        // Handle the login button click
        btnLogin.setOnClickListener(v -> {
            if (locked) return; // Do nothing if the button is locked
            String username = etUsername.getText().toString().trim(); // Get the username text
            String password = etPassword.getText().toString(); // Get the password text

            // Check if both fields are filled
            if (username.isEmpty() || password.isEmpty()) {
                showError("Please fill all fields"); // Show error if empty
                return;
            }

            btnLogin.setEnabled(false); // Disable button while checking credentials
            new Thread(() -> {
                // Get the database instance and search for the user
                DatabaseHelper db = DatabaseHelper.getInstance(LoginActivity.this);
                DatabaseHelper.User user = db.findByUsername(username);
                // Check if user exists and the password matches the hash
                boolean ok = user != null && BCrypt.checkpw(password, user.passwordHash);

                // Update the UI after checking
                runOnUiThread(() -> {
                    if (ok) {
                        // Save login information in the session
                        getSharedPreferences("cc_session", MODE_PRIVATE).edit()
                                .putBoolean("logged_in", true)
                                .putInt("user_id", user.userId)
                                .putString("username", user.username)
                                .putString("role", user.role)
                                .apply();
                        goToMain(); // Go to the main screen
                    } else {
                        handleFailure(); // Handle the wrong password case
                    }
                });
            }).start();
        });
    }

    // Handles what happens when a login fails
    private void handleFailure() {
        failCount++; // Increase the failure count
        btnLogin.setEnabled(true); // Enable the button again
        if (failCount >= 3) {
            startLockout(); // Lock the button if too many failures
        } else {
            // Show how many attempts are left
            showError("Invalid credentials. Attempt " + failCount + " of 3");
        }
    }

    /**
     * Starts a timer to lock the login button after three failed attempts.
     * This helps protect the app from unauthorized access.
     */
    private void startLockout() {
        locked = true; // Set locked state to true
        btnLogin.setEnabled(false); // Disable the login button
        // Start a 30-second timer
        lockTimer = new CountDownTimer(30000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                // Show the remaining time to the user
                showError("Locked. Try again in " + (millisUntilFinished / 1000) + "s");
            }
            @Override
            public void onFinish() {
                locked = false; // Unlock the state
                failCount = 0; // Reset the failure count
                btnLogin.setEnabled(true); // Enable the button again
                tvError.setVisibility(View.GONE); // Hide the error message
            }
        }.start();
    }

    // Shows an error message to the user
    private void showError(String msg) {
        tvError.setText(msg);
        tvError.setVisibility(View.VISIBLE);
    }

    // Moves the user to the main screen
    private void goToMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish(); // Close the login screen
    }

    @Override
    protected void onDestroy() {
        super.onDestroy(); // Call the parent onDestroy method
        // Stop the timer if the activity is destroyed to prevent memory leaks
        if (lockTimer != null) lockTimer.cancel();
    }
}
