package search;

import java.io.FileInputStream;
import java.io.IOException;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
/**
 * Implementação do cliente para o sistema distribuído.
 * 
 * <p>Esta classe comunica com o {@code Gateway_interface} remoto via RMI,
 * permitindo ao utilizador interagir com o sistema para indexar URLs,
 * pesquisar palavras, consultar páginas relacionadas e visualizar estatísticas.
 * 
 * <p>O cliente também subscreve atualizações periódicas de estatísticas
 * enviadas pelo gateway.
 * 
 * @author Pedro Ferreira, Lorando Ca
 */
public class ClientImp extends UnicastRemoteObject implements ClientInterface {
        /** Lista das 10 palavras mais pesquisadas. */
        static List<String> topTen;

        /** Lista com os nomes dos Storage Barrels registados. */
        static List<String> BarrelsNames;

        /** Nome atribuído a este cliente pelo Gateway. */
        static String nome;

        /** Duração média das pesquisas, atualizada pelo Gateway. */
        static long searchDur = 0;
    
    
        /**
         * Construtor da classe ClientImp.
         * 
         * @throws RemoteException se ocorrer um erro de comunicação RMI.
         */
        ClientImp() throws RemoteException {super();}
         
        /**
         * Atualiza as estatísticas enviadas periodicamente pelo Gateway.
         *
         * @param topTenUpdate Lista das 10 palavras mais pesquisadas.
         * @param BarrelsNamesUpdate Lista com os nomes dos Barrels ativos.
         * @param searchDurUpdate Tempo médio de pesquisa atualizado.
         */
    
        @Override
        public void updateStatistics(List<String> topTenUpdate, List<String> BarrelsNamesUpdate, long searchDurUpdate){//falta verificar barrels ativos e o tempo medio de pesquisa

            topTen= topTenUpdate;
            BarrelsNames= BarrelsNamesUpdate;
            searchDur=searchDurUpdate;

        }
    
    //Interface implemetnation end
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //METHODS
    
        /**
         * Subscreve o cliente no Gateway remoto, permitindo receber callbacks de estatísticas.
         *
         * @param gateway_stub Referência remota para o objeto Gateway.
         * @return Nome registado deste cliente no Gateway.
         */

        public static String subscribe(GatewayInterface gateway_stub){
            String res= null;
            try{
                ClientImp client= new ClientImp();
                res= gateway_stub.subscribe(client);
            }catch(Exception e){
                System.out.println("Exception in main: " + e); 
            }
    
            return res;
        } //Criar uma referencia e enviar a gateway, para fazer callback de estatisticas periodicamente
        //Uma thread por cliente a subscrever
    
        /**
         * Envia um novo URL para indexação no Gateway.
         *
         * @param url Endereço URL a ser indexado.
         * @param gateway_stub Referência remota para o objeto Gateway.
         */

        public static void indexNewURL(String url, GatewayInterface gateway_stub){
            try {
    
                gateway_stub.addURL(url);
            } catch (Exception e) {
    
                e.printStackTrace();
            }
            
        }
    
        /**
         * Método principal do cliente.
         * 
         * <p>Permite:
         * <ul>
         *   <li>Indexar novos URLs;</li>
         *   <li>Pesquisar palavras-chave;</li>
         *   <li>Consultar páginas que referenciam um URL;</li>
         *   <li>Visualizar estatísticas do sistema.</li>
         * </ul>
         *
         * <p>O cliente comunica com o Gateway através de RMI e exibe os resultados no terminal.
         *
         * @param args Argumentos da linha de comando (não utilizados).
         */
        public static void main(String[] args) {

            String endereço=null;
            String endereço_gateway=null;
            String porta=null;
            Properties config = new Properties();

            try (FileInputStream input = new FileInputStream("config.properties")) {
                // Carrega o arquivo .properties
                config.load(input);
                // Lê as propriedades
                endereço = config.getProperty("rmi.host1");//endereços para a gateway é do host2
                endereço_gateway= config.getProperty("rmi.host2");
                porta = config.getProperty("rmi.port1");
            }catch(IOException e) {
                System.out.println("Erro ao carregar arquivo de configuração: " + e.getMessage());
            }


            System.setProperty("java.rmi.server.hostname", endereço);
            GatewayInterface gateway_stub;
            Scanner scanner = new Scanner(System.in);
            //gateway interface setup
            try {
                gateway_stub = (GatewayInterface)Naming.lookup( String.format("rmi://%s:%s/Gateway",endereço_gateway,porta));
               
                nome=subscribe(gateway_stub);


            while(true){
                System.out.print("1. Indexar um URL\n2. Pesquisar uma palavra\n3.Consultar lista de páginas com ligação para uma página específica\n"+
                    "4.Estatisticas\n");
                
                int option=0;
                String line = scanner.nextLine(); // lê toda a linha
                try {
                    option = Integer.parseInt(line.trim()); // remove espaços e converte
                } catch (NumberFormatException e) {
                    System.out.println("Opção inválida. Digite um número entre 1 e 4.");
                }
                


                switch (option) {
                case 1:
                    System.out.println("Escreva a sua URL");
                    String url = scanner.nextLine(); //Possivel verificacao do formato para confirmar que é uma URL
                    indexNewURL(url,gateway_stub);
                    
                    break;
                
                    case 2:
                    System.out.println("Escreva uma palavra");
                    String wrd = scanner.nextLine(); //Possivel verificacao do formato para confirmar que é uma URL
                    try {
                        List<PageInfo> result = gateway_stub.pesquisa_word(wrd);
                        System.out.printf("%d\n\n", result.size());
                        System.out.printf("=== Resultados da pesquisa ===\n\n");
                        int counter = 1;
                        for(PageInfo i : result){
                            System.out.printf("Título: %s\n", i.getTitulo());
                            System.out.printf("URL: %s\n", i.getUrl());
                            System.out.printf("Citação: %s\n", i.getCitacao());
                            counter++;
                            if(counter%10==0){
                                System.out.println("Quer continuar vendo resultados da pesquisa? [S/N]");
                                String confirmacao = scanner.nextLine(); // Confirmação apra proceder com os resultados da pesquisa
                                if(confirmacao.equals("S")){
                                    continue;
                                }
                                else{
                                    break;
                                }
                            }
                        }   
                            

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    
                    break;

                    case 3:
                        System.out.println("Escreva a sua URL de referência\n");
                        url = scanner.nextLine(); 
                        try {
                            List<String> res= gateway_stub.pesquisa_URL(url);
                            System.out.println(res);
                            System.out.println("\n");
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    
                        break;

                    case 4:
                        try {
                            System.out.println("--------------------STATISTICS-----------------------------");
                            System.out.println(topTen);
                            System.out.println("\n");
                            System.out.println(BarrelsNames);
                            System.out.println("\n");
                            System.out.println(searchDur);
                            System.out.println("\n");
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        break;
                default:
                    break;
            }

        }
        } catch (Exception e) {
            e.printStackTrace();
        }
        //end 
        
    }
}