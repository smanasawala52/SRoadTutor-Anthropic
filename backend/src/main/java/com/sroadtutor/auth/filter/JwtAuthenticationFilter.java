package com.sroadtutor.auth.filter;

import com.sroadtutor.auth.service.JwtService;
import com.sroadtutor.exception.UnauthorizedException;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Reads the {@code Authorization: Bearer <jwt>} header on each request.
 * If present and valid, loads a lightweight Authentication into the
 * security context so downstream controllers and {@code @PreAuthorize}
 * can access the user.
 *
 * <p>If the token is missing or invalid, we do NOT throw — we just leave
 * the SecurityContext empty, and Spring Security will reject the request
 * later with 401 if the endpoint required auth.</p>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HEADER);
        if (header != null && header.startsWith(PREFIX)) {
            String token = header.substring(PREFIX.length());
            try {
                Claims claims = jwtService.parseAndValidate(token);
                var userId = jwtService.extractUserId(claims);
                var role = jwtService.extractRole(claims);

                var auth = new UsernamePasswordAuthenticationToken(
                        userId,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
                );
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (UnauthorizedException ex) {
                // Expected path for an invalid / expired / tampered JWT.
                // Clear the context and let SecurityConfig's filter chain
                // emit the 401 if the endpoint required auth. We log at
                // DEBUG only — every unauth request would otherwise spam
                // the logs.
                log.debug("Rejected JWT from {}: [{}] {}",
                        request.getRemoteAddr(), ex.getCode(), ex.getMessage());
                SecurityContextHolder.clearContext();
            }
            // Anything else (NPE, IllegalStateException, …) is a real bug
            // and MUST propagate so the global error handler can log it
            // with a stack trace and return 500. Swallowing it here would
            // mask the real defect under a generic 401.
        }
        filterChain.doFilter(request, response);
    }
}
