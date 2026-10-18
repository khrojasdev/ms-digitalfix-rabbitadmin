package cl.duoc.digitalfix.rabbitadmin.web;

import cl.duoc.digitalfix.rabbitadmin.service.RabbitAdminService;
import cl.duoc.digitalfix.rabbitadmin.web.dto.ColaRequest;
import cl.duoc.digitalfix.rabbitadmin.web.dto.RespuestaOperacion;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Properties;

/**
 * Administracion de colas.
 *   POST   /queues          crea una cola
 *   DELETE /queues/{nombre} elimina una cola
 *   GET    /queues/{nombre} consulta una cola (existencia y mensajes)
 */
@RestController
@RequestMapping("/queues")
public class ColaController {

    private final RabbitAdminService admin;

    public ColaController(RabbitAdminService admin) {
        this.admin = admin;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RespuestaOperacion crear(@Valid @RequestBody ColaRequest req) {
        String nombre = admin.crearCola(req);
        return RespuestaOperacion.creado("cola", nombre);
    }

    @DeleteMapping("/{nombre}")
    public ResponseEntity<RespuestaOperacion> eliminar(@PathVariable String nombre) {
        boolean eliminada = admin.eliminarCola(nombre);
        if (!eliminada) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(RespuestaOperacion.eliminado("cola", nombre));
    }

    @GetMapping("/{nombre}")
    public ResponseEntity<Map<String, Object>> consultar(@PathVariable String nombre) {
        Properties props = admin.propiedadesCola(nombre);
        if (props == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of(
                "nombre", nombre,
                "existe", true,
                "mensajes", String.valueOf(props.getOrDefault("QUEUE_MESSAGE_COUNT", "0")),
                "consumidores", String.valueOf(props.getOrDefault("QUEUE_CONSUMER_COUNT", "0"))
        ));
    }
}
