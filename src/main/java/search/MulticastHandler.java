package search;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Thread que escuta o grupo multicast e aplica updates recebidos de outros Barrels.
 *
 * <p>O handler faz join ao grupo multicast definido (230.0.0.0:4446) e processa mensagens
 * contendo actualizações de palavras ou links. Para cada mensagem válida envia um ACK
 * de volta ao emissor e invoca os métodos {@link MainStorageBarrel#addWordToStructure} ou
 * {@link MainStorageBarrel#addLinks} para aplicar a alteração localmente.
 *
 * @apiNote O código assume que a interface de rede está em 192.168.1.197; tornar configurável é recomendado.
 * @author Lorando Ca, Pedro Ferreira
 */

public class MulticastHandler extends Thread {

    String groupAddress ;
    int port ;
    MulticastSocket socket ;
    InetAddress group ;
    NetworkInterface netIf ;
    SocketAddress groupSockAddr ;
    MainStorageBarrel barrel;
    String endereço;
    String porta;

    /**
     * Constrói o handler multicast, configurando o socket, a interface de rede e
     * juntando-se ao grupo multicast. Lê as configurações necessárias do ficheiro
     * config.properties.
     *
     * @param barrel Instância local onde as atualizações recebidas serão aplicadas.
     */
    public MulticastHandler(MainStorageBarrel barrel){
        this.barrel= barrel;
        // TODO Auto-generated method stub
        groupAddress = "230.0.0.0";
        port = 4446;

        Properties config = new Properties();

        try (FileInputStream input = new FileInputStream("config.properties")) {
            // Carrega o arquivo .properties
            config.load(input);
            // Lê as propriedades
            endereço = config.getProperty("rmi.host2");//pega da sua maquina
            porta = config.getProperty("rmi.port2");
        }catch(IOException e) {
            System.out.println("Erro ao carregar arquivo de configuração: " + e.getMessage());
        }

        try{
            this.socket = new MulticastSocket(port);
            group = InetAddress.getByName(groupAddress);
            netIf = NetworkInterface.getByInetAddress(InetAddress.getByName(endereço));
            
            System.out.println(netIf);
            
            groupSockAddr = new InetSocketAddress(group, port);

            // Entrar no grupo multicast (novo método)
            socket.joinGroup(groupSockAddr, netIf);
            System.out.println("Aguardando mensagens no grupo " + groupAddress + " ...");
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    /**
     * Loop principal que recebe pacotes multicast, desserializa o objecto e aplica a actualização.
     *
     * <p>Mensagens esperadas:
     * <ul>
     *   <li>Map com keys {"words","url","ref_num","Crawler","pages"}</li>
     *   <li>Map com keys {"fromUrl","toUrls","ref_num","Crawler"}</li>
     * </ul>
     */
    //Mostly chatGPT
    @Override
    public void run() {
        while(true){
            
            try {
                byte[] data = new byte[8192];
                System.out.println("Setup");
                DatagramPacket packet = new DatagramPacket(data, data.length);
                socket.receive(packet);
                System.out.println("Recebi uma mensagem");
                
                System.out.println("Inet address:");
                System.out.println(InetAddress.getByName(endereço));
                System.out.println("Packet Address:");
                System.out.println(packet.getAddress());
                
                if (packet.getAddress().equals(InetAddress.getByName(endereço))) {//Deixar a mensagem ack vir do outro barrel
                    continue;
                }
                
                byte[] dados = packet.getData();
                int length = packet.getLength();
                //Enviar ACK 
                //Guardar a ultima ref recebida para manter a caracteristica at most once
                try (ObjectInputStream ois = new ObjectInputStream(
                    new ByteArrayInputStream(dados, 0, length))) {
    
                    Object obj = ois.readObject();
                    if (!(obj instanceof Map)) {
                        System.out.println("Objeto recebido não é um Map!");
                        continue;
                    }
                    
                    // Fazemos a conversão genérica
                    Map<?, ?> map = (Map<?, ?>) obj;
    
                    // Caso 1: contém "words" e "url"
                    if (map.containsKey("words") && map.containsKey("url")) {
                        Object c= map.get("Crawler");
                        Object w = map.get("words");
                        Object u = map.get("url");
                        Object b= map.get("pages");
    
                        if (w instanceof Set<?> && u instanceof String) {
                           
                            // Converte com segurança
                            @SuppressWarnings("unchecked")
                            Set<String> words = (Set<String>) w;
                            String url = (String) u;
                            
                            byte[] buffer= "ACK".getBytes();
                            // Cria o pacote e envia
                            DatagramPacket ackpack = new DatagramPacket(buffer, buffer.length, packet.getAddress(), packet.getPort());
                            System.out.println(packet.getAddress());
                            socket.send(ackpack);

                            System.out.println("\nENVIEI ACK\n");

                            new Thread(() -> {
                                barrel.addWordToStructure(words, url, (PageInfo) b, (String) c, -1);
                            }).start();

                            System.out.println("Apos receber na minha thread adicionei à minha estrutura de words");
                        } else {
                            System.err.println("Tipos incompatíveis para 'words' ou 'url'");
                        }
                    }
                    // Caso 2: contém "fromUrl" e "toUrls"
                    else if (map.containsKey("fromUrl") && map.containsKey("toUrls")) {
                        System.out.println("Links a serem recebidos\n");

                      
                        Object c= map.get("Crawler");

                        Object f = map.get("fromUrl");
                        Object t = map.get("toUrls");
    
                        if (f instanceof String && t instanceof Set<?>) {
                            @SuppressWarnings("unchecked")
                            Set<String> toUrls = (Set<String>) t;
                            String fromUrl = (String) f;

                            byte[] buffer= "ACK".getBytes();
                            // Cria o pacote e envia
                            DatagramPacket ackpack = new DatagramPacket(buffer, buffer.length, packet.getAddress(), packet.getPort());
                            socket.send(ackpack);

                            System.out.println("\nEnviei ACK");

                            new Thread(() -> {
                                barrel.addLinks(fromUrl, toUrls,(String)c, -1); //Atualizacao de barrel
                            }).start();

                            System.out.println("Apos receber na minha thread adicionei à minha estrutura de links");
                        } else {
                            System.err.println("Tipos incompatíveis para 'fromUrl' ou 'toUrls'");
                        }
                    }else {
                    System.err.println("Estrutura de dados desconhecida recebida: " + map.keySet());
                }

            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
                //mandar ACk X
                //Fazer codigo da parte do outro barrel q vai fazer envios multicast X
                //Fazer um envio completo quando um barrel se inscreve X
                //Fazer filtragem de duplicados, usando uma ref para cada operacao da parte do barrel q recebe X
                //
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}