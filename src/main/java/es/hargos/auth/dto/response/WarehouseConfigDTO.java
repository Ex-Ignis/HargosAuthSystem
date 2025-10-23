package es.hargos.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WarehouseConfigDTO {
    private BigDecimal warehouseCapacityM3;
    private Integer loadingDocks;
    private Integer inventorySkuLimit;
    private Boolean barcodeScanning;
    private Boolean rfidEnabled;
    private Integer temperatureControlledZones;
}
