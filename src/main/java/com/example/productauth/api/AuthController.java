package com.example.productauth.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final CsrfTokenRepository csrfTokenRepository;

    public AuthController(AuthenticationManager authenticationManager, CsrfTokenRepository csrfTokenRepository) {
        this.authenticationManager = authenticationManager;
        this.csrfTokenRepository = csrfTokenRepository;
    }

    @GetMapping("/csrf")
    public CsrfTokenResponse csrfToken(HttpServletRequest request) {
        CsrfToken token = csrfTokenRepository.loadToken(request);
        if (token == null) {
            throw new IllegalStateException("CSRF token was not initialized by the security filter");
        }
        return new CsrfTokenResponse(token.getHeaderName(), token.getParameterName(), token.getToken());
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest,
                               HttpServletResponse servletResponse) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        servletRequest.changeSessionId();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        HttpSession session = servletRequest.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        csrfTokenRepository.saveToken(null, servletRequest, servletResponse);
        return new LoginResponse(authentication.getName(), authentication.getAuthorities().stream()
                .map(a -> a.getAuthority().replace("ROLE_", "")).findFirst().orElse("ADMIN"));
    }

    @GetMapping("/me")
    public LoginResponse me(Authentication authentication) {
        return new LoginResponse(authentication.getName(), authentication.getAuthorities().stream()
                .map(a -> a.getAuthority().replace("ROLE_", "")).findFirst().orElse("VIEWER"));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        return ResponseEntity.noContent().build();
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record LoginResponse(String username, String role) {}
    public record CsrfTokenResponse(String headerName, String parameterName, String token) {}
}
