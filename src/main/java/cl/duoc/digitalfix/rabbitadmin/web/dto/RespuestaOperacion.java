package cl.duoc.digitalfix.rabbitadmin.web.dto;

/**
 * Respuesta uniforme para las operaciones de administracion. detalle explica
 * lo que se hizo ademas (por ejemplo, la DLQ creada junto con la cola).
 */
public record RespuestaOperacion(
        String recurso,
        String nombre,
        String resultado,
        String detalle
) {
    public RespuestaOperacion(String recurso, String nombre, String resultado) {
        this(recurso, nombre, resultado, null);
    }

    public static RespuestaOperacion creado(String recurso, String nombre) {
        return new RespuestaOperacion(recurso, nombre, "creado");
    }

    public static RespuestaOperacion eliminado(String recurso, String nombre) {
        return new RespuestaOperacion(recurso, nombre, "eliminado");
    }
}
