package es.hargos.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InvitationResponse {
    private Long id;
    private Long tenantId;
    private String tenantName;
    private String email;
    private String role;
    private Long invitedByUserId;
    private String invitedByUserName;
    private LocalDateTime expiresAt;
    private Boolean accepted;
    private LocalDateTime acceptedAt;
    private LocalDateTime createdAt;
    private Boolean isExpired;
    private Boolean isValid;
}
