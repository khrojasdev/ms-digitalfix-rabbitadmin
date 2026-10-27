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
        @Size(max = 255, message = "el exchange de la DLQ no puede superar 255 caracteres")
        @Pattern(regexp = "[A-Za-z0-9._-]*",
                 message = "el exchange de la DLQ solo admite letras, numeros, punto, guion y guion bajo")
        String deadLetterExchange,

        @Size(max = 255, message = "la routing key de la DLQ no puede superar 255 caracteres")
        @Pattern(regexp = "[A-Za-z0-9._-]*",
                 message = "la routing key de la DLQ solo admite letras, numeros, punto, guion y guion bajo")
        String deadLetterRoutingKey,

        // true: crea tambien la cola <nombre>.dlq y deja la cola enlazada a ella.
        Boolean conDlq
) {
    public ColaRequest(String nombre, Boolean durable, String deadLetterExchange, String deadLetterRoutingKey) {
        this(nombre, durable, deadLetterExchange, deadLetterRoutingKey, null);
    }

    public boolean esDurable() {
        return durable == null || durable;
    }
}
