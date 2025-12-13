package web;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

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

        try (InputStream input = ServingWebContentApplication.class.getClassLoader().getResourceAsStream("config.properties")) {
			Properties props = new Properties();

			// Carrega o arquivo .properties
			props.load(input);

			// Lê as propriedades
			String endereco= props.getProperty("rmi.host1");
            
		    System.setProperty("java.rmi.server.hostname", endereco);

        }catch(IOException e) {
            System.out.println("Erro ao carregar arquivo de configuração: " + e.getMessage());
        }
        
        SpringApplication.run(ServingWebContentApplication.class, args);
		
    }

}