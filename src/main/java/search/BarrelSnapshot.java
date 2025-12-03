package search;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;

public class BarrelSnapshot implements Serializable {
    public Map<String, Set<String>> index;
    public Map<String, Set<String>> linkPages;
    public Map<String, Integer> urlPopularity;
    public Map<String, PageInfo> pageInfo;
    public Map<String, Integer> last_sender;
}
