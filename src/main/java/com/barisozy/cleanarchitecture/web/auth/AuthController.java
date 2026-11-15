package com.barisozy.cleanarchitecture.web.auth;

import com.barisozy.cleanarchitecture.application.auth.AuthResult;
import com.barisozy.cleanarchitecture.application.auth.AuthService;
import com.barisozy.cleanarchitecture.application.auth.UserView;
import com.barisozy.cleanarchitecture.application.security.AuthenticatedUser;
import com.barisozy.cleanarchitecture.web.auth.dto.AuthResponse;
import com.barisozy.cleanarchitecture.web.auth.dto.LoginRequest;
import com.barisozy.cleanarchitecture.web.auth.dto.RegisterRequest;
import com.barisozy.cleanarchitecture.web.auth.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP adapter of the authentication feature.
 *
 * <p>Registration and login are public; the current profile requires a bearer token. Expected
 * business failures are thrown by {@link AuthService} and translated into a {@code ProblemDetail}
 * by the global exception handler.
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Registration and login")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @Operation(
            summary = "Register a user",
            description = "Creates a user with the USER role and issues an access token.")
    @ApiResponse(responseCode = "201", description = "User registered")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResult result = authService.register(request.email(), request.password());
        // The only representation of the created account is the current-user endpoint.
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/auth/me"))
                .cacheControl(CacheControl.noStore())
                .body(AuthResponse.from(result));
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate", description = "Validates the credentials and issues an access token.")
    @ApiResponse(responseCode = "200", description = "Authenticated")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResult result = authService.login(request.email(), request.password());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(AuthResponse.from(result));
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Current user", description = "Returns the profile of the authenticated user.")
    @ApiResponse(responseCode = "200", description = "Current user")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal AuthenticatedUser caller) {
        UserView current = authService.currentUser(caller);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(UserResponse.from(current));
    }
}
