package cl.duoc.digitalfix.rabbitadmin.web.dto;

import java.util.List;

/**
 * Cuerpo de error uniforme.
 */
public record RespuestaError(
        int estado,
        String mensaje,
        List<String> detalles
) {}
