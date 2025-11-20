package es.hargos.auth.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity for tracking rider limit violations reported by RiTrack.
 *
 * When RiTrack detects that a tenant has more riders than their subscription allows,
 * it creates a notification here for SUPER_ADMIN to review.
 */
@Entity
@Table(name = "limit_exceeded_notifications", schema = "auth")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LimitExceededNotificationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    @lombok.ToString.Exclude
    @lombok.EqualsAndHashCode.Exclude
    private TenantEntity tenant;

    @Column(name = "current_count", nullable = false)
    private Integer currentCount;

    @Column(name = "allowed_limit", nullable = false)
    private Integer allowedLimit;

    @Column(name = "excess_count", nullable = false)
    private Integer excessCount;

    @Column(name = "detected_at", nullable = false)
    private LocalDateTime detectedAt;

    @Column(name = "is_acknowledged", nullable = false)
    private Boolean isAcknowledged = false;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acknowledged_by_user_id")
    @lombok.ToString.Exclude
    @lombok.EqualsAndHashCode.Exclude
    private UserEntity acknowledgedByUser;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (detectedAt == null) {
            detectedAt = LocalDateTime.now();
        }
        if (isAcknowledged == null) {
            isAcknowledged = false;
        }
    }
}
