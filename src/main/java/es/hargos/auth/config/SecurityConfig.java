package es.hargos.auth.config;

import es.hargos.auth.filter.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth
                        // Endpoints that REQUIRE authentication (must be before /api/auth/**)
                        .requestMatchers("/api/auth/join-with-access-code").authenticated()
                        .requestMatchers("/api/auth/accept-invitation").authenticated()

                        // Public endpoints - authentication
                        .requestMatchers("/api/auth/**").permitAll()

                        // Actuator endpoints - only SUPER_ADMIN
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/actuator/**").hasAuthority("SUPER_ADMIN")

                        // Ritrack endpoints - public (called from external applications)
                        .requestMatchers("/api/ritrack/**").permitAll()

                        // Stripe webhook endpoint - public (Stripe calls this with signature verification)
                        .requestMatchers(HttpMethod.POST, "/api/stripe/webhook").permitAll()

                        // Purchase endpoints - require authentication (user must be logged in to purchase)
                        .requestMatchers("/api/purchase/**").authenticated()

                        // Stripe management endpoints - require authentication
                        .requestMatchers("/api/stripe/**").authenticated()

                        // Session management endpoints - require authentication
                        .requestMatchers("/api/sessions/**").authenticated()

                        // Admin endpoints - require authentication (method-level security with @PreAuthorize)
                        .requestMatchers("/api/admin/**").authenticated()

                        // Tenant Admin endpoints - require authentication
                        .requestMatchers("/api/tenant-admin/**").authenticated()

                        // All other requests require authentication
                        .anyRequest().authenticated()
                )
                // Add JWT filter before UsernamePasswordAuthenticationFilter
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Allowed origins - configure in application.properties
        configuration.setAllowedOrigins(Arrays.asList(
                "http://localhost:3000",
                "http://localhost:5173",
                "https://hargos.es",
                "https://*.hargos.es"
        ));

        // Allowed methods
        configuration.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"
        ));

        // Allowed headers
        configuration.setAllowedHeaders(Arrays.asList(
                "Authorization",
                "Content-Type",
                "Accept",
                "Origin",
                "X-Requested-With",
                "Stripe-Signature"
        ));

        // Expose headers
        configuration.setExposedHeaders(Arrays.asList(
                "Authorization",
                "Content-Type"
        ));

        // Allow credentials
        configuration.setAllowCredentials(true);

        // Max age
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}
