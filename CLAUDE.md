# ms-challenge — Pautas para Claude

Este archivo es la **fuente de verdad** que Claude Code utiliza al trabajar en este proyecto. Define stack, convenciones, reglas duras y pautas de revisión de PRs.

---

## 1. Stack tecnológico

- Java 21
- Spring Boot 3.2 (MVC, no WebFlux)
- PostgreSQL 16 + Flyway
- Retrofit 2 + OkHttp 3 (cliente HTTP externo)
- Resilience4j (retries)
- Bucket4j (rate limiting)
- SpringDoc OpenAPI (Swagger UI)
- JUnit 5 + Mockito + AssertJ
- Docker + Docker Compose

---

## 2. Comandos comunes

```bash
# Build + tests
mvn clean install

# Solo tests
mvn test

# Levantar todo (api + postgres + wiremock)
docker compose up -d --build

# Bajar todo
docker compose down

# Ver logs de la API
docker logs ms-challenge-api -f
```

---

## 3. Convenciones del proyecto

- **BigDecimal** siempre para cálculos numéricos. Nunca `double`/`float`.
- **Bean Validation constraints** (`@Valid`, `@Min`, `@Max`, `@NotNull`) viven en las **interfaces del controller** (`controller/api/*`), nunca en la implementación. Hibernate Validator falla con `HV000151` si se redefinen.
- **HTTP clients externos** se declaran via el patrón `EndpointsConfig` (un `@Bean` con `@ConfigurationProperties` por servicio) + `RestClientConfig` (Retrofit + OkHttp).
- **Excepciones de dominio** extienden `RuntimeException` y **no** llevan `@ResponseStatus`. El `GlobalExceptionHandler` (`@RestControllerAdvice`) decide el código HTTP.
- **Async** para auditoría y operaciones no críticas. Thread pool dedicado en `AsyncConfig` (no usar el ForkJoinPool default).
- **Tests**: JUnit 5 + Mockito + AssertJ. Evitar Hamcrest salvo necesidad puntual.
- **Imports**: nunca usar import wildcard (`import x.y.*`). Importar explícitamente cada clase.
- **Naming**: packages en lowercase sin camelCase (`service/impl`, no `serviceImpl`).

---

## 4. Reglas duras (bloquear PR si se incumplen)

### 4.1 Límite de inyecciones de dependencia
**Máximo 5 dependencias inyectadas por clase.** Si una clase necesita más, es señal de violación de SRP — extraer responsabilidades en colaboradores antes de mergear.

Detección: contar campos `final` inyectados por constructor (`@RequiredArgsConstructor` o constructor explícito) y campos con `@Autowired`. Si el total > 5, rechazar el PR con sugerencia de refactor.

### 4.2 Cobertura de tests mínima
**80% de cobertura de líneas y branches** sobre el código modificado en la PR (no del proyecto completo). Verificar via reporte de JaCoCo si está configurado; si no, exigir tests unitarios por cada método público nuevo o modificado.

Ejemplo de excepciones aceptables: clases puramente declarativas (entities sin lógica, records DTO, configuraciones `@Configuration`), código generado.

### 4.3 SOLID
Cada PR debe respetar:

- **SRP (Single Responsibility):** una clase tiene UNA razón para cambiar. Si describís lo que hace y necesitás la palabra "y", probablemente viola SRP.
- **OCP (Open/Closed):** abierta a extensión, cerrada a modificación. Preferir composición + interfaces sobre modificar clases existentes que ya tienen consumidores.
- **LSP (Liskov):** subtipos deben ser sustituibles sin romper el contrato. Cuidado con throws nuevos o pre/post-condiciones más fuertes.
- **ISP (Interface Segregation):** interfaces chicas y específicas. Mejor 3 interfaces de 2 métodos que 1 de 6.
- **DIP (Dependency Inversion):** depender de abstracciones, no de implementaciones. En este proyecto: `CalculatorService` (interfaz) inyectado, no `CalculatorServiceImpl`.

---

## 5. Pautas de revisión de PR

Cuando se pida revisar una PR, seguí este formato **exacto** y en este orden.

