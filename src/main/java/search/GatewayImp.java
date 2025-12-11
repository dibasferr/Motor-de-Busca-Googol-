package search;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.io.FileInputStream;
import java.io.IOException;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;

/**
 * Implementação da Gateway do sistema distribuído "Googol".
 *
 * <p>A Gateway funciona como ponto central de coordenação entre:
 * <ul>
 *     <li><b>Clientes</b>: executam pesquisas e recebem estatísticas via callback;</li>
 *     <li><b>Crawlers/Downloaders</b>: obtêm URLs a processar e submetem novos URLs descobertos;</li>
 *     <li><b>Storage Barrels</b>: mantêm o índice invertido e executam pesquisas distribuídas.</li>
 *     <li><b>Interface Web</b>: recebe métricas em tempo real para exibição num painel.</li>
 * </ul>
 *
 * <p><b>Principais responsabilidades:</b>
 * <ul>
 *     <li>Gerir a fila global de URLs a serem processados;</li>
 *     <li>Evitar duplicação via conjunto de URLs visitados;</li>
 *     <li>Manter e monitorizar Storage Barrels activos (com deteção de falhas);</li>
 *     <li>Realizar balanceamento de carga via round-robin;</li>
 *     <li>Executar pesquisas distribuídas com failover automático;</li>
 *     <li>Manter estatísticas globais (pesquisas mais frequentes, tempo médio, etc.);</li>
 *     <li>Emitir callbacks para clientes subscritos e interface Web.</li>
 * </ul>
 *
 * <p><b>Concorrência:</b> Métodos que manipulam {@code URL_queue}, {@code visited}
 * e outros recursos não-thread-safe utilizam {@code synchronized} para garantir
 * exclusão mútua em ambientes com múltiplos crawlers ou barrels concorrentes.
 *
 * @author Lorando Ca, Pedro Ferreira
 * @see StorageBarrelInterface
 * @see ClientInterface
 * @see WebInterface
 */

public class GatewayImp extends UnicastRemoteObject implements GatewayInterface{
    /** Referência para o servidor Web. */
    WebInterface web = null;

    /**
     * Fila global de URLs a serem processados pelos Crawlers.
     *
     * <p><b>Invariantes:</b>
     * <ul>
     *     <li>Não contém URLs duplicados;</li>
     *     <li>URLs já visitados não são reinseridos;</li>
     *     <li>A sincronização é garantida pelos métodos que a manipulam.</li>
     * </ul>
     */

    Queue<String> URL_queue= new LinkedList<>();

    /**
     * Conjunto de URLs já visitados.
     * <p>Evita reprocessamento e loop infinito na exploração do grafo Web.
     */
    Set <String> visited= new HashSet<>();

    /**
     * Lista de clientes subscritos para callback de estatísticas.
     * <p>
     * Cada elemento é uma referência remota a {@link Client_interface}.
     */
    List<ClientInterface> clients= new ArrayList<>();//do callback to all the stored references

    /**
     * Mapa de Storage Barrels activos: Nome atribuído → Stub RMI.
     * <p>Implementado com {@link ConcurrentHashMap} para segurança e performance.
     */
    private final ConcurrentHashMap<String, StorageBarrelInterface> barrelsMap = new ConcurrentHashMap<>();

    /** Soma acumulada do tempo de execução de todas as pesquisas. */
    long somaTempoExecucao=0;

    /** Contador de pesquisas executadas. */
    int countPesquisas=0;

    /** Índice usado em {@link #getBarrel()}. Identifica último barrel utilizado em uma pesquisa*/
    int prevBarrel= 0;

    /**
     * Contador simples para gerar identificadores de cliente (Client1, Client2, ...).
     */
    int client_counter=1;

    /**
     * Contador simples para gerar identificadores de barrel (Barrel1, Barrel2, ...).
     */
    int barrel_counter=1;

    /**
     * Map que conta frequência de pesquisas (termo -> frequência).
     * <p>
     * Utilizado para compor o top10 de termos pesquisados.
     */
    Map<String, Integer> searchFreq= new HashMap<>();

    /**
     * Construtor padrão.
     *
     * @throws RemoteException caso ocorra erro ao exportar objecto RMI.
     */
    public GatewayImp() throws RemoteException {super();
    }

    /**
     * Retorna o próximo URL da fila de trabalho para ser processado por um Crawler.
     *
     * @return Próximo URL a processar, ou {@code null} se a fila estiver vazia.
     * @apiNote O método é {@code synchronized} para garantir exclusão mútua entre múltiplos crawlers concorrentes.
     */
    @Override
    public synchronized String getURL(){
        if(URL_queue.isEmpty()) return null;
        return URL_queue.poll();
    }

