package com.qrattendance.backend.controller;

import com.qrattendance.backend.model.Session;
import com.qrattendance.backend.model.User;
import com.qrattendance.backend.payload.request.CreateSessionRequest;
import com.qrattendance.backend.payload.response.MessageResponse;
import com.qrattendance.backend.repository.SessionRepository;
import com.qrattendance.backend.repository.UserRepository;
import com.qrattendance.backend.security.services.UserDetailsImpl;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private UserRepository userRepository;

    @PostMapping("/start")
    public ResponseEntity<?> startSession(@Valid @RequestBody CreateSessionRequest request) {
        UserDetailsImpl userDetails = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User teacher = userRepository.findById(userDetails.getId()).orElseThrow();

        Session session = new Session();
        session.setTeacher(teacher);
        session.setSubject(request.getSubject());
        session.setStartTime(LocalDateTime.now());
        session.setActive(true);
        
        // Generate initial QR token (valid for 30 seconds for dynamic refresh)
        updateSessionQrToken(session);
        
        sessionRepository.save(session);
        return ResponseEntity.ok(session);
    }

    @PostMapping("/refresh/{sessionId}")
    public ResponseEntity<?> refreshQrToken(@PathVariable Long sessionId) {
        UserDetailsImpl userDetails = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        
        Session session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null || !session.isActive()) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: Session not found or is inactive."));
        }
        
        if (!session.getTeacher().getId().equals(userDetails.getId())) {
             return ResponseEntity.badRequest().body(new MessageResponse("Error: Unauthorized to modify this session."));
        }

        updateSessionQrToken(session);
        sessionRepository.save(session);
        
        return ResponseEntity.ok(session);
    }
    
    @PostMapping("/end/{sessionId}")
    public ResponseEntity<?> endSession(@PathVariable Long sessionId) {
        UserDetailsImpl userDetails = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        
        Session session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: Session not found."));
        }
        
        if (!session.getTeacher().getId().equals(userDetails.getId())) {
             return ResponseEntity.badRequest().body(new MessageResponse("Error: Unauthorized to modify this session."));
        }

        session.setActive(false);
        session.setEndTime(LocalDateTime.now());
        sessionRepository.save(session);
        
        return ResponseEntity.ok(new MessageResponse("Session ended successfully."));
    }
    
    @GetMapping("/my-sessions")
    public ResponseEntity<List<Session>> getMyActiveSessions() {
        UserDetailsImpl userDetails = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ResponseEntity.ok(sessionRepository.findByTeacherIdAndActiveTrue(userDetails.getId()));
    }

    private void updateSessionQrToken(Session session) {
        session.setCurrentQrToken(UUID.randomUUID().toString());
        // Token expires in 60 seconds to prevent proxy attendance
        session.setQrExpiration(LocalDateTime.now().plusSeconds(60));
    }
}
