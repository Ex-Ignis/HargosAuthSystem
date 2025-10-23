package es.hargos.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FleetConfigDTO {
    private Integer vehicleLimit;
    private Boolean gpsTracking;
    private Boolean maintenanceAlerts;
    private Boolean fuelMonitoring;
    private Boolean driverScoring;
    private Boolean telematicsEnabled;
}
