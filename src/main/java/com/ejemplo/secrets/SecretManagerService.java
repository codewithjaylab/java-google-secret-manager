package com.ejemplo.secrets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Servicio que expone los secretos a la aplicación.
 * <p>
 * Actúa como fachada (Facade Pattern) sobre {@link SecretProvider},
 * añadiendo logging estructurado y validación.
 * <p>
 * <strong>Regla de seguridad:</strong> Nunca loguear el valor del secreto,
 * solo su nombre.
 */
@Service
public class SecretManagerService {

    private static final Logger log = LoggerFactory.getLogger(SecretManagerService.class);

    private final SecretProvider secretProvider;

    public SecretManagerService(SecretProvider secretProvider) {
        this.secretProvider = Objects.requireNonNull(secretProvider, "secretProvider no puede ser null");
    }

    /**
     * Obtiene el valor de un secreto por su nombre.
     *
     * @param name nombre del secreto
     * @return valor del secreto en texto plano
     * @throws SecretNotFoundException     si el secreto no existe
     * @throws SecretRetrievalException    si ocurre un error de infraestructura
     */
    public String getSecret(String name) {
        Objects.requireNonNull(name, "El nombre del secreto no puede ser null");
        log.debug("Solicitando secreto [{}]", name);
        String value = secretProvider.getSecret(name);
        log.debug("Secreto [{}] entregado exitosamente", name);
        return value;
    }

    /**
     * Fuerza la invalidación de la caché y recarga del secreto.
     * Ideal para rotación manual o webhooks de cambio de secreto.
     *
     * @param name nombre del secreto a refrescar
     */
    public void refreshSecret(String name) {
        Objects.requireNonNull(name, "El nombre del secreto no puede ser null");
        log.info("Refresh manual solicitado para secreto [{}]", name);
        secretProvider.refreshSecret(name);
    }
}
