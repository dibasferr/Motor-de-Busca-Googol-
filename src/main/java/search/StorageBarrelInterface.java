package search;

import java.util.List;
import java.util.Set;
import java.rmi.Remote;
import java.rmi.RemoteException;

public interface StorageBarrelInterface extends Remote { 

    public int addWordToStructure(Set<String> word, String url,PageInfo page, String Crawler, int ref) throws RemoteException;
    public List<PageInfo> returnSearchResult(List<String> queryWords) throws RemoteException;
    public int addLinks(String fromUrl, Set<String> toUrls, String Crawler, int ref) throws RemoteException;
    public Set<String> searchUrl(String url) throws RemoteException;
    public BarrelSnapshot reboot() throws RemoteException;
    public void teste() throws RemoteException;
    public void guardarBarrelInfoBinario(String nomeFicheiro) throws RemoteException;
    public void carregarBarrelInfoBinario(String nomeFicheiro) throws RemoteException;

}
