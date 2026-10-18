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

### Colas
| Metodo | Ruta | Descripcion |
|---|---|---|
| POST | `/queues` | Crea una cola (opcional: DLQ) |
| DELETE | `/queues/{nombre}` | Elimina una cola (404 si no existe) |
| GET | `/queues/{nombre}` | Consulta existencia, mensajes y consumidores |

### Exchanges
| Metodo | Ruta | Descripcion |
|---|---|---|
| POST | `/exchanges` | Crea un exchange (direct, topic, fanout, headers) |
| DELETE | `/exchanges/{nombre}` | Elimina un exchange |

### Bindings
| Metodo | Ruta | Descripcion |
|---|---|---|
| POST | `/bindings` | Enlaza una cola a un exchange por routing key |
| DELETE | `/bindings` | Quita el binding |

### Ejemplos

```bash
# crear una cola con su DLQ
curl -X POST http://localhost:8087/queues -H "Content-Type: application/json" \
  -d '{"nombre":"demo.queue","durable":true,"deadLetterRoutingKey":"demo.dlq"}'

# crear un exchange direct
curl -X POST http://localhost:8087/exchanges -H "Content-Type: application/json" \
  -d '{"nombre":"demo.exchange","tipo":"direct","durable":true}'

# enlazarlos
curl -X POST http://localhost:8087/bindings -H "Content-Type: application/json" \
  -d '{"cola":"demo.queue","exchange":"demo.exchange","routingKey":"demo.key"}'

# eliminar la cola
curl -X DELETE http://localhost:8087/queues/demo.queue
```

## Validacion

Los cuerpos se validan con DTOs (`jakarta.validation`): el nombre es
obligatorio, de largo acotado y solo admite `[A-Za-z0-9._-]`; el tipo de
exchange debe ser uno de los cuatro validos. Un cuerpo invalido responde **400**
con el detalle del campo; un problema con el broker responde **502**.

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
