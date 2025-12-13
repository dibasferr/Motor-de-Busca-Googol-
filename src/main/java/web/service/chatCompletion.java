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

	private final Ollama ollama;
    private static final String MODEL = "tinyllama";

// 1. Mova a inicialização e o pullModel para o construtor
    public chatCompletion() {
        try {
            this.ollama = new Ollama("http://localhost:11434/");
            this.ollama.setRequestTimeoutSeconds(120);

            // Tentar descarregar o modelo APENAS uma vez na inicialização da aplicação
            System.out.println("A verificar e a descarregar o modelo Ollama: " + MODEL);
            this.ollama.pullModel(MODEL);
            System.out.println("Modelo Ollama pronto.");
            
        } catch (Exception e) {
            throw new RuntimeException("Erro ao iniciar a conexão Ollama.", e);
        }
    }

    @Override
    public String Completion(String wordToLook){
        try{
            // O objeto ollama já está inicializado
            
            OllamaChatRequest builder = OllamaChatRequest.builder().withModel(MODEL);

            OllamaChatRequest requestModel =
                builder.withMessage(OllamaChatMessageRole.USER, wordToLook)
                        .build();

            // Faça a chamada API
            OllamaChatResult chatResult = this.ollama.chat(requestModel, null);

            return chatResult.getResponseModel().getMessage().getResponse();

        }catch(Exception e){
            System.out.println("Erro ao comunicar com o Ollama server durante a chat completion.");
            e.printStackTrace();
            return null;
        }
    }
    
}