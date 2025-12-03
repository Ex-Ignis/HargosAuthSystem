package es.hargos.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

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
    // Mantenidos por compatibilidad
    private RidersConfigDTO ridersConfig;
    private WarehouseConfigDTO warehouseConfig;
    private FleetConfigDTO fleetConfig;

    // Configuración dinámica de la app externa (ej: RiTrack)
    // Contiene todos los settings obtenidos dinámicamente sin hardcodear
    private Map<String, Object> appConfig;

    // Conteo actual de riders (obtenido de la app externa)
    private Integer currentRiderCount;
}
