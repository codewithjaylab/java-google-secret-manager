package com.ejemplo.secrets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios para {@link LocalSecretProvider}.
 * Verifica carga de secretos desde Environment, casos borde y fail-fast.
 */
@ExtendWith(MockitoExtension.class)
class LocalSecretProviderTest {

    @Mock
    private Environment env;

    private LocalSecretProvider provider;

    @BeforeEach
    void setUp() {
        // Configuración base: stubs para secretos existentes
        lenient().when(env.getProperty("secrets.DB_PASSWORD")).thenReturn("pass123");
        lenient().when(env.getProperty("secrets.API_KEY_EXTERNA")).thenReturn("sk-abc");
    }

    @Test
    void shouldLoadSecretsFromEnvironment() {
        when(env.getProperty("secrets.DB_PASSWORD")).thenReturn("pass123");
        when(env.getProperty("secrets.API_KEY_EXTERNA")).thenReturn("sk-abc");

        provider = new LocalSecretProvider(env, List.of("DB_PASSWORD", "API_KEY_EXTERNA"));

        assertEquals("pass123", provider.getSecret("DB_PASSWORD"));
        assertEquals("sk-abc", provider.getSecret("API_KEY_EXTERNA"));
    }

    @Test
    void shouldThrowWhenSecretNotFound() {
        when(env.getProperty("secrets.DB_PASSWORD")).thenReturn("pass123");

        provider = new LocalSecretProvider(env, List.of("DB_PASSWORD"));

        SecretNotFoundException ex = assertThrows(
                SecretNotFoundException.class,
                () -> provider.getSecret("API_KEY_EXTERNA")
        );
        assertTrue(ex.getMessage().contains("API_KEY_EXTERNA"));
    }

    @Test
    void shouldThrowWhenSecretNotConfiguredInConstructor() {
        provider = new LocalSecretProvider(env, List.of());

        SecretNotFoundException ex = assertThrows(
                SecretNotFoundException.class,
                () -> provider.getSecret("DB_PASSWORD")
        );
        assertTrue(ex.getMessage().contains("DB_PASSWORD"));
    }

    @Test
    void shouldHandleNullSecretGracefully() {
        when(env.getProperty("secrets.DB_PASSWORD")).thenReturn("pass123");
        when(env.getProperty("secrets.JWT_SECRET")).thenReturn(null);

        provider = new LocalSecretProvider(env, List.of("DB_PASSWORD", "JWT_SECRET"));

        assertEquals("pass123", provider.getSecret("DB_PASSWORD"));

        SecretNotFoundException ex = assertThrows(
                SecretNotFoundException.class,
                () -> provider.getSecret("JWT_SECRET")
        );
        assertTrue(ex.getMessage().contains("JWT_SECRET"));
    }

    @Test
    void refreshSecretShouldBeNoOp() {
        when(env.getProperty("secrets.DB_PASSWORD")).thenReturn("pass123");

        provider = new LocalSecretProvider(env, List.of("DB_PASSWORD"));

        assertDoesNotThrow(() -> provider.refreshSecret("DB_PASSWORD"));
        // El valor debe seguir siendo el mismo
        assertEquals("pass123", provider.getSecret("DB_PASSWORD"));
    }
}
