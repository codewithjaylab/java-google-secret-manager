package com.ejemplo.secrets;

/**
 * Abstracción única para obtener secretos desde cualquier fuente.
 * <p>
 * Implementaciones concretas:
 * <ul>
 *   <li>{@link LocalSecretProvider} — lee desde application.yml (dev/test)</li>
 *   <li>{@link GcpSecretProvider} — llama a Google Secret Manager (producción)</li>
 * </ul>
 *
 * Aplica el Principio de Inversión de Dependencias (DIP — SOLID): los servicios
 * de negocio dependen de esta abstracción, no de implementaciones concretas.
 */
public interface SecretProvider {

    /**
     * Retorna el valor del secreto identificado por {@code secretName}.
     *
     * @param secretName nombre del secreto (sin prefijos ni rutas)
     * @return el valor del secreto en texto plano
     * @throws SecretNotFoundException si el secreto no existe
     * @throws SecretRetrievalException si ocurre un error de infraestructura
     */
    String getSecret(String secretName);

    /**
     * Invalida la caché para el secreto dado y fuerza una recarga en el
     * próximo acceso. En implementaciones sin caché (LocalSecretProvider)
     * puede ser no-op.
     *
     * @param secretName nombre del secreto a refrescar
     */
    void refreshSecret(String secretName);
}
