package de.htwsaar.minicdn.cli.service.system;

import de.htwsaar.minicdn.cli.di.CliSessionState;
import java.time.Duration;
import java.util.Objects;

/**
 * Beendet lokale Ressourcen, die in der aktuellen CLI-Session gestartet wurden.
 *
 * <p>Der Service liest die in {@link CliSessionState} gespeicherten Prozess-IDs der gestarteten
 * Dienste aus und versucht, diese der Reihe nach kontrolliert zu beenden. Falls ein Prozess nicht
 * rechtzeitig auf ein normales Beenden reagiert, wird ein erzwungenes Beenden versucht.
 */
public final class SystemShutdownService {

    /**
     * Maximale Wartezeit pro Beendigungsversuch, bevor ein weiterer Schritt eingeleitet wird.
     *
     * <p>Zuerst wird ein reguläres Beenden versucht. Falls der Prozess danach noch lebt, wird ein
     * erzwungenes Beenden mit derselben Timeout-Dauer ausgeführt.
     */
    private static final Duration PROCESS_STOP_TIMEOUT = Duration.ofSeconds(5);

    /**
     * Beendet alle in der aktuellen Session registrierten Dienste.
     *
     * <p>Die Dienste werden in der Reihenfolge Edge, Router und Origin verarbeitet. Wurde ein
     * Prozess erfolgreich beendet oder ist er bereits nicht mehr aktiv, wird die zugehörige PID aus
     * dem Session-State entfernt.
     *
     * @param sessionState aktueller Zustand der CLI-Session mit den gespeicherten Prozess-IDs
     * @return ein zusammengefasstes Ergebnisobjekt mit dem Beendigungsstatus für Origin, Router und
     *     Edge
     * @throws NullPointerException falls {@code sessionState} {@code null} ist
     */
    public ShutdownResult shutdown(CliSessionState sessionState) {
        Objects.requireNonNull(sessionState, "sessionState");

        CliSessionState.ShutdownSnapshot snapshot = sessionState.snapshot();

        StopStatus edge = stopProcess(snapshot.edgePid(), "edge");
        if (edge.stopped()) {
            sessionState.clearEdgePid();
        }

        StopStatus router = stopProcess(snapshot.routerPid(), "router");
        if (router.stopped()) {
            sessionState.clearRouterPid();
        }

        StopStatus origin = stopProcess(snapshot.originPid(), "origin");
        if (origin.stopped()) {
            sessionState.clearOriginPid();
        }

        return new ShutdownResult(origin, router, edge);
    }

    /**
     * Beendet einen einzelnen Prozess anhand seiner PID.
     *
     * <p>Das Verhalten ist wie folgt:
     *
     * <ul>
     *   <li>Ist keine PID vorhanden, wird der Status {@code SKIPPED} zurückgegeben.
     *   <li>Existiert der Prozess nicht mehr oder läuft nicht mehr, gilt er als bereits beendet.
     *   <li>Zuerst wird ein reguläres Beenden versucht.
     *   <li>Falls nötig, wird anschließend ein erzwungenes Beenden versucht.
     *   <li>Schlagen beide Versuche fehl, wird {@code FAILED} zurückgegeben.
     * </ul>
     *
     * @param pid Prozess-ID des zu beendenden Prozesses; kann {@code null} sein
     * @param serviceName logischer Name des Dienstes für Status- und Fehlermeldungen
     * @return Statusobjekt mit Ergebnis und erläuternder Nachricht
     */
    private StopStatus stopProcess(Long pid, String serviceName) {
        if (pid == null) {
            return StopStatus.skipped(serviceName);
        }

        ProcessHandle handle = ProcessHandle.of(pid).orElse(null);
        if (handle == null || !handle.isAlive()) {
            return StopStatus.stopped(serviceName, "Prozess war bereits beendet (pid=" + pid + ")");
        }

        handle.destroy();
        if (waitUntilStopped(handle, PROCESS_STOP_TIMEOUT)) {
            return StopStatus.stopped(serviceName, "Prozess beendet (pid=" + pid + ")");
        }

        handle.destroyForcibly();
        if (waitUntilStopped(handle, PROCESS_STOP_TIMEOUT)) {
            return StopStatus.stopped(serviceName, "Prozess erzwungen beendet (pid=" + pid + ")");
        }

        return StopStatus.failed(serviceName, "Prozess konnte nicht beendet werden (pid=" + pid + ")");
    }

    /**
     * Wartet bis zum angegebenen Timeout darauf, dass ein Prozess beendet ist.
     *
     * <p>Die Methode prüft in kurzen Intervallen, ob der Prozess noch lebt. Wird der Thread während
     * des Wartens unterbrochen, wird der Interrupt-Status wiederhergestellt und der aktuelle
     * Lebenszustand des Prozesses ausgewertet.
     *
     * @param handle Handle des zu überwachenden Prozesses
     * @param timeout maximale Wartezeit
     * @return {@code true}, wenn der Prozess innerhalb der Wartezeit beendet wurde, andernfalls
     *     {@code false}
     */
    private boolean waitUntilStopped(ProcessHandle handle, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (!handle.isAlive()) {
                return true;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return !handle.isAlive();
            }
        }
        return !handle.isAlive();
    }

    /**
     * Repräsentiert das Ergebnis des Beendens eines einzelnen Dienstes.
     *
     * @param serviceName Name des betroffenen Dienstes, z. B. {@code origin}, {@code router} oder
     *     {@code edge}
     * @param state technischer Statuswert, z. B. {@code SKIPPED}, {@code STOPPED} oder
     *     {@code FAILED}
     * @param message menschenlesbare Detailbeschreibung des Ergebnisses
     */
    public record StopStatus(String serviceName, String state, String message) {

        /**
         * Erzeugt einen Status für einen übersprungenen Dienst.
         *
         * <p>Dieser Fall tritt ein, wenn in der aktuellen Session keine PID für den Dienst
         * gespeichert wurde.
         *
         * @param serviceName Name des Dienstes
         * @return Status mit Zustand {@code SKIPPED}
         */
        public static StopStatus skipped(String serviceName) {
            return new StopStatus(serviceName, "SKIPPED", "nicht von dieser Session gestartet");
        }

        /**
         * Erzeugt einen erfolgreichen Beendigungsstatus.
         *
         * @param serviceName Name des Dienstes
         * @param message erläuternde Nachricht zum Erfolg
         * @return Status mit Zustand {@code STOPPED}
         */
        public static StopStatus stopped(String serviceName, String message) {
            return new StopStatus(serviceName, "STOPPED", message);
        }

        /**
         * Erzeugt einen Fehlerstatus für einen nicht erfolgreich beendeten Dienst.
         *
         * @param serviceName Name des Dienstes
         * @param message erläuternde Fehlermeldung
         * @return Status mit Zustand {@code FAILED}
         */
        public static StopStatus failed(String serviceName, String message) {
            return new StopStatus(serviceName, "FAILED", message);
        }

        /**
         * Prüft, ob der Dienst als erfolgreich beendet gilt.
         *
         * @return {@code true}, wenn der Status {@code STOPPED} ist, sonst {@code false}
         */
        public boolean stopped() {
            return "STOPPED".equals(state);
        }
    }

    /**
     * Aggregiertes Ergebnis des Shutdown-Vorgangs für alle verwalteten Dienste.
     *
     * @param origin Status des Origin-Dienstes
     * @param router Status des Router-Dienstes
     * @param edge Status des Edge-Dienstes
     */
    public record ShutdownResult(StopStatus origin, StopStatus router, StopStatus edge) {}
}
