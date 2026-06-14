package com.qrattendance.backend.controller;

import com.qrattendance.backend.model.AttendanceRecord;
import com.qrattendance.backend.model.Session;
import com.qrattendance.backend.model.User;
import com.qrattendance.backend.payload.request.ScanQrRequest;
import com.qrattendance.backend.payload.response.MessageResponse;
import com.qrattendance.backend.repository.AttendanceRecordRepository;
import com.qrattendance.backend.repository.SessionRepository;
import com.qrattendance.backend.repository.UserRepository;
import com.qrattendance.backend.security.jwt.JwtUtils;
import com.qrattendance.backend.security.services.UserDetailsImpl;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.Duration;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private AttendanceRecordRepository attendanceRecordRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtils jwtUtils;

    @PostMapping("/scan")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<?> markAttendance(@Valid @RequestBody ScanQrRequest request) {
        UserDetailsImpl userDetails = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User student = userRepository.findById(userDetails.getId()).orElseThrow();

        // Find session by QR Token
        Session session = sessionRepository.findByCurrentQrToken(request.getQrToken()).orElse(null);

        if (session == null || !session.isActive()) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: Invalid or expired QR code."));
        }

        // Temporal Validation (is QR expired?)
        if (session.getQrExpiration() != null && LocalDateTime.now().isAfter(session.getQrExpiration())) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: QR code has expired. Please wait for the teacher to refresh."));
        }

        // Uniqueness Constraint Validation
        if (attendanceRecordRepository.existsBySessionIdAndStudentId(session.getId(), student.getId())) {
             return ResponseEntity.badRequest().body(new MessageResponse("Error: Attendance already marked for this session."));
        }

        // Record Attendance
        AttendanceRecord record = new AttendanceRecord();
        record.setSession(session);
        record.setStudent(student);
        record.setScanTime(LocalDateTime.now());
        record.setValid(true);

        attendanceRecordRepository.save(record);

        return ResponseEntity.ok(new MessageResponse("Attendance marked successfully!"));
    }

    @PostMapping("/mark")
    public ResponseEntity<?> markAttendanceToken(@RequestParam("token") String token,
                                                 @RequestParam(value = "deviceId", required = false) String deviceId,
                                                 @RequestHeader("Authorization") String jwtHeader) {
        // 1. Resolve session parameters out of database
        Session session = sessionRepository.findByCurrentQrToken(token).orElse(null);
        if (session == null || !session.isActive()) {
            return ResponseEntity.badRequest().body(new MessageResponse("Invalid Verification Token."));
        }

        // 2. Anti-Cheat Check 1: Enforce the 5-minute Time-To-Live constraint
        long minutesElapsed = Duration.between(session.getStartTime(), LocalDateTime.now()).toMinutes();
        if (minutesElapsed > 5) {
            return ResponseEntity.status(410).body(new MessageResponse("Verification failed: Token has expired."));
        }

        // Check if QR code has expired (custom shorter temporal refresh in backend, e.g. 60 seconds)
        if (session.getQrExpiration() != null && LocalDateTime.now().isAfter(session.getQrExpiration())) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: QR code has expired. Please wait for the teacher to refresh."));
        }

        // 3. Extract the authenticated student identity from the verified signature context
        String username;
        try {
            String jwt = jwtHeader.replace("Bearer ", "");
            username = jwtUtils.getUserNameFromJwtToken(jwt);
        } catch (Exception e) {
            return ResponseEntity.status(401).body(new MessageResponse("Error: Unauthorized token extraction failed."));
        }

        User student = userRepository.findByUsername(username).orElse(null);
        if (student == null) {
            return ResponseEntity.status(404).body(new MessageResponse("Error: Student record not found."));
        }

        // Anti-Proxy Check: Device Binding
        if (deviceId != null && !deviceId.trim().isEmpty()) {
            if (student.getDeviceId() == null) {
                // First-time registration of device ID
                student.setDeviceId(deviceId);
                userRepository.save(student);
            } else if (!student.getDeviceId().equals(deviceId)) {
                return ResponseEntity.status(400).body(new MessageResponse("Transaction rejected: Device mismatch. You can only mark attendance from your registered device."));
            }
        }

        // 4. Anti-Cheat Check 2: Block double-scanning within the active session
        boolean duplicateExists = attendanceRecordRepository.existsBySessionIdAndStudentId(session.getId(), student.getId());
        if (duplicateExists) {
            return ResponseEntity.status(409).body(new MessageResponse("Transaction rejected: Attendance already recorded."));
        }

        // 5. Commit the verified attendance record to the transaction database ledger
        AttendanceRecord record = new AttendanceRecord();
        record.setSession(session);
        record.setStudent(student);
        record.setScanTime(LocalDateTime.now());
        record.setValid(true);

        attendanceRecordRepository.save(record);

        return ResponseEntity.ok(new MessageResponse("Attendance recorded successfully!"));
    }

    @GetMapping("/history")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<?> getAttendanceHistory() {
        UserDetailsImpl userDetails = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        List<AttendanceRecord> records = attendanceRecordRepository.findByStudentIdOrderByScanTimeDesc(userDetails.getId());
        
        List<Map<String, Object>> historyList = new ArrayList<>();
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy");
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("hh:mm a");

        for (AttendanceRecord record : records) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", record.getId());
            map.put("subject", record.getSession().getSubject());
            map.put("date", record.getScanTime().format(dateFormatter));
            map.put("time", record.getScanTime().format(timeFormatter));
            map.put("status", record.isValid() ? "Present" : "Absent");
            historyList.add(map);
        }
        
        return ResponseEntity.ok(historyList);
    }
}
