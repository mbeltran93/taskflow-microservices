# TaskFlow - Microservicios

[![CI](https://github.com/mbeltran93/taskflow-microservices/actions/workflows/ci.yml/badge.svg)](https://github.com/mbeltran93/taskflow-microservices/actions/workflows/ci.yml)

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

## Trazabilidad distribuida (X-Trace-Id)

Con 4 servicios separados, el problema real al debuggear en produccion no es
leer el log de uno: es seguir **un pedido puntual a traves de los 4**. Por
eso cada servicio tiene un filtro de trazas (`tracing/TraceIdFilter.java` en
user/project/task-service, `tracing/TraceIdGlobalFilter.java` en
api-gateway):

- Si la request entrante ya trae el header `X-Trace-Id`, lo reusa. Si no,
  genera uno nuevo (UUID).
- En `user-service`, `project-service` y `task-service` (Servlet, un thread
  fijo por request) el traceId se pone en el **MDC de SLF4J**, asi que
  `logging.pattern.console` (en cada `application.yml`) lo imprime en
  **todas** las lineas de log de esa request automaticamente, sin tocar cada
  `log.info(...)` uno por uno.
- `task-service` lo **propaga** en la llamada saliente real: cuando
  `TaskService.assign()` llama a `UserClient.getUserById()` para validar el
  assignee contra user-service, `UserClient` lee el traceId del MDC (misma
  request, mismo thread) y lo manda como header `X-Trace-Id` en esa llamada
  HTTP saliente.
- `api-gateway` (Spring Cloud Gateway, WebFlux) hace lo mismo al reenviar
  cualquier request a los servicios downstream: agrega el header
  `X-Trace-Id` a la request mutada antes de rutearla. Ahi el MDC no es
  confiable (el pipeline reactivo puede cambiar de thread entre
  operadores), asi que el filtro loguea el traceId explicito en el mensaje
  en vez de depender solo de `%X{traceId}` (el codigo lo documenta en
  `TraceIdGlobalFilter`).
- Los 4 servicios devuelven `X-Trace-Id` en la respuesta, para poder pedir
  "los logs de este pedido" por su id.

**Prueba real de punta a punta** (gateway -> task-service -> user-service,
disparando el caso real de asignar una tarea), con `X-Trace-Id:
demo-trace-1790878328` puesto a mano en el curl:

```
# log de task-service
traceId=demo-trace-1790878328 ... TraceIdFilter - Request recibido: PATCH /api/tasks/1/assign
traceId=demo-trace-1790878328 ... TaskService    - Asignando tarea taskId=1 a assigneeId=2
traceId=demo-trace-1790878328 ... UserClient     - Llamando a user-service para validar assigneeId=2 (propagando X-Trace-Id=demo-trace-1790878328)
traceId=demo-trace-1790878328 ... TraceIdFilter - Request completado: PATCH /api/tasks/1/assign -> status=200

# log de user-service, MISMO traceId, para la llamada que dispara task-service
traceId=demo-trace-1790878328 ... TraceIdFilter    - Request recibido: GET /api/users/2
traceId=demo-trace-1790878328 ... UserController   - Validando existencia de usuario id=2 (pedido por otro servicio)
traceId=demo-trace-1790878328 ... TraceIdFilter    - Request completado: GET /api/users/2 -> status=200
```

El mismo `traceId` aparece en ambos servicios para la misma operacion de
negocio, confirmando que viajo de punta a punta (gateway -> task-service ->
user-service) sin que nadie lo reenvie "a mano" en el curl del cliente mas
alla del primer header.

## Como correr todo

Requisitos: Docker Desktop (con Compose v2) y, para desarrollo local fuera
de Docker, Java 21 + Maven.

```bash
git clone https://github.com/mbeltran93/taskflow-microservices.git
cd taskflow-microservices
docker compose up --build
```

Esto levanta 7 containers: `user-db` (Postgres), `project-db` y `task-db`
(MySQL), los 3 microservicios y el `api-gateway`. Los healthchecks de las
bases de datos hacen que cada servicio espere a que su base este lista antes
de arrancar; ademas, los 4 servicios de aplicacion tienen `restart:
on-failure` para recuperarse solos si arrancan en la breve ventana en que
MySQL se reinicia tras inicializar su datadir (ver "Limitaciones conocidas").

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

## Coleccion de Postman

`postman_collection.json` (raiz del repo) cubre el mismo flujo end-to-end
que el bloque de curl de arriba, pero pensado para abrir en Postman y
ejecutar paso a paso o con el botón "Run collection":

1. **Importar**: Postman -> *Import* -> seleccionar `postman_collection.json`.
2. La coleccion trae sus propias variables (`base_url=http://localhost:8080`,
   `email`, `password`, `token`, `userId`, `projectId`, `taskId`) - no hace
   falta crear un Environment aparte.
3. Con el stack arriba (`docker compose up --build`), correr las carpetas en
   orden:
   - **01 - Auth**: registra un usuario y hace login. Un *test script* en la
     request de Login guarda el JWT devuelto en la variable de coleccion
     `token` automaticamente (`pm.collectionVariables.set('token', ...)`);
     las requests siguientes lo usan como `Authorization: Bearer {{token}}`
     sin que haya que copiarlo a mano.
   - **02 - Projects**: crea, consulta, lista y actualiza un proyecto (CRUD).
   - **03 - Tasks**: crea una tarea, la consulta, la asigna a un usuario que
     existe (200) y a uno que no existe (400, caso que valida contra
     user-service), y cambia su estado.
   - **04 - Cleanup**: borra el proyecto creado.
4. Todas las requests pasan por el **api-gateway** (`localhost:8080`), nunca
   por el puerto directo de cada microservicio.
5. Se puede correr headless con [Newman](https://github.com/postmanlabs/newman):
   `npx newman run postman_collection.json` (23 assertions, todas en verde
   contra el stack real de Docker al momento de escribir esto).

## Tests

Cada servicio tiene tests unitarios (JUnit 5 + Mockito, mockeando
repositorios y el cliente HTTP) y tests de integracion (`@SpringBootTest` +
`MockMvc`, con una base H2 en memoria) que ejercitan el flujo real
controller -> service -> repository con el filtro JWT activo. Ademas,
`user-service` tiene un test de integracion con **Testcontainers**
(`UserPostgresTestcontainersTest`) que levanta un Postgres 16 real en un
contenedor Docker (via `@ServiceConnection`) en vez de H2, para ejercitar el
mismo flujo contra el driver y el dialecto de Postgres de verdad. Requiere
Docker corriendo.

```bash
cd user-service    && mvn test   # 9 tests (incluye el de Testcontainers)
cd project-service  && mvn test   # 7 tests
cd task-service     && mvn test   # 9 tests
cd api-gateway      && mvn test   # 2 tests (arranque + registro de rutas)
```

En `task-service`, el test de integracion reemplaza `UserClient` por un
mock (`@MockBean`) para no depender de que user-service este corriendo; el
comportamiento real del cliente HTTP (incluyendo el caso "usuario no
existe") esta cubierto por el test unitario de `TaskService` y, end-to-end,
por el caso 400 de la coleccion de Postman.

## Kubernetes / GCP (GKE)

La carpeta [`k8s/`](k8s/README.md) tiene manifiestos de Kubernetes (un
`Deployment` + `Service` + `HorizontalPodAutoscaler` por servicio, mas
`Namespace`/`ConfigMap`/`Secret`) pensados para **GKE**, con el detalle de
por que una arquitectura de microservicios se beneficia especificamente de
Kubernetes (escalado independiente por servicio, resiliencia por servicio,
despliegues independientes) en [`k8s/README.md`](k8s/README.md).

No se desplego nada a GCP real desde este entorno (no hay cuenta/proyecto
configurado aca): los manifiestos se validaron localmente con

```bash
kubectl apply --dry-run=client -f k8s/ --recursive
```

contra un cluster `kind` descartable creado solo para esa validacion (sin
cluster no hay forma de que `kubectl` resuelva los `apiVersion`/`kind` de
cada recurso). Las 15 piezas se reconocen y validan sin errores.

## CI/CD

[`.github/workflows/ci.yml`](.github/workflows/ci.yml) corre en cada push
(y en cada PR):

1. **Tests**: `mvn test` de los 4 modulos Maven (`user-service`,
   `project-service`, `task-service`, `api-gateway`) en paralelo, uno por
   job de la matrix. Sube los reportes de Surefire como artifact aunque
   algun test falle.
2. **Build de imagenes Docker**: build de las 4 imagenes (`docker/
   build-push-action`, `push: false`) para validar que cada `Dockerfile`
   compila de punta a punta. No se pushea a ningun registry (no hay
   credenciales de Artifact Registry/Docker Hub configuradas en este repo).
3. **CodeQL**: analisis estatico de seguridad para Java sobre los 4 modulos
   (`github/codeql-action`, `build-mode: manual` ya que no hay un `pom.xml`
   raiz que los agregue), resultados en la pestaña *Security* del repo.

## Versiones

Spring Boot **3.5.16** (release train 3.5.x, la ultima linea 3.x con soporte
activo) y, en `api-gateway`, Spring Cloud **2025.0.3** (el release train que
acompaña a Boot 3.5.x). Se actualizo desde 3.3.2 / Spring Cloud 2023.0.2
(mediados de 2024) durante esta revision. De paso, `spring-cloud-starter-gateway`
se reemplazo por `spring-cloud-starter-gateway-server-webflux` (el artifact
que reemplaza al anterior, que esta deprecado desde Spring Cloud 2025.0.x) y
la config de rutas se movio a la clave nueva
`spring.cloud.gateway.server.webflux.routes` en `application.yml`.

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
- **`docker compose up` verificado de punta a punta**: con espacio libre en
  disco y Docker Desktop andando, se corrio `docker compose up --build`
  completo y el flujo real de curl/Postman contra los 4 servicios. Apareció
  (y se arreglo) un bug real de primera corrida: el healthcheck de MySQL
  (`mysqladmin ping`) puede reportar "healthy" durante el breve reinicio
  interno que MySQL hace al inicializar su datadir por primera vez: si
  `project-service`/`task-service` se conectan justo en esa ventana, Spring
  Boot aborta el arranque con "Connection refused" y, sin restart policy, el
  contenedor queda muerto para siempre aunque la base este lista un segundo
  despues. Se agrego `restart: on-failure:5` a los 4 microservicios en
  `docker-compose.yml` (ver comentario ahi) para que se recuperen solos de
  esa condicion de carrera.

## Estructura del repo

```
taskflow-microservices/
├── .github/workflows/ci.yml   (tests + build de imagenes + CodeQL)
├── docker-compose.yml
├── k8s/                        (manifiestos de Kubernetes/GKE, ver k8s/README.md)
├── user-service/       (Spring Boot, Postgres)
├── project-service/    (Spring Boot, MySQL)
├── task-service/       (Spring Boot, MySQL, cliente REST a user-service)
└── api-gateway/        (Spring Cloud Gateway)
```
