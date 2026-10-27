package cl.duoc.digitalfix.rabbitadmin.service;

import cl.duoc.digitalfix.rabbitadmin.config.PropiedadesAdministrador;
import cl.duoc.digitalfix.rabbitadmin.web.dto.BindingRequest;
import cl.duoc.digitalfix.rabbitadmin.web.dto.ColaRequest;
import cl.duoc.digitalfix.rabbitadmin.web.dto.EstadoCola;
import cl.duoc.digitalfix.rabbitadmin.web.dto.EstadoExchange;
import cl.duoc.digitalfix.rabbitadmin.web.dto.ExchangeRequest;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Encapsula todas las operaciones de administracion de RabbitMQ: crear,
 * consultar, vaciar y eliminar colas; crear, listar y eliminar exchanges;
 * crear y quitar bindings. Los controladores solo llaman a metodos de alto
 * nivel de esta clase y no conocen los detalles de la libreria.
 */
@Service
public class RabbitAdminService {

    /** Mismo criterio que los DTO: letras, numeros, punto, guion y guion bajo. */
    private static final String NOMBRE_VALIDO = "[A-Za-z0-9._-]{1,255}";

    private final RabbitAdmin rabbitAdmin;
    private final RabbitTemplate rabbitTemplate;
    private final PropiedadesAdministrador propiedades;

    public RabbitAdminService(RabbitAdmin rabbitAdmin, RabbitTemplate rabbitTemplate, PropiedadesAdministrador propiedades) {
        this.rabbitAdmin = rabbitAdmin;
        this.rabbitTemplate = rabbitTemplate;
        this.propiedades = propiedades;
    }

    /** Para las pruebas que solo ejercitan colas, exchanges y bindings. */
    public RabbitAdminService(RabbitAdmin rabbitAdmin) {
        this(rabbitAdmin, null, new PropiedadesAdministrador());
    }

    // ---- Colas ----

    /**
     * Crea la cola. Si se pide DLQ (conDlq, o una routing key de DLQ sin
     * exchange), crea tambien la cola de mensajes muertos y deja la cola
     * enlazada a ella por el exchange por defecto, que es como lo hacen los
     * microservicios del sistema.
     *
     * @return la cola creada y su DLQ (null si no se pidio)
     */
    public ColaCreada crearCola(ColaRequest req) {
        validarNombre(req.nombre());
        String dlx = req.deadLetterExchange();
        String dlrk = req.deadLetterRoutingKey();
        if (Boolean.TRUE.equals(req.conDlq()) && vacio(dlrk)) {
            dlrk = req.nombre() + ".dlq";
        }
        String dlqCreada = null;
        if (!vacio(dlrk) && vacio(dlx)) {
            // DLQ por el exchange por defecto: la routing key es el nombre de la DLQ.
            dlx = "";
            if (dlrk.equals(req.nombre())) {
                throw new IllegalArgumentException("la DLQ no puede llamarse igual que la cola");
            }
            rabbitAdmin.declareQueue(QueueBuilder.durable(dlrk).build());
            dlqCreada = dlrk;
        }

        QueueBuilder builder = req.esDurable()
                ? QueueBuilder.durable(req.nombre())
                : QueueBuilder.nonDurable(req.nombre());
        if (dlx != null && (!dlx.isEmpty() || !vacio(dlrk))) {
            builder.withArgument("x-dead-letter-exchange", dlx);
        }
        if (!vacio(dlrk)) {
            builder.withArgument("x-dead-letter-routing-key", dlrk);
        }
        Queue cola = builder.build();
        rabbitAdmin.declareQueue(cola);
        return new ColaCreada(cola.getName(), dlqCreada);
    }

    /** Resultado de crear una cola: su nombre y la DLQ creada junto con ella (o null). */
    public record ColaCreada(String nombre, String dlq) {}

    /** Devuelve true si la cola existia y se elimino. */
    public boolean eliminarCola(String nombre) {
        validarNombre(nombre);
        return rabbitAdmin.deleteQueue(nombre);
    }

    /** Null si la cola no existe; en caso contrario, sus propiedades. */
    public Properties propiedadesCola(String nombre) {
        validarNombre(nombre);
        return rabbitAdmin.getQueueProperties(nombre);
    }

