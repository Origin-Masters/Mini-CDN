package de.htwsaar.minicdn.cli.adapter.out.transport;

import de.htwsaar.minicdn.cli.domain.model.DownloadResult;
import java.nio.file.Path;

/**
 * Adapterinterne Abstraktion der konkreten Transportbindung.
 *
 * <p>Dieses Interface gehört bewusst zur Outbound-Infrastruktur der CLI und
 * nicht zur fachlichen Schicht. Es kapselt technische Request-/Response-
 * Übersetzungen für konkrete Bindungen wie HTTP, ohne fachliche Ports mit
 * Transportdetails zu belasten.</p>
 *
 * <p>Eine alternative Bindung, zum Beispiel gRPC, kann später dieses
 * Interface implementieren, während die fachlichen Ports der CLI unverändert
 * bleiben.</p>
 */
public interface TransportClient {

    /**
     * Führt einen textbasierten technischen Request aus.
     *
     * @param request adapterinterne technische Request-Beschreibung
     * @return normierte technische Antwort mit Status, Headern und optionalem Body
     */
    TransportResponse send(TransportRequest request);

    /**
     * Lädt den Response-Body binär in eine Datei herunter.
     *
     * @param request adapterinterne technische Request-Beschreibung
     * @param targetFile Zieldatei
     * @param overwrite legt fest, ob eine vorhandene Datei ersetzt werden darf
     * @return normiertes Download-Ergebnis
     */
    DownloadResult download(TransportRequest request, Path targetFile, boolean overwrite);
}
