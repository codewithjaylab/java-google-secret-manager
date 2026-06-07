package com.ejemplo.secrets;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.concurrent.TimeUnit;

/**
 * Configuración Spring que implementa el <strong>Patrón Factory</strong>:
 * <p>
 * Según la propiedad {@code secret-manager.source}, se instancia:
 * <ul>
 *   <li>{@code "local"} → {@link LocalSecretProvider} (desarrollo / tests)</li>
 *   <li>{@code "gsm"}   → {@link GcpSecretProvider} (producción con Google Secret Manager)</li>
 * </ul>
 * <p>
 * El cliente (servicios de negocio) depende de la interfaz {@link SecretProvider}
 * y nunca sabe qué implementación concreta está usando. Esto sigue el Principio
 * de Inversión de Dependencias (DIP) de SOLID y facilita las pruebas unitarias.
 */
@Configuration
public class SecretManagerConfig {

    /**
     * Factory method para entorno local.
     * Se activa cuando {@code secret-manager.source=local} o no está definida.
     */
    @Bean
    @ConditionalOnProperty(
            name = "secret-manager.source",
            havingValue = "local",
            matchIfMissing = true
    )
    public SecretProvider localSecretProvider(
            Environment env,
            SecretManagerProperties props) {
        return new LocalSecretProvider(env, props.getSecrets());
    }

    /**
     * Factory method para Google Secret Manager.
     * Se activa cuando {@code secret-manager.source=gsm}.
     */
    @Bean
    @ConditionalOnProperty(name = "secret-manager.source", havingValue = "gsm")
    public SecretProvider gcpSecretProvider(SecretManagerProperties props) {
        Cache<String, String> cache = Caffeine.newBuilder()
                .expireAfterWrite(props.getCacheTtlSeconds(), TimeUnit.SECONDS)
                .recordStats()
                .build();

        return new GcpSecretProvider(props.getProjectId(), cache, props.isFailOnMissing());
    }

    /**
     * Expone el servicio de secretos a la aplicación.
     * Siempre se crea, sin importar la fuente elegida.
     */
    @Bean
    public SecretManagerService secretManagerService(SecretProvider secretProvider) {
        return new SecretManagerService(secretProvider);
    }
}
