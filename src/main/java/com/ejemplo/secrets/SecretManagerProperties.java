package com.ejemplo.secrets;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Propiedades de configuración para el módulo de Secret Manager.
 * <p>
 * Mapea las propiedades bajo el prefijo {@code secret-manager} en application.yml.
 */
@Component
@ConfigurationProperties(prefix = "secret-manager")
public class SecretManagerProperties {

    /**
     * Fuente de secretos: "local" o "gsm".
     */
    private String source = "local";

    /**
     * Lista de nombres de secretos requeridos por la aplicación.
     */
    private List<String> secrets = List.of();

    /**
     * ID del proyecto GCP (solo para source=gsm).
     */
    private String projectId;

    /**
     * TTL del caché en segundos (solo para source=gsm).
     */
    private int cacheTtlSeconds = 300;

    /**
     * Si es {@code true}, falla rápido si un secreto no existe.
     */
    private boolean failOnMissing = true;

    // --- Getters y Setters ---

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public List<String> getSecrets() {
        return secrets;
    }

    public void setSecrets(List<String> secrets) {
        this.secrets = secrets;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public int getCacheTtlSeconds() {
        return cacheTtlSeconds;
    }

    public void setCacheTtlSeconds(int cacheTtlSeconds) {
        this.cacheTtlSeconds = cacheTtlSeconds;
    }

    public boolean isFailOnMissing() {
        return failOnMissing;
    }

    public void setFailOnMissing(boolean failOnMissing) {
        this.failOnMissing = failOnMissing;
    }
}
