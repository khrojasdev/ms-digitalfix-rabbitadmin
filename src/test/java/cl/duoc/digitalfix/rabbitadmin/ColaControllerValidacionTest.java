package cl.duoc.digitalfix.rabbitadmin;

import cl.duoc.digitalfix.rabbitadmin.service.ColaMuertaService;
import cl.duoc.digitalfix.rabbitadmin.service.RabbitAdminService;
import cl.duoc.digitalfix.rabbitadmin.web.BindingController;
import cl.duoc.digitalfix.rabbitadmin.web.ColaController;
import cl.duoc.digitalfix.rabbitadmin.web.dto.ResultadoReproceso;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La API valida la entrada antes de tocar el broker y responde con codigos
 * claros.
 */
@WebMvcTest({ColaController.class, BindingController.class})
class ColaControllerValidacionTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RabbitAdminService admin;

    @MockBean
    private ColaMuertaService colasMuertas;

    @Test
    void rechazaColaConNombreVacio() throws Exception {
        mockMvc.perform(post("/queues").contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"\"}"))
                .andExpect(status().isBadRequest());
        verify(admin, never()).crearCola(any());
    }

    @Test
    void rechazaUnaRoutingKeyDeDlqConCaracteresRaros() throws Exception {
        mockMvc.perform(post("/queues").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"cola.ok\",\"deadLetterRoutingKey\":\"con espacios\"}"))
                .andExpect(status().isBadRequest());
        verify(admin, never()).crearCola(any());
    }

    @Test
    void aceptaColaConDlq() throws Exception {
        when(admin.crearCola(any())).thenReturn(new RabbitAdminService.ColaCreada("cola.valida", "cola.valida.dlq"));

        mockMvc.perform(post("/queues").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"cola.valida\",\"durable\":true,\"conDlq\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.detalle").value("con su DLQ cola.valida.dlq"));
    }

    @Test
    void reprocesarValidaElMaximo() throws Exception {
        mockMvc.perform(post("/queues/ordenes.auditoria.dlq/reprocess").param("max", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/queues/ordenes.auditoria.dlq/reprocess").param("max", "1000"))
                .andExpect(status().isBadRequest());
        verify(colasMuertas, never()).reprocesar(any(), any(), anyInt());
    }

    @Test
    void reprocesarDevuelveElResumen() throws Exception {
        when(colasMuertas.reprocesar(eq("ordenes.auditoria.dlq"), isNull(), eq(50)))
                .thenReturn(new ResultadoReproceso("ordenes.auditoria.dlq", 3, 0, Map.of("ordenes.auditoria.queue", 3)));

        mockMvc.perform(post("/queues/ordenes.auditoria.dlq/reprocess"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reprocesados").value(3));
    }

    @Test
    void colaInexistenteEs404() throws Exception {
        when(colasMuertas.verMensajes("no.existe", 10)).thenReturn(null);
        mockMvc.perform(get("/queues/no.existe/messages")).andExpect(status().isNotFound());
        when(admin.vaciarCola("no.existe")).thenReturn(-1);
        mockMvc.perform(delete("/queues/no.existe/messages")).andExpect(status().isNotFound());
    }

    @Test
    void quitarBindingPorParametros() throws Exception {
        mockMvc.perform(delete("/bindings").param("cola", "demo.queue").param("exchange", "demo.exchange")
                        .param("routingKey", "demo.key"))
                .andExpect(status().isOk());
        verify(admin).eliminarBinding(any());
    }
}
