package de.htwsaar.minicdn.cli.transport;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Objects;

/**
 * Fabrik für konkrete {@link TransportClient}-Implementierungen.
 *
 * <p>Aktuell wird ein HTTP-basierter Adapter erzeugt.
 */
public final class TransportClientFactory {

    private TransportClientFactory() {}

    /**
     * Erstellt einen HTTP-basierten {@link TransportClient}.
     *
     * @param connectTimeout Verbindungs-Timeout des zugrunde liegenden HTTP-Clients
     * @param followRedirects legt fest, ob Redirects mit {@code Redirect.NORMAL} gefolgt werden
     * @return konfigurierte TransportClient-Instanz auf Basis von {@link HttpTransportClient}
     * @throws NullPointerException wenn {@code connectTimeout} {@code null} ist
     */
    public static TransportClient http(Duration connectTimeout, boolean followRedirects) {
        Objects.requireNonNull(connectTimeout, "connectTimeout");

        HttpClient.Builder builder = HttpClient.newBuilder().connectTimeout(connectTimeout);
        if (followRedirects) {
            builder.followRedirects(HttpClient.Redirect.NORMAL);
        }

        return new HttpTransportClient(builder.build());
    }
}
