package de.htwsaar.minicdn.cli.domain.port;

import de.htwsaar.minicdn.cli.domain.model.DownloadResult;
import de.htwsaar.minicdn.cli.domain.model.TransportRequest;
import de.htwsaar.minicdn.cli.domain.model.TransportResponse;
import java.nio.file.Path;

/**
 * Abstraktion der konkreten Transportschicht.
 *
 * <p>Fachliche Services sprechen nur noch mit diesem Interface und kennen keine
 * HTTP-spezifischen Klassen wie HttpClient/HttpRequest/HttpResponse mehr.
 *
 * <p>Eine alternative Bindung (z. B. gRPC) kann später dieses Interface
 * implementieren, ohne die fachliche Logik zu ändern.
 */
public interface TransportClient {

    /**
     * Führt einen textbasierten Request aus.
     *
     * @param request transportneutrale Request-Beschreibung
     * @return normierte Antwort mit Status, Headern und optionalem Body
     */
    TransportResponse send(TransportRequest request);

    /**
     * Lädt den Response-Body binär in eine Datei herunter.
     *
     * @param request transportneutrale Request-Beschreibung
     * @param targetFile Zieldatei
     * @param overwrite legt fest, ob eine vorhandene Datei ersetzt werden darf
     * @return normiertes Download-Ergebnis
     */
    DownloadResult download(TransportRequest request, Path targetFile, boolean overwrite);
}
