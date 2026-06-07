# Google Secret Manager Integration

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.5-6DB33F?logo=springboot)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-17%2B-ED8B00?logo=openjdk)](https://adoptium.net/)
[![Google Cloud](https://img.shields.io/badge/Google%20Secret%20Manager-4285F4?logo=googlecloud)](https://cloud.google.com/secret-manager)
[![Build: Maven](https://img.shields.io/badge/build-Maven-C71A36?logo=apachemaven)](https://maven.apache.org/)
[![CI](https://github.com/codewithjaylab/java-google-secret-manager/actions/workflows/ci.yml/badge.svg)](https://github.com/codewithjaylab/java-google-secret-manager/actions/workflows/ci.yml)
[![GitHub](https://img.shields.io/badge/repo-codewithjaylab/java--google--secret--manager-181717?logo=github)](https://github.com/codewithjaylab/java-google-secret-manager)

Modulo Spring Boot que integra **Google Secret Manager (GSM)** mediante un patron **Factory**, permitiendo alternar entre secretos locales (desarrollo) y secretos reales de GCP (produccion) con solo cambiar una propiedad de configuracion.

---

## Empezando

```bash
# 1. Clonar y compilar
mvn clean package

# 2. Ejecutar (modo local por defecto)
mvn spring-boot:run

# 3. O directamente con Java
java -jar target/java-google-secret-manager-1.0.0.jar

# 4. Cambiar a GSM en produccion
java -jar target/java-google-secret-manager-1.0.0.jar \
  --secret-manager.source=gsm \
  --secret-manager.project-id=brave-monitor-498704-c0

# 5. Consultar secretos via API (una vez corriendo)
curl http://localhost:8080/api/secrets/ai-api-key

# 6. Ejecutar tests
mvn clean test
```

**Requisitos:** Java 17+, Maven 3.8+, (opcional) proyecto GCP con API Secret Manager habilitada.

---

## Tabla de Contenidos

- [Empezando](#empezando)
- [Motivacion](#motivacion)
- [Arquitectura](#arquitectura)
  - [Diagrama de Clases](#diagrama-de-clases)
  - [Flujo de Resolucion](#flujo-de-resolucion)
  - [Patrones Aplicados](#patrones-aplicados)
- [Componentes](#componentes)
  - [SecretProvider (Interfaz)](#secretprovider-interfaz)
  - [GcpSecretProvider](#gcpsecretprovider)
  - [LocalSecretProvider](#localsecretprovider)
  - [SecretManagerService](#secretmanagerservice)
  - [SecretManagerConfig](#secretmanagerconfig)
  - [SecretManagerProperties](#secretmanagerproperties)
  - [SecretController](#secretcontroller)
  - [Excepciones](#excepciones)
- [Configuracion](#configuracion)
  - [Modo Local (desarrollo)](#modo-local-desarrollo)
  - [Modo GSM (produccion)](#modo-gsm-produccion)
  - [Propiedades Completas](#propiedades-completas)
- [Requisitos](#requisitos)
- [Compilacion y Ejecucion](#compilacion-y-ejecucion)
  - [Con Maven](#con-maven)
  - [Ejecutar la Aplicacion](#ejecutar-la-aplicacion)
  - [Ejecutar en Background y Detener](#ejecutar-en-background-y-detener)
- [Uso](#uso)
  - [Desde un Controlador REST](#desde-un-controlador-rest)
  - [Desde Otro Servicio](#desde-otro-servicio)
- [Pruebas](#pruebas)
  - [Tests Unitarios](#tests-unitarios)
  - [Tests de Integracion](#tests-de-integracion)
  - [Ejecutar Tests](#ejecutar-tests)
- [Cache (Caffeine)](#cache-caffeine)
- [Autenticacion GCP](#autenticacion-gcp)
- [Seguridad](#seguridad)
- [Estructura del Proyecto](#estructura-del-proyecto)
- [Contribuir](#contribuir)
- [Licencia](#licencia)

---

## Motivacion

En aplicaciones Spring Boot que se despliegan en Google Cloud (Cloud Run, GKE, Compute Engine), los secretos (credenciales de base de datos, API keys, tokens JWT) deben almacenarse en **Google Secret Manager** y no en el repositorio ni en variables de entorno. En desarrollo local, sin embargo, depender de GSM real es innecesariamente lento y costoso.

Este modulo resuelve el problema con un **Factory Pattern**: la misma interfaz `SecretProvider` se utiliza en ambos entornos. La unica diferencia es el valor de una propiedad, y Spring elige automaticamente la implementacion adecuada.

---

## Arquitectura

### Diagrama de Clases

```
┌─────────────────────────────────────────────────────────────────────┐
│                        SecretProvider (interface)                    │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │  + getSecret(name: String): String                            │  │
│  │  + refreshSecret(name: String): void                          │  │
│  └───────────────────────────────────────────────────────────────┘  │
│                      ▲                           ▲                   │
│                      │                           │                   │
│          implements   │                           │   implements     │
│                      │                           │                   │
│  ┌─────────────────────────────┐     ┌──────────────────────────┐   │
│  │    LocalSecretProvider      │     │    GcpSecretProvider      │   │
│  │  ──────────────────────     │     │  ────────────────────     │   │
│  │  + Lee de application.yml   │     │  + Llama a GSM API        │   │
│  │  + Sin dependencia GCP      │     │  + Cache Caffeine         │   │
│  │  + Thread-safe (CHM)        │     │  + ADC authentication     │   │
│  │  + refresh = no-op          │     │  + refresh invalida cache │   │
│  └─────────────────────────────┘     └──────────────────────────┘   │
│                                             │                        │
│                                             │ usa                    │
│                                             ▼                        │
│                                   ┌─────────────────────┐           │
│                                   │ SecretManagerService │           │
│                                   │  (GCP Client gRPC)  │           │
│                                   └─────────────────────┘           │
└─────────────────────────────────────────────────────────────────────┘
                           ▲
                           │ inyecta
                           │
              ┌─────────────────────────────┐
              │   SecretManagerConfig       │
              │   (Spring @Configuration)   │
              │   ┌──────────────────────┐  │
              │   │ source = "local" →   │  │
              │   │   LocalSecretProvider│  │
              │   │ source = "gsm"  →   │  │
              │   │   GcpSecretProvider │  │
              │   └──────────────────────┘  │
              └─────────────────────────────┘
              ┌─────────────────────────────┐
              │  SecretManagerProperties    │
              │  @ConfigurationProperties   │
              │  prefix="secret-manager"    │
              └─────────────────────────────┘
```

### Flujo de Resolucion

1. La aplicacion arranca y Spring procesa `SecretManagerConfig`.
2. `SecretManagerConfig` lee `secret-manager.source`:
   - **`local`** (default): crea un `LocalSecretProvider` que lee `secrets.*` del `Environment`.
   - **`gsm`**: crea un `GcpSecretProvider` con caché Caffeine que se conecta a GSM via gRPC.
3. `SecretManagerService` recibe el `SecretProvider` inyectado por Spring y lo expone como fachada.
4. Cualquier servicio de negocio depende unicamente de `SecretManagerService` (o de la interfaz `SecretProvider`).

### Patrones Aplicados

| Patron          | Donde se aplica                                   | Beneficio                                        |
|-----------------|---------------------------------------------------|---------------------------------------------------|
| **Factory**     | `SecretManagerConfig` segun `secret-manager.source` | Seleccion en tiempo de compilacion via conditional beans |
| **Facade**      | `SecretManagerService` sobre `SecretProvider`      | Punto unico de acceso con logging y validacion     |
| **Strategy**    | Interfaz `SecretProvider` con dos implementaciones | Intercambio de algoritmo sin cambiar el cliente    |
| **Dependency Inversion** | Servicios dependen de `SecretProvider` (abstracta) | Bajo acoplamiento, facil testing con mocks        |
| **Cache-Aside** | `GcpSecretProvider` usa Caffeine                   | Cache consultada primero, fallback a GSM API      |

---

## Componentes

### SecretProvider (Interfaz)

`SecretProvider` es el contrato que define como obtener secretos. Sigue el Principio de Inversion de Dependencias (DIP - la "D" de SOLID): los servicios de negocio dependen de esta abstraccion y jamas conocen la implementacion concreta.

```java
public interface SecretProvider {
    String getSecret(String secretName);
    void refreshSecret(String secretName);
}
```

### GcpSecretProvider

Implementacion para **produccion**. Se conecta a Google Secret Manager via gRPC usando Application Default Credentials (ADC).

Caracteristicas clave:
- **Client gRPC**: `SecretManagerServiceClient` se crea automaticamente con ADC.
- **Cache Caffeine**: evita llamadas repetidas a la API. TTL configurable via `secret-manager.cache-ttl-seconds`.
- **Thread-safe**: Caffeine es thread-safe por diseno.
- **Fail-fast**: si `fail-on-missing=true` (default), lanza `SecretNotFoundException` inmediatamente.
- **Refresh**: `refreshSecret()` invalida la entrada en cache; el proximo `getSecret()` recarga desde GSM.

```java
// Acceso real a GSM
SecretVersionName versionName = SecretVersionName.of(projectId, secretName, "latest");
AccessSecretVersionResponse response = client.accessSecretVersion(versionName);
return response.getPayload().getData().toStringUtf8();
```

### LocalSecretProvider

Implementacion para **desarrollo local y tests**. Lee secretos desde `application.yml` bajo la clave `secrets.*`.

Caracteristicas clave:
- **Sin dependencia GCP**: funciona sin conexion a internet ni credenciales.
- **Carga al inicio**: los secretos se precargan desde el `Environment` de Spring en el constructor.
- **Thread-safe**: usa `ConcurrentHashMap` internamente.
- **Sin cache real**: `refreshSecret()` es no-op porque los valores son estaticos en archivo.

```yaml
# application.yml
secrets:
  DB_PASSWORD: local-dev-password-123
  API_KEY_EXTERNA: sk-loc...-abc
```

### SecretManagerService

Fachada (Facade Pattern) que encapsula el `SecretProvider` y anade:
- **Validacion** de parametros (`Objects.requireNonNull`)
- **Logging estructurado** con SLF4J (nunca loguea el valor del secreto)
- **Punto unico de entrada** para toda la aplicacion

```java
@Service
public class SecretManagerService {
    public String getSecret(String name) { ... }
    public void refreshSecret(String name) { ... }
}
```

### SecretManagerConfig

Clase de configuracion Spring (`@Configuration`) que implementa el patron Factory mediante conditional beans:

```java
@Configuration
public class SecretManagerConfig {

    @Bean
    @ConditionalOnProperty(name = "secret-manager.source", havingValue = "local", matchIfMissing = true)
    public SecretProvider localSecretProvider(Environment env, SecretManagerProperties props) {
        return new LocalSecretProvider(env, props.getSecrets());
    }

    @Bean
    @ConditionalOnProperty(name = "secret-manager.source", havingValue = "gsm")
    public SecretProvider gcpSecretProvider(SecretManagerProperties props) {
        Cache<String, String> cache = Caffeine.newBuilder()
                .expireAfterWrite(props.getCacheTtlSeconds(), TimeUnit.SECONDS)
                .recordStats()
                .build();
        return new GcpSecretProvider(props.getProjectId(), cache, props.isFailOnMissing());
    }

    @Bean
    public SecretManagerService secretManagerService(SecretProvider secretProvider) {
        return new SecretManagerService(secretProvider);
    }
}
```

Nota el uso de `matchIfMissing = true` en el bean local: si no se define la propiedad, se asume modo local. Esto hace que el comportamiento por defecto sea seguro para desarrollo.

### SecretManagerProperties

Mapea las propiedades bajo el prefijo `secret-manager.*` usando `@ConfigurationProperties`:

| Propiedad                        | Tipo      | Default  | Descripcion                                         |
|----------------------------------|-----------|----------|-----------------------------------------------------|
| `secret-manager.source`          | String    | `"local"`| `"local"` o `"gsm"`                                 |
| `secret-manager.secrets`         | List      | `[]`     | Nombres de secretos requeridos por la aplicacion    |
| `secret-manager.project-id`      | String    | —        | ID del proyecto GCP (solo en modo gsm)              |
| `secret-manager.cache-ttl-seconds`| int      | `300`    | TTL del cache Caffeine en segundos (solo modo gsm)  |
| `secret-manager.fail-on-missing` | boolean   | `true`   | Fail-fast si un secreto no existe (solo modo gsm)   |

### SecretController

Controlador REST que expone los secretos via HTTP. Permite consultar cualquier secreto registrado sin necesidad de codigo adicional.

Caracteristicas clave:
- **GET `/api/secrets/{name}`** — devuelve el valor del secreto en texto plano
- **404** si el secreto no existe
- **500** con mensaje si hay un error de infraestructura
- Util para debugging, health checks y consumidores externos

```java
@RestController
@RequestMapping("/api/secrets")
public class SecretController {

    private final SecretManagerService secretManager;

    @GetMapping("/{name}")
    public ResponseEntity<String> getSecret(@PathVariable String name) {
        String value = secretManager.getSecret(name);
        return ResponseEntity.ok(value);
    }
}
```

```bash
# Ejemplos de uso
curl http://localhost:8080/api/secrets/ai-api-key
curl http://localhost:8080/api/secrets/DB_PASSWORD
curl http://localhost:8080/api/secrets/INEXISTENTE   # → 404
```

Nota: requiere la dependencia `spring-boot-starter-web` en el `pom.xml`.

### Excepciones

| Excepcion                    | Cuando se lanza                                              |
|------------------------------|--------------------------------------------------------------|
| `SecretNotFoundException`    | El secreto solicitado no existe en la fuente configurada.    |
| `SecretRetrievalException`   | Error de infraestructura: red, permisos, API deshabilitada.  |

Ambas extienden `RuntimeException` (unchecked) para no contaminar la firma de los metodos del negocio.

---

## Configuracion

### Modo Local (desarrollo)

**No requiere GCP.** Los secretos se definen directamente en `application.yml`:

```yaml
secret-manager:
  source: local
  secrets:
    - DB_PASSWORD
    - API_KEY_EXTERNA
    - JWT_SECRET
    - ai-api-key

secrets:
  DB_PASSWORD: local-dev-password-123
  API_KEY_EXTERNA: sk-loc...-abc
  JWT_SECRET: local-jwt-secret-no-usar-en-prod
  ai-api-key: placeholder-local-dev-key
```

### Modo GSM (produccion)

Requiere un proyecto GCP con la API de Secret Manager habilitada y el servicio autenticado via ADC:

```yaml
secret-manager:
  source: gsm
  project-id: mi-proyecto-gcp
  secrets:
    - DB_PASSWORD
    - API_KEY_EXTERNA
    - JWT_SECRET
    - ai-api-key
  cache-ttl-seconds: 300        # 5 minutos
  fail-on-missing: true         # falla rapido si falta un secreto
```

### Propiedades Completas

`application.yml` completo:

```yaml
# =============================================================================
# Configuracion del Modulo Google Secret Manager
# =============================================================================
# La propiedad 'secret-manager.source' determina que implementacion de
# SecretProvider se inyecta (Patron Factory):
#
#   source: local   => LocalSecretProvider   — lee de application.yml (dev/test)
#   source: gsm     => GcpSecretProvider     — llama a GSM real (produccion)
#
# Autenticacion con GCP: Application Default Credentials (ADC).
# En Windows, ejecutar: gcloud auth application-default login
# =============================================================================

secret-manager:
  source: local
  secrets:
    - DB_PASSWORD
    - API_KEY_EXTERNA
    - JWT_SECRET
    - ai-api-key
  project-id: brave-monitor-498704-c0
  cache-ttl-seconds: 300
  fail-on-missing: true

secrets:
  DB_PASSWORD: local-dev-password-123
  API_KEY_EXTERNA: sk-loc...-abc
  JWT_SECRET: local-jwt-secret-no-usar-en-prod
  ai-api-key: placeholder-local-dev-key

spring:
  application:
    name: google-secret-manager

server:
  port: 8080

management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-details: always
```

---

## Compilacion y Ejecucion (detalles)

### Build

```bash
# Compilar
mvn clean compile

# Empaquetar JAR
mvn clean package

# El JAR ejecutable queda en:
# target/java-google-secret-manager-1.0.0.jar
```

### Ejecutar la Aplicacion

```bash
# Ejecutar con Maven (modo local por defecto)
mvn spring-boot:run

# O directamente con Java
java -jar target/java-google-secret-manager-1.0.0.jar

# Especificar modo GSM
java -jar target/java-google-secret-manager-1.0.0.jar \
  --secret-manager.source=gsm \
  --secret-manager.project-id=mi-proyecto-gcp

# Usar un profile distinto
java -jar target/java-google-secret-manager-1.0.0.jar \
  --spring.profiles.active=prod
```

### Ejecutar en Background y Detener

```bash
# Iniciar en background (Windows)
start /B java -jar target/java-google-secret-manager-1.0.0.jar \
  --secret-manager.source=gsm \
  --secret-manager.project-id=brave-monitor-498704-c0

# O desde otro terminal:
java -jar target/java-google-secret-manager-1.0.0.jar \
  --secret-manager.source=gsm \
  --secret-manager.project-id=brave-monitor-498704-c0 &

# Verificar que esta corriendo
curl http://localhost:8080/api/secrets/ai-api-key

# Detener el proceso
# Opcion 1: encontrar el PID y matarlo
netstat -ano | findstr :8080
taskkill /F /PID <PID>

# Opcion 2: si se ejecuto en la misma terminal, Ctrl+C
```

> **Nota:** La aplicacion queda escuchando en `http://localhost:8080`. Para detenerla definitivamente, usa `taskkill` con el PID que muestra `netstat`.

---

## Uso

### Desde un Controlador REST

```java
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
        }
    }

    @PostMapping("/{name}/refresh")
    public ResponseEntity<Void> refreshSecret(@PathVariable String name) {
        secretManager.refreshSecret(name);
        return ResponseEntity.ok().build();
    }
}
```

### Desde Otro Servicio

```java
@Service
public class DatabaseService {

    private final SecretManagerService secrets;

    public DatabaseService(SecretManagerService secrets) {
        this.secrets = secrets;
    }

    public void connect() {
        String password = secrets.getSecret("DB_PASSWORD");
        String apiKey  = secrets.getSecret("API_KEY_EXTERNA");
        // usar los secretos...
    }
}
```

Tambien puedes inyectar `SecretProvider` directamente si no necesitas la fachada:

```java
@Service
public class ExternalApiClient {

    private final SecretProvider secretProvider;

    public ExternalApiClient(SecretProvider secretProvider) {
        this.secretProvider = secretProvider;
    }

    public void callExternalApi() {
        String apiKey = secretProvider.getSecret("API_KEY_EXTERNA");
        // llamar API externa...
    }
}
```

---

## Pruebas

El proyecto incluye tests unitarios con **JUnit 5 + Mockito** y un test de integracion con **Spring Boot Test**.

### Tests Unitarios

| Test Class                      | Que verifica                                               |
|---------------------------------|------------------------------------------------------------|
| `LocalSecretProviderTest`       | Carga de secretos desde Environment, casos borde (null, secretos faltantes), refresh no-op |
| `SecretManagerServiceTest`      | Delegacion al provider, validacion de null, propagacion de excepciones |
| `SecretManagerConfigTest`       | (Integracion) Contexto Spring, seleccion del provider correcto, resolucion de secretos |

### Tests de Integracion

`SecretManagerConfigTest` arranca el contexto completo de Spring con el perfil `test` y verifica:
- Que el contexto Spring se carga correctamente
- Que se inyecta `LocalSecretProvider` cuando `source=local`
- Que los secretos se resuelven desde `application-test.yml`
- Que se lanza `SecretNotFoundException` para secretos inexistentes

### Ejecutar Tests

```bash
mvn clean test
```

---

## Cache (Caffeine)

`GcpSecretProvider` utiliza [Caffeine](https://github.com/ben-manes/caffeine) como cache en memoria para evitar llamadas repetidas a la API de GSM.

Configuracion:
- **TTL**: `secret-manager.cache-ttl-seconds` (default: 300s = 5 minutos)
- **Stats**: `recordStats()` habilitado para monitoreo via Actuator
- **Thread-safe**: Caffeine es inherentemente thread-safe
- **Invalidacion**: `refreshSecret()` invalida la entrada, forzando una recarga en el proximo acceso

El cache no esta disponible en `LocalSecretProvider` porque los valores provienen de archivos estaticos.

---

## Autenticacion GCP

`GcpSecretProvider` usa **Application Default Credentials (ADC)** para autenticarse contra Google Secret Manager. El orden de resolucion de credenciales es:

1. **Variable de entorno** `GOOGLE_APPLICATION_CREDENTIALS` apuntando a un archivo JSON de service account.
2. **Archivo de credenciales por defecto** generado por `gcloud auth application-default login`.
3. **Metadata server de GCP** (Cloud Run, GKE, Compute Engine, etc.).

### En Windows (desarrollo local)

```bash
# Instalar gcloud CLI (si no lo tienes)
# Descargar desde: https://cloud.google.com/sdk/docs/install

# Iniciar sesion e inicializar credenciales por defecto
gcloud auth application-default login

# Seleccionar proyecto
gcloud config set project mi-proyecto-gcp
```

### En Cloud Run / GKE

No requiere configuracion adicional. La cuenta de servicio asociada al recurso se autentica automaticamente via el metadata server de GCP.

---

## Seguridad

- **Nunca se loguean valores de secretos**. Solo se registra el nombre del secreto solicitado.
- **Fail-fast**: con `fail-on-missing=true` (default), la aplicacion falla al arranque si un secreto no existe, evitando errores en runtime.
- **Cache con TTL**: los secretos en cache expiran automaticamente, reduciendo la ventana de exposicion si un secreto se rota.
- **Archivo `application-secrets.yml`** en `.gitignore`: los valores de secretos locales jamas deben subirse al repositorio. Usa `application-secrets.yml` (ignorado por git) para valores reales locales y manten `application.yml` con valores dummy o placeholder.
- **Principio de minimo privilegio**: la service account de GCP debe tener unicamente el rol `roles/secretmanager.secretAccessor`.

---

## Estructura del Proyecto

```
java-google-secret-manager/
├── pom.xml                        # Build Maven
├── .mvn/
│   └── jvm.config                 # Opciones JVM para Maven
├── .gitignore                     # Exclusiones git
├── README.md                      # Este archivo
│
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/ejemplo/secrets/
│   │   │       ├── GoogleSecretManagerApplication.java   # Main class
│   │   │       ├── SecretProvider.java                    # Interfaz (DIP)
│   │   │       ├── GcpSecretProvider.java                 # GSM (produccion)
│   │   │       ├── LocalSecretProvider.java               # Local (desarrollo)
│   │   │       ├── SecretManagerService.java              # Facade
│   │   │       ├── SecretManagerConfig.java               # Factory (@Configuration)
│   │   │       ├── SecretManagerProperties.java           # @ConfigurationProperties
│   │   │       ├── SecretController.java                  # REST endpoint
│   │   │       ├── SecretNotFoundException.java           # Excepcion
│   │   │       └── SecretRetrievalException.java          # Excepcion
│   │   │
│   │   └── resources/
│   │       └── application.yml                            # Configuracion principal
│   │
│   └── test/
│       ├── java/
│       │   └── com/ejemplo/secrets/
│       │       ├── LocalSecretProviderTest.java           # Tests unitarios
│       │       ├── SecretManagerServiceTest.java          # Tests unitarios
│       │       └── SecretManagerConfigTest.java           # Test de integracion
│       │
│       └── resources/
│           └── application-test.yml                       # Configuracion para tests
│
├── target/                         # Output de compilacion
```

---

## FAQ

**P: ?Que pasa si olvido definir `secret-manager.source`?**

R: Nada. `matchIfMissing = true` en el bean local hace que el modo local sea el default. La aplicacion arranca con `LocalSecretProvider` y lee secretos de `application.yml`.

**P: ?Puedo usar el provider directamente sin `SecretManagerService`?**

R: Si. La interfaz `SecretProvider` es publica y puedes inyectarla directamente en tus servicios. `SecretManagerService` es una fachada opcional que anade logging y validacion.

**P: ?Como aseguro que mis secretos locales no se suban al repositorio?**

R: El `.gitignore` ya excluye `application-secrets.yml`. Crea este archivo con tus valores reales y manten `application.yml` con datos dummy. Spring Boot carga ambos archivos automaticamente, y el segundo sobrescribe al primero.

**P: ?Como monitoreo el cache de Caffeine?**

R: Con `recordStats()` habilitado, puedes exponer las metricas del cache via Actuator agregando un bean `CacheStats` o usando `CacheManager` de Spring. Ejemplo:

```java
@Bean
public CacheManager cacheManager() {
    return new CaffeineCacheManager();
}
```

Luego consulta `/actuator/metrics/cache.*` o `/actuator/caches`.

---

## Contribuir

1. Haz fork del repositorio.
2. Crea una rama para tu feature: `git checkout -b feature/nueva-funcionalidad`
3. Asegurate de que los tests pasen: `mvn clean test`
4. Envia un Pull Request.

**Guia de estilo**:
- Codigo en **espanol** (nombres de clases, metodos, javadoc, commits)
- Tests en ingles (nombres de metodos)
- Sigue los principios SOLID
- No incluyas secretos reales en los archivos de configuracion

---

## Licencia

Este proyecto es de codigo abierto. Consulta el archivo `LICENSE` para mas detalles (si existe en el repositorio).
