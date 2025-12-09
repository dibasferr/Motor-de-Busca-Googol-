package web.service;

import org.springframework.stereotype.Service;

import io.github.ollama4j.Ollama;
import io.github.ollama4j.models.chat.OllamaChatMessageRole;
import io.github.ollama4j.models.chat.OllamaChatRequest;
import io.github.ollama4j.models.chat.OllamaChatResult;

@Service
public class chatCompletion implements chatCompletionInterface {

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
