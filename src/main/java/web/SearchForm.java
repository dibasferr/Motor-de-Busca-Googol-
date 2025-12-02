package web;

import java.io.Serializable;

public class SearchForm implements Serializable {
    private Long id;
    private String word;
    private String indexHackerNews;
    private boolean varTypeError;

    public SearchForm() {}

    public SearchForm(Long id, String word, boolean varTypeError,String indexHAckerNews ) {
        this.id = id;
        this.word = word;
        this.varTypeError = varTypeError;
        this.indexHackerNews= indexHAckerNews;
    }

    public Long getId() {
        return this.id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getWord() {
        return this.word;
    }

    public void setWord(String word) {
        this.word = word;
    }

    public boolean getVarTypeError() {
        return this.varTypeError;
    }

    public void setVarTypeError(boolean varTypeError) {
        this.varTypeError = varTypeError;
    }

    public String getIndexHackerNews() {
        return this.indexHackerNews;
    }

    public void setIndexHackerNews(String indexHAckerNews) {
        this.indexHackerNews = indexHAckerNews;
    }


}