    /**
     * Adiciona vários URLs à fila de processamento.
     * <p>
     * Cada URL é encaminhado para {@link #addURL(String)} que faz a verificação de duplicados.
     *
     * @param new_URLs Lista de URLs a adicionar.
     * @return {@code null} (tipo Void por compatibilidade com RMI).
     * @apiNote O método é {@code synchronized} para proteger a fila contra condições de corrida.
     */
    @Override
    public synchronized Void addURLs(List<String> new_URLs) {

        for (int i=0; i< new_URLs.size(); i++){
            this.addURL(new_URLs.get(i));
            
        }
        return null;
    }

    /**
     * Adiciona um novo URL à fila global, se ainda não estiver enfileirado nem visitado.
     *
     * @param new_URL URL a adicionar.
     * @return {@code null} (tipo Void por compatibilidade com RMI).
     * @apiNote O método é {@code synchronized} para evitar condições de corrida. A verificação
     *           {@code URL_queue.contains(new_URL)} é O(n); em grandes escalas recomenda-se uma
     *           estrutura auxiliar (p.ex. {@code Set}) para verificação em O(1).
     */
    @Override
    public synchronized Void addURL(String new_URL) {
        if(URL_queue.contains(new_URL) || visited.contains(new_URL)) return null;
        URL_queue.add(new_URL);
        return null;
    }

