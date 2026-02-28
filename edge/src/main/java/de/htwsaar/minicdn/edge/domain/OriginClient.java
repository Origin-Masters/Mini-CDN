package de.htwsaar.minicdn.edge.domain;

/**
 * Fachlicher Port zum Lesen von Inhalten und Metadaten vom Origin.
 *
 * <p>Der Port ist bewusst transport-agnostisch modelliert:
 * weder HTTP-Statuscodes noch HEAD/GET-Semantik tauchen hier auf.</p>
 */
public interface OriginClient {

    /**
     * Lädt den vollständigen Dateiinhalt vom Origin.
     *
     * @param path relativer Dateipfad
     * @return fachlicher Inhalt der Datei
     */
    OriginContent fetchFile(String path);

    /**
     * Lädt nur die Metadaten einer Datei vom Origin.
     *
     * @param path relativer Dateipfad
     * @return fachliche Metadaten der Datei
     */
    OriginMetadata fetchMetadata(String path);
}
