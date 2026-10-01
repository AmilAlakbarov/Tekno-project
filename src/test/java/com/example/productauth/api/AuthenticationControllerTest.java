package com.example.productauth.api;

import com.example.productauth.repository.AdminUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfTokenRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AuthenticationControllerTest {

    private final AuthController authController = new AuthController(null, mock(CsrfTokenRepository.class));
    private final AdminUserRepository users = mock(AdminUserRepository.class);
    private final AccountController accountController =
            new AccountController(users, mock(PasswordEncoder.class));

    @Test
    void returnsUnauthorizedForMissingAuthenticationOnMeEndpoint() {
        assertThat(authController.me(null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void returnsUnauthorizedForAnonymousAuthenticationOnMeEndpoint() {
        var authentication = new AnonymousAuthenticationToken(
                "test", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

        assertThat(authController.me(authentication).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void doesNotLookUpOrDeleteAccountWhenAuthenticationIsMissing() {
        assertThat(accountController.delete(UUID.randomUUID(), null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        verify(users, never()).findById(org.mockito.ArgumentMatchers.any());
        verify(users, never()).delete(org.mockito.ArgumentMatchers.any());
    }
}
