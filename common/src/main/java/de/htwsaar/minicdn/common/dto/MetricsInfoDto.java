package de.htwsaar.minicdn.common.dto;

import java.util.Map;

public class MetricsInfoDto {

    private long totalRequests;
    private long routingErrors;
    // Map: Region -> Anzahl Requests
    private Map<String, Long> requestsByRegion;
    // Optional: Weitere Felder wie "activeClients"

    public MetricsInfoDto() {}

    public MetricsInfoDto(long totalRequests, long routingErrors, Map<String, Long> requestsByRegion) {
        this.totalRequests = totalRequests;
        this.routingErrors = routingErrors;
        this.requestsByRegion = requestsByRegion;
    }

    public long getTotalRequests() {
        return totalRequests;
    }

    public void setTotalRequests(long totalRequests) {
        this.totalRequests = totalRequests;
    }

    public long getRoutingErrors() {
        return routingErrors;
    }

    public void setRoutingErrors(long routingErrors) {
        this.routingErrors = routingErrors;
    }

    public Map<String, Long> getRequestsByRegion() {
        return requestsByRegion;
    }

    public void setRequestsByRegion(Map<String, Long> requestsByRegion) {
        this.requestsByRegion = requestsByRegion;
    }
}
