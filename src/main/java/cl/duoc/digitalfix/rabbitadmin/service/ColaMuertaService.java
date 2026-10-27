package cl.duoc.digitalfix.rabbitadmin.service;

import cl.duoc.digitalfix.rabbitadmin.web.dto.MensajeEnCola;
import cl.duoc.digitalfix.rabbitadmin.web.dto.ResultadoReproceso;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.GetResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gestion de colas de mensajes muertos (DLQ): ver que hay adentro y devolver
 * los mensajes a su cola original una vez corregida la causa.
 *
 * RabbitMQ anota en cada mensaje muerto la cabecera x-death con la cola de la
 * que salio, el motivo (rejected, expired...) y cuantas veces murio. Con eso se
 * sabe a donde devolverlo sin que nadie lo tenga que indicar.
 */
@Service
public class ColaMuertaService {

    private static final Logger log = LoggerFactory.getLogger(ColaMuertaService.class);
    private static final int CUERPO_MAXIMO = 2000;
    static final String CABECERA_REPROCESOS = "x-reprocesos";

    private final RabbitTemplate rabbitTemplate;
    private final RabbitAdmin rabbitAdmin;

    public ColaMuertaService(RabbitTemplate rabbitTemplate, RabbitAdmin rabbitAdmin) {
        this.rabbitTemplate = rabbitTemplate;
        this.rabbitAdmin = rabbitAdmin;
    }

    /**
     * Muestra hasta {@code maximo} mensajes de la cola sin sacarlos: se leen
     * sin confirmar y al final se devuelven todos a la cola.
     *
     * @return null si la cola no existe
     */
    public List<MensajeEnCola> verMensajes(String cola, int maximo) {
        RabbitAdminService.validarNombre(cola);
        if (rabbitAdmin.getQueueProperties(cola) == null) {
            return null;
        }
        return rabbitTemplate.execute(canal -> {
            List<MensajeEnCola> vistos = new ArrayList<>();
            long ultimo = -1;
            try {
                for (int i = 0; i < maximo; i++) {
                    GetResponse respuesta = canal.basicGet(cola, false);
                    if (respuesta == null) {
                        break;
                    }
                    ultimo = respuesta.getEnvelope().getDeliveryTag();
                    vistos.add(describir(i + 1, respuesta));
                }
            } finally {
                if (ultimo >= 0) {
                    // multiple=true, requeue=true: todo lo leido vuelve a la cola.
                    canal.basicNack(ultimo, true, true);
                }
            }
            return vistos;
        });
    }

    /**
     * Devuelve hasta {@code maximo} mensajes de la DLQ a la cola de la que
     * salieron (o a {@code destinoForzado}, si se indica). Cada mensaje se
     * publica en el destino y recien entonces se confirma en la DLQ, asi no se
     * pierde ninguno si algo falla a mitad de camino.
     *
     * @return null si la DLQ no existe
     */
    public ResultadoReproceso reprocesar(String dlq, String destinoForzado, int maximo) {
        RabbitAdminService.validarNombre(dlq);
        if (destinoForzado != null && !destinoForzado.isBlank()) {
            RabbitAdminService.validarNombre(destinoForzado);
            if (rabbitAdmin.getQueueProperties(destinoForzado) == null) {
                throw new IllegalArgumentException("la cola destino '" + destinoForzado + "' no existe");
            }
        }
        if (rabbitAdmin.getQueueProperties(dlq) == null) {
            return null;
        }
        return rabbitTemplate.execute(canal -> moverMensajes(canal, dlq, destinoForzado, maximo));
    }

