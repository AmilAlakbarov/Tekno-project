package com.example.productauth.config;

import com.example.productauth.domain.AdminUser;
import com.example.productauth.repository.AdminUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminUserInitializer implements CommandLineRunner {
    private final AdminUserRepository users;
    private final PasswordEncoder encoder;
    @Value("${app.admin.username:admin}") private String username;
    @Value("${app.admin.password:}") private String password;

    public AdminUserInitializer(AdminUserRepository users, PasswordEncoder encoder) {
        this.users = users; this.encoder = encoder;
    }
    @Override public void run(String... args) {
        if (!password.isBlank() && users.findByUsername(username).isEmpty()) {
            users.save(new AdminUser(username, encoder.encode(password), "ADMIN"));
        }
    }
}
