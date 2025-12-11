package search;

import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;

/**
 * Implementação do Storage Barrel principal do sistema "Googol".
 *
 * <p>O Storage Barrel é responsável por armazenar o índice invertido, as relações de links entre
 * páginas (backlinks), informações de páginas (meta-dados) e métricas de popularidade das URLs.
 * Os barrels comunicam entre si através de multicast UDP para propagar atualizações (implementação
 * simples de replicação).
 *
 * <p>Funcionalidades principais:
 * <ul>
 *   <li>Armazenar índice invertido: palavra -> conjunto de URLs;</li>
 *   <li>Armazenar mapeamento de links (fromUrl -> toUrls) e calcular popularidade (in-degree);</li>
 *   <li>Receber updates de crawlers e multicast para replicação;</li>
 *   <li>Responder a pesquisas (returnSearchResult) retornando {@link PageInfo} ordenados por popularidade;</li>
 *   <li>Persistência (esqueleto) e sincronização inicial com um barrel activo.</li>
 * </ul>
 *
 * <p><b>Notas sobre fiabilidade:</b> a replicação é feita por multicast com ACKs; esta abordagem
 * fornece uma forma básica de propagação, mas não garante entrega a todas as réplicas em cenários
 * com perdas de rede. Para maior robustez, foi implementado uma coleta de ACKs.
 *
 * @apiNote O construtor tenta obter um snapshot inicial a partir de um barrel activo via {@code gateway.getBarrel().reboot()}.
 * @author Pedro Ferreira, Lorando Ca
 * @version 1
 */

public class MainStorageBarrel extends UnicastRemoteObject implements StorageBarrelInterface{
    // Indice invertido: palavra -> conjunto de URLs
    private Map<String, Set<String>> index;

    /**
     * Mapa de relações: fromUrl -> conjunto de URLs apontadas.
     */
    private Map<String, Set<String>> linkPages;

    /**
     * Popularidade de cada URL (número de links recebidos).
     */
    private Map<String, Integer> urlPopularity;

    /**
     * Referência à Gateway para registos e queries.
     */
    static GatewayInterface gateway;

    /**
     * Identificador do barrel atribuído pela Gateway.
     */
    static String nome;

    /**
     * Mapa para evitar reprocessamento duplicado por crawler.
     * Chave: crawler name; Valor: última referência processada.
     */
    private Map<String,Integer> last_sender;

    /**
     * Armazena meta-informação de cada página indexada.
     * Chave: URL; Valor: {@link PageInfo}
     */
    private Map<String,PageInfo> pageInfo;
    /**
     * Construtor: inicializa estruturas e tenta sincronizar o índice a partir de um barrel activo.
     *
     * @throws RemoteException Se ocorrer um erro na exportação RMI.
     * @apiNote A ligação ao gateway está hard-coded para "rmi://192.168.1.197:1099/Gateway" neste exemplo.
     */
    static String endereço=null;
    static String porta=null;
    static Properties config = new Properties();

    String fileName = "./fileBarrel.ser"; // Nome do arquivo binário para guardar e atualizar info de barrels

    int counter = 0; // Contador para gerenciar quando guardar info no ficheiro binário

