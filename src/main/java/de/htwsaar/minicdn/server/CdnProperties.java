package de.htwsaar.minicdn.server;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "cdn")
public class CdnProperties {

    /**
     * Map of region -> edge base url (e.g. "eu-west" -> "http://localhost:8081")
     */
    private Map<String, String> edges = new HashMap<>();

    public Map<String, String> getEdges() {
        return edges;
    }

    public void setEdges(Map<String, String> edges) {
        this.edges = edges;
    }
}
