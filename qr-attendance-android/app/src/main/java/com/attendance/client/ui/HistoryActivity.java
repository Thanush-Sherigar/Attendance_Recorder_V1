package com.attendance.client.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.attendance.client.R;
import com.attendance.client.model.AttendanceRecord;
import com.attendance.client.network.RetrofitClient;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HistoryActivity extends AppCompatActivity {

    private RecyclerView rvHistory;
    private TextView tvEmptyState;
    private AttendanceAdapter adapter;
    private List<AttendanceRecord> recordsList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        rvHistory = findViewById(R.id.rv_attendance_history);
        tvEmptyState = findViewById(R.id.tv_empty_state);

        rvHistory.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AttendanceAdapter(recordsList);
        rvHistory.setAdapter(adapter);

        fetchHistory();
    }

    private void fetchHistory() {
        tvEmptyState.setVisibility(View.GONE);
        
        RetrofitClient.getApiService(this).getHistory().enqueue(new Callback<List<AttendanceRecord>>() {
            @Override
            public void onResponse(Call<List<AttendanceRecord>> call, Response<List<AttendanceRecord>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    recordsList.clear();
                    recordsList.addAll(response.body());
                    adapter.notifyDataSetChanged();

                    if (recordsList.isEmpty()) {
                        tvEmptyState.setVisibility(View.VISIBLE);
                        tvEmptyState.setText("📂\nNo attendance records found.");
                    }
                } else {
                    tvEmptyState.setVisibility(View.VISIBLE);
                    tvEmptyState.setText("❌\nFailed to fetch history logs.");
                }
            }

            @Override
            public void onFailure(Call<List<AttendanceRecord>> call, Throwable t) {
                tvEmptyState.setVisibility(View.VISIBLE);
                tvEmptyState.setText("⚠️\nNetwork error: " + t.getMessage());
            }
        });
    }
}
