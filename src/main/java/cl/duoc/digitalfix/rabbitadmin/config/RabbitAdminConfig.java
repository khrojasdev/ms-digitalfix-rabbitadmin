package cl.duoc.digitalfix.rabbitadmin.config;

import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Expone el RabbitAdmin de Spring AMQP, que es la pieza que permite declarar y
 * eliminar colas, exchanges y bindings sobre el broker en tiempo de ejecucion.
 */
@Configuration
@EnableConfigurationProperties(PropiedadesAdministrador.class)
public class RabbitAdminConfig {

    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        // No tumbar el arranque si el broker aun no esta disponible; las
        // operaciones se haran cuando lleguen las peticiones REST.
        admin.setIgnoreDeclarationExceptions(true);
        return admin;
    }
}