    //@SuppressWarnings("unchecked")
    public MainStorageBarrel() throws RemoteException {
        index = new HashMap<>();
        linkPages = new HashMap<>();
        urlPopularity = new HashMap<>();
        last_sender= new HashMap<>();
        pageInfo= new HashMap<>();

        String endereço=null;
        String porta=null;
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

        try {
            gateway= (GatewayInterface)Naming.lookup(String.format("rmi://%s:%s/Gateway",endereço,porta));
            
            System.out.println("A pedir barrel ao gateway...");
            StorageBarrelInterface S = gateway.getBarrel();

            if (S == null) {
                File f = new File(fileName);
                
                if (f.exists()) {
                    this.carregarBarrelInfoBinario(fileName);
                } else{
                    System.out.println("O ficheiro não existe");
                }
            }
            else {
                System.out.println("Recebi do gateway: " + S);
            
                BarrelSnapshot snap = S.reboot();
                this.index = snap.index;
                this.linkPages = snap.linkPages;
                this.urlPopularity = snap.urlPopularity;
                this.pageInfo = snap.pageInfo;
            }
        } 
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Atualiza o índice invertido com o conjunto de palavras encontradas numa página.
     *
     * @param words Conjunto de palavras extraídas da página.
     * @param url URL da página indexada.
     * @param page Meta-informação da página ({@link PageInfo}).
     * @param Crawler Identificador do crawler que enviou os dados.
     * @param ref Número de referência sequencial vindo do crawler (usado para filtrar duplicados).
     * @return Novo valor de referência sugerido para o crawler (ref + número de chunks enviados).
     *
     * @apiNote Se {@code ref == -1} a operação é considerada proveniente de replicação (multicast) e não é retransmitida.
     *           Quando há múltiplos barrels, os updates são fragmentados em chunks e enviados por multicast para replicação.
     */
    @Override
    public synchronized int addWordToStructure(Set<String> words, String url,PageInfo page, String Crawler, int ref) {
        int count=0;

        //Se eu guardar o valor da ultima referencia dos dois crawlers, posso fazer filtragem corretamente
        if(last_sender.containsKey(Crawler) && (last_sender.get(Crawler) >= ref) && (ref!=-1)) return ref;

        if(ref!=-1){
            try {
                if( gateway.getBarrelNum() > 1){
                    List<String> lista_aux= new ArrayList<>(words);
                    int tam= words.size();
                    int chunkSize = 50;
                    int pos=0;
                    while (pos < tam) {
                        int end = Math.min(pos + chunkSize, tam);

                        Set<String> chunk = new HashSet<>(lista_aux.subList(pos, end));

                        count++;//retornar esse valor na funcao para o crawler atualizar a sua prox ref disponivel
                        last_sender.put(Crawler, ref + count);

                        multicast(chunk,url,page,Crawler,ref+count);
                    
                        pos = end;
                    }
                }
            } catch (Exception e) {
                //e.printStackTrace();
            }
        }

        for (String word : words){
            index.computeIfAbsent(word,  k -> new HashSet<>()).add(url);
        }
        System.out.printf("Foram enviados %d chunks\n", count);
        System.out.println("Index updated");
        urlPopularity.putIfAbsent(url, 0); // garante que a URL existe no mapa
        pageInfo.put(url, page);

        counter++;

        try{
            int quantidadeBarrels = gateway.getBarrelNum();
            if (counter >= quantidadeBarrels * 10) {
                counter %= quantidadeBarrels * 10;
                this.guardarBarrelInfoBinario(fileName);
                gateway.collback();
            }
        } 
        catch (Exception e) {
            System.out.println("Erro a guardar file de recuperação ou com o callback de estatisticas");
        }
       
        return ref + count;
    }

    /**
     * Atualiza a estrutura de links (backlinks) para uma página de origem.
     *
     * @param fromUrl URL de origem (a página que contém os links).
     * @param toUrls Conjunto de URLs de destino apontados por {@code fromUrl}.
     * @param Crawler Identificador do crawler que enviou os dados.
     * @param ref Número de referência sequencial (usado para filtrar duplicados).
     * @return Novo valor de referência sugerido (ref + números de chunks enviados).
     */
    @Override
    public synchronized int addLinks(String fromUrl, Set<String> toUrls, String Crawler, int ref){
        int count=0;
        if(last_sender.containsKey(Crawler) && (last_sender.get(Crawler)>=ref) && (ref!=-1)) return ref;
        
        

        if(ref!=-1){
            try {
                if( gateway.getBarrelNum() > 1){
                    System.out.println("Links a serem enviados");
                    List<String> lista_aux= new ArrayList<>(toUrls);
                    int tam= toUrls.size();
                    int chunkSize = 30;
                    int pos=0;
                    while (pos < tam) {
                        int end = Math.min(pos + chunkSize, tam);
                        Set<String> chunk = new HashSet<>(lista_aux.subList(pos, end));
                        
                        count++;//retornar esse valor na funcao para o crawler atualizar a sua prox ref disponivel
                        last_sender.put(Crawler, ref + count);

                        

                        multicast(fromUrl,chunk,Crawler,ref+count);
                        pos = end;
                    }
                }
            } catch (Exception e) {
                //e.printStackTrace();
            }
        }

        linkPages.put(fromUrl, toUrls);
        System.out.println("Links adicionados");
        for (String to : toUrls) {
            urlPopularity.put(to, urlPopularity.getOrDefault(to, 0) + 1);
        }

        return ref+count;
    }

    /**
     * Executa uma pesquisa por um conjunto de palavras e devolve a lista de {@code PageInfo}
     * correspondentes ordenadas por popularidade (número de links recebidos).
     *
     * <p>Algoritmo:
     * <ol>
     *   <li>Se a lista {@code words} for nula ou vazia, devolve uma lista vazia.</li>
     *   <li>Inicia o conjunto de resultados com as URLs associadas à primeira palavra.</li>
     *   <li>Faz interseção com os conjuntos das palavras subsequentes para obter apenas URLs que contenham todas as palavras.</li>
     *   <li>Ordena as URLs resultantes pelo valor em {@code urlPopularity} (decrescente).</li>
     *   <li>Mapeia as URLs ordenadas para seus {@code PageInfo} correspondentes e devolve a lista.</li>
     * </ol>
     *
     * @param words Lista de palavras a pesquisar; não deve ser {@code null}.
     * @return Lista de {@link PageInfo} correspondentes; lista vazia se nenhum resultado.
     * @throws RemoteException Repassa exceções remotas se ocorrerem problemas no RMI.
     * @apiNote Se {@code pageInfo} não contiver meta-informação para uma URL, {@code null} pode ser adicionado
     *           à lista resultante — idealmente, assegurar que {@code pageInfo} contém entradas para todas as URLs indexadas.
     */
    @Override
    public List<PageInfo> returnSearchResult(List<String> words) throws RemoteException {
       if (words == null || words.isEmpty()) {
            return new ArrayList<>();
       }

       Set<String> resultURLs = new HashSet<>(index.getOrDefault(words.get(0), new HashSet<>()));

       // Faz interseção com URLs das outras palavras
        for (int i = 1; i < words.size(); i++) {
            resultURLs.retainAll(index.getOrDefault(words.get(i), new HashSet<>()));
        }

        // Ordena pelos links recebidos (popularidade)
        List<String> sortedURLs = new ArrayList<>(resultURLs);
        sortedURLs.sort((a, b) -> {
            int popA = urlPopularity.getOrDefault(a, 0);
            int popB = urlPopularity.getOrDefault(b, 0);
            return popB - popA; // ordem decrescente
        });
        // Cria lista final de PageInfo
        List<PageInfo> finalResults = new ArrayList<>();
        for (String url : sortedURLs) {
            PageInfo info = pageInfo.get(url);
            finalResults.add(info);
        }

        return finalResults;
        
    }

    /**
     * Pesquisa inversa: devolve o conjunto de páginas que apontam para a URL especificada.
     *
     * @param url URL alvo para a pesquisa de backlinks.
     * @return Conjunto de URLs que têm ligação para {@code url}; conjunto vazio se não existirem.
     * @throws RemoteException Se ocorrer um erro remoto durante a operação.
     * @apiNote Esta operação itera sobre {@code linkPages} (complexidade O(N)); para grandes volumes recomenda-se
     *           manter um índice inverso auxiliar (destino -> set[fromUrls]) para obter O(1) por consulta.
     */
    @Override
    public Set<String> searchUrl(String url) throws RemoteException {
        Set<String> links = new HashSet<>();
        
        linkPages.forEach((fromUrl, toUrls) -> {
            if (toUrls.contains(url)) {
                links.add(fromUrl);
            }
        });
        return links;
    }
    
    /**
     * Envia um update contendo palavras e meta-informação da página para o grupo multicast.
     *
     * <p>O pacote contém as chaves: {@code "words","url","ref_num","Crawler","pages"} serializadas.
     * Cada receptor do grupo deve aplicar o update localmente (o handler deverá enviar um ACK).
     *
     * @param words   Conjunto de palavras indexadas.
     * @param url     URL da página.
     * @param page    Informação da página (PageInfo) — pode ser {@code null}.
     * @param Crawler Identificador do crawler emissor.
     * @param ref     Número de referência sequencial associado ao chunk.
     * @apiNote Configura {@code TimeToLive} e {@code NetworkInterface} para que o multicast funcione na LAN.
     */
    //Atualizar index
    public void multicast(Set<String> words, String url,PageInfo page, String Crawler, int ref) {
        String groupAddress = "230.0.0.0";
        int port = 4446;

        try (MulticastSocket socket = new MulticastSocket()) {
            InetAddress group = InetAddress.getByName(groupAddress);

            socket.setTimeToLive(2);
            socket.setNetworkInterface(NetworkInterface.getByInetAddress(InetAddress.getByName(endereço)));

            Map<String, Object> data = new HashMap<>();
            data.put("words", words);
            data.put("url", url);
            data.put("ref_num", ref);
            data.put("Crawler", Crawler);
            data.put("pages", page);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(data);
            oos.flush();
            byte[] buffer = baos.toByteArray();

            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, port);
            envio(packet, socket);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Envia o pacote multicast e espera por um ACK; em caso de timeout, reenvia.
     *
     * <p>Esta rotina tenta garantir que pelo menos um receptor reconheceu o pacote. Não existe
     * actualmente uma lógica de recolha de ACKs de todas as réplicas.
     *
     * @param packet Pacote multicast a enviar.
     * @param socket Socket multicast usado para envio.
     */
    public void envio(DatagramPacket packet, MulticastSocket socket){ //metodo auxiliar
        System.out.println("SENT\n\n");

        int counter=5; //5 tentativas de envio de envio
        
        while(counter>0){
            try {

                /*
                List<StorageBarrelInterface> aux= gateway.getBarrels();
                for (StorageBarrelInterface i:aux) {
                    try {
                        i.teste();
                    } catch (java.rmi.ConnectException | java.rmi.NoSuchObjectException e) {
                        gateway.removeBarrel(i);
                    } catch (Exception e) {
                        // NÃO remover, porque pode ser apenas overload
                        System.err.println("Barrel lento mas ainda vivo: " + e);
                    }   
                    } catch (Exception e){
                        System.out.println("Lento mas vivo");
                    }
                }*/

                if(gateway.getBarrelNum()==1) break;
                socket.send(packet);
                //Esperar ACK. Define um limite de espera para voltar a enviar XXXXXX
                socket.setSoTimeout(1000);
                byte[] ackBuffer = new byte[256];
                DatagramPacket ackPacket = new DatagramPacket(ackBuffer, ackBuffer.length);
                socket.receive(ackPacket);
                if (ackPacket.getLength() < 10) {
                    String msg = new String(ackPacket.getData(), 0, ackPacket.getLength(), StandardCharsets.UTF_8);
                
                    if (msg.equals("ACK")) {
                        System.out.println("Recebido ACK");
                        break;
                    }
                }
            
            } catch (SocketTimeoutException e) {
                counter--;
                System.out.println("TimeOut de socket de envio multicast no MainStorage");

            }catch(Exception e){
                e.printStackTrace();
            }
            
        }

    }

    /**
     * Envia um update de links (fromUrl -> toUrls) por multicast para replicação.
     *
     * @param fromUrl URL de origem.
     * @param toUrls  Conjunto de URLs de destino.
     * @param Crawler Identificador do crawler emissor.
     * @param ref     Número de referência para filtragem de duplicados.
     * @apiNote O TTL e NetworkInterface estão fixos; adaptar para o teu ambiente de rede.
     */
    //Atualizar relacoes de Urls
    public void multicast(String fromUrl, Set<String> toUrls, String Crawler, int ref) {
        System.out.println("LINKS SENT\n\n");
        String groupAddress = "230.0.0.0"; // endereço multicast (válido entre 224.0.0.0–239.255.255.255)
        int port = 4446;
    
        try (MulticastSocket socket = new MulticastSocket()) {  // Usa MulticastSocket!
            InetAddress group = InetAddress.getByName(groupAddress);
    
            //Configuração essencial do socket multicast
            socket.setTimeToLive(2); // Permite sair da máquina e alcançar a LAN
            socket.setNetworkInterface(NetworkInterface.getByInetAddress(
                InetAddress.getByName(endereço)  // substitui pelo IP da tua interface física
            ));
    
            // Cria o conteúdo a enviar
            Map<String, Object> data = new HashMap<>();
            data.put("fromUrl", fromUrl);
            data.put("toUrls", toUrls);
            data.put("ref_num", ref);
            data.put("Crawler", Crawler);
    
            // Serializa o objeto em bytes
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(data);
            oos.flush();
            byte[] buffer = baos.toByteArray();
    
            // Cria o pacote multicast
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, port);
    
            // Envia o pacote
            envio(packet, socket);
    
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Fornece um snapshot do índice local (apenas o índice invertido palavra->URLs).
     *
     * @return Mapa do índice invertido usado para sincronização inicial de novos barrels.
     * @throws RemoteException Se ocorrer um erro remoto.
     * @apiNote Este método devolve apenas {@code index}. Em versão melhorada, deveria devolver
     *           também {@code linkPages}, {@code urlPopularity} e {@code pageInfo}.
     */
    @Override
    public BarrelSnapshot reboot() throws RemoteException {
        BarrelSnapshot snap = new BarrelSnapshot();
        snap.index = this.index;
        snap.linkPages = this.linkPages;
        snap.urlPopularity = this.urlPopularity;
        snap.pageInfo = this.pageInfo;
        return snap;
    }

    @Override
    public synchronized void guardarBarrelInfoBinario(String nomeFicheiro) throws RemoteException{
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(nomeFicheiro))) {
            oos.writeObject(this.index);
            oos.writeObject(this.linkPages);
            oos.writeObject(this.urlPopularity);
            oos.writeObject(this.pageInfo);
            System.out.println("Index guardado em formato binário no ficheiro " + nomeFicheiro);
        }
        catch (IOException e) {
            System.err.println("Erro ao guardar o index binário: " + e.getMessage());
        }
    }

    @Override
    public synchronized void carregarBarrelInfoBinario(String nomeFicheiro) throws RemoteException{
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(nomeFicheiro))) {

            // Carregar os dados do Index
            Object obj = ois.readObject();
            if (obj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Set<String>> loadedIndex = (Map<String, Set<String>>) obj;
                this.index = loadedIndex;
                System.out.println("Index carregado de ficheiro binário: " + nomeFicheiro);
            }

            // Carregar os dados do Index
            Object obj2 = ois.readObject();
            if (obj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Set<String>> loadedLinkPages = (Map<String, Set<String>>) obj2;
                this.linkPages = loadedLinkPages;
                System.out.println("LinkPages carregado de ficheiro binário: " + nomeFicheiro);
            }

            // Carregar os dados do Index
            Object obj3 = ois.readObject();
            if (obj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Integer> loadedUrlPopularity = (Map<String, Integer>) obj3;
                this.urlPopularity = loadedUrlPopularity;
                System.out.println("UrlPopularity carregado de ficheiro binário: " + nomeFicheiro);
            }

            // Carregar os dados do Index
            Object obj4 = ois.readObject();
            if (obj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, PageInfo> loadedPageInfo = (Map<String, PageInfo>) obj4;
                this.pageInfo = loadedPageInfo;
                System.out.println("PageInfo carregado de ficheiro binário: " + nomeFicheiro);
            }

        } 
        catch (FileNotFoundException e) {
            System.out.println("Ficheiro não encontrado. Nenhum index carregado.");
        } 
        catch (EOFException e) {
            System.out.println("Ficheiro vazio ou incompleto.");
        } 
        catch (InvalidClassException e) {
            System.err.println("Classe incompatível com os dados serializados. Pode ter mudado a estrutura.");
        } 
        catch (IOException | ClassNotFoundException e) {
            System.err.println("Erro ao carregar o index binário: " + e.getMessage());
        }
    }

    /**
     * Método principal que arrancará o StorageBarrel:
     * <ul>
     *   <li>Configura a propriedade RMI hostname;</li>
     *   <li>Cria a instância do barrel;</li>
     *   <li>Regista o barrel na Gateway e faz bind no RMI registry;</li>
     *   <li>Inicia o thread {@link MulticastHandler} que processará updates vindos por multicast.</li>
     * </ul>
     *
     * @param args argumentos de linha de comando (não utilizados).
     */
    public static void main(String[] args) {

        //Setup

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
            LocateRegistry.createRegistry(1099);
            System.out.println("RMI registry iniciado na porta 1099");
            System.setProperty("java.rmi.server.hostname", endereço);
            MainStorageBarrel barrel = new MainStorageBarrel();


            nome= gateway.subscribe(barrel);
            
            System.out.printf("Eu sou %s\n", nome);

            Naming.rebind(nome, barrel);
           


            MulticastHandler t= new MulticastHandler(barrel);
            t.start();
            }
            catch (Exception e) {
                e.printStackTrace();
            }
    }

    @Override
    public void teste() throws RemoteException {
        return;
    }

    @Override
    public int returnSize() throws RemoteException {
        return index.keySet().size();
    }
}

