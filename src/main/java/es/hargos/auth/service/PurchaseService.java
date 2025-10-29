package es.hargos.auth.service;

import es.hargos.auth.dto.request.PurchaseProductRequest;
import es.hargos.auth.dto.response.*;
import es.hargos.auth.entity.*;
import es.hargos.auth.exception.DuplicateResourceException;
import es.hargos.auth.exception.ResourceNotFoundException;
import es.hargos.auth.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class PurchaseService {

    private final UserRepository userRepository;
    private final AppRepository appRepository;
    private final OrganizationRepository organizationRepository;
    private final TenantRepository tenantRepository;
    private final UserTenantRoleRepository userTenantRoleRepository;
    private final TenantRidersConfigRepository tenantRidersConfigRepository;
    private final TenantWarehouseConfigRepository tenantWarehouseConfigRepository;
    private final TenantFleetConfigRepository tenantFleetConfigRepository;

    @Transactional
    public TenantResponse purchaseProduct(String userEmail, PurchaseProductRequest request) {
        // 1. Obtener usuario
        UserEntity user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        // 2. Verificar que el app existe
        AppEntity app = appRepository.findById(request.getAppId())
                .orElseThrow(() -> new ResourceNotFoundException("App no encontrada"));

        // 3. Crear o buscar organización
        OrganizationEntity organization = organizationRepository.findByName(request.getOrganizationName())
                .orElseGet(() -> {
                    OrganizationEntity newOrg = new OrganizationEntity();
                    newOrg.setName(request.getOrganizationName());
                    newOrg.setDescription(request.getOrganizationDescription());
                    newOrg.setIsActive(true);
                    return organizationRepository.save(newOrg);
                });

        // 4. Verificar que no exista un tenant con ese nombre para esa app y organización
        if (tenantRepository.existsByAppAndOrganizationAndName(app, organization, request.getTenantName())) {
            throw new DuplicateResourceException(
                "Ya existe un tenant con nombre '" + request.getTenantName() +
                "' para la app '" + app.getName() + "' en la organización '" + organization.getName() + "'"
            );
        }

        // 5. Crear tenant
        TenantEntity tenant = new TenantEntity();
        tenant.setApp(app);
        tenant.setOrganization(organization);
        tenant.setName(request.getTenantName());
        tenant.setDescription(request.getTenantDescription());
        tenant.setAccountLimit(request.getAccountLimit());
        tenant.setIsActive(true);

        tenant = tenantRepository.save(tenant);

        // 6. Crear configuración específica según el tipo de app
        createTenantConfig(tenant, app.getName(), request);

        // 7. Asignar usuario como TENANT_ADMIN
        UserTenantRoleEntity userTenantRole = new UserTenantRoleEntity();
        userTenantRole.setUser(user);
        userTenantRole.setTenant(tenant);
        userTenantRole.setRole("TENANT_ADMIN");
        userTenantRoleRepository.save(userTenantRole);

        // 8. Mapear y devolver respuesta
        return mapToResponse(tenant);
    }

    private void createTenantConfig(TenantEntity tenant, String appName, PurchaseProductRequest request) {
        switch (appName) {
            case "RiTrack":
                if (request.getRidersConfig() != null) {
                    TenantRidersConfigEntity ridersConfig = new TenantRidersConfigEntity();
                    ridersConfig.setTenant(tenant);
                    ridersConfig.setRiderLimit(request.getRidersConfig().getRiderLimit());
                    ridersConfig.setDeliveryZones(request.getRidersConfig().getDeliveryZones());
                    ridersConfig.setMaxDailyDeliveries(request.getRidersConfig().getMaxDailyDeliveries());
                    ridersConfig.setRealTimeTracking(request.getRidersConfig().getRealTimeTracking());
                    ridersConfig.setSmsNotifications(request.getRidersConfig().getSmsNotifications());
                    tenantRidersConfigRepository.save(ridersConfig);
                }
                break;

            case "Warehouse Management":
                if (request.getWarehouseConfig() != null) {
                    TenantWarehouseConfigEntity warehouseConfig = new TenantWarehouseConfigEntity();
                    warehouseConfig.setTenant(tenant);

                    // Convertir Double a BigDecimal
                    if (request.getWarehouseConfig().getWarehouseCapacityM3() != null) {
                        warehouseConfig.setWarehouseCapacityM3(
                            BigDecimal.valueOf(request.getWarehouseConfig().getWarehouseCapacityM3())
                        );
                    }

                    warehouseConfig.setLoadingDocks(request.getWarehouseConfig().getLoadingDocks());
                    warehouseConfig.setInventorySkuLimit(request.getWarehouseConfig().getInventorySkuLimit());
                    warehouseConfig.setBarcodeScanning(request.getWarehouseConfig().getBarcodeScanning());
                    warehouseConfig.setRfidEnabled(request.getWarehouseConfig().getRfidEnabled());
                    warehouseConfig.setTemperatureControlledZones(request.getWarehouseConfig().getTemperatureControlledZones());
                    tenantWarehouseConfigRepository.save(warehouseConfig);
                }
                break;

            case "Fleet Management":
                if (request.getFleetConfig() != null) {
                    TenantFleetConfigEntity fleetConfig = new TenantFleetConfigEntity();
                    fleetConfig.setTenant(tenant);
                    fleetConfig.setVehicleLimit(request.getFleetConfig().getVehicleLimit());
                    fleetConfig.setGpsTracking(request.getFleetConfig().getGpsTracking());
                    fleetConfig.setMaintenanceAlerts(request.getFleetConfig().getMaintenanceAlerts());
                    fleetConfig.setFuelMonitoring(request.getFleetConfig().getFuelMonitoring());
                    fleetConfig.setDriverScoring(request.getFleetConfig().getDriverScoring());
                    fleetConfig.setTelematicsEnabled(request.getFleetConfig().getTelematicsEnabled());
                    tenantFleetConfigRepository.save(fleetConfig);
                }
                break;
        }
    }

    private TenantResponse mapToResponse(TenantEntity tenant) {
        long currentAccountCount = userTenantRoleRepository.countByTenant(tenant);

        TenantResponse response = new TenantResponse();
        response.setId(tenant.getId());
        response.setAppId(tenant.getApp().getId());
        response.setAppName(tenant.getApp().getName());
        response.setOrganizationId(tenant.getOrganization().getId());
        response.setOrganizationName(tenant.getOrganization().getName());
        response.setName(tenant.getName());
        response.setDescription(tenant.getDescription());
        response.setAccountLimit(tenant.getAccountLimit());
        response.setCurrentAccountCount(currentAccountCount);
        response.setIsActive(tenant.getIsActive());
        response.setCreatedAt(tenant.getCreatedAt());

        // Cargar configuración específica si existe
        String appName = tenant.getApp().getName();

        if ("RiTrack".equals(appName) && tenant.getRidersConfig() != null) {
            RidersConfigDTO ridersConfig = new RidersConfigDTO();
            ridersConfig.setRiderLimit(tenant.getRidersConfig().getRiderLimit());
            ridersConfig.setCurrentRiderCount(null);
            ridersConfig.setDeliveryZones(tenant.getRidersConfig().getDeliveryZones());
            ridersConfig.setMaxDailyDeliveries(tenant.getRidersConfig().getMaxDailyDeliveries());
            ridersConfig.setRealTimeTracking(tenant.getRidersConfig().getRealTimeTracking());
            ridersConfig.setSmsNotifications(tenant.getRidersConfig().getSmsNotifications());
            response.setRidersConfig(ridersConfig);
        } else if ("Warehouse Management".equals(appName) && tenant.getWarehouseConfig() != null) {
            WarehouseConfigDTO warehouseConfig = new WarehouseConfigDTO();
            warehouseConfig.setWarehouseCapacityM3(tenant.getWarehouseConfig().getWarehouseCapacityM3());
            warehouseConfig.setLoadingDocks(tenant.getWarehouseConfig().getLoadingDocks());
            warehouseConfig.setInventorySkuLimit(tenant.getWarehouseConfig().getInventorySkuLimit());
            warehouseConfig.setBarcodeScanning(tenant.getWarehouseConfig().getBarcodeScanning());
            warehouseConfig.setRfidEnabled(tenant.getWarehouseConfig().getRfidEnabled());
            warehouseConfig.setTemperatureControlledZones(tenant.getWarehouseConfig().getTemperatureControlledZones());
            response.setWarehouseConfig(warehouseConfig);
        } else if ("Fleet Management".equals(appName) && tenant.getFleetConfig() != null) {
            FleetConfigDTO fleetConfig = new FleetConfigDTO();
            fleetConfig.setVehicleLimit(tenant.getFleetConfig().getVehicleLimit());
            fleetConfig.setGpsTracking(tenant.getFleetConfig().getGpsTracking());
            fleetConfig.setMaintenanceAlerts(tenant.getFleetConfig().getMaintenanceAlerts());
            fleetConfig.setFuelMonitoring(tenant.getFleetConfig().getFuelMonitoring());
            fleetConfig.setDriverScoring(tenant.getFleetConfig().getDriverScoring());
            fleetConfig.setTelematicsEnabled(tenant.getFleetConfig().getTelematicsEnabled());
            response.setFleetConfig(fleetConfig);
        }

        return response;
    }
}
