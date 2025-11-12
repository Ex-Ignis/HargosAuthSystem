package es.hargos.auth.service;

import es.hargos.auth.dto.request.*;
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

    @Transactional(readOnly = true)
    public List<TenantResponse> getAllTenants() {
        return tenantRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public TenantResponse getTenantById(Long id) {
        TenantEntity tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant no encontrado"));
        return mapToResponse(tenant);
    }

    @Transactional(readOnly = true)
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

        if ("RiTrack".equals(appName)) {
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

    /**
     * Actualiza la información básica de un tenant (name, description, accountLimit)
     */
    @Transactional
    public TenantResponse updateTenant(Long id, UpdateTenantRequest request) {
        TenantEntity tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant no encontrado"));

        // Actualizar campos si se proporcionan
        if (request.getName() != null && !request.getName().trim().isEmpty()) {
            tenant.setName(request.getName());
        }

        if (request.getDescription() != null) {
            tenant.setDescription(request.getDescription());
        }

        if (request.getAccountLimit() != null) {
            tenant.setAccountLimit(request.getAccountLimit());
        }

        tenant = tenantRepository.save(tenant);
        return mapToResponse(tenant);
    }

    /**
     * Actualiza la configuración de RiTrack de un tenant
     */
    @Transactional
    public TenantResponse updateRidersConfig(Long tenantId, UpdateRidersConfigRequest request) {
        TenantEntity tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant no encontrado"));

        // Verificar que el tenant sea de tipo RiTrack
        if (!"RiTrack".equals(tenant.getApp().getName())) {
            throw new IllegalStateException("El tenant no es de tipo RiTrack");
        }

        // Buscar o crear configuración
        TenantRidersConfigEntity config = tenantRidersConfigRepository.findByTenantId(tenantId)
                .orElseGet(() -> {
                    TenantRidersConfigEntity newConfig = new TenantRidersConfigEntity();
                    newConfig.setTenant(tenant);
                    return newConfig;
                });

        // Actualizar campos si se proporcionan
        if (request.getRiderLimit() != null) {
            config.setRiderLimit(request.getRiderLimit());
        }
        if (request.getDeliveryZones() != null) {
            config.setDeliveryZones(request.getDeliveryZones());
        }
        if (request.getMaxDailyDeliveries() != null) {
            config.setMaxDailyDeliveries(request.getMaxDailyDeliveries());
        }
        if (request.getRealTimeTracking() != null) {
            config.setRealTimeTracking(request.getRealTimeTracking());
        }
        if (request.getSmsNotifications() != null) {
            config.setSmsNotifications(request.getSmsNotifications());
        }

        tenantRidersConfigRepository.save(config);
        return mapToResponse(tenant);
    }

    /**
     * Actualiza la configuración de Warehouse Management de un tenant
     */
    @Transactional
    public TenantResponse updateWarehouseConfig(Long tenantId, UpdateWarehouseConfigRequest request) {
        TenantEntity tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant no encontrado"));

        // Verificar que el tenant sea de tipo Warehouse Management
        if (!"Warehouse Management".equals(tenant.getApp().getName())) {
            throw new IllegalStateException("El tenant no es de tipo Warehouse Management");
        }

        // Buscar o crear configuración
        TenantWarehouseConfigEntity config = tenantWarehouseConfigRepository.findByTenantId(tenantId)
                .orElseGet(() -> {
                    TenantWarehouseConfigEntity newConfig = new TenantWarehouseConfigEntity();
                    newConfig.setTenant(tenant);
                    return newConfig;
                });

        // Actualizar campos si se proporcionan
        if (request.getWarehouseCapacityM3() != null) {
            config.setWarehouseCapacityM3(request.getWarehouseCapacityM3());
        }
        if (request.getLoadingDocks() != null) {
            config.setLoadingDocks(request.getLoadingDocks());
        }
        if (request.getInventorySkuLimit() != null) {
            config.setInventorySkuLimit(request.getInventorySkuLimit());
        }
        if (request.getBarcodeScanning() != null) {
            config.setBarcodeScanning(request.getBarcodeScanning());
        }
        if (request.getRfidEnabled() != null) {
            config.setRfidEnabled(request.getRfidEnabled());
        }
        if (request.getTemperatureControlledZones() != null) {
            config.setTemperatureControlledZones(request.getTemperatureControlledZones());
        }

        tenantWarehouseConfigRepository.save(config);
        return mapToResponse(tenant);
    }

    /**
     * Actualiza la configuración de Fleet Management de un tenant
     */
    @Transactional
    public TenantResponse updateFleetConfig(Long tenantId, UpdateFleetConfigRequest request) {
        TenantEntity tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant no encontrado"));

        // Verificar que el tenant sea de tipo Fleet Management
        if (!"Fleet Management".equals(tenant.getApp().getName())) {
            throw new IllegalStateException("El tenant no es de tipo Fleet Management");
        }

        // Buscar o crear configuración
        TenantFleetConfigEntity config = tenantFleetConfigRepository.findByTenantId(tenantId)
                .orElseGet(() -> {
                    TenantFleetConfigEntity newConfig = new TenantFleetConfigEntity();
                    newConfig.setTenant(tenant);
                    return newConfig;
                });

        // Actualizar campos si se proporcionan
        if (request.getVehicleLimit() != null) {
            config.setVehicleLimit(request.getVehicleLimit());
        }
        if (request.getGpsTracking() != null) {
            config.setGpsTracking(request.getGpsTracking());
        }
        if (request.getMaintenanceAlerts() != null) {
            config.setMaintenanceAlerts(request.getMaintenanceAlerts());
        }
        if (request.getFuelMonitoring() != null) {
            config.setFuelMonitoring(request.getFuelMonitoring());
        }
        if (request.getDriverScoring() != null) {
            config.setDriverScoring(request.getDriverScoring());
        }
        if (request.getTelematicsEnabled() != null) {
            config.setTelematicsEnabled(request.getTelematicsEnabled());
        }

        tenantFleetConfigRepository.save(config);
        return mapToResponse(tenant);
    }
}