    /** Las colas del sistema con sus mensajes, consumidores y su DLQ. */
    public List<EstadoCola> listarColas() {
        List<EstadoCola> estado = new ArrayList<>();
        for (PropiedadesAdministrador.ColaVigilada cola : propiedades.getColas()) {
            Properties principal = rabbitAdmin.getQueueProperties(cola.getNombre());
            Properties dlq = cola.getDlq() == null ? null : rabbitAdmin.getQueueProperties(cola.getDlq());
            estado.add(new EstadoCola(
                    cola.getNombre(),
                    cola.getServicio(),
                    principal != null,
                    contar(principal, RabbitAdmin.QUEUE_MESSAGE_COUNT),
                    contar(principal, RabbitAdmin.QUEUE_CONSUMER_COUNT),
                    cola.getDlq(),
                    dlq != null,
                    contar(dlq, RabbitAdmin.QUEUE_MESSAGE_COUNT)));
        }
        return estado;
    }

    /**
     * Vacia la cola (borra todos sus mensajes, la cola sigue existiendo).
     *
     * @return cuantos mensajes se borraron, o -1 si la cola no existe
     */
    public int vaciarCola(String nombre) {
        validarNombre(nombre);
        if (rabbitAdmin.getQueueProperties(nombre) == null) {
            return -1;
        }
        return rabbitAdmin.purgeQueue(nombre);
    }

    // ---- Exchanges ----

    public String crearExchange(ExchangeRequest req) {
        Exchange exchange = construirExchange(req);
        rabbitAdmin.declareExchange(exchange);
        return exchange.getName();
    }

    public void eliminarExchange(String nombre) {
        validarNombre(nombre);
        rabbitAdmin.deleteExchange(nombre);
    }

    /** Los exchanges del sistema y si existen (declaracion pasiva: no crea nada). */
    public List<EstadoExchange> listarExchanges() {
        List<EstadoExchange> estado = new ArrayList<>();
        for (String nombre : propiedades.getExchanges()) {
            estado.add(new EstadoExchange(nombre, existeExchange(nombre)));
        }
        return estado;
    }

    private boolean existeExchange(String nombre) {
        try {
            rabbitTemplate.execute(canal -> {
                canal.exchangeDeclarePassive(nombre);
                return Boolean.TRUE;
            });
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private Exchange construirExchange(ExchangeRequest req) {
        ExchangeBuilder builder = switch (req.tipo()) {
            case "direct"  -> ExchangeBuilder.directExchange(req.nombre());
            case "topic"   -> ExchangeBuilder.topicExchange(req.nombre());
            case "fanout"  -> ExchangeBuilder.fanoutExchange(req.nombre());
            case "headers" -> ExchangeBuilder.headersExchange(req.nombre());
            default        -> throw new IllegalArgumentException("tipo de exchange no soportado: " + req.tipo());
        };
        builder.durable(req.esDurable());
        return builder.build();
    }

    // ---- Bindings ----

    public void crearBinding(BindingRequest req) {
        rabbitAdmin.declareBinding(binding(req));
    }

    public void eliminarBinding(BindingRequest req) {
        rabbitAdmin.removeBinding(binding(req));
    }

    private static Binding binding(BindingRequest req) {
        validarNombre(req.cola());
        validarNombre(req.exchange());
        return new Binding(req.cola(), Binding.DestinationType.QUEUE, req.exchange(), req.routingKeySegura(), null);
    }

    // ---- Apoyo ----

    static void validarNombre(String nombre) {
        if (nombre == null || !nombre.matches(NOMBRE_VALIDO)) {
            throw new IllegalArgumentException("nombre invalido: '" + nombre
                    + "'. Solo letras, numeros, punto, guion y guion bajo (1 a 255).");
        }
    }

    private static boolean vacio(String texto) {
        return texto == null || texto.isBlank();
    }

    private static int contar(Properties propiedades, Object clave) {
        if (propiedades == null) {
            return -1;
        }
        Object valor = propiedades.get(clave);
        return valor instanceof Number n ? n.intValue() : 0;
    }
}
