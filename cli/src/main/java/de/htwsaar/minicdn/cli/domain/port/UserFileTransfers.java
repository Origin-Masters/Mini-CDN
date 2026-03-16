package de.htwsaar.minicdn.cli.domain.port;

import de.htwsaar.minicdn.cli.domain.model.DownloadResult;
import de.htwsaar.minicdn.cli.domain.model.RemoteFileProbe;
import de.htwsaar.minicdn.cli.domain.model.ResolvedFileRoute;
import java.net.URI;
import java.nio.file.Path;

/**
 * Fachlicher Port für Dateiübertragungen der CLI.
 *
 * <p>Segmentierung und Download-Orchestrierung arbeiten gegen diese fachliche
 * Sicht. Technische Details der konkreten Transportanbindung sind
 * ausschließlich Sache des Adapters.</p>
 */
public interface UserFileTransfers {

    /**
     * Lädt eine Datei direkt über den Router herunter.
     *
     * @param routerBaseUrl Basis-URL des Routers
     * @param remotePath relativer Dateipfad
     * @param region Client-Region
     * @param clientId optionale Client-ID
     * @param userId optionale technische Benutzer-ID
     * @param out lokale Zieldatei
     * @param overwrite legt fest, ob eine bestehende Datei überschrieben werden darf
     * @return normiertes Download-Ergebnis
     */
    DownloadResult downloadViaRouter(
            URI routerBaseUrl,
            String remotePath,
            String region,
            String clientId,
            Long userId,
            Path out,
            boolean overwrite);

    /**
     * Löst die fachliche Dateiroute über den Router auf.
     *
     * @param routerBaseUrl Basis-URL des Routers
     * @param remotePath relativer Dateipfad
     * @param region Client-Region
     * @param clientId optionale Client-ID
     * @param userId optionale technische Benutzer-ID
     * @return aufgelöstes Routen-Ziel
     */
    ResolvedFileRoute resolveRoute(URI routerBaseUrl, String remotePath, String region, String clientId, Long userId);

    /**
     * Liest Metadaten einer entfernten Datei für segmentierte Downloads.
     *
     * @param route aufgelöstes Datei-Ziel
     * @return fachliche Dateimetadaten
     */
    RemoteFileProbe probeRemoteFile(ResolvedFileRoute route);

    /**
     * Lädt ein Byte-Segment einer Datei herunter.
     *
     * @param route aufgelöstes Datei-Ziel
     * @param startInclusive erstes Byte des Segments
     * @param endInclusive letztes Byte des Segments
     * @param region Client-Region
     * @param clientId optionale Client-ID
     * @param userId optionale technische Benutzer-ID
     * @param out lokale Zieldatei für das Segment
     * @return normiertes Download-Ergebnis
     */
    DownloadResult downloadSegment(
            ResolvedFileRoute route,
            long startInclusive,
            long endInclusive,
            String region,
            String clientId,
            Long userId,
            Path out);
}
