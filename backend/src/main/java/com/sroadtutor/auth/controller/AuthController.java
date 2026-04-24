package com.sroadtutor.auth.controller;

import com.sroadtutor.auth.dto.AuthResponse;
import com.sroadtutor.auth.dto.LoginRequest;
import com.sroadtutor.auth.dto.LogoutRequest;
import com.sroadtutor.auth.dto.OAuthLoginRequest;
import com.sroadtutor.auth.dto.RefreshRequest;
import com.sroadtutor.auth.dto.SignupRequest;
import com.sroadtutor.auth.service.AuthService;
import com.sroadtutor.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Auth endpoints.  All of these are publicly reachable — see SecurityConfig
 * for the whitelist.  Everything else requires a valid access token.
 */
@RestController
@RequestMapping("/auth")
@Tag(name = "Auth", description = "Signup, login, OAuth, refresh, logout")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    @Operation(summary = "Create a new email+password account")
    public ResponseEntity<ApiResponse<AuthResponse>> signup(
            @Valid @RequestBody SignupRequest request,
            HttpServletRequest http
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(authService.signup(request, http)));
    }

    @PostMapping("/login")
    @Operation(summary = "Log in with email + password")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest http
    ) {
        return ResponseEntity.ok(ApiResponse.of(authService.login(request, http)));
    }

    @PostMapping("/google")
    @Operation(summary = "Sign in / up with a Google ID token")
    public ResponseEntity<ApiResponse<AuthResponse>> google(
            @Valid @RequestBody OAuthLoginRequest request,
            HttpServletRequest http
    ) {
        return ResponseEntity.ok(ApiResponse.of(authService.loginWithGoogle(request, http)));
    }

    @PostMapping("/facebook")
    @Operation(summary = "Sign in / up with a Facebook access token")
    public ResponseEntity<ApiResponse<AuthResponse>> facebook(
            @Valid @RequestBody OAuthLoginRequest request,
            HttpServletRequest http
    ) {
        return ResponseEntity.ok(ApiResponse.of(authService.loginWithFacebook(request, http)));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange a refresh token for a new access + refresh token pair")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @Valid @RequestBody RefreshRequest request,
            HttpServletRequest http
    ) {
        return ResponseEntity.ok(ApiResponse.of(authService.refresh(request.refreshToken(), http)));
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke a refresh token server-side")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }
}
