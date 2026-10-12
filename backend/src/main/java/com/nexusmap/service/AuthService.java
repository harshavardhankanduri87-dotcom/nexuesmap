package com.nexusmap.service;

import com.nexusmap.dto.AuthResponse;
import com.nexusmap.dto.LoginRequest;
import com.nexusmap.dto.RegisterRequest;
import com.nexusmap.dto.UserSummaryDto;
import com.nexusmap.exception.EmailAlreadyExistsException;
import com.nexusmap.model.Role;
import com.nexusmap.model.User;
import com.nexusmap.repository.UserRepository;
import com.nexusmap.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = request.email().toLowerCase().trim();

        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new EmailAlreadyExistsException("An account with email '" + normalizedEmail + "' already exists");
        }

        User user = User.builder()
            .name(request.name().trim())
            .email(normalizedEmail)
            .password(passwordEncoder.encode(request.password()))
            .role(Role.ROLE_USER)
            .ghostMode(false)
            .build();

        User savedUser = userRepository.save(user);
        String token = tokenProvider.generateToken(savedUser);

        return AuthResponse.of(token, tokenProvider.getExpirationMs(), UserSummaryDto.fromEntity(savedUser));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.email().toLowerCase().trim();

        try {
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalizedEmail, request.password())
            );
        } catch (BadCredentialsException ex) {
            throw new BadCredentialsException("Invalid email or password");
        }

        User user = userRepository.findByEmailIgnoreCase(normalizedEmail)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + normalizedEmail));

        String token = tokenProvider.generateToken(user);
        return AuthResponse.of(token, tokenProvider.getExpirationMs(), UserSummaryDto.fromEntity(user));
    }

    @Transactional(readOnly = true)
    public UserSummaryDto getCurrentUser(String email) {
        User user = userRepository.findByEmailIgnoreCase(email.toLowerCase().trim())
            .orElseThrow(() -> new UsernameNotFoundException("User profile not found for: " + email));
        return UserSummaryDto.fromEntity(user);
    }
}
