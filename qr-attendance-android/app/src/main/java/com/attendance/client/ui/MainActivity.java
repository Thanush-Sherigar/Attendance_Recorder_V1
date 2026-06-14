package com.attendance.client.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.attendance.client.R;
import com.attendance.client.network.RetrofitClient;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private TextView tvStudentName;
    private TextView tvStudentId;
    private Button btnOpenScanner;
    private Button btnViewHistory;
    
    private final String simulatedDeviceId = "device-pixel9-1ds24mc108";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStudentName = findViewById(R.id.tv_student_name);
        tvStudentId = findViewById(R.id.tv_student_id);
        btnOpenScanner = findViewById(R.id.btn_open_scanner);
        btnViewHistory = findViewById(R.id.btn_view_history);

        // Populate student profile info
        SharedPreferences prefs = getSharedPreferences("SmartAttendancePrefs", Context.MODE_PRIVATE);
        String username = prefs.getString("student_username", "student@dsce.edu.in");
        String displayName = username.split("@")[0];
        tvStudentName.setText(displayName.substring(0, 1).toUpperCase() + displayName.substring(1));
        
        // Setup listeners
        btnOpenScanner.setOnClickListener(v -> startQrScanner());
        btnViewHistory.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, HistoryActivity.class)));
        
        // Toolbar logout
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_logout) {
                logout();
                return true;
            }
            return false;
        });
        toolbar.inflateMenu(R.menu.menu_main);
    }

    private void startQrScanner() {
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.setPrompt("Scan the classroom attendance QR Code");
        integrator.setCameraId(0); // Use back camera
        integrator.setBeepEnabled(true);
        integrator.setBarcodeImageEnabled(true);
        integrator.setOrientationLocked(false);
        integrator.initiateScan();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null) {
            if (result.getContents() == null) {
                Toast.makeText(this, "Scanning cancelled", Toast.LENGTH_SHORT).show();
            } else {
                // Scanned token
                markAttendance(result.getContents());
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void markAttendance(String token) {
        btnOpenScanner.setEnabled(false);
        btnOpenScanner.setText("Submitting...");

        RetrofitClient.getApiService(this).markAttendance(token, simulatedDeviceId)
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                        btnOpenScanner.setEnabled(true);
                        btnOpenScanner.setText("OPEN QR SCANNER");

                        if (response.isSuccessful()) {
                            showSuccessDialog();
                        } else {
                            handleMarkError(response);
                        }
                    }

                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {
                        btnOpenScanner.setEnabled(true);
                        btnOpenScanner.setText("OPEN QR SCANNER");
                        showErrorDialog("Cannot Mark Attendance", "Network communication failed: " + t.getMessage());
                    }
                });
    }

    private void handleMarkError(Response<ResponseBody> response) {
        try {
            String errorMsg = response.errorBody() != null ? response.errorBody().string() : "Validation failed.";
            int code = response.code();

            if (code == 410) {
                // TTL Closed - Show the beautiful Page 34 layout Dialog
                showTimeWindowClosedDialog();
            } else {
                showErrorDialog("Cannot Mark Attendance", errorMsg);
            }
        } catch (IOException e) {
            showErrorDialog("Cannot Mark Attendance", "Validation failed.");
        }
    }

    private void showSuccessDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Attendance Marked")
                .setMessage("Your attendance has been recorded successfully in the database ledger.")
                .setPositiveButton("OK", null)
                .show();
    }

    private void showTimeWindowClosedDialog() {
        // Build the Page 34 layout
        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.US);
        String currentTimeStr = sdf.format(new Date());

        new MaterialAlertDialogBuilder(this)
                .setTitle("Attendance Window Closed")
                .setMessage("You are trying to mark attendance outside the allowed time window for this session.\n\n" +
                        "Allowed Time: 09:00 AM - 09:30 AM\n" +
                        "Current Time: " + currentTimeStr)
                .setPositiveButton("OK", null)
                .show();
    }

    private void showErrorDialog(String title, String message) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    private void logout() {
        SharedPreferences.Editor editor = getSharedPreferences("SmartAttendancePrefs", Context.MODE_PRIVATE).edit();
        editor.clear();
        editor.apply();

        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }
}
