package es.hargos.auth.controller;

import es.hargos.auth.dto.request.RefreshTokenRequest;
import es.hargos.auth.dto.response.MessageResponse;
import es.hargos.auth.dto.response.SessionResponse;
import es.hargos.auth.service.SessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;

    /**
     * Obtiene todas las sesiones activas del usuario autenticado
     */
    @GetMapping("/active")
    public ResponseEntity<List<SessionResponse>> getActiveSessions(Authentication authentication) {
        String userEmail = authentication.getName();
        List<SessionResponse> sessions = sessionService.getUserActiveSessions(userEmail);
        return ResponseEntity.ok(sessions);
    }

    /**
     * Obtiene todas las sesiones del usuario (activas e inactivas)
     */
    @GetMapping
    public ResponseEntity<List<SessionResponse>> getAllSessions(Authentication authentication) {
        String userEmail = authentication.getName();
        List<SessionResponse> sessions = sessionService.getAllUserSessions(userEmail);
        return ResponseEntity.ok(sessions);
    }

    /**
     * Revoca una sesión específica por su ID
     */
    @DeleteMapping("/{sessionId}")
    public ResponseEntity<MessageResponse> revokeSession(
            @PathVariable Long sessionId,
            Authentication authentication) {
        String userEmail = authentication.getName();
        sessionService.revokeSession(userEmail, sessionId);
        return ResponseEntity.ok(new MessageResponse("Sesión revocada correctamente"));
    }

    /**
     * Revoca todas las sesiones excepto la actual
     */
    @PostMapping("/revoke-others")
    public ResponseEntity<MessageResponse> revokeAllOtherSessions(
            @Valid @RequestBody RefreshTokenRequest request,
            Authentication authentication) {
        String userEmail = authentication.getName();
        sessionService.revokeAllOtherSessions(userEmail, request.getRefreshToken());
        return ResponseEntity.ok(new MessageResponse("Todas las demás sesiones han sido cerradas"));
    }
}
