package de.htwsaar.minicdn.cli.application.admin;

import de.htwsaar.minicdn.cli.domain.model.CallResult;
import de.htwsaar.minicdn.cli.domain.port.AdminOperations;
import java.net.URI;
import java.util.Objects;

/**
 * Fachlicher Service für einen einfachen Erreichbarkeitscheck.
 */
public final class AdminPingService {

    private final AdminOperations adminOperations;

    public AdminPingService(AdminOperations adminOperations) {
        this.adminOperations = Objects.requireNonNull(adminOperations, "adminOperations");
    }

    /**
     * Führt den Health-Check gegen den relativen Zielpfad aus.
     */
    public CallResult ping(URI baseUrl, String relativePath) {
        return adminOperations.ping(baseUrl, relativePath);
    }
}
