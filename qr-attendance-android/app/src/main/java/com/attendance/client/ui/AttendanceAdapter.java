package com.attendance.client.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.attendance.client.R;
import com.attendance.client.model.AttendanceRecord;

import java.util.List;

public class AttendanceAdapter extends RecyclerView.Adapter<AttendanceAdapter.ViewHolder> {

    private final List<AttendanceRecord> records;

    public AttendanceAdapter(List<AttendanceRecord> records) {
        this.records = records;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_attendance, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AttendanceRecord record = records.get(position);
        holder.tvSubject.setText(record.getSubject());
        holder.tvDate.setText("📅 " + record.getDate());
        holder.tvTime.setText("⏰ " + record.getTime());
        holder.tvStatus.setText(record.getStatus());
    }

    @Override
    public int getItemCount() {
        return records.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public final TextView tvSubject;
        public final TextView tvDate;
        public final TextView tvTime;
        public final TextView tvStatus;

        public ViewHolder(View view) {
            super(view);
            tvSubject = view.findViewById(R.id.tv_item_subject);
            tvDate = view.findViewById(R.id.tv_item_date);
            tvTime = view.findViewById(R.id.tv_item_time);
            tvStatus = view.findViewById(R.id.tv_item_status);
        }
    }
}
