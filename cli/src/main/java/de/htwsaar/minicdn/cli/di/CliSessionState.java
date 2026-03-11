package de.htwsaar.minicdn.cli.di;

import de.htwsaar.minicdn.cli.service.system.SystemInitService;

/**
 * Hält lokale Ressourcen fest, die in der aktuellen CLI-Session gestartet wurden.
 *
 * <p>Die Klasse speichert die Prozess-IDs der durch die CLI gestarteten Dienste,
 * sodass diese später gezielt beendet oder der aktuelle Zustand als Snapshot
 * abgefragt werden kann.</p>
 *
 * <p>Alle Zugriffe sind synchronisiert, damit der Zustand auch bei parallelem
 * Zugriff konsistent bleibt.</p>
 */
public final class CliSessionState {
    /** Prozess-ID des Origin-Servers, sofern in dieser Session gestartet. */
    private Long originPid;

    /** Prozess-ID des Edge-Servers, sofern in dieser Session gestartet. */
    private Long edgePid;

    /** Prozess-ID des Routers, sofern in dieser Session gestartet. */
    private Long routerPid;

    /** ID des aktuell eingeloggten Users. */
    private Long loggedInUserId;

    /** Name des aktuell eingeloggten Users. */
    private String loggedInUsername;

    /** Rolle des aktuell eingeloggten Users (0=USER, 1=ADMIN). */
    private Integer loggedInRole;

    /**
     * Übernimmt alle gestarteten Dienste aus dem Initialisierungsergebnis in den Session-Zustand.
     *
     * <p>Nur Dienste mit dem Zustand {@code STARTED} werden gespeichert.</p>
     *
     * @param result das Ergebnis einer Systeminitialisierung mit den einzelnen Dienstzuständen
     */
    public synchronized void remember(SystemInitService.InitResult result) {
        remember(result.origin());
        remember(result.edge());
        remember(result.router());
    }

    /**
     * Erstellt einen unveränderlichen Snapshot des aktuell gespeicherten Zustands.
     *
     * @return ein Snapshot mit den momentan bekannten Prozess-IDs
     */
    public synchronized ShutdownSnapshot snapshot() {
        return new ShutdownSnapshot(originPid, edgePid, routerPid);
    }

    /**
     * Entfernt die gespeicherte Prozess-ID des Origin-Dienstes.
     */
    public synchronized void clearOriginPid() {
        originPid = null;
    }

    /**
     * Entfernt die gespeicherte Prozess-ID des Edge-Dienstes.
     */
    public synchronized void clearEdgePid() {
        edgePid = null;
    }

    /**
     * Entfernt die gespeicherte Prozess-ID des Router-Dienstes.
     */
    public synchronized void clearRouterPid() {
        routerPid = null;
    }

    /**
     * Prüft, ob in der aktuellen Session mindestens ein verwalteter Dienst gespeichert ist.
     *
     * @return {@code true}, wenn mindestens eine Prozess-ID vorhanden ist, sonst {@code false}
     */
    public synchronized boolean hasManagedResources() {
        return originPid != null || edgePid != null || routerPid != null;
    }

    /**
     * Speichert den aktuell eingeloggten User in der Session.
     *
     * @param userId technische User-ID
     * @param username Benutzername
     * @param role Rollen-ID (0=USER, 1=ADMIN)
     */
    public synchronized void rememberLoggedInUser(long userId, String username, int role) {
        this.loggedInUserId = userId;
        this.loggedInUsername = username;
        this.loggedInRole = role;
    }

    /**
     * Entfernt den Login-Zustand der Session.
     */
    public synchronized void clearLogin() {
        this.loggedInUserId = null;
        this.loggedInUsername = null;
        this.loggedInRole = null;
    }

    /**
     * @return {@code true}, wenn ein User eingeloggt ist
     */
    public synchronized boolean isLoggedIn() {
        return loggedInUserId != null;
    }

    /**
     * @return {@code true}, wenn ein Admin-User eingeloggt ist
     */
    public synchronized boolean isAdminLoggedIn() {
        return loggedInUserId != null && Integer.valueOf(1).equals(loggedInRole);
    }

    public synchronized Long loggedInUserId() {
        return loggedInUserId;
    }

    public synchronized String loggedInUsername() {
        return loggedInUsername;
    }

    public synchronized Integer loggedInRole() {
        return loggedInRole;
    }

    /**
     * Speichert die Prozess-ID eines einzelnen Dienstes, falls dieser erfolgreich gestartet wurde.
     *
     * <p>Nur Zustände ungleich {@code null} mit {@code state() == "STARTED"} werden berücksichtigt.
     * Die Zuordnung erfolgt anhand des Dienstnamens.</p>
     *
     * @param status der Status eines einzelnen Dienstes
     */
    private void remember(SystemInitService.ServiceStatus status) {
        if (status == null || !"STARTED".equals(status.state())) {
            return;
        }

        switch (status.name()) {
            case "origin" -> originPid = status.pid();
            case "edge" -> edgePid = status.pid();
            case "router" -> routerPid = status.pid();
            default -> {
                // no-op
            }
        }
    }

    /**
     * Unveränderlicher Snapshot der aktuell verwalteten Prozess-IDs.
     *
     * @param originPid Prozess-ID des Origin-Dienstes oder {@code null}
     * @param edgePid Prozess-ID des Edge-Dienstes oder {@code null}
     * @param routerPid Prozess-ID des Router-Dienstes oder {@code null}
     */
    public record ShutdownSnapshot(Long originPid, Long edgePid, Long routerPid) {}
}
