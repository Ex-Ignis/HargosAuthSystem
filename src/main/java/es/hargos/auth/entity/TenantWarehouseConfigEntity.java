package es.hargos.auth.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "tenant_warehouse_config", schema = "auth")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TenantWarehouseConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false, unique = true)
    @lombok.ToString.Exclude
    @lombok.EqualsAndHashCode.Exclude
    private TenantEntity tenant;

    @Column(name = "warehouse_capacity_m3", precision = 10, scale = 2)
    private BigDecimal warehouseCapacityM3;

    @Column(name = "loading_docks")
    private Integer loadingDocks;

    @Column(name = "inventory_sku_limit")
    private Integer inventorySkuLimit;

    @Column(name = "barcode_scanning")
    private Boolean barcodeScanning = true;

    @Column(name = "rfid_enabled")
    private Boolean rfidEnabled = false;

    @Column(name = "temperature_controlled_zones")
    private Integer temperatureControlledZones;

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
