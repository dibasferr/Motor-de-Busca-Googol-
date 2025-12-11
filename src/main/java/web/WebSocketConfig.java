package web;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;


/**
 * Configuração do WebSocket usando STOMP para comunicação em tempo real entre
 * o servidor e clientes web.
 *
 * <p>Esta configuração habilita um broker simples para tópicos, define o prefixo
 * das mensagens de aplicação, registra endpoints STOMP e escuta eventos de conexão
 * e desconexão de sessões WebSocket.</p>
 *
 * <p>Funcionalidades principais:
 * <ul>
 *   <li>Configura um broker simples para mensagens publicadas em "/topic".</li>
 *   <li>Define o prefixo de destino de mensagens enviadas do cliente para o servidor como "/app".</li>
 *   <li>Registra o endpoint "/my-websocket" para clientes se conectarem via STOMP.</li>
 *   <li>Escuta eventos de conexão/desconexão de sessões e loga mensagens no console.</li>
 * </ul>
 * </p>
 *
 * @author Lorando Ca, Pedro Ferreira
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
	@Override
	public void configureMessageBroker(MessageBrokerRegistry config) {
		config.enableSimpleBroker("/topic");
		config.setApplicationDestinationPrefixes("/app");
	}

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		registry.addEndpoint("/my-websocket");
	}

	@EventListener
	public void SessionConnect(SessionConnectEvent event) {
		System.out.println("Session connected");
	}

	@EventListener
	public void SessionDisconnect(SessionDisconnectEvent event) {
		System.out.println("Session disconnected");
	}
}

