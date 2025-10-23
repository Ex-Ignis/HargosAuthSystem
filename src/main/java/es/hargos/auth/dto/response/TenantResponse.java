package es.hargos.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TenantResponse {
    private Long id;
    private Long appId;
    private String appName;
    private Long organizationId;
    private String organizationName;
    private String name;
    private String description;
    private Integer accountLimit;
    private Long currentAccountCount;
    private Boolean isActive;
    private LocalDateTime createdAt;

    // Configuraciones específicas por app (solo una estará presente según el app)
    private RidersConfigDTO ridersConfig;
    private WarehouseConfigDTO warehouseConfig;
    private FleetConfigDTO fleetConfig;
}
