package cl.duoc.digitalfix.rabbitadmin.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Datos para enlazar una cola a un exchange por una routing key.
 */
public record BindingRequest(

        @NotBlank(message = "la cola es obligatoria")
        String cola,

        @NotBlank(message = "el exchange es obligatorio")
        String exchange,

        // Para fanout la routing key se ignora; para direct/topic es la clave.
        String routingKey
) {
    public String routingKeySegura() {
        return routingKey == null ? "" : routingKey;
    }
}
