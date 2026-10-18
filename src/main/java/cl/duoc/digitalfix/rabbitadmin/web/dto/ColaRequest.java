package cl.duoc.digitalfix.rabbitadmin.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Datos para crear una cola. La validacion impide crear colas con nombre vacio
 * o con caracteres que no correspondan.
 */
public record ColaRequest(

        @NotBlank(message = "el nombre de la cola es obligatorio")
        @Size(max = 255, message = "el nombre no puede superar 255 caracteres")
        @Pattern(regexp = "[A-Za-z0-9._-]+",
                 message = "el nombre solo admite letras, numeros, punto, guion y guion bajo")
        String nombre,

        Boolean durable,

        // Opcional: si se indica, los mensajes muertos se enrutan a esta DLQ.
        String deadLetterExchange,
        String deadLetterRoutingKey
) {
    public boolean esDurable() {
        return durable == null || durable;
    }
}
