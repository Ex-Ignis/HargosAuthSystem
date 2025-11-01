package es.hargos.auth.controller;

import es.hargos.auth.dto.request.AcceptInvitationRequest;
import es.hargos.auth.dto.request.ForgotPasswordRequest;
import es.hargos.auth.dto.request.LoginRequest;
import es.hargos.auth.dto.request.RefreshTokenRequest;
import es.hargos.auth.dto.request.RegisterRequest;
import es.hargos.auth.dto.request.RegisterWithAccessCodeRequest;
import es.hargos.auth.dto.request.ResetPasswordRequest;
import es.hargos.auth.dto.response.LoginResponse;
import es.hargos.auth.dto.response.MessageResponse;
import es.hargos.auth.dto.response.TokenValidationResponse;
import es.hargos.auth.dto.response.UserResponse;
import es.hargos.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Registro simple sin tenant (para clientes que luego comprarán productos)
     */
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest) {
        UserResponse response = authService.register(request, httpRequest);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * Registro aceptando una invitación por email (con auto-login)
     */
    @PostMapping("/register/invitation")
    public ResponseEntity<LoginResponse> registerFromInvitation(
            @Valid @RequestBody AcceptInvitationRequest request,
            HttpServletRequest httpRequest) {
        LoginResponse response = authService.registerFromInvitation(request, httpRequest);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * Registro usando un código de acceso
     */
    @PostMapping("/register/access-code")
    public ResponseEntity<UserResponse> registerWithAccessCode(
            @Valid @RequestBody RegisterWithAccessCodeRequest request,
            HttpServletRequest httpRequest) {
        UserResponse response = authService.registerWithAccessCode(request, httpRequest);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * Verificar si una invitación es válida y obtener información del tenant
     */
    @GetMapping("/invitation/{token}")
    public ResponseEntity<?> getInvitationInfo(@PathVariable String token) {
        return ResponseEntity.ok(authService.getInvitationInfo(token));
    }

    /**
     * Aceptar invitación para usuario YA EXISTENTE (autenticado)
     */
    @PostMapping("/accept-invitation")
    public ResponseEntity<UserResponse> acceptInvitation(
            @RequestParam String token,
            Authentication authentication) {
        String userEmail = authentication.getName();
        UserResponse response = authService.acceptInvitationForExistingUser(token, userEmail);
        return ResponseEntity.ok(response);
    }

    /**
     * Unirse a un tenant usando código de acceso para usuario YA EXISTENTE (autenticado)
     */
    @PostMapping("/join-with-access-code")
    public ResponseEntity<UserResponse> joinWithAccessCode(
            @RequestParam String code,
            Authentication authentication) {
        String userEmail = authentication.getName();
        UserResponse response = authService.joinTenantWithAccessCode(code, userEmail);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        LoginResponse response = authService.login(request, httpRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        LoginResponse response = authService.refreshAccessToken(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.ok(new MessageResponse("Sesión cerrada correctamente"));
    }

    @GetMapping("/validate")
    public ResponseEntity<TokenValidationResponse> validateToken(
            @RequestHeader("Authorization") String authHeader) {

        String token = authHeader.replace("Bearer ", "");
        TokenValidationResponse response = authService.validateToken(token);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<TokenValidationResponse> getCurrentUser(
            @RequestHeader("Authorization") String authHeader) {

        String token = authHeader.replace("Bearer ", "");
        TokenValidationResponse response = authService.validateToken(token);

        if (!response.getValid()) {
            return ResponseEntity.status(401).body(response);
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest httpRequest) {
        authService.forgotPassword(request, httpRequest);
        return ResponseEntity.ok(new MessageResponse("Si el email existe, recibirás un enlace de recuperación"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(new MessageResponse("Contraseña restablecida correctamente"));
    }
}
