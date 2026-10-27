package cl.duoc.digitalfix.rabbitadmin;

import cl.duoc.digitalfix.rabbitadmin.service.ColaMuertaService;
import cl.duoc.digitalfix.rabbitadmin.web.dto.MensajeEnCola;
import cl.duoc.digitalfix.rabbitadmin.web.dto.ResultadoReproceso;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.GetResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.ChannelCallback;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Ver y reprocesar mensajes muertos, con un canal simulado que devuelve
 * mensajes como los que deja RabbitMQ en una DLQ (cabecera x-death).
 */
class ColaMuertaServiceTest {

    private final RabbitTemplate template = mock(RabbitTemplate.class);
    private final RabbitAdmin admin = mock(RabbitAdmin.class);
    private final Channel canal = mock(Channel.class);
    private final ColaMuertaService servicio = new ColaMuertaService(template, admin);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void canalSimulado() {
        when(template.execute(any(ChannelCallback.class))).thenAnswer(i -> ((ChannelCallback<Object>) i.getArgument(0)).doInRabbit(canal));
        when(admin.getQueueProperties(anyString())).thenReturn(new Properties());
    }

    /** Un mensaje muerto tal como lo deja RabbitMQ al hacer NACK sin reencolar. */
    private GetResponse muerto(long tag, String cuerpo, String colaOriginal) {
        Map<String, Object> muerte = Map.of("queue", colaOriginal, "reason", "rejected", "count", 1L,
                "exchange", "ordenes.negocio.exchange", "routing-keys", List.of("ordenes.eventos.creada"));
        AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                .contentType("text/plain")
                .headers(Map.of("x-death", List.of(muerte), "x-first-death-queue", colaOriginal))
                .build();
        return new GetResponse(new Envelope(tag, false, "", "ordenes.auditoria.dlq"), props,
                cuerpo.getBytes(StandardCharsets.UTF_8), 0);
    }

    @Test
    void verMensajesLosMuestraYLosDevuelveTodosALaCola() throws Exception {
        when(canal.basicGet("ordenes.auditoria.dlq", false))
                .thenReturn(muerto(1, "{\"tipoEvento\":\"CREADA\"}", "ordenes.auditoria.queue"))
                .thenReturn(muerto(2, "esto no es json", "ordenes.auditoria.queue"))
                .thenReturn(null);

        List<MensajeEnCola> mensajes = servicio.verMensajes("ordenes.auditoria.dlq", 10);

        assertThat(mensajes).hasSize(2);
        assertThat(mensajes.get(1).cuerpo()).isEqualTo("esto no es json");
        assertThat(mensajes.get(0).colaOriginal()).isEqualTo("ordenes.auditoria.queue");
        assertThat(mensajes.get(0).motivo()).isEqualTo("rejected");
        assertThat(mensajes.get(0).routingKeyOriginal()).isEqualTo("ordenes.eventos.creada");
        verify(canal).basicNack(2L, true, true);
        verify(canal, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    void reprocesarDevuelveCadaMensajeASuColaOriginalYLuegoConfirma() throws Exception {
        when(canal.basicGet("ordenes.auditoria.dlq", false))
                .thenReturn(muerto(1, "{\"a\":1}", "ordenes.auditoria.queue"))
                .thenReturn(muerto(2, "{\"a\":2}", "ordenes.auditoria.queue"))
                .thenReturn(null);

        ResultadoReproceso resultado = servicio.reprocesar("ordenes.auditoria.dlq", null, 50);

        assertThat(resultado.reprocesados()).isEqualTo(2);
        assertThat(resultado.porDestino()).containsEntry("ordenes.auditoria.queue", 2);
        ArgumentCaptor<AMQP.BasicProperties> props = ArgumentCaptor.forClass(AMQP.BasicProperties.class);
        verify(canal, times(2)).basicPublish(eq(""), eq("ordenes.auditoria.queue"), props.capture(), any());
        assertThat(props.getValue().getHeaders())
                .doesNotContainKeys("x-death", "x-first-death-queue")
                .containsEntry("x-reprocesos", 1L)
                .containsEntry("x-reprocesado-desde", "ordenes.auditoria.dlq");
        verify(canal).basicAck(1L, false);
        verify(canal).basicAck(2L, false);
    }

    @Test
    void sinCabeceraDeOrigenLoDejaEnLaDlqYSeDetiene() throws Exception {
        AMQP.BasicProperties sinCabeceras = new AMQP.BasicProperties.Builder().build();
        when(canal.basicGet("demo.dlq", false))
                .thenReturn(new GetResponse(new Envelope(9, false, "", "demo.dlq"), sinCabeceras, "x".getBytes(), 0));

        ResultadoReproceso resultado = servicio.reprocesar("demo.dlq", null, 50);

        assertThat(resultado.reprocesados()).isZero();
        assertThat(resultado.sinDestino()).isEqualTo(1);
        verify(canal).basicNack(9L, false, true);
        verify(canal, never()).basicPublish(anyString(), anyString(), any(), any());
    }

    @Test
    void conDestinoIndicadoLosMandaAhi() throws Exception {
        when(canal.basicGet("demo.dlq", false)).thenReturn(muerto(3, "{}", "otra.queue")).thenReturn(null);

        servicio.reprocesar("demo.dlq", "demo.queue", 50);

        verify(canal).basicPublish(eq(""), eq("demo.queue"), any(), any());
    }

    @Test
    void colaInexistente() {
        when(admin.getQueueProperties("no.existe")).thenReturn(null);
        assertThat(servicio.verMensajes("no.existe", 5)).isNull();
        assertThat(servicio.reprocesar("no.existe", null, 5)).isNull();
        assertThatThrownBy(() -> servicio.reprocesar("demo.dlq", "no.existe", 5)).isInstanceOf(IllegalArgumentException.class);
    }
}
