package es.hargos.auth.dto.request;

import lombok.Data;

@Data
public class UpdateFleetConfigRequest {

    private Integer vehicleLimit;

    private Boolean gpsTracking;

    private Boolean maintenanceAlerts;

    private Boolean fuelMonitoring;

    private Boolean driverScoring;

    private Boolean telematicsEnabled;
}
