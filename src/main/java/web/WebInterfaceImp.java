package web;


import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;
import java.util.Map;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import search.WebInterface;

/**
 * Implementação da interface remota {@link WebInterface} usada para comunicação entre
 * a Gateway e o front-end web via WebSocket.
 *
 * <p>Recebe atualizações de estatísticas do sistema (por exemplo, número de URLs indexadas
 * por cada crawler) e propaga essas informações em tempo real para clientes conectados
 * através do tópico "/topic/messages".</p>
 *
 * <p>Esta classe é registrada como componente Spring e é exportada como objeto RMI
 * para permitir que a Gateway invoque métodos remotamente.</p>
 *
 * @author Lorando Ca, Pedro Ferreira
 */
@Component
public class WebInterfaceImp extends UnicastRemoteObject implements WebInterface{
    /** Template Spring para envio de mensagens WebSocket aos clientes. */
    public SimpMessagingTemplate messagingTemplate;

    /** Estatísticas atuais recebidas da Gateway (crawler -> lista de URLs). */
    public Map<String, List<String>> statistics;


    /**
     * Construtor que injeta o template de mensagens WebSocket e exporta o objeto para RMI.
     *
     * @param messagingTemplate template Spring para envio de mensagens aos clientes
     * @throws RemoteException se houver erro na exportação RMI do objeto
     */
    @Autowired
    public WebInterfaceImp(SimpMessagingTemplate messagingTemplate) throws RemoteException {
        super();
        this.messagingTemplate = messagingTemplate;
    }


    /**
     * Recebe uma atualização de estatísticas da Gateway e propaga para todos os clientes
     * conectados via WebSocket.
     *
     * @param info mapa contendo informações de estatísticas (ex.: crawler -> URLs)
     */
    @Override
    public void update(Map<String, List<String>> info) {
        statistics = info;
        System.out.println("Message received");
        messagingTemplate.convertAndSend("/topic/messages", new Message(info));
    }
}