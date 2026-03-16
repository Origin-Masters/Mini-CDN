package de.htwsaar.minicdn.cli.application.admin;

import de.htwsaar.minicdn.cli.domain.model.CallResult;
import de.htwsaar.minicdn.cli.domain.port.AdminOperations;
import java.net.URI;
import java.util.Objects;

/**
 * Fachlicher Service für regionenweite Cache-Invalidierung über den Router.
 *
 * <p>Die transporttechnische Umsetzung liegt vollständig im Outbound-Adapter. Dieser
 * Service validiert nur Eingaben und delegiert den fachlichen Use-Case.</p>
 */
public final class AdminCacheService {

    private final AdminOperations adminOperations;
    private final String adminToken;

    /**
     * Erzeugt den Service.
     *
     * @param adminOperations fachlicher Port für administrative Remote-Aufrufe
     * @param adminToken Admin-Token für geschützte Operationen
     */
    public AdminCacheService(AdminOperations adminOperations, String adminToken) {
        this.adminOperations = Objects.requireNonNull(adminOperations, "adminOperations");
        this.adminToken = requireText(adminToken, "adminToken");
    }

    /**
     * Invalidiert genau eine gecachte Datei in einer Region.
     *
     * @param routerBaseUrl Basis-URL des Routers
     * @param region Zielregion
     * @param path relativer Dateipfad
     * @return normiertes Ergebnis des Use-Cases
     */
    public CallResult invalidateFile(URI routerBaseUrl, String region, String path) {
        return adminOperations.invalidateFile(routerBaseUrl, adminToken, region, path);
    }

    /**
     * Invalidiert alle Cache-Einträge eines Präfixes in einer Region.
     *
     * @param routerBaseUrl Basis-URL des Routers
     * @param region Zielregion
     * @param prefix relatives Dateipräfix
     * @return normiertes Ergebnis des Use-Cases
     */
    public CallResult invalidatePrefix(URI routerBaseUrl, String region, String prefix) {
        return adminOperations.invalidatePrefix(routerBaseUrl, adminToken, region, prefix);
    }

    /**
     * Leert den Cache einer Region vollständig.
     *
     * @param routerBaseUrl Basis-URL des Routers
     * @param region Zielregion
     * @return normiertes Ergebnis des Use-Cases
     */
    public CallResult clearRegion(URI routerBaseUrl, String region) {
        return adminOperations.clearRegion(routerBaseUrl, adminToken, region);
    }

    /**
     * Validiert einen Pflichttext und liefert die getrimmte Form zurück.
     *
     * @param value Eingabewert
     * @param fieldName Feldname für Fehlermeldungen
     * @return getrimmter Pflichttext
     */
    private static String requireText(String value, String fieldName) {
        String trimmed = Objects.toString(value, "").trim();
        if (trimmed.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return trimmed;
    }
}
