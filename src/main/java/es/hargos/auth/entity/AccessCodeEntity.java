package es.hargos.auth.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "access_codes", schema = "auth")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccessCodeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    @lombok.ToString.Exclude
    @lombok.EqualsAndHashCode.Exclude
    private TenantEntity tenant;

    @Column(nullable = false, length = 50, unique = true)
    private String code;

    @Column(nullable = false, length = 50)
    private String role; // USER, TENANT_ADMIN (normalmente USER)

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Column(name = "max_uses")
    private Integer maxUses; // NULL = ilimitado

    @Column(name = "current_uses", nullable = false)
    private Integer currentUses = 0;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt; // NULL = nunca expira

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean hasReachedMaxUses() {
        return maxUses != null && currentUses >= maxUses;
    }

    public boolean isValid() {
        return isActive && !isExpired() && !hasReachedMaxUses();
    }

    public void incrementUses() {
        this.currentUses++;
    }
}
