package es.hargos.auth.dto.request;

import lombok.Data;

@Data
public class UpdateRidersConfigRequest {

    private Integer riderLimit; // null = ilimitado

    private Integer deliveryZones;

    private Integer maxDailyDeliveries;

    private Boolean realTimeTracking;

    private Boolean smsNotifications;
}
