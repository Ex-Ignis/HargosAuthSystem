package es.hargos.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RidersConfigDTO {
    private Integer riderLimit;
    private Long currentRiderCount; // Este valor vendrá del droplet
    private Integer deliveryZones;
    private Integer maxDailyDeliveries;
    private Boolean realTimeTracking;
    private Boolean smsNotifications;
}
