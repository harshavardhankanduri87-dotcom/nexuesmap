package com.nexusmap.controller;

import com.nexusmap.dto.AuthResponse;
import com.nexusmap.dto.LoginRequest;
import com.nexusmap.dto.RegisterRequest;
import com.nexusmap.dto.UserSummaryDto;
import com.nexusmap.security.JwtAuthenticationFilter;
import com.nexusmap.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping({"/api/auth", "/api/v1/auth"})
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @org.springframework.beans.factory.annotation.Value("${nexusmap.cookies.secure:false}")
    private boolean secureCookies;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletResponse response
    ) {
        AuthResponse authResponse = authService.register(request);
        addAuthCookie(response, authResponse.token(), authResponse.expiresIn());
        return ResponseEntity.status(HttpStatus.CREATED).body(authResponse);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response
    ) {
        AuthResponse authResponse = authService.login(request);
        addAuthCookie(response, authResponse.token(), authResponse.expiresIn());
        return ResponseEntity.ok(authResponse);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(JwtAuthenticationFilter.COOKIE_NAME, "")
            .httpOnly(true)
            .secure(secureCookies)
            .path("/")
            .maxAge(0)
            .sameSite("Lax")
            .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    @GetMapping("/me")
    public ResponseEntity<UserSummaryDto> getCurrentUser(@AuthenticationPrincipal UserDetails userDetails) {
        UserSummaryDto userSummary = authService.getCurrentUser(userDetails.getUsername());
        return ResponseEntity.ok(userSummary);
    }

    private void addAuthCookie(HttpServletResponse response, String token, long expiresInMs) {
        ResponseCookie cookie = ResponseCookie.from(JwtAuthenticationFilter.COOKIE_NAME, token)
            .httpOnly(true)
            .secure(secureCookies)
            .path("/")
            .maxAge(expiresInMs / 1000)
            .sameSite("Lax")
            .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
