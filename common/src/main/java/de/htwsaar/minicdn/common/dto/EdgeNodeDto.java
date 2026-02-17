package de.htwsaar.minicdn.common.dto;

/**
 * Used to send edge node information
 */
public class EdgeNodeDto {

    private String url;
    private String region;
    private boolean online;

    public EdgeNodeDto() {}

    public EdgeNodeDto(String url, String region, boolean online) {
        this.url = url;
        this.region = region;
        this.online = online;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }
}
