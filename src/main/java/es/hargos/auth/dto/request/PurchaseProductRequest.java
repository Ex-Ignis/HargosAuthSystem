package es.hargos.auth.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PurchaseProductRequest {

    @NotBlank(message = "El nombre de la organizaci\u00f3n es obligatorio")
    private String organizationName;

    private String organizationDescription;

    @NotNull(message = "El ID de la app es obligatorio")
    private Long appId; // 1=SYSTEM, 2=Riders Management, 3=Warehouse, 4=Fleet

    @NotBlank(message = "El nombre del tenant es obligatorio")
    private String tenantName;

    private String tenantDescription;

    @NotNull(message = "El l\u00edmite de cuentas es obligatorio")
    @Min(value = 1, message = "El l\u00edmite de cuentas debe ser al menos 1")
    private Integer accountLimit;

    // Configuraci\u00f3n espec\u00edfica para Riders Management
    private RidersConfigRequest ridersConfig;

    // Configuraci\u00f3n espec\u00edfica para Warehouse Management
    private WarehouseConfigRequest warehouseConfig;

    // Configuraci\u00f3n espec\u00edfica para Fleet Management
    private FleetConfigRequest fleetConfig;

    @Data
    public static class RidersConfigRequest {
        private Integer riderLimit;
        private Integer deliveryZones;
        private Integer maxDailyDeliveries;
        private Boolean realTimeTracking = true;
        private Boolean smsNotifications = false;
    }

    @Data
    public static class WarehouseConfigRequest {
        private Double warehouseCapacityM3;
        private Integer loadingDocks;
        private Integer inventorySkuLimit;
        private Boolean barcodeScanning = true;
        private Boolean rfidEnabled = false;
        private Integer temperatureControlledZones;
    }

    @Data
    public static class FleetConfigRequest {
        private Integer vehicleLimit;
        private Boolean gpsTracking = true;
        private Boolean maintenanceAlerts = true;
        private Boolean fuelMonitoring = false;
        private Boolean driverScoring = false;
        private Boolean telematicsEnabled = false;
    }
}
