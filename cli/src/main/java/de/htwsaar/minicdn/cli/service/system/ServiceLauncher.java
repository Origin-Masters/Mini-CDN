package de.htwsaar.minicdn.cli.service.system;

import java.nio.file.Path;

/**
 * Adapter-Port zum Starten lokaler Service-Prozesse.
 *
 * <p>Die fachliche Startlogik kennt nur dieses Interface und bleibt damit
 * von der konkreten Prozess-/Transport-Bindung entkoppelt.
 */
public interface ServiceLauncher {

    /**
     * Startet einen Service-Prozess.
     *
     * @param jarPath Pfad zur ausführbaren JAR
     * @param springProfile aktives Spring-Profil
     * @param logPath Ziel-Datei für stdout/stderr
     * @return gestarteter Prozess
     */
    Process start(Path jarPath, String springProfile, Path logPath);
}
