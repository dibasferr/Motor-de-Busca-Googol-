package search;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.rmi.Remote;
import java.rmi.RemoteException;

public interface GatewayInterface extends Remote {

    public String getURL() throws RemoteException;
    
    public Void addURLs(List<String> new_URLs) throws RemoteException;
    
    public Void addURL(String new_URL) throws RemoteException;
    
    public List<String> pesquisa_URL(String url) throws RemoteException; //pesquisa todas as URL's relacionadas a "url"
   
    public List<PageInfo> pesquisa_word(String word) throws RemoteException;

    public String statistics() throws RemoteException;

    public String subscribe(ClientInterface c) throws RemoteException; 

    public String subscribe(StorageBarrelInterface c) throws RemoteException;

    public Map<String, List<String>> subscribe(WebInterface c) throws RemoteException;
    
    public  void collback() throws RemoteException; 

    public  Integer getBarrelNum() throws RemoteException; 

    public StorageBarrelInterface getBarrel() throws RemoteException;

    public List<String> getBarrelsNames() throws RemoteException;

    public List<StorageBarrelInterface> getBarrels() throws RemoteException;
    
    public void removeBarrel(StorageBarrelInterface c) throws RemoteException;
}
