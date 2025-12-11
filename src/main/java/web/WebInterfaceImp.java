package web;


import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;
import java.util.Map;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import search.WebInterface;

@Component
public class WebInterfaceImp extends UnicastRemoteObject implements WebInterface{
    public SimpMessagingTemplate messagingTemplate;
    public Map<String, List<String>> statistics;

    @Autowired
    public WebInterfaceImp(SimpMessagingTemplate messagingTemplate) throws RemoteException {
        super();
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void update(Map<String, List<String>> info) {
        statistics = info;
        System.out.println("Message received");
        messagingTemplate.convertAndSend("/topic/messages", new Message(info));
    }
}