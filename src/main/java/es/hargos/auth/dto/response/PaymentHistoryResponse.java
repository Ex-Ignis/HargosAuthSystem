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
public class PaymentHistoryResponse {
    private Long id;
    private String stripeInvoiceId;
    private Integer amountCents;
    private Double amountEuros;
    private String currency;
    private String status;
    private String invoicePdfUrl;
    private String hostedInvoiceUrl;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
}
