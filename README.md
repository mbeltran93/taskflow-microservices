# TaskFlow - Microservicios

Proyecto de portafolio: una version reducida de una app de gestion de
tareas/proyectos (tipo Trello/Jira), construida como un sistema de
**microservicios independientes** con Spring Boot, cada uno con su propia
base de datos, comunicandose entre si por REST y orquestados con Docker
Compose.

Este repo es el par "microservicios" de un repo hermano que modela el mismo
dominio como monolito Spring Boot. Aca el foco no es la funcionalidad (que es
deliberadamente simple) sino la arquitectura: servicios desplegables por
separado, persistencia poliglota, comunicacion sincronica entre servicios y
un API Gateway como punto de entrada unico.

## Arquitectura

```
                                   ┌─────────────────────┐
                                   │       Cliente        │
                                   │  (curl / Postman /    │
                                   │   frontend, etc.)     │
                                   └──────────┬───────────┘
                                              │  HTTP
                                              ▼
                                   ┌─────────────────────┐
                                   │     api-gateway       │
                                   │  Spring Cloud Gateway  │
                                   │      puerto 8080       │
                                   └──┬────────┬────────┬──┘
                    /api/users/**     │        │        │     /api/tasks/**
                    ┌─────────────────┘        │        └─────────────────┐
                    │                /api/projects/**                     │
                    ▼                          ▼                          ▼
        ┌─────────────────────┐   ┌─────────────────────┐   ┌─────────────────────┐
        │     user-service      │   │   project-service     │   │     task-service       │
        │      puerto 8081       │   │      puerto 8082       │   │      puerto 8083       │
        │                         │   │                         │   │                         │
        │ - registro / login      │   │ - CRUD de proyectos      │   │ - CRUD de tareas         │
        │ - emite el JWT           │   │ - valida dueno via JWT    │   │ - asigna tareas           │
        │ - GET /api/users/{id}    │   │                         │   │ - valida status           │
        └───────────┬─────────────┘   └─────────────────────┘   └───────────┬─────────────┘
                    │                                                        │
                    │        GET /api/users/{id}  (valida que el             │
                    │◄───────────────assigneeId exista)──────────────────────┘
                    │        Authorization: Bearer <jwt reenviado>
                    │
        ┌───────────▼─────────────┐   ┌─────────────────────┐   ┌─────────────────────┐
        │       user-db            │   │      project-db        │   │       task-db           │
        │   PostgreSQL 16           │   │       MySQL 8            │   │       MySQL 8            │
        └───────────────────────────┘   └─────────────────────┘   └─────────────────────┘
```

Cada base de datos es exclusiva de su servicio: nadie mas la toca ni conoce
sus tablas. Eso es lo que se llama "persistencia poliglota" - Postgres para
usuarios, MySQL para proyectos y tareas (en instancias/containers separados,
no una base compartida) - y es intencional para mostrar que cada
microservicio es dueno total de su propio modelo de datos.

## Que hace cada servicio

### user-service (puerto 8081, PostgreSQL)
- `POST /api/users/register` - crea un usuario (password hasheado con BCrypt).
- `POST /api/users/login` - valida credenciales y devuelve un JWT firmado
  (HS256) con el mismo secreto (`JWT_SECRET`) que comparten los demas
  servicios.
- `GET /api/users/{id}` - devuelve un usuario. Es el endpoint que consultan
  los demas servicios (en este caso, task-service) para validar que un id
  de usuario existe de verdad.
- Es el unico servicio que **emite** tokens. Los demas solo los **validan**.

### project-service (puerto 8082, MySQL)
- `POST /api/projects` - crea un proyecto; el `ownerId` sale del JWT, no del
  body (no confia en que el cliente diga quien es).
- `GET /api/projects/{id}`, `GET /api/projects?ownerId=...` - consulta.
- `PUT /api/projects/{id}`, `DELETE /api/projects/{id}` - solo el dueno del
  proyecto puede modificarlo o borrarlo (403 si no lo es).

