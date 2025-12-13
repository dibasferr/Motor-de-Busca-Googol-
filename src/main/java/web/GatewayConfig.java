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


import search.GatewayInterface;


@Configuration
public class GatewayConfig {

    private String endereço_gateway=null;
	private String porta=null;
    
	/**
     * Cria e fornece um stub RMI para o Gateway, disponível ao nível da aplicação.
     *
     * <p>O método lê o ficheiro de propriedades, obtém o endereço e a porta do servidor
     * RMI e realiza o lookup do objeto remoto {@link GatewayInterface}. O bean gerado
     * é application-scoped, garantindo que existe apenas um stub para toda a aplicação.</p>
     *
     * @return instância remota de {@link GatewayInterface}, ou null caso ocorra erro
	 * @author Lorando Ca, Pedro Ferreira
     */
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
		System.setProperty("java.rmi.server.hostname", "172.20.10.2");
		try {
			GatewayInterface stub = (GatewayInterface)Naming.lookup( String.format("rmi://%s:%s/Gateway",endereço_gateway,porta));
			return stub;

		}catch(Exception exception){
			System.out.println("Erro a criar a interface gateway");
			exception.printStackTrace();
		}

        return null;
    }
}