package cl.duoc.digitalfix.rabbitadmin.web.dto;

/** Un exchange del sistema y si existe en el broker. */
public record EstadoExchange(String nombre, boolean existe) {}
