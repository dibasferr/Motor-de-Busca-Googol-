package web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


/**
 * Classe principal da aplicação Spring Boot responsável por iniciar o servidor web.
 *
 * <p>Esta classe configura automaticamente o contexto Spring, inicia o
 * servidor embutido (por exemplo, Tomcat) e prepara todos os beans definidos
 * na aplicação.</p>
 * @author Lorando Ca, Pedro Ferreira
 */
@SpringBootApplication
public class ServingWebContentApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServingWebContentApplication.class, args);
		
    }

}