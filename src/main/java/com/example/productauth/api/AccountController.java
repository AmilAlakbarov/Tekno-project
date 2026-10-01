package com.example.productauth.api;

import com.example.productauth.domain.AdminUser;
import com.example.productauth.repository.AdminUserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/accounts")
public class AccountController {
    private final AdminUserRepository users;
    private final PasswordEncoder passwordEncoder;

    public AccountController(AdminUserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public List<AccountRow> list() {
        return users.findAll().stream().map(AccountRow::from).toList();
    }

    @PostMapping
    public AccountRow create(@Valid @RequestBody AccountRequest request) {
        String username = request.username().trim();
        String role = request.role().toUpperCase(Locale.ROOT);
        if (users.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("That username is already in use.");
        }
        AdminUser user = new AdminUser(username, passwordEncoder.encode(request.password()), role);
        return AccountRow.from(users.save(user));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        AdminUser user = users.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Account was not found."));
        if (authentication.getName().equalsIgnoreCase(user.getUsername())) {
            throw new IllegalArgumentException("You cannot delete the account currently being used.");
        }
        users.delete(user);
        return ResponseEntity.noContent().build();
    }

    public record AccountRequest(
            @NotBlank @Size(max = 100) String username,
            @NotBlank @Size(min = 12, max = 200) String password,
            @NotBlank @Pattern(regexp = "ADMIN|OPERATOR|VIEWER") String role) {
    }

    public record AccountRow(UUID id, String username, String role, boolean enabled) {
        static AccountRow from(AdminUser user) {
            return new AccountRow(user.getId(), user.getUsername(), user.getRole(), user.isEnabled());
        }
    }
}
