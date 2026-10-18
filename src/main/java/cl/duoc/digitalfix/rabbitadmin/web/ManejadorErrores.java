package cl.duoc.digitalfix.rabbitadmin.web;

import cl.duoc.digitalfix.rabbitadmin.web.dto.RespuestaError;
import org.springframework.amqp.AmqpException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Traduce los errores a codigos HTTP claros:
 *  - validacion o parametro invalido -> 400
 *  - problema al hablar con el broker -> 502
 */
@RestControllerAdvice
public class ManejadorErrores {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<RespuestaError> validacion(MethodArgumentNotValidException ex) {
        List<String> detalles = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .toList();
        return ResponseEntity.badRequest()
                .body(new RespuestaError(400, "parametros invalidos", detalles));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<RespuestaError> argumento(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(new RespuestaError(400, ex.getMessage(), List.of()));
    }

    @ExceptionHandler(AmqpException.class)
    public ResponseEntity<RespuestaError> broker(AmqpException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new RespuestaError(502, "no se pudo comunicar con RabbitMQ: " + ex.getMessage(), List.of()));
    }
}