### task-service (puerto 8083, MySQL)
- `POST /api/tasks` - crea una tarea en estado `TODO`.
- `GET /api/tasks/{id}`, `GET /api/tasks?projectId=...` - consulta.
- `PATCH /api/tasks/{id}/status` - cambia el estado (`TODO`, `IN_PROGRESS`,
  `DONE`).
- `PATCH /api/tasks/{id}/assign` - asigna la tarea a un `assigneeId`. **Antes
  de guardar el cambio, task-service llama por REST a
  `GET user-service:8081/api/users/{assigneeId}`** (usando `RestClient`),
  reenviando el mismo header `Authorization` que trajo el request original.
  Si user-service devuelve 404, la asignacion falla con 400 y nada se
  persiste. Si user-service esta caido, falla con 503. Esta es la
  comunicacion sincronica entre microservicios que pide el enunciado.

### api-gateway (puerto 8080, Spring Cloud Gateway)
- Unico punto de entrada publico del sistema.
- Enruta por prefijo de path:
  - `/api/users/**` -> `user-service:8081`
  - `/api/projects/**` -> `project-service:8082`
  - `/api/tasks/**` -> `task-service:8083`
- No valida JWT por su cuenta: reenvia el request tal cual (incluido el
  header `Authorization`) y cada servicio downstream valida el token con el
  mismo secreto compartido. Esto evita duplicar logica de auth en el gateway
  y mantiene a cada servicio responsable de su propia seguridad.

## Autenticacion (JWT)

- `user-service` es el unico que conoce las contrasenas y el unico que firma
  tokens (`JwtUtil.generateToken`).
- `project-service` y `task-service` tienen su propia copia (deliberada, no
  una libreria compartida - cada microservicio es independiente) de un
  `JwtUtil` que solo *verifica* la firma con el mismo `JWT_SECRET`, sin
  llamar a user-service por cada request. Si la firma es valida, confian en
  el `sub` (userId) del token.
- Un `JwtAuthFilter` (`OncePerRequestFilter`) protege todas las rutas
  `/api/**` de project-service y task-service; en user-service protege todo
  menos `/register` y `/login`.
- El secreto se pasa por la variable de entorno `JWT_SECRET`, identica en
  los tres servicios (ver `docker-compose.yml`).

## Como correr todo

Requisitos: Docker Desktop (con Compose v2) y, para desarrollo local fuera
de Docker, Java 21 + Maven.

```bash
git clone https://github.com/mbeltran93/taskflow-microservices.git
cd taskflow-microservices
docker compose up --build
```

Esto levanta 8 containers: `user-db` (Postgres), `project-db` y `task-db`
(MySQL), los 3 microservicios y el `api-gateway`. Los healthchecks de las
bases de datos hacen que cada servicio espere a que su base este lista antes
de arrancar.

Puertos expuestos en el host:

| Servicio         | Puerto  |
|------------------|---------|
| api-gateway      | 8080    |
| user-service     | 8081    |
| project-service  | 8082    |
| task-service     | 8083    |
| user-db (Postgres)| 5432   |
| project-db (MySQL)| 3307   |
| task-db (MySQL)   | 3308   |

Para bajar todo: `docker compose down` (agregar `-v` para borrar tambien los
volumenes de datos).

## Flujo de prueba end-to-end (via el gateway, puerto 8080)

