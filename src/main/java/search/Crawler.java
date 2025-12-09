package search;

import java.io.*;
import java.rmi.Naming;
import java.util.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;


/**
 * Classe {@code Crawler} responsável por realizar o processo de rastreamento (crawl)
 * de páginas web, coletando URLs, títulos e trechos de texto para indexação.
 * 
 * <p>O crawler se comunica com um servidor remoto via RMI, utilizando interfaces
 * {@code Gateway_interface} e {@code StorageBarrelInterface} para coordenar o
 * armazenamento e o envio de novas URLs a serem processadas.</p>
 *
 * <p>O fluxo básico é:
 * <ol>
 *   <li>Conectar ao gateway remoto via RMI.</li>
 *   <li>Obter uma URL inicial para processar.</li>
 *   <li>Extrair texto e links da página utilizando a biblioteca Jsoup.</li>
 *   <li>Enviar os dados para o "barrel" de armazenamento remoto.</li>
 *   <li>Adicionar os novos links à fila de URLs no gateway.</li>
 *   <li>Repetir o processo até não haver mais URLs disponíveis.</li>
 * </ol>
 * </p>
 * 
 * <p>O código também trata erros de conexão e tenta reconectar ao servidor RMI
 * quando necessário.</p>
 * 
 * @author Pedro Ferreira, Lorando Ca
 * @version 1.0
 */
public class Crawler {
    /**
     * Método principal responsável pela execução do crawler.
     *
     * @param args Argumentos de linha de comando.
     *             <ul>
     *                 <li>args[0] - URL inicial a ser rastreada</li>
     *                 <li>args[1] - Nome do crawler (identificador)</li>
     *             </ul>
     */
    public static void main(String args[]) {
         //Setup
        String endereço=null;
        String porta=null;
        Properties config = new Properties();

        try (FileInputStream input = new FileInputStream("config.properties")) {
            // Carrega o arquivo .properties
            config.load(input);
            // Lê as propriedades
            endereço = config.getProperty("rmi.host2");//endereços para a gateway é do host2
            porta = config.getProperty("rmi.port2");
        }catch(IOException e) {
            System.out.println("Erro ao carregar arquivo de configuração: " + e.getMessage());
        }
        

        try {
            GatewayInterface stub = (GatewayInterface)Naming.lookup(String.format("rmi://%s:%s/Gateway",endereço,porta));
            //Setup end

            String url = args[0];
            String crawler_name= args[1];
            System.out.printf("Eu sou %s\n", crawler_name);
            int ref=0;
            try {
                StorageBarrelInterface stub_barrel= stub.getBarrel(); //Todos os crawlers comunicam com esse barrel: ERRADO
                        //Aqui tem que ser um storage aleatorio ou o contrario do ultimo utilizado
                while(url!=null){
                    try{
                        stub_barrel= stub.getBarrel();

                        System.out.printf("%s\n", url);
                        
                        Document doc = Jsoup.connect(url)
                                .userAgent("Mozilla/5.0 (compatible; MeuCrawler/1.0; +http://meusite.com)")
                                .header("From", "seuemail@dominio.com") // opcional, indica contato
                                .timeout(10_000) // timeout em ms
                                .get();
                        StringTokenizer tokens = new StringTokenizer(doc.text());
                        int countTokens = 0;

                        Set< String> words_indexed= new HashSet<>();

                        while (tokens.hasMoreElements() && countTokens++ < 100){
                            words_indexed.add(tokens.nextToken().toLowerCase());  
                        }

                        // Armazenar para cada página sua url, seu título e citação
                        String titulo = doc.title();
                        String texto = doc.body().text();
                        String citacao = texto.length() > 150 ? texto.substring(0, 150) + "..." : texto;

                        //Fazer uma thread para essa adicionar isso 
                        ref=stub_barrel.addWordToStructure(words_indexed, url, new PageInfo(url, titulo, citacao),crawler_name, ref);
                        ref++;
                        System.out.printf("A referencia atual é %d\n",ref);
                        Elements links = doc.select("a[href]");
                        Set<String> Refs = new HashSet<>();

                        for (Element link : links){
                            Refs.add(link.attr("abs:href"));
                            
                        }

                        ref=stub_barrel.addLinks(url, Refs,crawler_name, ref);
                        ref++;
                        
                        //adicionar url indexado a queue de visitados. Garantindo que so apos ser visitado sera considerado efetivamente visitado
                        stub.addVisited(url);
                        
                        stub.addURLs(new ArrayList<>(Refs)); //Inserir elementos na url queue
                        //mandar esses links ao barrel tmb. O barrel vai receber uma lista um lista de links e o link aonde eles sairam 

                        url=stub.getURL();
                        System.out.println("reach out");

                    } catch (java.rmi.ConnectException e) {
                        stub.removeBarrel(stub_barrel);
                        stub_barrel= stub.getBarrel();
                        System.out.println("Erro: servidor RMI não acessível (" + e.getMessage() + ")");
                    } catch (java.net.ConnectException e) {
                        stub_barrel= stub.getBarrel();
                        System.out.println("Erro de conexão TCP: " + e.getMessage());
                    }catch (Exception e) {
                        System.out.println("Pagina nao tratavel");
                        url=stub.getURL();
                        stub.addVisited(url); //Se for nao tratavel, adiciona aos visitados para nao visitar novamente
                        e.printStackTrace();
                    }
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            System.out.println("null");
            e.printStackTrace();
        }
    }
}