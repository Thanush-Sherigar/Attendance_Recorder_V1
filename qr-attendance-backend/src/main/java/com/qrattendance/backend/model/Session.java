package com.qrattendance.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "sessions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Session {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id", nullable = false)
    private User teacher;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false)
    private LocalDateTime startTime;

    private LocalDateTime endTime;

    // The dynamic value embedded in the QR Code
    @Column(name = "current_qr_token", unique = true)
    private String currentQrToken;

    @Column(name = "qr_expiration")
    private LocalDateTime qrExpiration;

    @Column(nullable = false)
    private boolean active = true;
}
