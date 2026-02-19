package de.htwsaar.minicdn.common.dto;

public class RegisterNodeDto {

    /**
     * USed to register edge nodes.
     */
    private String region;

    private String url;

    public RegisterNodeDto() {}

    public RegisterNodeDto(String region, String url) {
        this.region = region;
        this.url = url;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
