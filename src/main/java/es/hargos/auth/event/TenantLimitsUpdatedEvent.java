package es.hargos.auth.event;

import lombok.Getter;

/**
 * Event published when a tenant's account or rider limits are updated.
 * This event is published BEFORE the transaction commits.
 */
@Getter
public class TenantLimitsUpdatedEvent {
    private final Long tenantId;
    private final Integer accountQuantity;
    private final Integer ridersQuantity;

    public TenantLimitsUpdatedEvent(Long tenantId, Integer accountQuantity, Integer ridersQuantity) {
        this.tenantId = tenantId;
        this.accountQuantity = accountQuantity;
        this.ridersQuantity = ridersQuantity;
    }
}
