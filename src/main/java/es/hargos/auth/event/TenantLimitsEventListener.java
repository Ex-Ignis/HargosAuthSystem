package es.hargos.auth.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.client.RestTemplate;

/**
 * Listener for tenant limits update events.
 * Notifies RiTrack AFTER the database transaction commits.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TenantLimitsEventListener {

    private final RestTemplate restTemplate;

    @Value("${ritrack.base-url}")
    private String ritrackBaseUrl;

    /**
     * Handles TenantLimitsUpdatedEvent AFTER the transaction commits.
     * This ensures RiTrack sees the updated limits when it queries getTenantInfo.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTenantLimitsUpdated(TenantLimitsUpdatedEvent event) {
        log.info("Transaction committed - notifying RiTrack for tenant {}", event.getTenantId());

        try {
            String url = ritrackBaseUrl + "/api/ritrack-public/tenant/" + event.getTenantId() + "/recheck-limits";
            log.info("Notifying RiTrack to recheck limits for tenant {}: {}", event.getTenantId(), url);

            ResponseEntity<RecheckLimitsResponse> response = restTemplate.postForEntity(url, null, RecheckLimitsResponse.class);

            if (response.getBody() != null) {
                log.info("RiTrack recheck result for tenant {}: resolved={}, message={}",
                        event.getTenantId(), response.getBody().resolved(), response.getBody().message());
            }
        } catch (Exception e) {
            log.warn("Failed to notify RiTrack to recheck limits for tenant {}: {}", event.getTenantId(), e.getMessage());
            // Don't fail if RiTrack notification fails - limits are already updated in DB
        }
    }

    /**
     * Response from RiTrack public API for limit recheck
     */
    private record RecheckLimitsResponse(boolean resolved, String message) {
    }
}
