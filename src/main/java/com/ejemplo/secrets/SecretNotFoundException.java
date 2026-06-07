package com.ejemplo.secrets;

/**
 * Lanzada cuando un secreto solicitado no existe en la fuente configurada.
 */
public class SecretNotFoundException extends RuntimeException {

    public SecretNotFoundException(String secretName) {
        super("Secreto no encontrado: " + secretName);
    }

    public SecretNotFoundException(String secretName, Throwable cause) {
        super("Secreto no encontrado: " + secretName, cause);
    }
}
