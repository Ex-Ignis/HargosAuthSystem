package es.hargos.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccessCodeResponse {
    private Long id;
    private Long tenantId;
    private String tenantName;
    private String code;
    private String role;
    private Long createdByUserId;
    private String createdByUserName;
    private Integer maxUses;
    private Integer currentUses;
    private Integer remainingUses; // maxUses - currentUses (NULL si ilimitado)
    private LocalDateTime expiresAt;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private Boolean isExpired;
    private Boolean hasReachedMaxUses;
    private Boolean isValid;
}
