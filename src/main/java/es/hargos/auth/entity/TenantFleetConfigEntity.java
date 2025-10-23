package es.hargos.auth.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "tenant_fleet_config", schema = "auth")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TenantFleetConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false, unique = true)
    private TenantEntity tenant;

    @Column(name = "vehicle_limit")
    private Integer vehicleLimit;

    @Column(name = "gps_tracking")
    private Boolean gpsTracking = true;

    @Column(name = "maintenance_alerts")
    private Boolean maintenanceAlerts = true;

    @Column(name = "fuel_monitoring")
    private Boolean fuelMonitoring = false;

    @Column(name = "driver_scoring")
    private Boolean driverScoring = false;

    @Column(name = "telematics_enabled")
    private Boolean telematicsEnabled = false;

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
