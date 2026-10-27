package cl.duoc.digitalfix.rabbitadmin.web.dto;

/**
 * Como esta una cola del sistema y su cola de mensajes muertos. mensajes es
 * -1 si la cola no existe en el broker.
 */
public record EstadoCola(
        String nombre,
        String servicio,
        boolean existe,
        int mensajes,
        int consumidores,
        String dlq,
        boolean dlqExiste,
        int mensajesMuertos
) {}
