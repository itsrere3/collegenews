package com.example.news;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import org.mindrot.jbcrypt.BCrypt;

public class LoginActivity extends AppCompatActivity {
    private EditText etUsername, etPassword;
    private ImageButton ibEye;
    private TextView tvError;
    private Button btnLogin;
    private int failCount = 0;
    private CountDownTimer lockTimer = null;
    private boolean locked = false;
    private boolean pwVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etUsername = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);
        ibEye = findViewById(R.id.ib_eye);
        tvError = findViewById(R.id.tv_error);
        btnLogin = findViewById(R.id.btn_login);

        SharedPreferences prefs = getSharedPreferences("cc_session", MODE_PRIVATE);
        if (prefs.getBoolean("logged_in", false)) {
            goToMain();
            return;
        }

        ibEye.setOnClickListener(v -> {
            if (!pwVisible) {
                etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                ibEye.setImageResource(android.R.drawable.ic_menu_view);
                pwVisible = true;
            } else {
                etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                ibEye.setImageResource(android.R.drawable.ic_secure);
                pwVisible = false;
            }
            etPassword.setSelection(etPassword.getText().length());
        });

        btnLogin.setOnClickListener(v -> {
            if (locked) return;
            String username = etUsername.getText().toString().trim();
            String password = etPassword.getText().toString();

            if (username.isEmpty() || password.isEmpty()) {
                showError("Please fill all fields");
                return;
            }

            btnLogin.setEnabled(false);
            new Thread(() -> {
                DatabaseHelper db = DatabaseHelper.getInstance(LoginActivity.this);
                DatabaseHelper.User user = db.findByUsername(username);
                boolean ok = user != null && BCrypt.checkpw(password, user.passwordHash);

                runOnUiThread(() -> {
                    if (ok) {
                        getSharedPreferences("cc_session", MODE_PRIVATE).edit()
                                .putBoolean("logged_in", true)
                                .putInt("user_id", user.userId)
                                .putString("username", user.username)
                                .putString("role", user.role)
                                .apply();
                        goToMain();
                    } else {
                        handleFailure();
                    }
                });
            }).start();
        });
    }

    private void handleFailure() {
        failCount++;
        btnLogin.setEnabled(true);
        if (failCount >= 3) {
            startLockout();
        } else {
            showError("Invalid credentials. Attempt " + failCount + " of 3");
        }
    }

    private void startLockout() {
        locked = true;
        btnLogin.setEnabled(false);
        lockTimer = new CountDownTimer(30000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                showError("Locked. Try again in " + (millisUntilFinished / 1000) + "s");
            }
            @Override
            public void onFinish() {
                locked = false;
                failCount = 0;
                btnLogin.setEnabled(true);
                tvError.setVisibility(View.GONE);
            }
        }.start();
    }

    private void showError(String msg) {
        tvError.setText(msg);
        tvError.setVisibility(View.VISIBLE);
    }

    private void goToMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (lockTimer != null) lockTimer.cancel();
    }
}
