package cl.duoc.digitalfix.rabbitadmin.service;

import cl.duoc.digitalfix.rabbitadmin.web.dto.BindingRequest;
import cl.duoc.digitalfix.rabbitadmin.web.dto.ColaRequest;
import cl.duoc.digitalfix.rabbitadmin.web.dto.ExchangeRequest;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.stereotype.Service;

/**
 * Encapsula todas las operaciones de administracion de RabbitMQ (crear y
 * eliminar colas, exchanges y bindings). Los controladores solo llaman a
 * metodos de alto nivel de esta clase y no conocen los detalles de la libreria.
 */
@Service
public class RabbitAdminService {

    private final RabbitAdmin rabbitAdmin;

    public RabbitAdminService(RabbitAdmin rabbitAdmin) {
        this.rabbitAdmin = rabbitAdmin;
    }

    // ---- Colas ----

    public String crearCola(ColaRequest req) {
        QueueBuilder builder = req.esDurable()
                ? QueueBuilder.durable(req.nombre())
                : QueueBuilder.nonDurable(req.nombre());
        if (req.deadLetterExchange() != null && !req.deadLetterExchange().isBlank()) {
            builder.withArgument("x-dead-letter-exchange", req.deadLetterExchange());
        }
        if (req.deadLetterRoutingKey() != null && !req.deadLetterRoutingKey().isBlank()) {
            builder.withArgument("x-dead-letter-routing-key", req.deadLetterRoutingKey());
        }
        Queue cola = builder.build();
        rabbitAdmin.declareQueue(cola);
        return cola.getName();
    }

    /** Devuelve true si la cola existia y se elimino. */
    public boolean eliminarCola(String nombre) {
        return rabbitAdmin.deleteQueue(nombre);
    }

    /** Null si la cola no existe; en caso contrario, sus propiedades. */
    public java.util.Properties propiedadesCola(String nombre) {
        return rabbitAdmin.getQueueProperties(nombre);
    }

    // ---- Exchanges ----

    public String crearExchange(ExchangeRequest req) {
        Exchange exchange = construirExchange(req);
        rabbitAdmin.declareExchange(exchange);
        return exchange.getName();
    }

    public void eliminarExchange(String nombre) {
        rabbitAdmin.deleteExchange(nombre);
    }

    private Exchange construirExchange(ExchangeRequest req) {
        ExchangeBuilder builder = switch (req.tipo()) {
            case "direct"  -> ExchangeBuilder.directExchange(req.nombre());
            case "topic"   -> ExchangeBuilder.topicExchange(req.nombre());
            case "fanout"  -> ExchangeBuilder.fanoutExchange(req.nombre());
            case "headers" -> ExchangeBuilder.headersExchange(req.nombre());
            default        -> throw new IllegalArgumentException("tipo de exchange no soportado: " + req.tipo());
        };
        if (req.esDurable()) {
            builder.durable(true);
        }
        return builder.build();
    }

    // ---- Bindings ----

    public void crearBinding(BindingRequest req) {
        Binding binding = new Binding(
                req.cola(), Binding.DestinationType.QUEUE,
                req.exchange(), req.routingKeySegura(), null);
        rabbitAdmin.declareBinding(binding);
    }

    public void eliminarBinding(BindingRequest req) {
        Binding binding = new Binding(
                req.cola(), Binding.DestinationType.QUEUE,
                req.exchange(), req.routingKeySegura(), null);
        rabbitAdmin.removeBinding(binding);
    }
}
