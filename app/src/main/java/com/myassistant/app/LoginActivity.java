package com.myassistant.app;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.security.MessageDigest;

public class LoginActivity extends Activity {

    EditText username, password;
    Button login, create;
    TextView error;
    android.content.SharedPreferences sp;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        sp = getSharedPreferences("auth", MODE_PRIVATE);

        if (sp.getBoolean("logged_in", false)) {
            openChat();
            return;
        }

        setContentView(R.layout.activity_login);

        username = findViewById(R.id.username);
        password = findViewById(R.id.password);
        login = findViewById(R.id.login);
        create = findViewById(R.id.createAccount);
        error = findViewById(R.id.error);

        login.setOnClickListener(v -> doLogin());
        create.setOnClickListener(v -> createAccount());
    }

    void createAccount() {
        String u = username.getText().toString().trim();
        String p = password.getText().toString();

        if (u.length() < 3 || p.length() < 6) {
            error.setText("Username 3+ characters aur password 6+ characters rakho.");
            return;
        }

        if (sp.contains("username")) {
            error.setText("Account already exists. Login karo.");
            return;
        }

        sp.edit()
            .putString("username", u)
            .putString("password_hash", hash(p))
            .putBoolean("logged_in", true)
            .apply();

        openChat();
    }

    void doLogin() {
        String u = username.getText().toString().trim();
        String p = password.getText().toString();

        String savedUser = sp.getString("username", "");
        String savedHash = sp.getString("password_hash", "");

        if (savedUser.equals(u) && savedHash.equals(hash(p))) {
            sp.edit().putBoolean("logged_in", true).apply();
            openChat();
        } else {
            error.setText("Username ya password galat hai.");
        }
    }

    String hash(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(value.getBytes("UTF-8"));
            StringBuilder out = new StringBuilder();
            for (byte x : bytes) out.append(String.format("%02x", x));
            return out.toString();
        } catch (Exception e) {
            return "";
        }
    }

    void openChat() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
