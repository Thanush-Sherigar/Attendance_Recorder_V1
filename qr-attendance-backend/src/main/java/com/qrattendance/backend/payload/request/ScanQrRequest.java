package com.qrattendance.backend.payload.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ScanQrRequest {
    @NotBlank
    private String qrToken;
}
