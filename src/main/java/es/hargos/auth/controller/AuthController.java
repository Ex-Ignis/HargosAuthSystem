package es.hargos.auth.controller;

import es.hargos.auth.dto.request.LoginRequest;
import es.hargos.auth.dto.request.RefreshTokenRequest;
import es.hargos.auth.dto.response.LoginResponse;
import es.hargos.auth.dto.response.MessageResponse;
import es.hargos.auth.dto.response.TokenValidationResponse;
import es.hargos.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
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
}
