package web;


import search.PageInfo;

import search.GatewayInterface;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.*;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import org.springframework.ui.Model;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;

import web.service.chatCompletion;

@Controller
public class GreetingController {
	public Map<String, List<String>> statistics ;

	private final chatCompletion chat;
	
	
	@Autowired
	WebInterfaceImp conector;

	@Resource(name = "applicationScopedGatewayGenerator")
	private GatewayInterface gateway_stub;

	private HackerNewsService hackerNewsService;

	public GreetingController(chatCompletion chat, HackerNewsService hackerNewsService){
		this.chat= chat;
		this.hackerNewsService = hackerNewsService;
	}


    @PostConstruct
    public void setupSubscription() {
				
        // Obtenha o bean do controller (injete-o ou crie-o, se não for um bean
        String endereco= null;
        try {
            // A ação de subscrição é executada uma vez
			try (InputStream input = getClass().getClassLoader().getResourceAsStream("config.properties")) {
			Properties props = new Properties();

			// Carrega o arquivo .properties
			props.load(input);

			// Lê as propriedades
			endereco= props.getProperty("rmi.host1");
		
			}catch(IOException e) {
				System.out.println("Erro ao carregar arquivo de configuração: " + e.getMessage());
			}

			System.setProperty("java.rmi.server.hostname", endereco);
            conector.statistics= gateway_stub.subscribe(conector);
			System.out.println("Controller subscrito com sucesso");
        } catch (Exception e) {
            System.err.println("Erro a fazer subscrição Controller -> Gateway: " + e.getMessage());
        }
    }

	
	


	public boolean isValidURL(String wordToIndex) {
		try {
			URI uri = new URI(wordToIndex);
			String scheme = uri.getScheme();
			// Verifica se tem esquema (http/https)
			if (scheme == null) return false;
			return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
		} catch (URISyntaxException e) {
			return false;
		}
	}


    @GetMapping("/")
    public String redirect(Model model) {
        model.addAttribute("initialStatus",conector.statistics);
		return "index" ;
    }


	@GetMapping("/goToSearch")
	public String goToSearch(Model model, @RequestParam(defaultValue = "false") boolean wichOne) {
		model.addAttribute("searchForm", new SearchForm());
		if (wichOne) return "indexURL";
		else return "Search";
	}


	//Se for introduzida uma URL, faz-se a pesquisa das URLs ligadas a essa
	@GetMapping("/Search")
	public String Search(@ModelAttribute SearchForm searchForm, Model model) {
		
		String wordToLook= searchForm.getWord();
		
		if(isValidURL(wordToLook)){ //a pesquisa de urls ligado a uma url também é feita pelo endpoint /Search

			try{

				List<String> result = gateway_stub.pesquisa_URL(wordToLook);
				model.addAttribute("resultado", result);
				
				

			}catch(Exception e){
				System.out.println("erro ao comunicar com a gateway. URl nao pesquisado");
				e.printStackTrace();
			}
			return "URLSearchResults.html";
		}
		else{
			try {
				
				// Supondo que Completion retorna String
				Callable<String> tarefa = () -> chat.Completion(wordToLook);

				// Cria um executor para gerenciar threads
				ExecutorService executor = Executors.newSingleThreadExecutor();

				// Submete a tarefa e obtém um Future
				Future<String> futureResult = executor.submit(tarefa);

				// Executa outras coisas enquanto a thread trabalha
	
				List<PageInfo> result = gateway_stub.pesquisa_word(wordToLook);



				model.addAttribute("resultado", result);
				// Para pegar o resultado da thread (bloqueia até terminar)
				
				try {
					
					String completionResult = futureResult.get(); 
					System.out.println(completionResult);
					model.addAttribute("resultadoCompletion", completionResult);
				} catch (InterruptedException e) {
					System.out.println("Problema com a thread de execução do Ollama");
				}catch (ExecutionException e) {
					System.out.println("Problema com a thread de execução do Ollama");
				}

				// Encerra o executor
				executor.shutdown();
						
					
				String confirmation= searchForm.getIndexHackerNews();
				confirmation = confirmation.replace(",", "");

				if(confirmation.equals("yes")){
					//Codigo para index URLs de top Stories de HackerNews que contenham os termos da variavel "wordToLook"
					List<String> hnUrls = hackerNewsService.getFilteredTopStories(wordToLook);
					
					int count = 0;

					for (String url : hnUrls) {
						System.out.println("Indexando HN URL: " + url);
						try {
							gateway_stub.addURL(url);
							count++;
						} catch (Exception e) {
							System.out.println("Erro ao indexar: " + url);
							e.printStackTrace();
						}
					}

					model.addAttribute("hnIndexMessage", 
						"Foram indexadas " + count + " top stories do Hacker News que continham '" + wordToLook + "'."
					);

					System.out.println("hnIndexMessage enviado: " + model.getAttribute("hnIndexMessage"));

				}

			} catch (Exception e) {
				System.out.println("Erro a comunicar com a gateway. Frase nao pesquisada");
				e.printStackTrace();
			}
		}

		return "SearchResults";
	}


	@GetMapping("/indexUrl")
	public String indexUrl(@ModelAttribute SearchForm searchForm, Model model) {

		String wordToIndex= searchForm.getWord();

		if(isValidURL(wordToIndex)){
			try {
			gateway_stub.addURL(wordToIndex);
			System.out.println("URL indexed");
			} catch (Exception e) {
				System.out.println("Erro a comunicar com a gateway");
			}
			return redirect(model);
		}
		else{
			searchForm.setVarTypeError(true); //passa-se o ultimo parametro para gerar
																												//um allert na view
			return "indexURL";
		}

	}
}