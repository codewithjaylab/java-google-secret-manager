package com.ejemplo.secrets;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Aplicación Spring Boot para integración con Google Secret Manager.
 * <p>
 * Usa el patrón Factory para seleccionar la fuente de secretos según
 * la propiedad {@code secret-manager.source}:
 * <ul>
 *   <li>{@code local} → Lee secretos de application.yml (desarrollo)</li>
 *   <li>{@code gsm}   → Lee secretos desde Google Secret Manager (producción)</li>
 * </ul>
 * <p>
 * La autenticación con GCP se realiza mediante Application Default Credentials (ADC),
 * soportando gcloud CLI en Windows y metadata server en Cloud Run / GKE.
 */
@SpringBootApplication
public class GoogleSecretManagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(GoogleSecretManagerApplication.class, args);
    }
}
