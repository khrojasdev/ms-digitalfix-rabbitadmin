package cl.duoc.digitalfix.rabbitadmin.web.dto;

/**
 * Respuesta uniforme para las operaciones de administracion.
 */
public record RespuestaOperacion(
        String recurso,
        String nombre,
        String resultado
) {
    public static RespuestaOperacion creado(String recurso, String nombre) {
        return new RespuestaOperacion(recurso, nombre, "creado");
    }

    public static RespuestaOperacion eliminado(String recurso, String nombre) {
        return new RespuestaOperacion(recurso, nombre, "eliminado");
    }
}
