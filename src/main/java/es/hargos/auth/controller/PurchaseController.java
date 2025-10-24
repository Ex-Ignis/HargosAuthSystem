package es.hargos.auth.controller;

import es.hargos.auth.dto.request.PurchaseProductRequest;
import es.hargos.auth.dto.response.TenantResponse;
import es.hargos.auth.service.PurchaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * Controller para compra de productos/servicios.
 * Los usuarios registrados pueden comprar productos y convertirse en TENANT_ADMIN.
 */
@RestController
@RequestMapping("/api/purchase")
@RequiredArgsConstructor
public class PurchaseController {

    private final PurchaseService purchaseService;

    /**
     * Comprar un producto (Riders, Warehouse, Fleet Management).
     * Crea una organizaci\u00f3n, un tenant y asigna al usuario como TENANT_ADMIN.
     */
    @PostMapping
    public ResponseEntity<TenantResponse> purchaseProduct(@Valid @RequestBody PurchaseProductRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String userEmail = auth.getName();

        TenantResponse response = purchaseService.purchaseProduct(userEmail, request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
}
