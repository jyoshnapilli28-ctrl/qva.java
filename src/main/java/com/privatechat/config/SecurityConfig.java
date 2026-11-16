package com.privatechat.config;

import com.privatechat.service.UserDetailsServiceImpl;
import com.privatechat.operations.OperationsAccess;
import com.privatechat.operations.WorkspaceSessions;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Spring Security configuration.
 * - Form-based login for the browser chat client.
 * - Session-based authentication (no JWT needed for a private 4-person system).
 * - BCrypt password hashing.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private final UserDetailsServiceImpl userDetailsService;

    public SecurityConfig(UserDetailsServiceImpl userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, OperationsAccess operations,
                                          SessionRegistry sessions, WorkspaceSessions workspace) throws Exception {
        var v2 = new AntPathRequestMatcher("/api/v2/**");
        var legacyApi = new AntPathRequestMatcher("/api/**");
        http
            .authenticationProvider(authenticationProvider())
            .authorizeHttpRequests(auth -> auth
                // Public static resources and login endpoint (permits both GET and HEAD)
                .requestMatchers("/", "/index.html", "/css/**", "/js/**", "/favicon.ico").permitAll()
                .requestMatchers(HttpMethod.GET, QvaWebRoutes.PUBLIC_RESOURCES).permitAll()
                .requestMatchers(HttpMethod.HEAD, QvaWebRoutes.PUBLIC_RESOURCES).permitAll()
                .requestMatchers("/api/auth/login", "/api/auth/logout", "/api/auth/status", "/api/auth/csrf",
                        "/api/auth/register", "/api/health").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/api/v2/host/**").access((authentication, context) -> {
                    var principal = authentication.get();
                    return new AuthorizationDecision(principal != null && principal.isAuthenticated()
                            && !(principal instanceof AnonymousAuthenticationToken)
                            && operations.isHostRequest(principal.getName(), context.getRequest()));
                })
                // Preserve the classic chat route, but require the same approved-device boundary as /api/v2.
                .requestMatchers("/api/rooms/**", "/api/users/**", "/ws/**").access((authentication, context) -> {
                    var principal = authentication.get();
                    return new AuthorizationDecision(principal != null && principal.isAuthenticated()
                            && !(principal instanceof AnonymousAuthenticationToken)
                            && operations.isApprovedDeviceRequest(principal.getName(), context.getRequest()));
                })
                .requestMatchers(v2).authenticated()
                // Everything else requires authentication
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginProcessingUrl("/api/auth/login")
                .successHandler(loginSuccessHandler())
                .failureHandler(loginFailureHandler())
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .addLogoutHandler((request, response, authentication) ->
                        workspace.logout(request.getSession(false)))
                .logoutSuccessHandler(logoutSuccessHandler())
                .deleteCookies("JSESSIONID")
                .invalidateHttpSession(true)
                .permitAll()
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .maximumSessions(1).sessionRegistry(sessions)
            )
            .csrf(csrf -> csrf
                // Keep established legacy requests compatible. Account creation and every v2 mutation require CSRF.
                .ignoringRequestMatchers(request -> legacyApi.matches(request) && !v2.matches(request)
                                && !new AntPathRequestMatcher("/api/auth/register", "POST").matches(request),
                        new AntPathRequestMatcher("/ws/**"))
            )
            .exceptionHandling(ex -> ex
                // Return 401 JSON instead of redirect for API clients
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setContentType("application/json");
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.getWriter().write("{\"error\":\"Authentication required\",\"status\":401}");
                })
            );

        return http.build();
    }

    private AuthenticationSuccessHandler loginSuccessHandler() {
        return (request, response, authentication) -> {
            log.info("Authentication succeeded for user '{}'", authentication.getName());
            userDetailsService.recordLogin(authentication.getName());
            response.setContentType("application/json");
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(
                "{\"success\":true,\"username\":\"" + authentication.getName() + "\"}"
            );
        };
    }

    private SimpleUrlAuthenticationFailureHandler loginFailureHandler() {
        return new SimpleUrlAuthenticationFailureHandler() {
            @Override
            public void onAuthenticationFailure(
                    jakarta.servlet.http.HttpServletRequest request,
                    jakarta.servlet.http.HttpServletResponse response,
                    org.springframework.security.core.AuthenticationException exception)
                    throws java.io.IOException {
                String attemptedUsername = request.getParameter("username");
                log.warn("Authentication failed for username '{}': {} ({})",
                        attemptedUsername != null ? attemptedUsername : "unknown",
                        exception.getMessage(),
                        exception.getClass().getSimpleName());
                response.setContentType("application/json");
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"success\":false,\"error\":\"Invalid username or password\"}");
            }
        };
    }

    private LogoutSuccessHandler logoutSuccessHandler() {
        return (request, response, authentication) -> {
            response.setContentType("application/json");
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write("{\"success\":true,\"message\":\"Logged out\"}");
        };
    }
}

