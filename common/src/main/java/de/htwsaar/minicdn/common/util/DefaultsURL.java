package de.htwsaar.minicdn.common.util;

/**
 * Zentrale CLI-Defaults für Compile-Time-Konstanten.
 */
public final class DefaultsURL {

    /**
     * Standard-URL für den Origin-Service.
     */
    public static final String ORIGIN_URL = "http://localhost:8080";

    /**
     * Standard-URL für den Edge-Service.
     */
    public static final String EDGE_URL = "http://localhost:8081";

    /**
     * Standard-URL für den Router-Service.
     */
    public static final String ROUTER_URL = "http://localhost:8082";

    private DefaultsURL() {
        throw new AssertionError("Utility class");
    }
}
