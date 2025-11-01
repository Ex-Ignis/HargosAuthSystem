package es.hargos.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SessionResponse {
    private Long id;
    private String ipAddress;
    private String userAgent;
    private String deviceType;
    private LocalDateTime lastActivityAt;
    private LocalDateTime createdAt;
    private Boolean isActive;
}