    /**
     * Executa uma pesquisa distribuída por palavras.
     *
     * <p>Fluxo:
     * <ol>
     *     <li>Divide a query em palavras;</li>
     *     <li>Faz load balancing para escolher barrel a utilizar;</li>
     *     <li>Em caso de falha, remove o barrel e tenta outro (failover);</li>
     *     <li>Actualiza métricas locais;</li>
     *     <li>Dispara callback para clientes e interface Web.</li>
     * </ol>
     *
     * @param word string com um ou mais termos de pesquisa.
     * @return lista ordenada de {@link PageInfo}.
     * @throws RemoteException propagado caso ocorra erro RMI.
     */
    @Override
    public List<PageInfo> pesquisa_word(String word) throws RemoteException{

        long inicio = System.currentTimeMillis();

        List<PageInfo> result=null;
        String[] words= word.split(" ");
        List <String> wordss= new ArrayList<>(Arrays.asList(words));

        while(true){
            StorageBarrelInterface barrel = getBarrel();
            if (barrel == null) {
                System.out.println("Nenhum barrel ativo disponível.");
                return Collections.emptyList();
            }

            try {
                result= barrel.returnSearchResult(wordss);
                System.out.println(result);
                break;

            } catch (java.rmi.ConnectException e) {

                System.out.println("Barrel desconectado. Tentando outro...");
                removeBarrel(barrel);
                continue;

            } catch (java.rmi.RemoteException e) {
                System.out.println("Erro remoto ao contactar Barrel.");
                e.printStackTrace();
                continue;

            }catch (Exception e) {
                e.printStackTrace();
            }
            break;
        }

        long fim = System.currentTimeMillis();
        somaTempoExecucao+= fim-inicio;
        countPesquisas++;

        searchFreq.put(word, searchFreq.getOrDefault(word, 0) + 1);

        //Atualizacao apos alteracao
        //Ordenar pelo valor (de menor para maior) ChatGPT
        searchFreq = searchFreq.entrySet()
            .stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()) // .reversed() para maior→menor
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (e1, e2) -> e1,  // merge function
                LinkedHashMap::new // mantém a ordem do stream
            ));
        
        this.collback();
        System.out.println("Informações de callback adicionados");
        return result; //vai retornar a lista de palavras
    }


    /**
     * Envia estatísticas actualizadas para:
     * <ul>
     *     <li>Clientes subscritos;</li>
     *     <li>Interface Web.</li>
     * </ul>
     *
     * <p>Remove automaticamente clientes com falha de conexão.</p>
     */
    @Override
    public void collback() {
        List<String> listaPesq = new ArrayList<>(searchFreq.keySet());

        Iterator<ClientInterface> it = clients.iterator(); //Para q a alteracao da lista nao afete a iteracao sobre ela

        double tempoMedio=0;
        if(countPesquisas!=0){
            tempoMedio=somaTempoExecucao/countPesquisas;
        }

        if(web != null){
            
            try {

                updateWeb(new ArrayList<>(listaPesq.subList(0, Math.min(10, listaPesq.size()))), 
                getBarrelsNames(), tempoMedio);
 
                System.out.println("fiz o update\n\n");//Tem q considerar o cliente web
                
            } catch (Exception e) {
                System.out.println("Erro a comunicar com o web Server");
                e.printStackTrace();
            }
            
        }

        while (it.hasNext()) {
            ClientInterface client = it.next();
            try {
                ((ClientInterface)client).updateStatistics(new ArrayList<>(listaPesq.subList(0, Math.min(10, listaPesq.size()))), 
                                                            getBarrelsNames(), (long)tempoMedio);

                

            } catch (java.rmi.ConnectException e) {
                System.out.println("Cliente desconectado. Removendo da lista...");
                it.remove(); // o cliente caiu
            } catch (java.rmi.RemoteException e) {
                System.out.println("Erro remoto ao contactar cliente. Removendo...");
                e.printStackTrace();
                it.remove();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
  
    }


    /**
     * Atualiza o servidor Web com:
     * <ul>
     *     <li>Top 10 pesquisas;</li>
     *     <li>Lista de barrels activos;</li>
     *     <li>Tamanhos dos indexes;</li>
     *     <li>Tempo médio de execução.</li>
     * </ul>
     *
     * @param topTen lista das top 10 pesquisas.
     * @param barrels nomes dos barrels activos.
     * @param Time tempo médio.
     */
    public void updateWeb(List<String> topTen, List<String> barrels, double Time){

        Map<String, List<String>> var = new HashMap<>();
        var.put("barrels", barrels);

        List<String> sizes= new ArrayList<>();
        List<StorageBarrelInterface> BarrelSet = new ArrayList<>(barrelsMap.values());
        
        for (StorageBarrelInterface b : BarrelSet) {
            try {
                sizes.add( String.valueOf(b.returnSize()) );
                System.out.println("O tamanho do barrel é " + b.returnSize());
            } catch (Exception e) {
                System.out.println("Erro a obter o tamanho do barrel");
            }        
        }

        var.put("sizes", sizes);
        var.put("topTen", topTen);
        List<String> exectime= new ArrayList<>();
        exectime.add(String.valueOf(Time));
        var.put("execTime", exectime);
        try {
            web.update(var);
        } catch (Exception e) {
            System.out.println("Erro a fazer update no webServer");
            e.printStackTrace();
        }
        
    }


    /**
     * Retorna um resumo de estatísticas do sistema (API prevista).
     *
     * @return {@code String} com dados de estatísticas.
     */
    @Override
    public String statistics(){
        List<String> chaves = new ArrayList<>(searchFreq.keySet());
        
        List<String> pesquisasComuns= new ArrayList<>();
        for(int i=0; i<chaves.size();i++){
            pesquisasComuns.add(chaves.get(i));
        }
        return null;
    }
    
    /**
     * Pesquisa inversa: devolve as páginas que têm ligação para a URL especificada.
     *
     * @param url URL alvo para a pesquisa de backlinks.
     * @return Lista de URLs que apontam para {@code url}.
     */
    @Override
    public List<String> pesquisa_URL(String url){
        
        try {
            // Load balancing usando getBarrel()
            StorageBarrelInterface barrel = getBarrel();
            if (barrel == null) return Collections.emptyList();
            Set<String> res = barrel.searchUrl(url);
            return new ArrayList<>(res);
       //end
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
        
    }

    /**
     * Regista um cliente para receber callbacks de estatísticas.
     *
     * @param c Referência remota para o cliente.
     * @return Identificador atribuído ao cliente (ex.: "Client1").
     */
    @Override
    public String subscribe(ClientInterface c){ //altere: retornar nome de cliente
        clients.add(c);
        return String.format("Client%d", client_counter++);    
    }

    /**
     * Regista um Storage Barrel no sistema.
     *
     * @param b Referência remota para um {@link StorageBarrelInterface}.
     * @return Identificador atribuído ao barrel (ex.: "Barrel1").
     * @apiNote Após o registo, seria desejável forçar uma sincronização (snapshot) do novo barrel com um barrel activo para garantir consistência.
     */
    @Override
    public String subscribe(StorageBarrelInterface b){
        String name = String.format("Barrel%d", barrel_counter++);
        barrelsMap.put(name, b);
        System.out.println("Adicionado com sucesso: " + name);
        this.collback();
        // não devolvemos snapshot aqui — barrels novos podem pedir snapshot eles próprios
        return name;
    }


    /**
     * Regista a interface Web e devolve imediatamente o estado atual
     * (barrels, top 10, tamanhos, tempo médio).
     *
     * @param c interface Web.
     * @return mapa contendo estatísticas actuais.
     */
    @Override
    public Map<String, List<String>> subscribe(WebInterface c) throws RemoteException {
        
        List<String> listaPesq = new ArrayList<>(searchFreq.keySet());
        web= c;
        Map<String, List<String>> var = new HashMap<>();
        var.put("barrels", getBarrelsNames());
        var.put("topTen", new ArrayList<>(listaPesq.subList(0, Math.min(10, listaPesq.size()))));
        List<String> exectime= new ArrayList<>();
        if(countPesquisas==0){
            exectime.add(String.valueOf(0));
        }else {
            exectime.add(String.valueOf(somaTempoExecucao/countPesquisas));
        }
        List<String> sizes= new ArrayList<>();
        List<StorageBarrelInterface> BarrelSet = new ArrayList<>(barrelsMap.values());
        
        for (StorageBarrelInterface b : BarrelSet) {
            try {
                sizes.add( String.valueOf(b.returnSize()) );
                System.out.println("O tamanho do barrel é " + b.returnSize());
            } catch (Exception e) {
                System.out.println("Erro a obter o tamanho do barrel");
            }        
        }

        var.put("sizes", sizes);
        
        var.put("execTime", exectime);
        return var;
    }


    /**
     * Devolve o número de Storage Barrels actualmente registados na Gateway.
     *
     * @return Número de barrels activos.
     */
    @Override
    public int getBarrelNum(){
        return barrelsMap.size();
    }

    /**
     * Selecciona um Storage Barrel activo com estratégia round-robin.
     *
     * @return barrel activo ou {@code null} se nenhum existir.
     */
    @Override
    public StorageBarrelInterface getBarrel(){ //fixando o numero de barrels à quantidade de barrels na lista
        List<StorageBarrelInterface> values = new ArrayList<>(barrelsMap.values());
        if (values.isEmpty()) return null;

        if(getBarrelNum()==1){
            return values.get(0);
        }

        //Load balance
        StorageBarrelInterface resultado= values.get((prevBarrel+1)%values.size());
        prevBarrel= (prevBarrel+1)%values.size();

        return resultado ;
    }


    /**
     * Lista os nomes de todos os barrels activos, removendo aqueles que falharem durante o teste.
     *
     * @return lista de nomes de barrels activos.
     */
    @Override
    public synchronized List<String> getBarrelsNames() throws RemoteException {
        Iterator<Map.Entry<String, StorageBarrelInterface>> it = barrelsMap.entrySet().iterator();
        while(it.hasNext()) {
            Map.Entry<String, StorageBarrelInterface> entry = it.next();
            try {
                entry.getValue().teste(); // método RMI leve só pra testar se tá vivo
            } catch(Exception e) {
                it.remove();
                System.out.println("Removed dead barrel: " + entry.getKey());
            }
        }

        return new ArrayList<>(barrelsMap.keySet());
    }


    /**
     * Remove explicitamente um barrel do sistema.
     *
     * @param c stub remoto do barrel a remover.
     */
    @Override
    public void removeBarrel(StorageBarrelInterface c) throws RemoteException {
        String keyToRemove = null;
        for (Map.Entry<String, StorageBarrelInterface> e : barrelsMap.entrySet()) {
            if (e.getValue().equals(c)) {
                keyToRemove = e.getKey();
                break;
            }
        }
        if (keyToRemove != null) {
            barrelsMap.remove(keyToRemove);
            System.out.println("Removed barrel : " + keyToRemove);
        }
        System.out.println("aquiiii");
        this.collback();
    }

    /**
     * @return lista dos barrels actualmente registados.
     */
    @Override
    public List<StorageBarrelInterface> getBarrels() throws RemoteException {
        return new ArrayList<>(barrelsMap.values());
    }

    /**
     * Marca um URL como visitado.
     *
     * @param url URL a marcar.
     */
    @Override
    public void addVisited(String url) throws RemoteException {
        this.visited.add(url);
    }

//End o interface implementation
//=======================================================================================================
    /**
     * Método principal que inicia o Registry RMI e faz bind da Gateway.
     */
    public static void main(String[] args) {
        String endereço=null;
        String porta=null;
        Properties config = new Properties();

        try (FileInputStream input = new FileInputStream("config.properties")) {
            // Carrega o arquivo .properties
            config.load(input);
            // Lê as propriedades
            endereço = config.getProperty("rmi.host2");
            porta= config.getProperty("rmi.port2");
        }catch (IOException e) {
            System.out.println("Erro ao carregar arquivo de configuração: " + e.getMessage());
        }

        try {
            System.setProperty("java.rmi.server.hostname", endereço);
            LocateRegistry.createRegistry(1099); // cria o registry na porta 1099
            GatewayImp server = new GatewayImp();
            Naming.rebind(String.format("rmi://%s:%s/Gateway", endereço,porta), server);
            //java -Djava.rmi.server.hostname=192.168.176.1 MeuServidor: definir um ip para um server

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}