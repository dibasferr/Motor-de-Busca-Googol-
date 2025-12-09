package web;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class HackerNewsService {

    private final RestTemplate rest = new RestTemplate();

    // retorna apenas as URLs externas das topstories que contem os termos
    public List<String> getFilteredTopStories(String searchTerms) {

        List<String> urls = new ArrayList<>();

        try {
            // 1. pegar IDs
            Integer[] ids = rest.getForObject(
                "https://hacker-news.firebaseio.com/v0/topstories.json",
                Integer[].class
            );

            if (ids == null) return urls;

            // normalizar termos do user
            String loweredTerms = searchTerms.toLowerCase();

            // 2. iterar sobre TODOS os IDs
            for (int id : ids) {

                // endpoint de cada item
                Map item = rest.getForObject(
                    "https://hacker-news.firebaseio.com/v0/item/" + id + ".json",
                    Map.class
                );

                if (item == null) continue;
                if (!"story".equals(item.get("type"))) continue;
                if (item.get("title") == null) continue;

                String title = item.get("title").toString().toLowerCase();

                // verificar se contém o termo buscado
                if (!title.contains(loweredTerms)) continue;

                // tem URL externa?
                if (item.get("url") == null) continue;

                String externalUrl = item.get("url").toString();

                urls.add(externalUrl);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return urls;
    }
}