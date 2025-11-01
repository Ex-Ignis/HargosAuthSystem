package es.hargos.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionResponse {
    private Long id;
    private Long tenantId;
    private String tenantName;
    private Long organizationId;
    private String organizationName;
    private String stripeCustomerId;
    private String stripeSubscriptionId;
    private String stripePriceId;
    private String status;
    private LocalDateTime currentPeriodStart;
    private LocalDateTime currentPeriodEnd;
    private Boolean cancelAtPeriodEnd;
    private LocalDateTime canceledAt;
    private LocalDateTime createdAt;
}
