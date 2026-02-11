# Ledger Service (Spring Boot)

Microservicio backend para registrar operaciones contables (ledger) y consultar saldos e historial.

Incluye persistencia con PostgreSQL, migraciones con Flyway, documentación con Swagger (OpenAPI) y una batería de tests (unit, web e integration con Testcontainers).

---

## Tech Stack

- Java 21  
- Spring Boot 3 (Web, Validation, Security)  
- Spring Data JPA (Hibernate)  
- PostgreSQL  
- Flyway (migrations)  
- Swagger / OpenAPI (springdoc)  
- JUnit 5 + Mockito  
- Testcontainers (PostgreSQL)  

---

## Features

### Ledger Entries
- Crear entry individual  
- Crear movimiento compuesto (múltiples entries)  
- Consultar entry por ID  
- Listar entries (paginado)  

### Operations
- Consultar operación por `operationId`  
- Consultar entries de una operación  
- Reversar una operación (genera entradas de reverso)  

### Account Balance
- Consultar saldo actual de una cuenta  
- Consultar historial de saldo por fecha  

---

## API Documentation (Swagger)

Con la aplicación levantada:

- Swagger UI:  
  `http://localhost:8081/swagger-ui/index.html`

- OpenAPI JSON:  
  `http://localhost:8081/v3/api-docs`

> Swagger refleja el contrato OpenAPI de la API: endpoints, parámetros, request/response y códigos HTTP.

---

## Run Locally

### 1) Levantar PostgreSQL con Docker

Asegúrate de tener Docker corriendo:

```bash
docker compose up -d
```

### 2) Ejecutar la aplicación

```bash
./gradlew bootRun
```

La aplicación quedará disponible en:

```
http://localhost:8081
```

---

## Testing

Ejecutar todos los tests:

```bash
./gradlew test
```

Incluye:

- Unit tests (JUnit 5 + Mockito)  
- Web tests (MockMvc)  
- Integration tests con Testcontainers (PostgreSQL real en contenedor)  