    ResultadoReproceso moverMensajes(Channel canal, String dlq, String destinoForzado, int maximo) throws Exception {
        Map<String, Integer> porDestino = new LinkedHashMap<>();
        int reprocesados = 0;
        int sinDestino = 0;
        for (int i = 0; i < maximo; i++) {
            GetResponse respuesta = canal.basicGet(dlq, false);
            if (respuesta == null) {
                break;
            }
            long tag = respuesta.getEnvelope().getDeliveryTag();
            Map<String, Object> cabeceras = cabeceras(respuesta.getProps());
            String destino = destinoForzado != null && !destinoForzado.isBlank()
                    ? destinoForzado : colaOriginal(cabeceras);
            if (destino == null || destino.equals(dlq)) {
                // No se sabe a donde devolverlo: se deja en la DLQ y se corta, para
                // no volver a leer el mismo mensaje en bucle.
                canal.basicNack(tag, false, true);
                sinDestino++;
                break;
            }
            canal.basicPublish("", destino, propiedadesParaReprocesar(respuesta.getProps(), dlq), respuesta.getBody());
            canal.basicAck(tag, false);
            reprocesados++;
            porDestino.merge(destino, 1, Integer::sum);
        }
        log.info("Reproceso de {}: {} mensajes devueltos {} ; {} sin destino", dlq, reprocesados, porDestino, sinDestino);
        return new ResultadoReproceso(dlq, reprocesados, sinDestino, porDestino);
    }

    /** La cola de la que salio el mensaje, segun la cabecera x-death de RabbitMQ. */
    static String colaOriginal(Map<String, Object> cabeceras) {
        Object primera = cabeceras.get("x-first-death-queue");
        if (primera != null) {
            return String.valueOf(primera);
        }
        Map<String, Object> muerte = primeraMuerte(cabeceras);
        return muerte == null || muerte.get("queue") == null ? null : String.valueOf(muerte.get("queue"));
    }

    /**
     * Las mismas propiedades sin el historial de muerte (para que el mensaje
     * vuelva "limpio") y con la cuenta de cuantas veces se reproceso.
     */
    static AMQP.BasicProperties propiedadesParaReprocesar(AMQP.BasicProperties originales, String dlq) {
        Map<String, Object> cabeceras = new HashMap<>(cabeceras(originales));
        long reprocesos = numero(cabeceras.get(CABECERA_REPROCESOS)) + 1;
        cabeceras.keySet().removeIf(clave -> clave.startsWith("x-death") || clave.startsWith("x-first-death")
                || clave.startsWith("x-last-death"));
        cabeceras.put(CABECERA_REPROCESOS, reprocesos);
        cabeceras.put("x-reprocesado-desde", dlq);
        AMQP.BasicProperties.Builder builder = originales == null ? new AMQP.BasicProperties.Builder() : originales.builder();
        return builder.headers(cabeceras).build();
    }

    private static MensajeEnCola describir(int posicion, GetResponse respuesta) {
        Map<String, Object> cabeceras = cabeceras(respuesta.getProps());
        Map<String, Object> muerte = primeraMuerte(cabeceras);
        String cuerpo = respuesta.getBody() == null ? "" : new String(respuesta.getBody(), StandardCharsets.UTF_8);
        if (cuerpo.length() > CUERPO_MAXIMO) {
            cuerpo = cuerpo.substring(0, CUERPO_MAXIMO) + "...";
        }
        return new MensajeEnCola(
                posicion,
                cuerpo,
                colaOriginal(cabeceras),
                muerte == null ? null : texto(muerte.get("exchange")),
                muerte == null ? null : primeraRoutingKey(muerte.get("routing-keys")),
                muerte == null ? null : texto(muerte.get("reason")),
                muerte == null ? 0 : numero(muerte.get("count")),
                numero(cabeceras.get(CABECERA_REPROCESOS)));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> primeraMuerte(Map<String, Object> cabeceras) {
        Object muertes = cabeceras.get("x-death");
        if (muertes instanceof List<?> lista && !lista.isEmpty() && lista.get(0) instanceof Map<?, ?> mapa) {
            return (Map<String, Object>) mapa;
        }
        return null;
    }

    private static Map<String, Object> cabeceras(AMQP.BasicProperties propiedades) {
        return propiedades == null || propiedades.getHeaders() == null ? Map.of() : propiedades.getHeaders();
    }

    private static String primeraRoutingKey(Object valor) {
        if (valor instanceof List<?> lista && !lista.isEmpty()) {
            return texto(lista.get(0));
        }
        return texto(valor);
    }

    /** Los textos de las cabeceras llegan como LongString: String.valueOf los convierte. */
    private static String texto(Object valor) {
        return valor == null ? null : String.valueOf(valor);
    }

    private static long numero(Object valor) {
        if (valor instanceof Number n) {
            return n.longValue();
        }
        try {
            return valor == null ? 0 : Long.parseLong(String.valueOf(valor));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
