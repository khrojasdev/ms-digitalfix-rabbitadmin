package cl.duoc.digitalfix.rabbitadmin;

import cl.duoc.digitalfix.rabbitadmin.config.PropiedadesAdministrador;
import cl.duoc.digitalfix.rabbitadmin.service.RabbitAdminService;
import cl.duoc.digitalfix.rabbitadmin.web.dto.BindingRequest;
import cl.duoc.digitalfix.rabbitadmin.web.dto.ColaRequest;
import cl.duoc.digitalfix.rabbitadmin.web.dto.EstadoCola;
import cl.duoc.digitalfix.rabbitadmin.web.dto.ExchangeRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitAdmin;

import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * El servicio delega en RabbitAdmin y arma la topologia pedida, sin broker.
 */
class RabbitAdminServiceTest {

    private final RabbitAdmin rabbitAdmin = mock(RabbitAdmin.class);
    private final RabbitAdminService service = new RabbitAdminService(rabbitAdmin);

    private List<Queue> colasDeclaradas(int cuantas) {
        ArgumentCaptor<Queue> captor = ArgumentCaptor.forClass(Queue.class);
        verify(rabbitAdmin, times(cuantas)).declareQueue(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void conDlqCreaLaColaYSuDlqEnlazadas() {
        RabbitAdminService.ColaCreada creada = service.crearCola(new ColaRequest("demo.queue", true, null, null, true));

        assertThat(creada.nombre()).isEqualTo("demo.queue");
        assertThat(creada.dlq()).isEqualTo("demo.queue.dlq");
        List<Queue> colas = colasDeclaradas(2);
        assertThat(colas.get(0).getName()).isEqualTo("demo.queue.dlq");
        Queue principal = colas.get(1);
        assertThat(principal.isDurable()).isTrue();
        assertThat(principal.getArguments())
                .containsEntry("x-dead-letter-exchange", "")
                .containsEntry("x-dead-letter-routing-key", "demo.queue.dlq");
    }

    @Test
    void conRoutingKeyDeDlqSinExchangeTambienCreaLaDlq() {
        // Antes este caso solo ponia el argumento: la DLQ no existia y sin
        // x-dead-letter-exchange los mensajes muertos no iban a ninguna parte.
        RabbitAdminService.ColaCreada creada = service.crearCola(
                new ColaRequest("ordenes.avisos.queue", true, "", "ordenes.avisos.dlq"));

        assertThat(creada.dlq()).isEqualTo("ordenes.avisos.dlq");
        Queue principal = colasDeclaradas(2).get(1);
        assertThat(principal.getArguments()).containsEntry("x-dead-letter-exchange", "");
    }

    @Test
    void conUnExchangeDeDlqPropioSoloPoneLosArgumentos() {
        RabbitAdminService.ColaCreada creada = service.crearCola(
                new ColaRequest("cola.x", true, "dlx.exchange", "cola.x.muertos"));

        assertThat(creada.dlq()).isNull();
        Queue principal = colasDeclaradas(1).get(0);
        assertThat(principal.getArguments()).containsEntry("x-dead-letter-exchange", "dlx.exchange");
    }

    @Test
    void sinDlqEsUnaColaSimple() {
        assertThat(service.crearCola(new ColaRequest("cola.simple", false, null, null)).dlq()).isNull();
        Queue cola = colasDeclaradas(1).get(0);
        assertThat(cola.isDurable()).isFalse();
        assertThat(cola.getArguments()).isEmpty();
    }

    @Test
    void laDlqNoPuedeLlamarseIgualQueLaCola() {
        assertThatThrownBy(() -> service.crearCola(new ColaRequest("cola.y", true, null, "cola.y")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void eliminarColaDevuelveElResultadoDeRabbitAdmin() {
        when(rabbitAdmin.deleteQueue("cola.x")).thenReturn(true);
        assertThat(service.eliminarCola("cola.x")).isTrue();
        verify(rabbitAdmin).deleteQueue("cola.x");
    }

    @Test
    void unNombreRaroSeRechazaAntesDeLlegarAlBroker() {
        assertThatThrownBy(() -> service.eliminarCola("cola con espacios")).isInstanceOf(IllegalArgumentException.class);
        verify(rabbitAdmin, never()).deleteQueue(anyString());
    }

    @Test
    void vaciarDevuelveCuantosBorroOMenosUnoSiNoExiste() {
        when(rabbitAdmin.getQueueProperties("ordenes.auditoria.dlq")).thenReturn(new Properties());
        when(rabbitAdmin.purgeQueue("ordenes.auditoria.dlq")).thenReturn(4);
        assertThat(service.vaciarCola("ordenes.auditoria.dlq")).isEqualTo(4);
        assertThat(service.vaciarCola("no.existe")).isEqualTo(-1);
    }

    @Test
    void listaLasColasDelSistemaConSuDlq() {
        PropiedadesAdministrador propiedades = new PropiedadesAdministrador();
        PropiedadesAdministrador.ColaVigilada cola = new PropiedadesAdministrador.ColaVigilada();
        cola.setNombre("ordenes.auditoria.queue");
        cola.setDlq("ordenes.auditoria.dlq");
        cola.setServicio("ms-digitalfix-audit");
        propiedades.setColas(List.of(cola));
        Properties principal = new Properties();
        principal.put(RabbitAdmin.QUEUE_MESSAGE_COUNT, 0);
        principal.put(RabbitAdmin.QUEUE_CONSUMER_COUNT, 1);
        Properties dlq = new Properties();
        dlq.put(RabbitAdmin.QUEUE_MESSAGE_COUNT, 7);
        dlq.put(RabbitAdmin.QUEUE_CONSUMER_COUNT, 0);
        when(rabbitAdmin.getQueueProperties("ordenes.auditoria.queue")).thenReturn(principal);
        when(rabbitAdmin.getQueueProperties("ordenes.auditoria.dlq")).thenReturn(dlq);

        List<EstadoCola> estado = new RabbitAdminService(rabbitAdmin, null, propiedades).listarColas();

        assertThat(estado).containsExactly(new EstadoCola("ordenes.auditoria.queue", "ms-digitalfix-audit",
                true, 0, 1, "ordenes.auditoria.dlq", true, 7));
    }

    @Test
    void crearExchangeDirectDeclaraElTipoCorrecto() {
        service.crearExchange(new ExchangeRequest("catalogo.stock.exchange", "direct", true));

        ArgumentCaptor<Exchange> captor = ArgumentCaptor.forClass(Exchange.class);
        verify(rabbitAdmin).declareExchange(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("catalogo.stock.exchange");
        assertThat(captor.getValue().getType()).isEqualTo("direct");
    }

    @Test
    void crearYQuitarBindingEnlazaColaYExchange() {
        BindingRequest req = new BindingRequest("ordenes.avisos.queue", "ordenes.negocio.exchange", "ordenes.eventos.*");
        service.crearBinding(req);
        service.eliminarBinding(req);

        ArgumentCaptor<Binding> captor = ArgumentCaptor.forClass(Binding.class);
        verify(rabbitAdmin).declareBinding(captor.capture());
        assertThat(captor.getValue().getDestination()).isEqualTo("ordenes.avisos.queue");
        assertThat(captor.getValue().getExchange()).isEqualTo("ordenes.negocio.exchange");
        assertThat(captor.getValue().getRoutingKey()).isEqualTo("ordenes.eventos.*");
        verify(rabbitAdmin).removeBinding(any(Binding.class));
    }
}
