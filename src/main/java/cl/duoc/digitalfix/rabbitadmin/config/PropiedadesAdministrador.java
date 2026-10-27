package cl.duoc.digitalfix.rabbitadmin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Colas y exchanges del sistema que el administrador vigila. Se leen de
 * application.yml (rabbitadmin.*), igual que el resto de los nombres.
 *
 * AMQP no permite listar todas las colas del broker; por eso el listado se
 * arma con esta configuracion: las colas de trabajo del sistema y su DLQ.
 */
@ConfigurationProperties(prefix = "rabbitadmin")
public class PropiedadesAdministrador {

    /** Cada cola de trabajo con su cola de mensajes muertos. */
    private List<ColaVigilada> colas = new ArrayList<>();

    /** Exchanges del sistema. */
    private List<String> exchanges = new ArrayList<>();

    public List<ColaVigilada> getColas() { return colas; }
    public void setColas(List<ColaVigilada> colas) { this.colas = colas; }
    public List<String> getExchanges() { return exchanges; }
    public void setExchanges(List<String> exchanges) { this.exchanges = exchanges; }

    public static class ColaVigilada {
        private String nombre;
        private String dlq;
        private String servicio;

        public String getNombre() { return nombre; }
        public void setNombre(String nombre) { this.nombre = nombre; }
        public String getDlq() { return dlq; }
        public void setDlq(String dlq) { this.dlq = dlq; }
        public String getServicio() { return servicio; }
        public void setServicio(String servicio) { this.servicio = servicio; }
    }
}
