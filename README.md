# ms-challenge — API REST Spring Boot

REST API construida con Java 21 + Spring Boot 3 para el challenge técnico de Backend Developer de Tenpo.

---

## Tabla de contenidos

- [Descripción](#descripción)
- [Tecnologías](#tecnologías)
- [Arquitectura](#arquitectura)
- [Decisiones técnicas](#decisiones-técnicas)
- [Prerrequisitos](#prerrequisitos)
- [Cómo ejecutar](#cómo-ejecutar)
- [Configuración](#configuración)
- [Endpoints](#endpoints)
- [Manejo de errores](#manejo-de-errores)
- [Tests](#tests)
- [Docker Hub](#docker-hub)

---

## Descripción

API REST que calcula `(num1 + num2) + porcentaje%` aplicando un porcentaje dinámico obtenido de un servicio externo (mockeado con WireMock). Incluye:

- Reintentos automáticos al servicio externo (3 intentos)
- Historial asíncrono de llamadas con paginación
- Rate limiting (3 RPM) con respuesta 429
- Manejo centralizado de errores 4xx/5xx
- Persistencia en PostgreSQL via Flyway
- Documentación OpenAPI (Swagger UI)

---

## Tecnologías

| Componente               | Tecnología                     |
|--------------------------|--------------------------------|
| Lenguaje                 | Java 21                        |
| Framework                | Spring Boot 3.2 (MVC)          |
| Base de datos            | PostgreSQL 16                  |
| Migraciones              | Flyway                         |
| Retries                  | Resilience4j 2.2               |
| Rate Limiting            | Bucket4j 8.9                   |
| Cliente HTTP             | Retrofit 2 + OkHttp3           |
| Documentación API        | SpringDoc OpenAPI 2.5          |
| Contenedores             | Docker + Docker Compose        |
| Mock servicio externo    | WireMock 3.6                   |
| Testing                  | JUnit 5, Mockito, AssertJ      |

---

## Arquitectura

Arquitectura en capas con separación clara de responsabilidades:

```
com.tenpo.challenge
├── controller/
│   ├── api/                  # CalculatorApi — interfaz solo con anotaciones Swagger / Bean Validation
│   └── impl/                 # CalculatorApiImpl — anotaciones Spring MVC (@RestController)
│
├── service/
│   ├── CalculatorService.java
│   ├── HistoryService.java
│   └── impl/                 # Lógica de negocio: cálculo + persistencia async del historial
│
├── proxy/
│   ├── api/                  # PercentageApi (interfaz Retrofit) + PercentageResponse
│   └── service/
│       ├── PercentageService.java       # Interfaz
│       └── impl/             # PercentageServiceImpl — @Retry implementation
│
├── repository/               # Spring Data JPA — HistoryRepository
├── entity/                   # HistoryEntity — JPA (tabla api_call_history)
│
├── model/
│   ├── request/              # CalculationRequest
│   ├── response/             # CalculationResponse, HistoryResponse, ErrorResponse
│   └── dto/                  # CalculationDto — lógica pura de cálculo
│
├── config/                   # EndpointsConfig, RestClientConfig, AppConfig,
│                             # OpenApiConfig, RequestBodyCachingFilter, ClientIpResolver
├── async/                    # AsyncConfig — @EnableAsync + thread pool dedicado
├── exception/                # GlobalExceptionHandler + ExternalServiceException
└── ratelimit/                # RateLimitInterceptor — Bucket4j (3 RPM)
```

### Flujo de un `POST /api/v1/calculate`

```
Client → RequestBodyCachingFilter (cache body for error logging)
       → RateLimitInterceptor (Bucket4j 3 RPM)
       → CalculatorApiImpl
              ↓
        CalculatorServiceImpl
              ↓
        PercentageServiceImpl
        (Retrofit + OkHttp + Retry x3)
       ↙ success                ↘ failure (después de 3 intentos)
   CalculationDto.compute()   ExternalServiceException
              ↓                        ↓
   HistoryService.saveAsync     GlobalExceptionHandler
   (200 — async)                ↓
              ↓                 HistoryService.saveAsync (503 — async)
        Response 200            ↓
                                Response 503
```

---

## Decisiones técnicas

### `BigDecimal` para los cálculos
Se usa `BigDecimal` en lugar de `double`/`float` para evitar pérdida de precisión. En operaciones financieras `0.1 + 0.2 == 0.30000000000000004` con `double`; con `BigDecimal` el resultado es exacto.

### Retrofit 2 + OkHttp3 para el cliente HTTP
Retrofit da una interfaz declarativa limpia. OkHttp3 expone timeouts (`connectTimeout`, `readTimeout`) y `HttpLoggingInterceptor` configurables vía `application.yml` bajo `http-client.*`. Toda la configuración del cliente se centraliza en `EndpointsConfig` con el pattern `@Bean + @ConfigurationProperties` (un bean `Endpoint` por servicio externo).

### Resilience4j para retries
`@Retry(name = "externalPercentage", fallbackMethod = "fallback")` mantiene la lógica declarativa. 3 intentos con 500ms entre cada uno. Si los 3 fallan, el fallback lanza `ExternalServiceException` y el handler global responde 503.

### Historial asíncrono
`HistoryServiceImpl.saveAsync()` corre en un thread pool dedicado (`async-history-*`) con `@Async`. Si la persistencia falla, el error se loguea pero no se propaga — el endpoint principal nunca se ve afectado. El handler de rejected tasks loguea warning en lugar de bloquear el thread del request (vs el default `CallerRunsPolicy` que sí bloquea).

### Separación interfaz/implementación del controller
`CalculatorApi` (interfaz) lleva solo Swagger + Bean Validation. `CalculatorApiImpl` lleva las anotaciones Spring MVC (`@RestController`, `@PostMapping`, etc.). Esto evita el error de Bean Validation HV000151 (constraints no pueden redefinirse en la implementación) y separa la documentación del routing.

### `GlobalExceptionHandler` + `RequestBodyCachingFilter`
El `@RestControllerAdvice` centraliza el manejo de errores HTTP y registra **toda llamada fallida** en el historial (`MethodArgumentNotValidException`, `HttpMessageNotReadableException`, `ConstraintViolationException`, `ExternalServiceException`, `RuntimeException` genérica, 404 y 405).

Como Jackson consume el `InputStream` del request al deserializar — y si falla la deserialización el body queda inaccesible — el filtro `RequestBodyCachingFilter` envuelve cada request con `ContentCachingRequestWrapper` para poder releer el body desde el exception handler y guardarlo en historial. También registra el `startTime` en un atributo del request para calcular `durationMs` en errores tempranos.

### Rate limiting con Bucket4j
Token bucket en memoria: 3 requests por minuto aplicado a **todos los endpoints `/api/**`** (incluyendo `/history`, según lectura literal del requerimiento *"La API debe soportar un máximo de 3 RPM"*). Se excluyen únicamente endpoints técnicos: `/swagger-ui`, `/api-docs` y `/actuator`. Cuando se excede el límite, el interceptor escribe la respuesta 429 directamente con header `Retry-After` y **registra el evento en el historial** (status 429, IP del cliente) para que el rate limiting sea auditable.

> Limitación conocida: el bucket es **global**. En producción debería ser por consumidor (API key, IP, sessionId). Como el requerimiento dice "La API debe soportar 3 RPM" sin más detalle, se cumple literalmente.

### Captura de IP del cliente
Cada registro de historial incluye la IP de origen (`client_ip`). El `ClientIpResolver` prioriza headers de proxy (`X-Forwarded-For`, `X-Real-IP`) y cae a `request.getRemoteAddr()` como último recurso — necesario cuando la API corre detrás de un load balancer o reverse proxy.

### Constraints en la interfaz, no en la implementación
`@Valid`, `@Min`, `@Max` viven en `CalculatorApi`. Bean Validation (HV000151) prohíbe que un método que sobreescribe a otro redefina anotaciones de constraint. Manteniendo todo en la interfaz se evita el error y la documentación Swagger queda alineada con la validación real.

---

## Prerrequisitos

- Docker y Docker Compose
- Java 21 (solo si compilás local)
- Maven 3.9+ (solo si compilás local)

---

## Cómo ejecutar

### Opción 1: Docker Compose (recomendado)

```bash
git clone <repository-url>
cd ms-challenge

docker compose up --build
```

Servicios levantados:
- **API** → http://localhost:8080
- **Swagger UI** → http://localhost:8080/swagger-ui.html
- **PostgreSQL** → localhost:5432 (db: `tenpo`, user: `tenpo`, pass: `tenpo`)
- **WireMock** (mock del porcentaje, retorna 10%) → http://localhost:9090

### Opción 2: Compilar local

```bash
# Levantar solo db y mock
docker compose up postgres percentageMock -d

# Compilar y correr
mvn clean package -DskipTests
java -jar target/ms-challenge-1.0.0.jar
```

---

## Configuración

Los clientes HTTP externos se configuran bajo `http-client.*` en `application.yml`:

```yaml
http-client:
  percentage-service:
    base-url: http://localhost:9090   # overridable con PERCENTAGE_SERVICE_URL
    connect-timeout: 3000             # milisegundos
    read-timeout: 5000                # milisegundos
    logging-level: BASIC              # NONE | BASIC | HEADERS | BODY
```

Para agregar un cliente externo nuevo: agregar un `@Bean` en `EndpointsConfig` y otro `@Bean` correspondiente en `RestClientConfig`.

Rate limit:
```yaml
rate-limit:
  capacity: 3
  refill-tokens: 3
  refill-period-minutes: 1
```

---

## Endpoints

### `POST /api/v1/calculate`

Calcula `(num1 + num2) + porcentaje%`. El porcentaje se obtiene del servicio externo.

**Request:**
```json
{ "num1": 5, "num2": 5 }
```

**Response 200:**
```json
{
  "num1": 5,
  "num2": 5,
  "percentageApplied": 10.0,
  "result": 11.00
}
```

**Curl:**
```bash
curl -X POST http://localhost:8080/api/v1/calculate \
  -H "Content-Type: application/json" \
  -d '{"num1": 5, "num2": 5}'
```

---

### `GET /api/v1/history`

Historial paginado de llamadas, ordenado por fecha descendente.

| Param | Default | Descripción          |
|-------|---------|----------------------|
| page  | 0       | Página (0-based)     |
| size  | 20      | Tamaño (1-100)       |

**Response 200:**
```json
{
  "content": [
    {
      "id": 1,
      "calledAt": "2026-05-11T01:52:36.728Z",
      "endpoint": "/api/v1/calculate",
      "httpMethod": "POST",
      "parameters": "{\"num1\":5,\"num2\":5}",
      "response": "{\"num1\":5,\"num2\":5,\"percentageApplied\":10,\"result\":11.00}",
      "error": null,
      "statusCode": 200,
      "durationMs": 45,
      "clientIp": "192.168.1.10"
    }
  ],
  "totalElements": 1,
  "totalPages": 1
}
```

**Curl:**
```bash
curl http://localhost:8080/api/v1/history?page=0&size=20
```

---

## Manejo de errores

Todas las respuestas de error siguen este formato:
```json
{ "status": 400, "error": "Bad Request", "message": "num1: must not be null" }
```

| Código | Cuándo se dispara                                                                        | Se guarda en historial |
|--------|------------------------------------------------------------------------------------------|------------------------|
| 400    | Validación falla (`@NotNull`, etc.) o JSON malformado (`HttpMessageNotReadableException`)| ✅                      |
| 400    | Parámetros de query inválidos (`page < 0`, `size > 100`)                                 | ✅                      |
| 404    | Endpoint no existe                                                                       | ❌ (no es ruido útil)   |
| 405    | HTTP method incorrecto                                                                   | ❌                      |
| 429    | Rate limit excedido (>3 RPM)                                                             | ✅                      |
| 500    | Error inesperado en runtime                                                              | ✅                      |
| 503    | Servicio externo no disponible tras 3 reintentos                                         | ✅                      |

---

## Tests

```bash
# Correr todos los tests
mvn test

# Solo tests unitarios (no requieren Docker)
mvn test -Dtest="CalculationDtoTest,CalculatorServiceTest,RateLimitInterceptorTest"
```

**22 tests totales** — todos pasan sin requerir Docker:

| Tipo       | Clase                         | Cobertura                                                                            |
|------------|-------------------------------|--------------------------------------------------------------------------------------|
| Unit       | `CalculationDtoTest`          | Caso base, casos parametrizados, precisión BigDecimal (7 tests)                      |
| Unit       | `CalculatorServiceTest`       | Happy path, falla externa propaga, distintos porcentajes (3 tests)                   |
| Unit       | `PercentageServiceImplTest`   | HTTP 200, IOException → ExternalServiceException, HTTP 500, fallback (4 tests)       |
| Unit       | `RateLimitInterceptorTest`    | Dentro del límite, excede límite (429 + history), excluidos, /history aplica (4 tests)|
| Web layer  | `CalculatorApiImplTest`       | 200 happy path, 400 validation, 400 JSON malformado, 503 (4 tests)                   |

`CalculatorApiImplTest` usa `@WebMvcTest` con `MockMvc` y `@MockBean` — verifica que `GlobalExceptionHandler` se ejecuta y que `HistoryService.saveAsync` se invoca para cada caso de error.

---

## Docker Hub

> **TODO antes de entregar:** publicar la imagen en Docker Hub y reemplazar este bloque.

Para publicar:
```bash
docker build -t <usuario>/ms-challenge:1.0.0 .
docker push <usuario>/ms-challenge:1.0.0
```

Luego actualizar `docker-compose.yml` para usar la imagen publicada (en lugar de `build: .`):
```yaml
api:
  image: <usuario>/ms-challenge:1.0.0
```
