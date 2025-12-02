package web;

import java.io.IOException;
import java.io.InputStream;
import java.rmi.Naming;
import java.util.Properties;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.web.context.WebApplicationContext;

import web.GreetingController;
import search.GatewayInterface;


@Configuration
public class GatewayConfig {

    private String endereço_gateway=null;
	private String porta=null;
	private GatewayInterface gateway_stub;

    @Bean(name = "applicationScopedGatewayGenerator")
    @Scope(value = WebApplicationContext.SCOPE_APPLICATION, proxyMode = ScopedProxyMode.TARGET_CLASS)


    public GatewayInterface gatewayConfig() {
        
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("config.properties")) {
			Properties props = new Properties();

			// Carrega o arquivo .properties
			props.load(input);

			// Lê as propriedades
			endereço_gateway= props.getProperty("rmi.host2");
			porta = props.getProperty("rmi.port2");
		
        }catch(IOException e) {
			System.out.println("Erro ao carregar arquivo de configuração: " + e.getMessage());
		}
		
		//gateway interface setup
		try {
			gateway_stub = (GatewayInterface)Naming.lookup( String.format("rmi://%s:%s/Gateway",endereço_gateway,porta));
			return gateway_stub;

		}catch(Exception exception){
			System.out.println("Erro a criar a interface gateway");
			exception.printStackTrace();
		}

        return null;
    }
}