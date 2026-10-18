package cl.duoc.digitalfix.rabbitadmin;

import cl.duoc.digitalfix.rabbitadmin.service.RabbitAdminService;
import cl.duoc.digitalfix.rabbitadmin.web.dto.BindingRequest;
import cl.duoc.digitalfix.rabbitadmin.web.dto.ColaRequest;
import cl.duoc.digitalfix.rabbitadmin.web.dto.ExchangeRequest;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitAdmin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifica que el servicio delega correctamente en RabbitAdmin y arma la
 * topologia pedida, sin necesidad de un broker real.
 */
class RabbitAdminServiceTest {

    @Test
    void crearColaDeclaraUnaColaConDlq() {
        RabbitAdmin rabbitAdmin = mock(RabbitAdmin.class);
        RabbitAdminService service = new RabbitAdminService(rabbitAdmin);

        String nombre = service.crearCola(
                new ColaRequest("ordenes.avisos.queue", true, "", "ordenes.avisos.dlq"));

        assertThat(nombre).isEqualTo("ordenes.avisos.queue");
        org.mockito.ArgumentCaptor<Queue> captor = org.mockito.ArgumentCaptor.forClass(Queue.class);
        verify(rabbitAdmin).declareQueue(captor.capture());
        Queue declarada = captor.getValue();
        assertThat(declarada.getName()).isEqualTo("ordenes.avisos.queue");
        assertThat(declarada.isDurable()).isTrue();
        assertThat(declarada.getArguments()).containsEntry("x-dead-letter-routing-key", "ordenes.avisos.dlq");
    }

    @Test
    void eliminarColaDevuelveElResultadoDeRabbitAdmin() {
        RabbitAdmin rabbitAdmin = mock(RabbitAdmin.class);
        when(rabbitAdmin.deleteQueue("cola.x")).thenReturn(true);
        RabbitAdminService service = new RabbitAdminService(rabbitAdmin);

        assertThat(service.eliminarCola("cola.x")).isTrue();
        verify(rabbitAdmin).deleteQueue("cola.x");
    }

    @Test
    void crearExchangeDirectDeclaraElTipoCorrecto() {
        RabbitAdmin rabbitAdmin = mock(RabbitAdmin.class);
        RabbitAdminService service = new RabbitAdminService(rabbitAdmin);

        service.crearExchange(new ExchangeRequest("catalogo.stock.exchange", "direct", true));

        org.mockito.ArgumentCaptor<Exchange> captor = org.mockito.ArgumentCaptor.forClass(Exchange.class);
        verify(rabbitAdmin).declareExchange(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("catalogo.stock.exchange");
        assertThat(captor.getValue().getType()).isEqualTo("direct");
    }

    @Test
    void crearBindingEnlazaColaYExchange() {
        RabbitAdmin rabbitAdmin = mock(RabbitAdmin.class);
        RabbitAdminService service = new RabbitAdminService(rabbitAdmin);

        service.crearBinding(new BindingRequest("notify.stock.queue", "catalogo.stock.exchange", "stock.bajo-minimo"));

        org.mockito.ArgumentCaptor<Binding> captor = org.mockito.ArgumentCaptor.forClass(Binding.class);
        verify(rabbitAdmin).declareBinding(captor.capture());
        Binding b = captor.getValue();
        assertThat(b.getDestination()).isEqualTo("notify.stock.queue");
        assertThat(b.getExchange()).isEqualTo("catalogo.stock.exchange");
        assertThat(b.getRoutingKey()).isEqualTo("stock.bajo-minimo");
    }
}
