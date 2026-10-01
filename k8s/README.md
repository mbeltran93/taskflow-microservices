# TaskFlow en Kubernetes (GKE)

Manifiestos de Kubernetes para los 4 servicios de TaskFlow, pensados para
desplegarse en **GKE (Google Kubernetes Engine)**. Esta carpeta es
documentacion + manifiestos validados localmente: no se desplego nada a GCP
desde este entorno (no hay cuenta/proyecto de GCP configurado aca).

## Por que Kubernetes para una arquitectura de microservicios

Con `docker-compose.yml` los 4 servicios viven en un solo host, con una sola
replica cada uno y sin forma de reaccionar a la carga. Pasar a Kubernetes
(GKE) es lo que permite explotar la razon de ser de haber separado el
monolito en 4 servicios:

- **Escalado independiente por servicio**: `task-service` es el que mas
  trafico recibe (cada asignacion de tarea ademas dispara una llamada
  sincronica a `user-service`), asi que arranca con 3 replicas y un
  `HorizontalPodAutoscaler` que lo escala hasta 10 solo a el, sin tocar
  `user-service` o `project-service`. Un monolito no puede escalar "solo la
  parte de tareas"; aca cada `Deployment` + `HorizontalPodAutoscaler` es
  independiente (ver `*/hpa.yaml`).
- **Resiliencia por servicio**: si `project-service` se cae o queda sin
  memoria, Kubernetes lo reinicia (`livenessProbe`) y deja de enrutarle
  trafico mientras no este listo (`readinessProbe`), sin afectar a los otros
  3 servicios.
- **Despliegues independientes**: cada servicio tiene su propio
  `Deployment`, asi que se puede actualizar `user-service` (rolling update)
  sin tocar los otros tres — el equivalente de microservicios a poder pisar
  un solo modulo de un monolito sin re-desplegar todo.
- **Un unico punto de entrada publico**: solo `api-gateway` tiene un
  `Service` de tipo `LoadBalancer` (GKE le asigna una IP publica real via un
  Network Load Balancer). `user-service`, `project-service` y
  `task-service` quedan `ClusterIP`, solo alcanzables dentro del cluster —
  el mismo principio que en `docker-compose.yml`, donde solo el gateway
  publica su puerto al host.

## Estructura

```
k8s/
  namespace.yaml              # namespace "taskflow"
  config/
    configmap.yaml            # URLs internas (no sensibles) de cada servicio
    secret.yaml                # PLANTILLA: JWT_SECRET + credenciales de DB
  user-service/
    deployment.yaml  service.yaml  hpa.yaml
  project-service/
    deployment.yaml  service.yaml  hpa.yaml
  task-service/
    deployment.yaml  service.yaml  hpa.yaml
  api-gateway/
    deployment.yaml  service.yaml  hpa.yaml
```

Cada servicio tiene su propio `Deployment` + `Service` (en vez de un unico
archivo multi-documento) para que se pueda aplicar o revisar un servicio a
la vez, igual que en el repo cada servicio es su propio modulo Maven.

## Bases de datos: Cloud SQL, no Postgres/MySQL dentro del cluster

En `docker-compose.yml` cada servicio tiene su propia base en un contenedor
(`user-db`, `project-db`, `task-db`). En GKE la practica correcta para datos
relacionales con estado NO es correr Postgres/MySQL dentro del cluster, sino
usar **Cloud SQL** (una instancia Postgres y dos instancias/bases MySQL,
manteniendo "una base por servicio") y conectarse desde cada Pod via el
**Cloud SQL Auth Proxy** (sidecar en el mismo Pod, o Cloud SQL Auth Proxy con
Workload Identity). Por eso los `DB_URL` en los `deployment.yaml` apuntan a
`127.0.0.1:<puerto>` — es la direccion del proxy corriendo en el mismo Pod,
no una base dentro del cluster. El sidecar del proxy no esta incluido en
estos manifiestos (requeriria una instancia de Cloud SQL real y sus
credenciales de servicio), pero es la pieza que faltaria agregar antes de un
despliegue real a GKE.

## Secrets

`config/secret.yaml` es una **plantilla** con valores de relleno
(`CHANGEME_*`), solo para que los manifiestos sean validos y aplicables de
punta a punta en una validacion local. Nunca se commitea un Secret real. En
GKE, la practica es:

```bash
kubectl create secret generic taskflow-secrets \
  --namespace=taskflow \
  --from-literal=JWT_SECRET='<secreto real>' \
  --from-literal=USER_DB_USERNAME='<...>' \
  --from-literal=USER_DB_PASSWORD='<...>' \
  --from-literal=PROJECT_DB_USERNAME='<...>' \
  --from-literal=PROJECT_DB_PASSWORD='<...>' \
  --from-literal=TASK_DB_USERNAME='<...>' \
  --from-literal=TASK_DB_PASSWORD='<...>'
```

o, mejor todavia, Secret Manager de GCP + el CSI driver de Secret Manager
(o External Secrets Operator), para que el secreto viva en Secret Manager
con IAM y rotacion, y Kubernetes solo lo proyecte en runtime.

## Imagenes

Los `deployment.yaml` usan `taskflow/<servicio>:latest` como placeholder. En
GKE real se reemplaza por la imagen publicada en **Artifact Registry**, por
ejemplo:

```
us-central1-docker.pkg.dev/<PROJECT_ID>/taskflow/user-service:<tag>
```

El workflow de CI (`.github/workflows/ci.yml`) ya construye las 4 imagenes
Docker en cada push (sin pushearlas a ningun registry); el paso que faltaria
para un deploy real es loguearse en Artifact Registry y pushear con ese tag.

## Como se valido

Sin un cluster de GKE a mano en este entorno, los manifiestos se validaron
contra un cluster real pero descartable (`kind`, Kubernetes-in-Docker, solo
para este chequeo y borrado despues) con el comando pedido:

```bash
kubectl apply --dry-run=client -f k8s/ --recursive
```

Resultado: las 15 piezas (`Namespace`, `ConfigMap`, `Secret`, y
`Deployment`+`Service`+`HorizontalPodAutoscaler` x4) se reconocen y validan
sin errores.

## Aplicar (cuando haya un cluster real, GKE o local)

```bash
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/config/
kubectl apply -f k8s/user-service/ -f k8s/project-service/ -f k8s/task-service/ -f k8s/api-gateway/
```

o, de forma equivalente, `kubectl apply -f k8s/ --recursive`.