### Información que recibirás
- `GIT_DIFF`: diff unificado de los archivos modificados.
- `changeType` puede ser: **A** (Added), **M** (Modified), **D** (Deleted).

### Tareas

1. **Alcance de enfoque**
   - Inspeccionar *solo* código añadido o modificado (líneas `+`) y su contexto inmediato.
   - Para líneas eliminadas (`-`), evaluar si la eliminación crea riesgo funcional o de regresión.

2. **Analizar en busca de**
   - **Posibles Bugs** — errores de lógica, edge cases, excepciones no manejadas, race conditions, manejo incorrecto de datos, tests que ahora fallan.
   - **SOLID / Diseño** — violaciones SRP/OCP/LSP/ISP/DIP, clases grandes, "God objects", acoplamiento fuerte, falta de abstracciones, **más de 5 dependencias inyectadas**.
   - **Clean Code y estilo** — legibilidad, nombres, duplicación, comentarios vs código autodocumentado, magic numbers, funciones grandes, idioms del lenguaje, micro-issues de performance/memoria obvios. **Imports wildcard (`*`) son un problema.**
   - **Impacto del código eliminado** — ¿la eliminación rompe comportamiento, API contract o tests?
   - **Cobertura de tests** — verificar que código nuevo tenga tests. Flag si la cobertura del cambio parece <80%.

3. **Formato de salida obligatorio** (no agregar preámbulo ni cierre):

```
# Resumen y propósito de la PR
…
# Análisis de los cambios
## Posibles Bugs
…
## Posibles problemas de Diseño / SOLID
…
## Posibles problemas de Clean Code
…
## Posibles problemas por impacto del Código Eliminado
…
## Violaciones de lineamientos REST
…
## Violaciones de lineamientos de Swagger
…
## Cobertura de tests
…
```

Reglas de formato:
- Cada viñeta: `- [Archivo:Línea] descripción breve – sugerencia opcional.`
- Si una sección no tiene hallazgos: `- _No se encontraron problemas_`.
- Cada viñeta ≤ 200 caracteres salvo hallazgo crítico.
- **No** mencionar código que no cambió.
- **Solo** listar problemas reales; no llenar de comentarios irrelevantes.

4. **Tono**
   - Objetivo, constructivo, específico.
   - No aceptar/rechazar la PR — solo enumerar observaciones.
   - Imperativo: "Renombrar variable…", "Agregar null-check…", "Extraer método…".

5. **Documentación de referencia**
   - Si la PR toca endpoints REST → revisar contra §6 (Lineamientos REST). **No** confundir anotaciones Retrofit (`@GET` del cliente) con Spring (`@GetMapping`). Las reglas de path solo aplican a Spring MVC.
   - Si la PR toca documentación Swagger → revisar contra §7 (Lineamientos Swagger).
   - Si encontrás algo que no cumple, indicar cómo modificarlo.

---

## 6. Lineamientos REST

### 6.1 Naming de endpoints
- Sustantivos en **plural**: `/customers` (no `/customer`, no `/get-customer`). Excepción: conceptos singulares como `admin` o `config`.
- Nombres claros, sin abreviaturas (`firstname`, no `fn`).
- Solo minúsculas (RFC 3986).
- Sin caracteres especiales.
- `/` para jerarquía, más específico de izquierda a derecha: `/customers/135/personal-info`.
- Múltiples palabras con **kebab-case**: `personal-info` (no `personalInfo` ni `personal_info`).
- Consistencia en referencias, recursos, entidades y parámetros.

### 6.2 Métodos HTTP
Todos idempotentes excepto POST. GET debe ser **seguro** (no muta estado).

- **GET** — consultas. Sin body.
  - Lista: `GET /clients` → array de DTOs.
  - Individual: `GET /clients/{id}` → DTO.
- **POST** — creación. Body con atributos.
- **PUT** — crear o reemplazar recurso **completo**.
- **PATCH** — actualización **parcial**.
- **DELETE** — eliminar por ID.
- **HEAD** — como GET pero sin body en response.
- **OPTIONS** — describir opciones de comunicación.

