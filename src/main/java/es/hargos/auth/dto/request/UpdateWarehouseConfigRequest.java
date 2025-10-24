package es.hargos.auth.dto.request;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateWarehouseConfigRequest {

    private BigDecimal warehouseCapacityM3;

    private Integer loadingDocks;

    private Integer inventorySkuLimit;

    private Boolean barcodeScanning;

    private Boolean rfidEnabled;

    private Integer temperatureControlledZones;
}
