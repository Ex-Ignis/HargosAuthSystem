package es.hargos.auth.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Cliente HTTP para comunicación interna con RiTrack.
 * Obtiene configuración dinámica de tenants de RiTrack.
 */
@Component
public class RiTrackClient {

    private static final Logger logger = LoggerFactory.getLogger(RiTrackClient.class);

    private final RestTemplate restTemplate;

    @Value("${ritrack.base-url:http://localhost:8080}")
    private String ritrackBaseUrl;

    @Value("${ritrack.internal.api-key:}")
    private String internalApiKey;

    public RiTrackClient() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * Actualiza settings de un tenant en RiTrack.
     *
     * @param hargosTenantId ID del tenant en HargosAuth
     * @param settings Map con los settings a actualizar
     * @return true si se actualizó correctamente, false en caso de error
     */
    public boolean updateTenantSettings(Long hargosTenantId, Map<String, Object> settings) {
        if (internalApiKey == null || internalApiKey.isEmpty()) {
            logger.warn("RiTrack internal API key no configurada");
            return false;
        }

        try {
            String url = ritrackBaseUrl + "/internal/admin/tenants/" + hargosTenantId + "/settings";

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Api-Key", internalApiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(settings, headers);

            logger.info("Actualizando settings en RiTrack: {} con settings: {}", url, settings);

            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.PUT,
                    entity,
                    Map.class
            );

            logger.info("Respuesta de RiTrack: status={}, body={}", response.getStatusCode(), response.getBody());

            if (response.getStatusCode() == HttpStatus.OK) {
                logger.info("Settings actualizados en RiTrack para hargosTenantId: {}", hargosTenantId);
                return true;
            }

            logger.warn("RiTrack respondió con status no OK: {}", response.getStatusCode());
            return false;

        } catch (Exception e) {
            logger.error("Error actualizando settings en RiTrack para tenant {}: {}", hargosTenantId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Obtiene la configuración de un tenant desde RiTrack.
     *
     * @param hargosTenantId ID del tenant en HargosAuth
     * @return Map con la configuración del tenant, o null si no existe/error
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getTenantConfig(Long hargosTenantId) {
        if (internalApiKey == null || internalApiKey.isEmpty()) {
            logger.warn("RiTrack internal API key no configurada");
            return null;
        }

        try {
            String url = ritrackBaseUrl + "/internal/admin/tenants/" + hargosTenantId + "/config";

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Api-Key", internalApiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Void> entity = new HttpEntity<>(headers);

            logger.debug("Llamando a RiTrack: {}", url);

            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                Boolean found = (Boolean) body.get("found");

                if (found != null && found) {
                    logger.info("Config obtenida de RiTrack para hargosTenantId: {}", hargosTenantId);
                    return body;
                } else {
                    logger.debug("Tenant {} no encontrado en RiTrack", hargosTenantId);
                    return null;
                }
            }

            return null;

        } catch (Exception e) {
            logger.warn("Error llamando a RiTrack para tenant {}: {}", hargosTenantId, e.getMessage());
            return null;
        }
    }

    // ==================== WARNINGS CRUD ====================

    /**
     * Obtiene todos los warnings de un tenant desde RiTrack.
     *
     * @param hargosTenantId ID del tenant en HargosAuth
     * @return List de warnings, o null si error
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getTenantWarnings(Long hargosTenantId) {
        if (internalApiKey == null || internalApiKey.isEmpty()) {
            logger.warn("RiTrack internal API key no configurada");
            return null;
        }

        try {
            String url = ritrackBaseUrl + "/internal/admin/tenants/" + hargosTenantId + "/warnings";

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Api-Key", internalApiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Void> entity = new HttpEntity<>(headers);

            logger.debug("Obteniendo warnings de RiTrack: {}", url);

            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                return (List<Map<String, Object>>) body.get("warnings");
            }

            return List.of();

        } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
            // 404 = tenant no configurado en RiTrack, no es un error
            logger.debug("Tenant {} no configurado en RiTrack, sin warnings", hargosTenantId);
            return List.of();
        } catch (Exception e) {
            logger.warn("Error obteniendo warnings de RiTrack para tenant {}: {}", hargosTenantId, e.getMessage());
            return List.of();
        }
    }

    /**
     * Crea un nuevo warning para un tenant en RiTrack.
     *
     * @param hargosTenantId ID del tenant en HargosAuth
     * @param warningData Datos del warning
     * @return Map con el warning creado, o null si error
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> createWarning(Long hargosTenantId, Map<String, Object> warningData) {
        if (internalApiKey == null || internalApiKey.isEmpty()) {
            logger.warn("RiTrack internal API key no configurada");
            return null;
        }

        try {
            String url = ritrackBaseUrl + "/internal/admin/tenants/" + hargosTenantId + "/warnings";

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Api-Key", internalApiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(warningData, headers);

            logger.info("Creando warning en RiTrack: {} con data: {}", url, warningData);

            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    Map.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                logger.info("Warning creado en RiTrack para tenant {} (status: {})", hargosTenantId, response.getStatusCode());
                return response.getBody();
            }

            logger.warn("RiTrack respondió con status inesperado: {}", response.getStatusCode());
            return null;

        } catch (Exception e) {
            logger.error("Error creando warning en RiTrack para tenant {}: {}", hargosTenantId, e.getMessage());
            return null;
        }
    }

    /**
     * Actualiza un warning existente en RiTrack.
     *
     * @param hargosTenantId ID del tenant en HargosAuth
     * @param warningId ID del warning
     * @param warningData Datos a actualizar
     * @return Map con el warning actualizado, o null si error
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> updateWarning(Long hargosTenantId, Long warningId, Map<String, Object> warningData) {
        if (internalApiKey == null || internalApiKey.isEmpty()) {
            logger.warn("RiTrack internal API key no configurada");
            return null;
        }

        try {
            String url = ritrackBaseUrl + "/internal/admin/tenants/" + hargosTenantId + "/warnings/" + warningId;

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Api-Key", internalApiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(warningData, headers);

            logger.info("Actualizando warning {} en RiTrack: {}", warningId, warningData);

            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.PUT,
                    entity,
                    Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                logger.info("Warning {} actualizado en RiTrack", warningId);
                return response.getBody();
            }

            return null;

        } catch (Exception e) {
            logger.error("Error actualizando warning {} en RiTrack: {}", warningId, e.getMessage());
            return null;
        }
    }

    /**
     * Crea un tenant en RiTrack con su schema de PostgreSQL.
     * Llamado al crear un tenant de tipo RiTrack desde HargosAuth.
     *
     * @param hargosTenantId ID del tenant en HargosAuth
     * @param tenantName Nombre del tenant
     * @return true si se creó correctamente (o ya existía), false si error
     */
    public boolean createTenant(Long hargosTenantId, String tenantName) {
        if (internalApiKey == null || internalApiKey.isEmpty()) {
            logger.warn("RiTrack internal API key no configurada");
            return false;
        }

        try {
            String url = ritrackBaseUrl + "/internal/admin/tenants";

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Api-Key", internalApiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = Map.of(
                    "hargosTenantId", hargosTenantId,
                    "name", tenantName
            );

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            logger.info("Creando tenant {} en RiTrack (hargosTenantId: {})", tenantName, hargosTenantId);

            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    Map.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                logger.info("Tenant {} creado en RiTrack correctamente: {}", hargosTenantId, response.getBody());
                return true;
            }

            logger.warn("RiTrack respondió con status inesperado: {}", response.getStatusCode());
            return false;

        } catch (Exception e) {
            logger.error("Error creando tenant {} en RiTrack: {}", hargosTenantId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Elimina un tenant y su schema de RiTrack.
     * Esta operación es DESTRUCTIVA - elimina el schema y todos sus datos.
     *
     * @param hargosTenantId ID del tenant en HargosAuth
     * @return true si se eliminó correctamente (o no existía), false si error
     */
    public boolean deleteTenant(Long hargosTenantId) {
        if (internalApiKey == null || internalApiKey.isEmpty()) {
            logger.warn("RiTrack internal API key no configurada");
            return false;
        }

        try {
            String url = ritrackBaseUrl + "/internal/admin/tenants/" + hargosTenantId;

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Api-Key", internalApiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Void> entity = new HttpEntity<>(headers);

            logger.info("Eliminando tenant {} de RiTrack (incluyendo schema)", hargosTenantId);

            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.DELETE,
                    entity,
                    Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                logger.info("Tenant {} eliminado de RiTrack correctamente", hargosTenantId);
                return true;
            }

            logger.warn("RiTrack respondió con status inesperado: {}", response.getStatusCode());
            return false;

        } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
            // 404 = tenant no existía en RiTrack, lo consideramos éxito
            logger.info("Tenant {} no existía en RiTrack, nada que eliminar", hargosTenantId);
            return true;
        } catch (Exception e) {
            logger.error("Error eliminando tenant {} de RiTrack: {}", hargosTenantId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Elimina un warning en RiTrack.
     *
     * @param hargosTenantId ID del tenant en HargosAuth
     * @param warningId ID del warning a eliminar
     * @return true si se eliminó correctamente, false si error
     */
    public boolean deleteWarning(Long hargosTenantId, Long warningId) {
        if (internalApiKey == null || internalApiKey.isEmpty()) {
            logger.warn("RiTrack internal API key no configurada");
            return false;
        }

        try {
            String url = ritrackBaseUrl + "/internal/admin/tenants/" + hargosTenantId + "/warnings/" + warningId;

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Api-Key", internalApiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Void> entity = new HttpEntity<>(headers);

            logger.info("Eliminando warning {} en RiTrack", warningId);

            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.DELETE,
                    entity,
                    Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                logger.info("Warning {} eliminado de RiTrack", warningId);
                return true;
            }

            return false;

        } catch (Exception e) {
            logger.error("Error eliminando warning {} en RiTrack: {}", warningId, e.getMessage());
            return false;
        }
    }
}