### 6.3 Composición de paths
Estructura: `/{service-name}/{prefix}/{resource-path}`.

- **service-name**: nombre del microservicio (`customer`, `partner`, `discount`).
- **prefix**: identifica autenticación:
  - `b2c` — usuarios persona (token b2c via API Gateway). Datos solo del usuario autenticado (usar ID del token, no del request).
  - `b2b` — usuarios empresa (token b2b via API Gateway).
  - `bo` — back-office (token bo).
  - `ext` — servicios externos sin token. No info sensible.
  - `iuse` — uso interno entre microservicios. Solo accesible privadamente.
  - `sfc` — comunicación exclusiva con Salesforce. Privado.
  - `notification` — webhooks de proveedores. API Gateway separado con API Key.
- **resource-path**: el recurso específico.

### 6.4 Request/Response bodies
- **Siempre DTOs** en request y response. Nunca entities, Strings, Maps, ni wrappers ambiguos.
- DTOs anotados con `@Schema` y validaciones (`@NotNull`, `@NotBlank`, etc.).

### 6.5 Versionado
- Header `X-Api-Version` (case-insensitive, RFC 2616).
- Default: `@RequestHeader(value = "X-API-VERSION", defaultValue = "1")`.
- Versión específica: `@GetMapping(..., headers = "X-API-VERSION=2")`.
- Versionar **solo** cuando cambios contractuales rompen compatibilidad o para A/B testing.
- TL es responsable de migrar a única versión y eliminar las viejas.

---

## 7. Lineamientos Swagger

### 7.1 Separación interfaz / controller
Para cada `@RestController`, crear una interfaz Java separada que lleve **toda** la documentación Swagger. El `@RestController` solo lleva mappings (`@GetMapping`, etc.) y llamadas al service.

Ejemplo del proyecto:
- `CalculatorApi` (interfaz, en `controller/api/`) → `@Tag`, `@Operation`, `@ApiResponse`, `@Schema`, **y** las constraints de Bean Validation.
- `CalculatorApiImpl` (clase, en `controller/impl/`) → `@RestController`, `@RequestMapping`, `@PostMapping`.

### 7.2 Anotaciones obligatorias

**En la interfaz (controller):**
```java
@Tag(name = "Calculator API", description = "Endpoints para el challenge")
public interface CalculatorApi { … }
```

**En cada método:**
```java
@Operation(
    summary = "Título corto",
    description = "Descripción larga del comportamiento",
    tags = {"rol1", "rol2"},
    security = {@SecurityRequirement(name = "rol3")}
)
@ApiResponses({
    @ApiResponse(responseCode = "200", description = "Éxito"),
    @ApiResponse(responseCode = "400", description = "Parámetros inválidos"),
    @ApiResponse(responseCode = "503", description = "Servicio externo no disponible")
})
ResponseEntity<CalculationResponse> calculate(@Valid CalculationRequest request);
```

### 7.3 DTOs y parámetros
- `@Schema(name, description, example, required)` en cada campo de los DTOs.
- `@Parameter` o `@ParameterObject` para query/path params.

```java
@Schema(name = "CalculationRequest", description = "Request del cálculo")
public record CalculationRequest(
    @NotNull
    @Schema(description = "Primer operando", example = "5", required = true)
    BigDecimal num1,

    @NotNull
    @Schema(description = "Segundo operando", example = "10", required = true)
    BigDecimal num2
) {}
```

### 7.4 Servers (opcional, solo si va via API Gateway)
Dentro del `@Operation`, anotar `servers` solo cuando el endpoint se expone via APIGW (sobrescribe los servers globales del `OpenApiConfig`):
```java
servers = {
    @Server(url = "http://localhost:8080/", description = "Local"),
    @Server(url = "https://dominio-privado.dev/", description = "LB Privado DEV"),
    @Server(url = "https://dominio-publico.dev/", description = "API Gateway DEV")
}
```

---

## 8. Generador de README para PRs

Cuando se pida generar el README del PR (para pegar en la descripción), seguir este template. Máximo **4800 caracteres** (reserva de 200 para ajustes).

