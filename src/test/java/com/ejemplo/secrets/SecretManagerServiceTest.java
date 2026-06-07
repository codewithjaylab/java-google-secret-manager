package com.ejemplo.secrets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios para {@link SecretManagerService}.
 * Verifica la fachada sobre SecretProvider: logging, validación, delegación.
 */
@ExtendWith(MockitoExtension.class)
class SecretManagerServiceTest {

    @Mock
    private SecretProvider secretProvider;

    private SecretManagerService service;

    @BeforeEach
    void setUp() {
        service = new SecretManagerService(secretProvider);
    }

    @Test
    void shouldDelegateGetSecretToProvider() {
        when(secretProvider.getSecret("DB_PASSWORD")).thenReturn("super-secret");

        String result = service.getSecret("DB_PASSWORD");

        assertEquals("super-secret", result);
        verify(secretProvider, times(1)).getSecret("DB_PASSWORD");
    }

    @Test
    void shouldThrowWhenNameIsNull() {
        assertThrows(NullPointerException.class, () -> service.getSecret(null));
        verify(secretProvider, never()).getSecret(any());
    }

    @Test
    void shouldThrowWhenSecretNotFound() {
        when(secretProvider.getSecret("MISSING"))
                .thenThrow(new SecretNotFoundException("MISSING"));

        assertThrows(SecretNotFoundException.class, () -> service.getSecret("MISSING"));
    }

    @Test
    void shouldDelegateRefreshToProvider() {
        service.refreshSecret("API_KEY_EXTERNA");

        verify(secretProvider, times(1)).refreshSecret("API_KEY_EXTERNA");
    }

    @Test
    void shouldThrowOnRefreshWithNullName() {
        assertThrows(NullPointerException.class, () -> service.refreshSecret(null));
        verify(secretProvider, never()).refreshSecret(any());
    }

    @Test
    void shouldPropagateRetrievalException() {
        when(secretProvider.getSecret("DB_PASSWORD"))
                .thenThrow(new SecretRetrievalException("DB_PASSWORD",
                        new RuntimeException("Network error")));

        assertThrows(SecretRetrievalException.class, () -> service.getSecret("DB_PASSWORD"));
    }
}
