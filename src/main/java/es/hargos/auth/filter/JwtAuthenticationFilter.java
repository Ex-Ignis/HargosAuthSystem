package es.hargos.auth.filter;

import com.nimbusds.jwt.JWTClaimsSet;
import es.hargos.auth.repository.UserSessionRepository;
import es.hargos.auth.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserSessionRepository userSessionRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        // Si no hay header Authorization o no es Bearer, continuar sin autenticar
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // Extraer el token
            final String jwt = authHeader.substring(7);

            // Validar el token (firma y expiración)
            if (!jwtUtil.validateToken(jwt)) {
                filterChain.doFilter(request, response);
                return;
            }

            // Extraer JTI y verificar que no esté revocado
            String jti = jwtUtil.getJtiFromToken(jwt);
            if (jti != null) {
                // Verificar si existe una sesión activa con este JTI
                boolean isRevoked = userSessionRepository.findActiveSessionByJti(jti).isEmpty();
                if (isRevoked) {
                    // El token fue revocado (logout)
                    logger.warn("Access token with JTI " + jti + " has been revoked");
                    filterChain.doFilter(request, response);
                    return;
                }
            }

            // Extraer claims
            JWTClaimsSet claims = jwtUtil.getClaimsFromToken(jwt);
            String email = claims.getSubject();

            // Si el usuario ya está autenticado, no hacer nada
            if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                // Extraer roles de los tenants
                List<GrantedAuthority> authorities = new ArrayList<>();

                try {
                    List<Map<String, Object>> tenants = (List<Map<String, Object>>) claims.getClaim("tenants");

                    if (tenants != null) {
                        for (Map<String, Object> tenant : tenants) {
                            String role = (String) tenant.get("role");
                            if (role != null) {
                                authorities.add(new SimpleGrantedAuthority(role));
                            }
                        }
                    }
                } catch (Exception e) {
                    // Si hay error parseando tenants, continuar sin roles
                    logger.warn("Error parsing tenant roles from JWT: " + e.getMessage());
                }

                // Crear el objeto de autenticación
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        email,
                        null,
                        authorities
                );

                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // Establecer la autenticación en el contexto de seguridad
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }

        } catch (Exception e) {
            logger.error("Error processing JWT token: " + e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
