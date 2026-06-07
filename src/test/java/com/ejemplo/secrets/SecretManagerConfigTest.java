package com.ejemplo.secrets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test de integración: verifica que el contexto de Spring carga correctamente
 * la configuración Factory y el bean {@link SecretManagerService}.
 * <p>
 * Usa el perfil {@code test} que apunta a {@code secret-manager.source=local}.
 * Si el contexto arranca, el Factory seleccionó LocalSecretProvider
 * y todos los beans se inyectaron correctamente.
 */
@SpringBootTest
@ActiveProfiles("test")
class SecretManagerConfigTest {

    @Autowired
    private SecretManagerService secretManagerService;

    @Autowired
    private SecretProvider secretProvider;

    @Test
    void contextShouldLoad() {
        assertNotNull(secretManagerService, "SecretManagerService debe ser inyectado");
    }

    @Test
    void shouldUseLocalProvider() {
        // Con source=local, el provider debe ser LocalSecretProvider
        assertInstanceOf(LocalSecretProvider.class, secretProvider,
                "Con source=local el provider debe ser LocalSecretProvider");
    }

    @Test
    void shouldResolveLocalSecret() {
        // Los secretos locales se definen en application-test.yml
        String password = secretManagerService.getSecret("DB_PASSWORD");
        assertEquals("test-pass", password);
    }

    @Test
    void shouldThrowForMissingSecret() {
        assertThrows(SecretNotFoundException.class,
                () -> secretManagerService.getSecret("NONEXISTENT"));
    }
}
