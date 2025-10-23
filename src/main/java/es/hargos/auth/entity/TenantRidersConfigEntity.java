package es.hargos.auth.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "tenant_riders_config", schema = "auth")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TenantRidersConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false, unique = true)
    private TenantEntity tenant;

    @Column(name = "rider_limit")
    private Integer riderLimit;

    @Column(name = "delivery_zones")
    private Integer deliveryZones;

    @Column(name = "max_daily_deliveries")
    private Integer maxDailyDeliveries;

    @Column(name = "real_time_tracking")
    private Boolean realTimeTracking = true;

    @Column(name = "sms_notifications")
    private Boolean smsNotifications = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
