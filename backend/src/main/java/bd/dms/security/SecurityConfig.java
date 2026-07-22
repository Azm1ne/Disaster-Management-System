package bd.dms.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * The access-control boundary. Authorization is enforced here server-side — the UI hiding
 * routes is a convenience, not the boundary. Sessions are stateless; every request proves
 * itself with a bearer token (except the explicitly public endpoints below).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/login", "/auth/refresh", "/auth/logout").permitAll()
                        .requestMatchers("/public/**").permitAll()
                        // The WebSocket handshake carries no bearer header; the realtime session is
                        // authenticated at STOMP CONNECT and authorized per topic subscription
                        // (see StompAuthChannelInterceptor).
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        // /error is the container's internal dispatch target; it must stay open or a
                        // 403 from the access-denied handler would be re-dispatched and overwritten with 401.
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        // Reading the DEMO clock is open to any signed-in user so every screen
                        // shows the same simulation time; driving the simulation is not.
                        .requestMatchers(HttpMethod.GET, "/simulation/clock").authenticated()
                        .requestMatchers(HttpMethod.POST, "/simulation/**")
                                .hasAnyRole("ADMIN", "COORDINATOR")
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        // Unauthenticated -> 401; authenticated-but-forbidden -> 403.
                        .authenticationEntryPoint((req, res, e) -> res.sendError(HttpServletResponse.SC_UNAUTHORIZED))
                        .accessDeniedHandler((req, res, e) -> res.sendError(HttpServletResponse.SC_FORBIDDEN)))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable());
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }
}
