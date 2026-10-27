package cl.duoc.digitalfix.rabbitadmin.web.dto;

/**
 * Un mensaje visto sin sacarlo de la cola. En una DLQ, colaOriginal, motivo y
 * veces dicen de donde vino y por que murio (cabecera x-death de RabbitMQ).
 */
public record MensajeEnCola(
        int posicion,
        String cuerpo,
        String colaOriginal,
        String exchangeOriginal,
        String routingKeyOriginal,
        String motivo,
        long veces,
        long reprocesos
) {}
