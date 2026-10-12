package com.nexusmap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusmap.dto.LoginRequest;
import com.nexusmap.dto.RegisterRequest;
import com.nexusmap.model.Role;
import com.nexusmap.model.User;
import com.nexusmap.repository.UserRepository;
import com.nexusmap.security.JwtAuthenticationFilter;
import com.nexusmap.security.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("TASK 1: Should successfully register a new user and return 201 Created with JWT")
    void shouldRegisterNewUser() throws Exception {
        RegisterRequest request = new RegisterRequest(
            "Alice Explorer",
            "alice@nexus.edu",
            "SecurePass123!"
        );

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.token").isString())
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.user.name").value("Alice Explorer"))
            .andExpect(jsonPath("$.user.email").value("alice@nexus.edu"))
            .andExpect(jsonPath("$.user.role").value("ROLE_USER"))
            .andExpect(jsonPath("$.user.password").doesNotExist()) // Never expose password
            .andExpect(cookie().exists(JwtAuthenticationFilter.COOKIE_NAME));
    }

    @Test
    @DisplayName("TASK 1: Should reject registration with duplicate email with 409 Conflict")
    void shouldRejectDuplicateEmailRegistration() throws Exception {
        userRepository.save(User.builder()
            .name("Existing User")
            .email("duplicate@nexus.edu")
            .password(passwordEncoder.encode("ExistingPass123!"))
            .role(Role.ROLE_USER)
            .build());

        RegisterRequest duplicateRequest = new RegisterRequest(
            "Another User",
            "duplicate@nexus.edu",
            "NewPassword123!"
        );

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(duplicateRequest)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.error").value("Conflict"))
            .andExpect(jsonPath("$.message", containsString("already exists")));
    }

    @Test
    @DisplayName("TASK 1: Should reject registration with invalid input with 400 Bad Request")
    void shouldRejectInvalidRegistrationInputs() throws Exception {
        RegisterRequest invalidRequest = new RegisterRequest(
            "", // Blank name
            "not-an-email", // Invalid email
            "short" // Too short (< 8 chars)
        );

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.validationErrors.name").exists())
            .andExpect(jsonPath("$.validationErrors.email").exists())
            .andExpect(jsonPath("$.validationErrors.password").exists());
    }

    @Test
    @DisplayName("TASK 2: Should successfully authenticate user and return JWT token")
    void shouldLoginSuccessfully() throws Exception {
        userRepository.save(User.builder()
            .name("Bob Architect")
            .email("bob@nexus.edu")
            .password(passwordEncoder.encode("MatrixPass2026!"))
            .role(Role.ROLE_USER)
            .build());

        LoginRequest loginRequest = new LoginRequest("bob@nexus.edu", "MatrixPass2026!");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").isString())
            .andExpect(jsonPath("$.user.email").value("bob@nexus.edu"))
            .andExpect(cookie().exists(JwtAuthenticationFilter.COOKIE_NAME));
    }

    @Test
    @DisplayName("TASK 2: Should reject login with invalid password with 401 Unauthorized")
    void shouldRejectInvalidCredentials() throws Exception {
        userRepository.save(User.builder()
            .name("Bob Architect")
            .email("bob@nexus.edu")
            .password(passwordEncoder.encode("MatrixPass2026!"))
            .role(Role.ROLE_USER)
            .build());

        LoginRequest wrongPassword = new LoginRequest("bob@nexus.edu", "WrongPassword123!");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(wrongPassword)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    @DisplayName("TASK 3 & 6: Should reject access to protected /api/auth/me without token (401)")
    void shouldRejectUnauthenticatedAccessToMe() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("TASK 3 & 6: Should allow access to /api/auth/me with valid Bearer token")
    void shouldAllowAccessToMeWithBearerToken() throws Exception {
        User user = userRepository.save(User.builder()
            .name("Charlie Node")
            .email("charlie@nexus.edu")
            .password(passwordEncoder.encode("Passcode123!"))
            .role(Role.ROLE_USER)
            .build());

        String token = jwtTokenProvider.generateToken(user);

        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Charlie Node"))
            .andExpect(jsonPath("$.email").value("charlie@nexus.edu"))
            .andExpect(jsonPath("$.role").value("ROLE_USER"));
    }

    @Test
    @DisplayName("TASK 3 & 6: Should allow access to /api/auth/me using HttpOnly cookie")
    void shouldAllowAccessToMeWithHttpOnlyCookie() throws Exception {
        User user = userRepository.save(User.builder()
            .name("Diana Mesh")
            .email("diana@nexus.edu")
            .password(passwordEncoder.encode("Passcode123!"))
            .role(Role.ROLE_USER)
            .build());

        String token = jwtTokenProvider.generateToken(user);

        mockMvc.perform(get("/api/auth/me")
                .cookie(new Cookie(JwtAuthenticationFilter.COOKIE_NAME, token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Diana Mesh"))
            .andExpect(jsonPath("$.email").value("diana@nexus.edu"));
    }

    @Test
    @DisplayName("TASK 3 & 6: Should reject invalid/malformed JWT token on protected endpoint")
    void shouldRejectInvalidJwtToken() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer invalid.jwt.token"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("TASK 3: Should return 403 Forbidden when regular USER attempts to access ADMIN endpoint")
    void shouldDenyUserAccessToAdminEndpoint() throws Exception {
        User regularUser = userRepository.save(User.builder()
            .name("Regular User")
            .email("regular@nexus.edu")
            .password(passwordEncoder.encode("Passcode123!"))
            .role(Role.ROLE_USER)
            .build());

        String userToken = jwtTokenProvider.generateToken(regularUser);

        mockMvc.perform(get("/api/admin/metrics")
                .header("Authorization", "Bearer " + userToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    @DisplayName("TASK 3: Should allow access to /api/admin/metrics when authenticated as ADMIN")
    void shouldAllowAdminAccessToAdminEndpoint() throws Exception {
        User adminUser = userRepository.save(User.builder()
            .name("System Admin")
            .email("admin@nexus.edu")
            .password(passwordEncoder.encode("AdminPass123!"))
            .role(Role.ROLE_ADMIN)
            .build());

        String adminToken = jwtTokenProvider.generateToken(adminUser);

        mockMvc.perform(get("/api/admin/metrics")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("AUTHORIZED"))
            .andExpect(jsonPath("$.accessLevel").value("ADMINISTRATOR"));
    }
}
