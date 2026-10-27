package cl.duoc.digitalfix.rabbitadmin.web;

import cl.duoc.digitalfix.rabbitadmin.service.RabbitAdminService;
import cl.duoc.digitalfix.rabbitadmin.web.dto.ExchangeRequest;
import cl.duoc.digitalfix.rabbitadmin.web.dto.RespuestaOperacion;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import cl.duoc.digitalfix.rabbitadmin.web.dto.EstadoExchange;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Administracion de exchanges.
 *   POST   /exchanges          crea un exchange (direct, topic, fanout, headers)
 *   DELETE /exchanges/{nombre} elimina un exchange
 */
@RestController
@RequestMapping("/exchanges")
public class ExchangeController {

    private final RabbitAdminService admin;

    public ExchangeController(RabbitAdminService admin) {
        this.admin = admin;
    }

    /** Los exchanges del sistema y si existen en el broker. */
    @GetMapping
    public List<EstadoExchange> listar() {
        return admin.listarExchanges();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RespuestaOperacion crear(@Valid @RequestBody ExchangeRequest req) {
        String nombre = admin.crearExchange(req);
        return RespuestaOperacion.creado("exchange", nombre);
    }

    @DeleteMapping("/{nombre}")
    public RespuestaOperacion eliminar(@PathVariable String nombre) {
        admin.eliminarExchange(nombre);
        return RespuestaOperacion.eliminado("exchange", nombre);
    }
}
