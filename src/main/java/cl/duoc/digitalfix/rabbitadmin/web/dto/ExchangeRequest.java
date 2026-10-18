package cl.duoc.digitalfix.rabbitadmin.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Datos para crear un exchange. El tipo queda restringido a los validos.
 */
public record ExchangeRequest(

        @NotBlank(message = "el nombre del exchange es obligatorio")
        @Size(max = 255, message = "el nombre no puede superar 255 caracteres")
        @Pattern(regexp = "[A-Za-z0-9._-]+",
                 message = "el nombre solo admite letras, numeros, punto, guion y guion bajo")
        String nombre,

        @NotBlank(message = "el tipo es obligatorio")
        @Pattern(regexp = "direct|topic|fanout|headers",
                 message = "el tipo debe ser direct, topic, fanout o headers")
        String tipo,

        Boolean durable
) {
    public boolean esDurable() {
        return durable == null || durable;
    }
}
