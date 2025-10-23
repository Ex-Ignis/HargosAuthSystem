package es.hargos.auth.service;

import es.hargos.auth.dto.request.CreateTenantRequest;
import es.hargos.auth.dto.response.*;
import es.hargos.auth.entity.*;
import es.hargos.auth.exception.DuplicateResourceException;
import es.hargos.auth.exception.ResourceNotFoundException;
import es.hargos.auth.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;
    private final AppRepository appRepository;
    private final OrganizationRepository organizationRepository;
    private final UserTenantRoleRepository userTenantRoleRepository;
    private final TenantRidersConfigRepository tenantRidersConfigRepository;
    private final TenantWarehouseConfigRepository tenantWarehouseConfigRepository;
    private final TenantFleetConfigRepository tenantFleetConfigRepository;

    @Transactional
    public TenantResponse createTenant(CreateTenantRequest request) {
        AppEntity app = appRepository.findById(request.getAppId())
                .orElseThrow(() -> new ResourceNotFoundException("App no encontrada"));

        OrganizationEntity organization = organizationRepository.findById(request.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Organizacion no encontrada"));

        if (tenantRepository.existsByAppAndName(app, request.getName())) {
            throw new DuplicateResourceException("Tenant con nombre: " + request.getName() + " ya existe para esta app");
        }

        TenantEntity tenant = new TenantEntity();
        tenant.setApp(app);
        tenant.setOrganization(organization);
        tenant.setName(request.getName());
        tenant.setDescription(request.getDescription());
        tenant.setAccountLimit(request.getAccountLimit());
        tenant.setIsActive(true);

        tenant = tenantRepository.save(tenant);

        return mapToResponse(tenant);
    }

    public List<TenantResponse> getAllTenants() {
        return tenantRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public TenantResponse getTenantById(Long id) {
        TenantEntity tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant no encontrado"));
        return mapToResponse(tenant);
    }

    public List<TenantResponse> getTenantsByOrganization(Long organizationId) {
        OrganizationEntity organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Organizacion no encontrada"));

        return tenantRepository.findByOrganization(organization).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteTenant(Long id) {
        TenantEntity tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant no encontrado"));
        tenantRepository.delete(tenant);
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

        // Cargar configuración específica según el app
        String appName = tenant.getApp().getName();

        if ("Riders Management".equals(appName)) {
            tenantRidersConfigRepository.findByTenantId(tenant.getId())
                    .ifPresent(config -> {
                        RidersConfigDTO ridersConfig = new RidersConfigDTO();
                        ridersConfig.setRiderLimit(config.getRiderLimit());
                        ridersConfig.setCurrentRiderCount(null); // Lo enviará el droplet
                        ridersConfig.setDeliveryZones(config.getDeliveryZones());
                        ridersConfig.setMaxDailyDeliveries(config.getMaxDailyDeliveries());
                        ridersConfig.setRealTimeTracking(config.getRealTimeTracking());
                        ridersConfig.setSmsNotifications(config.getSmsNotifications());
                        response.setRidersConfig(ridersConfig);
                    });
        } else if ("Warehouse Management".equals(appName)) {
            tenantWarehouseConfigRepository.findByTenantId(tenant.getId())
                    .ifPresent(config -> {
                        WarehouseConfigDTO warehouseConfig = new WarehouseConfigDTO();
                        warehouseConfig.setWarehouseCapacityM3(config.getWarehouseCapacityM3());
                        warehouseConfig.setLoadingDocks(config.getLoadingDocks());
                        warehouseConfig.setInventorySkuLimit(config.getInventorySkuLimit());
                        warehouseConfig.setBarcodeScanning(config.getBarcodeScanning());
                        warehouseConfig.setRfidEnabled(config.getRfidEnabled());
                        warehouseConfig.setTemperatureControlledZones(config.getTemperatureControlledZones());
                        response.setWarehouseConfig(warehouseConfig);
                    });
        } else if ("Fleet Management".equals(appName)) {
            tenantFleetConfigRepository.findByTenantId(tenant.getId())
                    .ifPresent(config -> {
                        FleetConfigDTO fleetConfig = new FleetConfigDTO();
                        fleetConfig.setVehicleLimit(config.getVehicleLimit());
                        fleetConfig.setGpsTracking(config.getGpsTracking());
                        fleetConfig.setMaintenanceAlerts(config.getMaintenanceAlerts());
                        fleetConfig.setFuelMonitoring(config.getFuelMonitoring());
                        fleetConfig.setDriverScoring(config.getDriverScoring());
                        fleetConfig.setTelematicsEnabled(config.getTelematicsEnabled());
                        response.setFleetConfig(fleetConfig);
                    });
        }

        return response;
    }
}
