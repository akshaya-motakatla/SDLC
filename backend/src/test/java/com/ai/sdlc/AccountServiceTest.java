package com.ai.sdlc;

import com.ai.sdlc.domain.User;
import com.ai.sdlc.dto.RegisterRequest;
import com.ai.sdlc.dto.UserResponse;
import com.ai.sdlc.exception.ConflictException;
import com.ai.sdlc.repository.UserRepository;
import com.ai.sdlc.service.AccountService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {
    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AccountService accountService;

    @Test
    void registerNormalizesIdentityAndStoresOnlyEncodedPassword() {
        when(userRepository.existsByUsernameIgnoreCase("developer")).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCase("dev@example.com")).thenReturn(false);
        when(passwordEncoder.encode("strong-password-123")).thenReturn("$2a$encoded");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = accountService.register(new RegisterRequest(
                " developer ",
                " Dev@Example.com ",
                "strong-password-123",
                "Dev",
                "User"
        ));

        assertEquals("developer", response.username());
        assertEquals("dev@example.com", response.email());
        verify(passwordEncoder).encode("strong-password-123");
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertEquals("$2a$encoded", userCaptor.getValue().getPasswordHash());
    }

    @Test
    void registerRejectsExistingUsernameBeforeEncodingPassword() {
        when(userRepository.existsByUsernameIgnoreCase("developer")).thenReturn(true);

        assertThrows(ConflictException.class, () -> accountService.register(new RegisterRequest(
                "developer",
                "dev@example.com",
                "strong-password-123",
                "Dev",
                "User"
        )));

        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any(User.class));
    }
}
