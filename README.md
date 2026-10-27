# ms-digitalfix-rabbitadmin

**Proyecto:** DigitalFix - Gestion de ordenes de trabajo para una red de 20 empresas de mantenimiento electrico
**Componente:** Microservicio administrador de RabbitMQ
**Asignatura:** DSY1107 Desarrollo Cloud Native I - Duoc UC

## Descripcion

Microservicio administrador de RabbitMQ: expone una API REST para **crear y
eliminar colas, exchanges y bindings** sobre el broker en tiempo de ejecucion.
Toda la operacion se concentra en un servicio dedicado (`RabbitAdminService`)
que usa el `RabbitAdmin` de Spring AMQP; los controladores no conocen los
detalles de la libreria.

## API

Todas las operaciones pasan por `service.RabbitAdminService` (colas, exchanges y
bindings) y `service.ColaMuertaService` (mensajes muertos). Los controladores no
conocen la libreria de RabbitMQ.

### Colas
| Metodo | Ruta | Descripcion |
|---|---|---|
| GET | `/queues` | Colas del sistema con mensajes, consumidores y su DLQ (`rabbitadmin.colas` en `application.yml`) |
| POST | `/queues` | Crea una cola; con `"conDlq": true` crea tambien `<nombre>.dlq` y las deja enlazadas |
| GET | `/queues/{nombre}` | Existencia, mensajes y consumidores |
| DELETE | `/queues/{nombre}` | Elimina la cola (404 si no existe) |
| GET | `/queues/{nombre}/messages?max=10` | Muestra mensajes **sin sacarlos** (en una DLQ: cola de origen, motivo y veces que murio) |
| DELETE | `/queues/{nombre}/messages` | Vacia la cola (la cola sigue existiendo) |
| POST | `/queues/{dlq}/reprocess?max=50&destino=` | Devuelve los mensajes muertos a la cola de la que salieron (o a `destino`) |

### Exchanges
| Metodo | Ruta | Descripcion |
|---|---|---|
| GET | `/exchanges` | Exchanges del sistema y si existen (`rabbitadmin.exchanges`) |
| POST | `/exchanges` | Crea un exchange (direct, topic, fanout, headers) |
| DELETE | `/exchanges/{nombre}` | Elimina un exchange |

### Bindings
| Metodo | Ruta | Descripcion |
|---|---|---|
| POST | `/bindings` | Enlaza una cola a un exchange por routing key |
| DELETE | `/bindings?cola=&exchange=&routingKey=` | Quita el binding (tambien acepta el mismo cuerpo del POST) |

### Gestion de colas muertas

Cuando un consumidor no puede procesar un mensaje, lo manda a la DLQ de su cola.
RabbitMQ le agrega la cabecera `x-death` (cola de origen, motivo y cuantas veces
murio). Con eso:

1. `GET /queues` muestra en `mensajesMuertos` cuantos hay en cada DLQ.
2. `GET /queues/ordenes.auditoria.dlq/messages` muestra el contenido y por que murio,
   sin sacarlos (se leen sin confirmar y vuelven a la DLQ).
3. Se corrige la causa (por ejemplo, se despliega el arreglo del consumidor).
4. `POST /queues/ordenes.auditoria.dlq/reprocess` devuelve cada mensaje a su cola de
   origen: primero lo publica en el destino y recien despues lo confirma en la DLQ,
   asi no se pierde ninguno. Se le quita el historial `x-death` y se le suma la
   cabecera `x-reprocesos`. Si un mensaje no indica de donde vino, queda en la DLQ.
5. `DELETE /queues/ordenes.auditoria.dlq/messages` descarta lo que no sirva.

### Ejemplos (en el despliegue el puerto es 8088)

```bash
# estado de todas las colas del sistema y sus DLQ
curl http://localhost:8088/queues

# crear una cola con su DLQ, un exchange direct y enlazarlos
curl -X POST http://localhost:8088/queues -H "Content-Type: application/json" \
  -d '{"nombre":"demo.queue","durable":true,"conDlq":true}'
curl -X POST http://localhost:8088/exchanges -H "Content-Type: application/json" \
  -d '{"nombre":"demo.exchange","tipo":"direct","durable":true}'
curl -X POST http://localhost:8088/bindings -H "Content-Type: application/json" \
  -d '{"cola":"demo.queue","exchange":"demo.exchange","routingKey":"demo.key"}'

# ver y reprocesar los mensajes muertos de la auditoria
curl "http://localhost:8088/queues/ordenes.auditoria.dlq/messages?max=5"
curl -X POST "http://localhost:8088/queues/ordenes.auditoria.dlq/reprocess"

# limpiar lo de prueba
curl -X DELETE "http://localhost:8088/bindings?cola=demo.queue&exchange=demo.exchange&routingKey=demo.key"
curl -X DELETE http://localhost:8088/queues/demo.queue
curl -X DELETE http://localhost:8088/queues/demo.queue.dlq
curl -X DELETE http://localhost:8088/exchanges/demo.exchange
```

## Validacion

Los cuerpos se validan con DTOs (`jakarta.validation`): el nombre es
obligatorio, de largo acotado y solo admite `[A-Za-z0-9._-]` (lo mismo la
routing key y el exchange de la DLQ); el tipo de exchange debe ser uno de los
cuatro validos. Los nombres que llegan en la ruta se validan igual en el
servicio, `max` debe estar entre 1 y 200, una DLQ no puede llamarse igual que
su cola y el destino de un reproceso debe existir. Un dato invalido responde
**400** con el detalle; un recurso que no existe, **404**; un problema con el
broker, **502**.

## Tecnologias

Java 21, Spring Boot 3.3.5, Spring AMQP (RabbitMQ), Bean Validation, Actuator.

## Variables de entorno

| Variable | Defecto | Uso |
|---|---|---|
| `RABBITMQ_HOST` | `localhost` | Host de RabbitMQ |
| `RABBITMQ_PORT` | `5672` | Puerto de RabbitMQ |
| `RABBITMQ_USER` | `guest` | Usuario |
| `RABBITMQ_PASSWORD` | `guest` | Clave |
| `PUERTO` | `8087` | Puerto HTTP del servicio |

## Integrantes

| Integrante | GitHub |
|---|---|
| Kevin Rojas | @khrojasdev |
| Christopher Perez | @ChrisPerezV |
| Diego Lopez | @DiegoLopez-f |
