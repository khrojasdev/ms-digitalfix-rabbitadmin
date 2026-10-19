package cl.duoc.digitalfix.rabbitadmin;

import cl.duoc.digitalfix.rabbitadmin.service.RabbitAdminService;
import cl.duoc.digitalfix.rabbitadmin.web.ColaController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifica que la API valida la entrada: una cola con nombre vacio se rechaza
 * con 400 y no llega al servicio.
 */
@WebMvcTest(ColaController.class)
class ColaControllerValidacionTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RabbitAdminService admin;

    @Test
    void rechazaColaConNombreVacio() throws Exception {
        mockMvc.perform(post("/queues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"\"}"))
                .andExpect(status().isBadRequest());

        verify(admin, never()).crearCola(any());
    }

    @Test
    void aceptaColaConNombreValido() throws Exception {
        when(admin.crearCola(any())).thenReturn("cola.valida");

        mockMvc.perform(post("/queues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"cola.valida\",\"durable\":true}"))
                .andExpect(status().isCreated());

        verify(admin).crearCola(any());
    }
}
