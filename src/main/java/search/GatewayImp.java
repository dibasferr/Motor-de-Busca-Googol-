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
 * <p>A Gateway atua como ponto de entrada e coordenação entre:
 * <ul>
 *   <li>Clientes: que efetuam pesquisas e recebem estatísticas via callback;</li>
 *   <li>Crawlers/Downloaders: que solicitam URLs a processar e submetem novos URLs;</li>
 *   <li>Storage Barrels: que armazenam o índice invertido e respondem a pesquisas.</li>
 * </ul>
 *
 * <p>Responsabilidades principais:
 * <ul>
 *   <li>Gerir a fila global de URLs a indexar (`URL_queue`) e o conjunto de URLs já visitados (`visited`);</li>
 *   <li>Distribuir carga de pesquisa entre Storage Barrels (round-robin);</li>
 *   <li>Executar callbacks para clientes subscritos com as pesquisas mais frequentes (top10);</li>
 *   <li>Efetuar failover na pesquisa caso um Barrel falhe, removendo-o da lista de barrels activos.</li>
 * </ul>
 *
 * <p><b>Notas sobre concorrência:</b> métodos que acedem a `URL_queue` e `visited` são {@code synchronized}
 * para proteger a integridade da fila em cenários com múltiplos crawlers concorrentes.
 * 
 * @author Pedro Ferreira, Lorando Ca
 * @see StorageBarrelInterface
 * @see Client_interface
 */

public class GatewayImp extends UnicastRemoteObject implements GatewayInterface{
    WebInterface web = null;

    /**
     * Fila de URLs a processar por Crawlers.
     * <p>
     * Invariants:
     * - URLs duplicados são evitados através de verificação em {@link #addURL(String)}.
     */

    Queue<String> URL_queue= new LinkedList<>();

    /**
     * Conjunto de URLs já visitados (para evitar re-indexação).
     */
    Set <String> visited= new HashSet<>();

    /**
     * Lista de clientes subscritos para callback de estatísticas.
     * <p>
     * Cada elemento é uma referência remota a {@link Client_interface}.
     */
    List<ClientInterface> clients= new ArrayList<>();//do callback to all the stored references

    /** Map de barrels registados: nome -> stub (thread-safe) */
    private final ConcurrentHashMap<String, StorageBarrelInterface> barrelsMap = new ConcurrentHashMap<>();

    long somaTempoExecucao=0;
    int countPesquisas=0;

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
     * Nome do cliente (uso experimental/no momento não usado de forma consistente).
     */
    String client_name= new String();

    /**
     * Map que conta frequência de pesquisas (termo -> frequência).
     * <p>
     * Utilizado para compor o top10 de termos pesquisados.
     */
    Map<String, Integer> searchFreq= new HashMap<>();

    /**
     * Construtor padrão: inicializa a Gateway e exporta o objecto RMI.
     *
     * @throws RemoteException Se ocorrer um erro durante a exportação RMI.
     */
    public GatewayImp() throws RemoteException {super();
    }

    /**
     * Retorna o próximo URL da fila de trabalho para ser processado por um Crawler.
     * O URL é marcado como visitado antes de ser devolvido, evitando reprocessamento.
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
     * Executa uma pesquisa por um conjunto de palavras (termo livre possivelmente com espaços).
     * <p>
     * O método:
     * <ol>
     *   <li>Separa a query em palavras;</li>
     *   <li>Selecciona um Storage Barrel activo (round-robin) para delegar a pesquisa;</li>
     *   <li>Em caso de falha de conexão com o barrel seleccionado, remove-o da lista e tenta outro;</li>
     *   <li>Atualiza as métricas de pesquisa (searchFreq) e dispara o callback para os clientes subscritos.</li>
     * </ol>
     *
     * @param word String com a query (um ou mais termos).
     * @return Lista de URLs ordenada por relevância (implementação actual: ordena por popularidade/urlPopularity).
     * @apiNote A selecção round-robin é feita incrementando {@code prev_barrel} e tomando módulo por {@code barrels.size()}.
     * @throws RemoteException Exceções remotas propagadas durante a chamada ao barrel.
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
     * Dispara callbacks para todos os clientes subscritos enviando as pesquisas mais frequentes.
     * <p>
     * Para evitar a modificação concorrente da lista durante iteração, usa-se um {@code Iterator}
     * e remove-se qualquer cliente que gere uma {@code ConnectException} ou {@code RemoteException}.
     *
     * @apiNote O método assume que {@link #searchFreq} está ordenado por frequência (ordenação aplicada em {@link #pesquisa_word(String)}).
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
     * Selecciona um StorageBarrel activo para uso por Crawlers.
     * <p>
     * Implementação actual: selecciona aleatoriamente um barrel se existirem mais do que 1; retorna {@code null} se nenhum existir.
     *
     * @return Referência remota para um {@link StorageBarrelInterface} ou {@code null} se a lista estiver vazia.
     * @apiNote <b>BUG conhecido:</b> a expressão {@code r.nextInt(1)} devolve sempre 0 — deve ser substituída por {@code r.nextInt(barrels.size())}
     *           ou idealmente por um mecanismo thread-safe round-robin (usar {@code AtomicInteger}).
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

//End o interface implementation
//=======================================================================================================
    /**
     * Método principal que inicia o registry RMI e faz o bind da Gateway no RMI registry.
     * <p>
     *
     * @param args Argumentos de linha de comando (não utilizados).
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

    @Override
    public List<StorageBarrelInterface> getBarrels() throws RemoteException {
        return new ArrayList<>(barrelsMap.values());
    }

    @Override
    public void addVisited(String url) throws RemoteException {
        this.visited.add(url);
    }

}