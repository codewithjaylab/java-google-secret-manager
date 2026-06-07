package com.ejemplo.secrets;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controlador REST para consultar secretos en runtime.
 * Solo para desarrollo y debugging — no exponer en produccion sin autenticacion.
 */
@RestController
@RequestMapping("/api/secrets")
public class SecretController {

    private final SecretManagerService secretManager;

    public SecretController(SecretManagerService secretManager) {
        this.secretManager = secretManager;
    }

    @GetMapping("/{name}")
    public ResponseEntity<String> getSecret(@PathVariable String name) {
        try {
            String value = secretManager.getSecret(name);
            return ResponseEntity.ok(value);
        } catch (SecretNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (SecretRetrievalException e) {
            return ResponseEntity.internalServerError()
                    .body("Error retrieving secret: " + e.getMessage());
        }
    }
}
