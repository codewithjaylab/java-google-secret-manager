package com.ejemplo.secrets;

import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import com.github.benmanes.caffeine.cache.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Implementación de {@link SecretProvider} que obtiene secretos desde
 * <strong>Google Secret Manager</strong>.
 * <p>
 * Usa Application Default Credentials (ADC) para autenticación:
 * <ol>
 *   <li>Variable de entorno {@code GOOGLE_APPLICATION_CREDENTIALS}</li>
 *   <li>Archivo generado por {@code gcloud auth application-default login}</li>
 *   <li>Metadata server de GCP (Cloud Run, GKE, Compute Engine)</li>
 * </ol>
 * <p>
 * Incluye caché Caffeine para evitar llamadas repetidas a la API de GSM.
 * Thread-safe.
 */
public class GcpSecretProvider implements SecretProvider {

    private static final Logger log = LoggerFactory.getLogger(GcpSecretProvider.class);

    private final SecretManagerServiceClient client;
    private final String projectId;
    private final Cache<String, String> cache;
    private final boolean failOnMissing;

    /**
     * Crea el provider. El cliente GSM se construye automáticamente con ADC.
     *
     * @param projectId     ID del proyecto de GCP donde están los secretos
     * @param cache         caché Caffeine (TTL y stats configurados externamente)
     * @param failOnMissing si {@code true}, falla rápido al obtener un secreto inexistente
     */
    public GcpSecretProvider(String projectId, Cache<String, String> cache, boolean failOnMissing) {
        this.projectId = Objects.requireNonNull(projectId, "projectId no puede ser null");
        this.cache = Objects.requireNonNull(cache, "cache no puede ser null");
        this.failOnMissing = failOnMissing;
        try {
            this.client = SecretManagerServiceClient.create();
            log.info("GcpSecretProvider inicializado — projectId=[{}], failOnMissing=[{}]",
                    projectId, failOnMissing);
        } catch (Exception e) {
            throw new SecretRetrievalException("inicializar cliente GSM", e);
        }
    }

    /**
     * Constructor con failOnMissing por defecto = {@code true}.
     */
    public GcpSecretProvider(String projectId, Cache<String, String> cache) {
        this(projectId, cache, true);
    }

    @Override
    public String getSecret(String secretName) {
        try {
            return cache.get(secretName, this::fetchFromGsm);
        } catch (Exception e) {
            log.error("Error al obtener secreto [{}] desde GSM", secretName, e);
            throw new SecretRetrievalException(secretName, e);
        }
    }

    private String fetchFromGsm(String secretName) {
        SecretVersionName versionName = SecretVersionName.of(projectId, secretName, "latest");
        AccessSecretVersionResponse response = client.accessSecretVersion(versionName);
        String value = response.getPayload().getData().toStringUtf8();

        log.info("Secreto [{}] obtenido desde GSM exitosamente", secretName);
        return value;
    }

    @Override
    public void refreshSecret(String secretName) {
        cache.invalidate(secretName);
        log.info("Caché invalidada para secreto [{}] — se recargará en el próximo acceso", secretName);
    }
}
