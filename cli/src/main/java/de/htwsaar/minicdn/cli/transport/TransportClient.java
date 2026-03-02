package de.htwsaar.minicdn.cli.transport;

import de.htwsaar.minicdn.cli.dto.DownloadResult;
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
     * Führt einen Request aus und liefert Status, Header und optionalen Text-Body zurück.
     */
    TransportResponse send(TransportRequest request);

    /**
     * Lädt den Response-Body binär in eine Datei herunter.
     */
    DownloadResult download(TransportRequest request, Path targetFile, boolean overwrite);
}
