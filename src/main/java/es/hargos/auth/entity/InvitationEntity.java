package es.hargos.auth.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "invitations", schema = "auth")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InvitationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private TenantEntity tenant;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false, length = 500, unique = true)
    private String token;

    @Column(nullable = false, length = 50)
    private String role; // USER, TENANT_ADMIN

    @Column(name = "invited_by_user_id")
    private Long invitedByUserId;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "accepted", nullable = false)
    private Boolean accepted = false;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (expiresAt == null) {
            expiresAt = LocalDateTime.now().plusDays(7); // 7 días por defecto
        }
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isValid() {
        return !accepted && !isExpired();
    }
}
