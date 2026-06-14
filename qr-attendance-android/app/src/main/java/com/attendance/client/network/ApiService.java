package com.attendance.client.network;

import com.attendance.client.model.AttendanceRecord;
import com.attendance.client.model.JwtResponse;
import com.attendance.client.model.LoginRequest;

import java.util.List;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface ApiService {

    @POST("api/auth/signin")
    Call<JwtResponse> signIn(@Body LoginRequest loginRequest);

    @POST("api/attendance/mark")
    Call<ResponseBody> markAttendance(
        @Query("token") String token,
        @Query("deviceId") String deviceId
    );

    @GET("api/attendance/history")
    Call<List<AttendanceRecord>> getHistory();
}