```bash
# 1. Registrar un usuario
curl -s -X POST http://localhost:8080/api/users/register \
  -H "Content-Type: application/json" \
  -d '{"name":"Ada Lovelace","email":"ada@taskflow.dev","password":"secret123"}'

# 2. Login -> obtener el JWT
TOKEN=$(curl -s -X POST http://localhost:8080/api/users/login \
  -H "Content-Type: application/json" \
  -d '{"email":"ada@taskflow.dev","password":"secret123"}' | jq -r .token)

# 3. Crear un proyecto (el ownerId sale del JWT)
PROJECT_ID=$(curl -s -X POST http://localhost:8080/api/projects \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"TaskFlow","description":"Proyecto de portafolio"}' | jq -r .id)

# 4. Crear una tarea en ese proyecto
TASK_ID=$(curl -s -X POST http://localhost:8080/api/tasks \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"title\":\"Disenar el gateway\",\"projectId\":$PROJECT_ID}" | jq -r .id)

# 5. Asignarla a Ada (id 1) -> task-service valida el id contra user-service
curl -s -X PATCH http://localhost:8080/api/tasks/$TASK_ID/assign \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"assigneeId":1}'

# 6. Marcarla como en progreso
curl -s -X PATCH http://localhost:8080/api/tasks/$TASK_ID/status \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"status":"IN_PROGRESS"}'
```

Si en el paso 5 se manda un `assigneeId` que no existe (ej. `999999`),
task-service responde `400 Bad Request` sin asignar nada, porque la llamada
a user-service devuelve 404.

## Tests

Cada servicio tiene tests unitarios (JUnit 5 + Mockito, mockeando
repositorios y el cliente HTTP) y tests de integracion (`@SpringBootTest` +
`MockMvc`, con una base H2 en memoria) que ejercitan el flujo real
controller -> service -> repository con el filtro JWT activo.

```bash
cd user-service    && mvn test   # 8 tests
cd project-service  && mvn test   # 7 tests
cd task-service     && mvn test   # 9 tests
cd api-gateway      && mvn test   # 2 tests (arranque + registro de rutas)
```

En `task-service`, el test de integracion reemplaza `UserClient` por un
mock (`@MockBean`) para no depender de que user-service este corriendo; el
comportamiento real del cliente HTTP (incluyendo el caso "usuario no
existe") esta cubierto por el test unitario de `TaskService`.

## Limitaciones conocidas

- **Sin descubrimiento de servicios**: las URLs entre servicios son fijas
  por variable de entorno (`USER_SERVICE_URL`, etc.), no hay Eureka/Consul.
  Para 3 servicios es una simplificacion razonable; en un sistema mas grande
  se usaria service discovery.
- **Sin circuit breaker**: si user-service esta caido, task-service devuelve
  503 en vez de degradar con una respuesta cacheada o un breaker
  (Resilience4j). Quedo fuera de alcance para mantener el foco del ejercicio.
- **El gateway no valida JWT**: delega esa responsabilidad en cada servicio.
  Es una decision de diseno valida, pero una alternativa comun es validar el
  token una sola vez en el gateway con un `GlobalFilter`.
- **No hay outbox/eventos asincronicos**: toda la comunicacion entre
  servicios es sincronica (REST). No hay un broker de mensajes (Kafka/
  RabbitMQ), que seria el paso natural siguiente para desacoplar mas los
  servicios.
- **JWT sin refresh token**: el login devuelve un unico token de corta
  duracion (1 hora); no hay flujo de renovacion.
- **`ddl-auto: update`**: para un proyecto de portafolio se uso
  actualizacion automatica del esquema en vez de migraciones versionadas
  (Flyway/Liquibase).
- **Verificacion de `docker compose up`**: el codigo compila, todos los
  tests (26 en total) pasan y `docker compose config` valida el archivo sin
  errores, pero no se pudo ejecutar `docker compose up` de punta a punta en
  esta maquina porque el disco `C:` del entorno de build se quedo sin
  espacio libre (Docker Desktop no llega a arrancar). Para probarlo:
  liberar espacio en `C:` (o mover el disco de datos de Docker Desktop a
  otra unidad desde *Settings > Resources > Advanced*) y correr
  `docker compose up --build` seguido del flujo de curl de arriba.

## Estructura del repo

```
taskflow-microservices/
├── docker-compose.yml
├── user-service/       (Spring Boot, Postgres)
├── project-service/    (Spring Boot, MySQL)
├── task-service/       (Spring Boot, MySQL, cliente REST a user-service)
└── api-gateway/        (Spring Cloud Gateway)
```
