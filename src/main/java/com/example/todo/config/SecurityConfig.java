package com.example.todo.config;

import com.example.todo.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

//    @Bean
//    CorsConfigurationSource corsConfigurationSource(
//            @Value("${CORS_ALLOWED_ORIGINS:http://localhost:3000,http://localhost:5173}") String origins) {
//
//        var allowed = Arrays.stream(origins.split(","))
//                .map(String::trim).filter(s -> !s.isEmpty()).toList();
//
//        var cfg = new CorsConfiguration();
//        cfg.setAllowedOrigins(allowed); // или setAllowedOriginPatterns(...)
//        cfg.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
//        cfg.setAllowedHeaders(List.of("*"));
//        cfg.setExposedHeaders(List.of("ETag"));
//        cfg.setAllowCredentials(true);
//        cfg.setMaxAge(3600L); // cash preflight for 1 hour
//
//        var src = new UrlBasedCorsConfigurationSource();
//        src.registerCorsConfiguration("/**", cfg);
//        return src;
//    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${cors.allowed-origins:http://localhost:3000,http://localhost:5173}") String origins) {
        List<String> allowed = Arrays.stream(origins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
                "http://localhost:3000",
                "http://localhost:5173",
                "https://todolist-anastasiia-desiateryk.netlify.app"
        ));
        config.addAllowedMethod("*");
        config.addAllowedHeader("*");
        config.setExposedHeaders(List.of("ETag"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", config);
        return src;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers(HttpMethod.POST, "/auth/google", "/auth/logout").permitAll()
                    .requestMatchers("/auth/**", "/swagger-ui/**", "/v3/api-docs/**", "/actuator/health", "/actuator/health", "/actuator/health/**", "/actuator/health/readiness", "/actuator/health/liveness", "/readyz", "/livez").permitAll()

                    .anyRequest().authenticated()
            )
            .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) -> res.sendError(401))
                        .accessDeniedHandler((req, res, e) -> res.sendError(403))
                );
        return http.build();
    }
}
