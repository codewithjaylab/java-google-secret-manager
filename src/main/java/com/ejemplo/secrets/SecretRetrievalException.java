package com.ejemplo.secrets;

/**
 * Lanzada cuando ocurre un error de infraestructura al recuperar un secreto
 * (fallo de red, permisos, API deshabilitada, etc.).
 */
public class SecretRetrievalException extends RuntimeException {

    public SecretRetrievalException(String secretName, Throwable cause) {
        super("Error al recuperar el secreto: " + secretName, cause);
    }
}
