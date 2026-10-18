package cl.duoc.digitalfix.rabbitadmin.web;

import cl.duoc.digitalfix.rabbitadmin.service.RabbitAdminService;
import cl.duoc.digitalfix.rabbitadmin.web.dto.BindingRequest;
import cl.duoc.digitalfix.rabbitadmin.web.dto.RespuestaOperacion;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administracion de bindings entre colas y exchanges.
 *   POST   /bindings crea un binding
 *   DELETE /bindings elimina un binding
 */
@RestController
@RequestMapping("/bindings")
public class BindingController {

    private final RabbitAdminService admin;

    public BindingController(RabbitAdminService admin) {
        this.admin = admin;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RespuestaOperacion crear(@Valid @RequestBody BindingRequest req) {
        admin.crearBinding(req);
        return RespuestaOperacion.creado("binding", req.cola() + " <- " + req.exchange());
    }

    @DeleteMapping
    public RespuestaOperacion eliminar(@Valid @RequestBody BindingRequest req) {
        admin.eliminarBinding(req);
        return RespuestaOperacion.eliminado("binding", req.cola() + " <- " + req.exchange());
    }
}
