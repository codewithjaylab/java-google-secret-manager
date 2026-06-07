package com.ejemplo.secrets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementación de {@link SecretProvider} que lee secretos desde las properties
 * de Spring Boot (application.yml / application-secrets.yml).
 * <p>
 * Útil para desarrollo local y tests, donde no se desea depender de GCP.
 * Los secretos se configuran con el prefijo {@code secrets.}:
 * <pre>
 * secrets:
 *   DB_PASSWORD: local-dev-pass
 *   API_KEY_EXTERNA: sk-test-12345
 * </pre>
 * <p>
 * Thread-safe gracias a {@link ConcurrentHashMap}.
 */
public class LocalSecretProvider implements SecretProvider {

    private static final Logger log = LoggerFactory.getLogger(LocalSecretProvider.class);

    private final Map<String, String> secrets = new ConcurrentHashMap<>();

    /**
     * Carga los secretos desde el Environment de Spring.
     *
     * @param env         Environment de Spring
     * @param secretNames lista de nombres de secretos a precargar
     */
    public LocalSecretProvider(Environment env, List<String> secretNames) {
        for (String name : secretNames) {
            String value = env.getProperty("secrets." + name);
            if (value != null) {
                secrets.put(name, value);
                log.debug("Secreto local cargado: [{}]", name);
            } else {
                log.warn("Secreto local [{}] no encontrado en application.yml", name);
            }
        }
        log.info("LocalSecretProvider inicializado con {} secreto(s)", secrets.size());
    }

    @Override
    public String getSecret(String secretName) {
        String value = secrets.get(secretName);
        if (value == null) {
            log.error("Secreto local [{}] no encontrado", secretName);
            throw new SecretNotFoundException(secretName);
        }
        log.debug("Secreto local [{}] entregado", secretName);
        return value;
    }

    @Override
    public void refreshSecret(String secretName) {
        log.debug("LocalSecretProvider: refresh no aplica (secretos estáticos)");
        // No-op: los valores locales son estáticos y no cambian en runtime.
    }
}
