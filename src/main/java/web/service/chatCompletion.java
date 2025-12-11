package web.service;

import org.springframework.stereotype.Service;

import io.github.ollama4j.Ollama;
import io.github.ollama4j.models.chat.OllamaChatMessageRole;
import io.github.ollama4j.models.chat.OllamaChatRequest;
import io.github.ollama4j.models.chat.OllamaChatResult;

@Service
public class chatCompletion implements chatCompletionInterface {

	/**
     * Gera uma resposta de chat usando o servidor local do Ollama.
     *
     * <p>O método envia o texto recebido como mensagem de utilizador para o modelo
     * configurado (por padrão {@code mistral:7b}), aguarda o processamento e retorna
     * a resposta produzida pelo modelo.</p>
     *
     * @param wordToLook texto inserido pelo utilizador
     * @return resposta gerada pelo modelo; null caso ocorra algum erro
	 * @author Lorando Ca, Pedro Ferreira
     */
    @Override
    //Para a implementacao do chat completion, foram usadas as informações disponíveis nesta pagina oficial : https://ollama4j.github.io/ollama4j/apis-generate/chat
	public String Completion(String wordToLook){
		try{
			Ollama ollama = new Ollama("http://localhost:11434/");
			ollama.setRequestTimeoutSeconds(120);
			
			String model = "mistral:7b";
			ollama.pullModel(model);

			OllamaChatRequest builder = OllamaChatRequest.builder().withModel(model);

			// create first user question
			OllamaChatRequest requestModel =
					builder.withMessage(OllamaChatMessageRole.USER, wordToLook)
 
							.build();

			// start conversation with model
			OllamaChatResult chatResult = ollama.chat(requestModel, null);

			return  chatResult.getResponseModel().getMessage().getResponse();

		}catch(Exception e){
			System.out.println("Erro ao comunicar com o Ollama server");
			e.printStackTrace();
		}
        return null;
    }
    
}