### Validación previa
Si detectás typos en código (variables, métodos, clases mal escritos), **frená** y devolvé el listado de typos con sugerencias antes de generar el README. Ejemplos: `campignType` → `campaignType`, `retreive()` → `retrieve()`, `CostumerService` → `CustomerService`.

### Categorización de cambios
- **Alta prioridad:** Controllers, Services, DTOs (lógica de negocio).
- **Media prioridad:** Repositories, Configurations.
- **Baja prioridad:** Tests, formato, imports.

### Detección de impacto
- **Dependencias externas** = SÍ si hay interfaces Retrofit nuevas o anotaciones `@GET/@POST/@PUT` con endpoints nuevos.
- **API Gateway** = SÍ si hay `@RestController` con `@RequestMapping` y el controller **no** tiene sufijo `iuse`.
- **Cambios BD** = SÍ si hay archivos `.yaml` con migraciones o scripts SQL en `src/main/resources/db/migration/`.

### Template

```markdown
# PR: <Título conciso del cambio principal>

---

## Información del PR

| Campo | Valor |
|-------|-------|
| Encargado | <Nombre> |
| Ticket | [GROW-XXXX](https://global66.atlassian.net/browse/GROW-XXXX) |
| Dependencias | [Sí / No] |
| API Gateway | [Sí / No] |
| Cambios BD | [Sí / No] |

---

## Resumen ejecutivo

<2-3 líneas de propósito y alcance técnico>

### Punto de entrada para review
Comenzar por: `<NombreClaseEstrategia>` — <razón: entry point principal / controller base / service core / etc.>

### Cambios implementados
- <Cambio 1: capa de presentación>
- <Cambio 2: lógica de negocio>
- <Cambio 3: capa de datos>
- <Cambio N: tests y validaciones>

---

## Modificaciones técnicas

### [Tipo] [Nueva / Modificada] `NombreClase`
Ruta: `ruta/completa/archivo.java`

Cambio: <descripción técnica precisa>

```java
// Solo las líneas clave del diff
```

---

## Índice de clases modificadas

### Clases nuevas
- `ClaseNueva1` — <propósito en ≤ 8 palabras>
- `ClaseNueva2` — <propósito en ≤ 8 palabras>

### Clases modificadas
- `ClaseExistente1` — <tipo de cambio + impacto en ≤ 10 palabras>
- `ClaseExistente2` — <tipo de cambio + impacto en ≤ 10 palabras>

---

## Cobertura de testing

Métodos de prueba afectados:
- `nombreMetodoTest1()`
- `nombreMetodoTest2()`

---

## Resumen de impacto

| Categoría | Archivos |
|-----------|----------|
| Clases nuevas | <lista o "Ninguna"> |
| Lógica modificada | <lista o "Ninguna"> |
| Solo formato | <lista o "Ninguna"> |

---

Impacto: este PR <mejora / implementa / optimiza> <funcionalidad específica> manteniendo estándares de código y cobertura ≥ 80%.
```

### Criterios de exclusión del README
- Archivos con solo cambios de indentación.
- Reordenamiento de imports sin lógica nueva.
- Comentarios de documentación sin cambios funcionales.

### Optimización si excede 4800 caracteres
1. Reducir bloques de código a las líneas estrictamente esenciales.
2. Convertir el "Índice de clases" a formato más conciso.
3. Priorizar info arquitectónica sobre detalles de implementación.

---

## 9. Cosas que NO hacer

- ❌ Wildcard imports (`import x.y.*`).
- ❌ Más de 5 dependencias inyectadas en una clase.
- ❌ Mergear código sin tests si la cobertura del cambio queda <80%.
- ❌ Anotaciones de Bean Validation en la clase impl del controller (causan `HV000151`).
- ❌ `@ResponseStatus` en excepciones de dominio (lo decide el handler).
- ❌ Construcción manual de JSON con `String.format`. Usar `ObjectMapper`.
- ❌ DTOs/Strings/Maps como request/response. Solo DTOs explícitos con `@Schema`.
- ❌ `double` o `float` para dinero o porcentajes. Siempre `BigDecimal`.
- ❌ Lógica de negocio en controllers — solo routing, validación y delegación al service.
