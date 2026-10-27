package cl.duoc.digitalfix.rabbitadmin.web;

import cl.duoc.digitalfix.rabbitadmin.service.ColaMuertaService;
import cl.duoc.digitalfix.rabbitadmin.service.RabbitAdminService;
import cl.duoc.digitalfix.rabbitadmin.web.dto.ColaRequest;
import cl.duoc.digitalfix.rabbitadmin.web.dto.EstadoCola;
import cl.duoc.digitalfix.rabbitadmin.web.dto.MensajeEnCola;
import cl.duoc.digitalfix.rabbitadmin.web.dto.RespuestaOperacion;
import cl.duoc.digitalfix.rabbitadmin.web.dto.ResultadoReproceso;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Administracion de colas y de sus mensajes muertos.
 * <pre>
 *   GET    /queues                         colas del sistema, mensajes, consumidores y su DLQ
 *   POST   /queues                         crea una cola (con conDlq: tambien su DLQ)
 *   GET    /queues/{nombre}                consulta una cola
 *   DELETE /queues/{nombre}                elimina una cola
 *   GET    /queues/{nombre}/messages       ve mensajes sin sacarlos (?max=10)
 *   DELETE /queues/{nombre}/messages       vacia la cola
 *   POST   /queues/{dlq}/reprocess         devuelve los mensajes muertos a su cola (?max=50&amp;destino=)
 * </pre>
 */
@RestController
@RequestMapping("/queues")
public class ColaController {

    private static final int MAXIMO_POR_LLAMADA = 200;

    private final RabbitAdminService admin;
    private final ColaMuertaService colasMuertas;

    public ColaController(RabbitAdminService admin, ColaMuertaService colasMuertas) {
        this.admin = admin;
        this.colasMuertas = colasMuertas;
    }

    @GetMapping
    public List<EstadoCola> listar() {
        return admin.listarColas();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RespuestaOperacion crear(@Valid @RequestBody ColaRequest req) {
        RabbitAdminService.ColaCreada creada = admin.crearCola(req);
        return new RespuestaOperacion("cola", creada.nombre(), "creado",
                creada.dlq() == null ? null : "con su DLQ " + creada.dlq());
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

    /** Ver que hay en la cola (tipicamente una DLQ) sin sacar los mensajes. */
    @GetMapping("/{nombre}/messages")
    public ResponseEntity<List<MensajeEnCola>> verMensajes(@PathVariable String nombre,
                                                          @RequestParam(defaultValue = "10") int max) {
        List<MensajeEnCola> mensajes = colasMuertas.verMensajes(nombre, validarMaximo(max));
        return mensajes == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(mensajes);
    }

    /** Vacia la cola: borra sus mensajes, la cola sigue existiendo. */
    @DeleteMapping("/{nombre}/messages")
    public ResponseEntity<RespuestaOperacion> vaciar(@PathVariable String nombre) {
        int borrados = admin.vaciarCola(nombre);
        if (borrados < 0) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(new RespuestaOperacion("cola", nombre, "vaciada", borrados + " mensajes borrados"));
    }

    /**
     * Devuelve los mensajes de una DLQ a la cola de la que salieron (o a
     * ?destino=). Se usa despues de corregir la causa del error.
     */
    @PostMapping("/{nombre}/reprocess")
    public ResponseEntity<ResultadoReproceso> reprocesar(@PathVariable String nombre,
                                                         @RequestParam(defaultValue = "50") int max,
                                                         @RequestParam(required = false) String destino) {
        ResultadoReproceso resultado = colasMuertas.reprocesar(nombre, destino, validarMaximo(max));
        return resultado == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(resultado);
    }

    private static int validarMaximo(int max) {
        if (max < 1 || max > MAXIMO_POR_LLAMADA) {
            throw new IllegalArgumentException("max debe estar entre 1 y " + MAXIMO_POR_LLAMADA);
        }
        return max;
    }
}
