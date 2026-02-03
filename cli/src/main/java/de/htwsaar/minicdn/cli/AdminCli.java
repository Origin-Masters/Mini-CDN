package de.htwsaar.minicdn.cli;

import de.htwsaar.minicdn.cli.adminCommands.AdminConfigCommand;

import java.io.IOException;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Admin CLI Tool zur Verwaltung der CDN Routing-Tabelle und Metriken-Abfrage via picocli.
 * Kommuniziert mit der RoutingAdminApi des CDNControllers.
 */
@Command(
        name = "admin-cli",
        mixinStandardHelpOptions = true,
        version = "1.0",
        description = "Verwaltet die Edge-Nodes des Mini-CDN und fragt Metriken ab.",
        subcommands = {AdminCommand.class})
public class AdminCli implements Callable<Integer> {

    @Option(
            names = {"-s", "--server"},
            description = "Basis-URL des CDN-Servers",
            defaultValue = "http://localhost:8082")
    private String serverUrl;

    private final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Override
    public Integer call() {
        CommandLine.usage(this, System.out);
        return 0;
    }

    @Command(name = "addEdge", description = "Fügt eine neue Edge-Node hinzu.")
    public Integer addEdge(
            @Parameters(index = "0", description = "Die Region (z.B. eu-west)") String region,
            @Parameters(index = "1", description = "Die URL der Edge-Node") String url) {

        String targetUrl = String.format("%s/api/cdn/routing?region=%s&url=%s", serverUrl, region, url);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        return executeRequest(
                request,
                HttpURLConnection.HTTP_CREATED,
                "Edge-Node erfolgreich hinzugefügt.",
                "Fehler beim Hinzufügen");
    }

    @Command(name = "removeEdge", description = "Entfernt eine Edge-Node aus einer Region.")
    public Integer removeEdge(
            @Parameters(index = "0", description = "Die Region") String region,
            @Parameters(index = "1", description = "Die URL der zu entfernenden Edge-Node") String url) {

        String targetUrl = String.format("%s/api/cdn/routing?region=%s&url=%s", serverUrl, region, url);

        HttpRequest request =
                HttpRequest.newBuilder().uri(URI.create(targetUrl)).DELETE().build();

        return executeRequest(
                request, HttpURLConnection.HTTP_OK, "Edge-Node erfolgreich entfernt.", "Fehler beim Löschen");
    }

    @Command(name = "bulkUpdate", description = "Führt ein Bulk-Update mittels einer JSON-Datei aus.")
    public Integer bulkUpdate(
            @Parameters(
                            index = "0",
                            description = "Pfad zur JSON-Datei mit den Update-Anweisungen",
                            paramLabel = "FILE")
                    String filePath) {

        try {
            Path path = Path.of(filePath);
            if (!Files.exists(path)) {
                System.err.println("Fehler: Die Datei " + filePath + " existiert nicht.");
                return 1;
            }

            String jsonContent = Files.readString(path);
            String targetUrl = serverUrl + "/api/cdn/routing/bulk";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonContent))
                    .build();

            return executeRequest(
                    request,
                    HttpURLConnection.HTTP_OK,
                    "Bulk-Update erfolgreich ausgeführt:",
                    "Fehler beim Bulk-Update");

        } catch (IOException e) {
            System.err.println("Fehler beim Lesen der Datei: " + e.getMessage());
            return 1;
        }
    }

    @Command(name = "getEdgeIndex", description = "Zeigt die aktuelle Routing-Tabelle (JSON).")
    public Integer getEdgeIndex(
            @Option(
                    names = {"--check-health"},
                    description = "Führt einen aktiven Health-Check durch")
            boolean checkHealth) {

        String query = checkHealth ? "?checkHealth=true" : "";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serverUrl + "/api/cdn/routing" + query))
                .GET()
                .build();

        return executeRequest(
                request, HttpURLConnection.HTTP_OK, "Aktueller Routing-Index:", "Fehler beim Abrufen des Index");
    }

    @Command(name = "getMetrics", description = "Ruft die Laufzeit-Statistiken des CDN-Servers ab.")
    public Integer getMetrics() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serverUrl + "/api/cdn/routing/metrics"))
                .GET()
                .build();

        return executeRequest(request, HttpURLConnection.HTTP_OK, "CDN-Metriken:", "Fehler beim Abrufen der Metriken");
    }

    /**
     * Hilfsmethode zur Ausführung von Requests und einheitlicher Fehlerausgabe.
     */
    private Integer executeRequest(HttpRequest request, int expectedStatus, String successPrefix, String errorPrefix) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == expectedStatus) {
                System.out.println(successPrefix);
                System.out.println(response.body());
                return 0;
            } else if (response.statusCode() == HttpURLConnection.HTTP_NOT_FOUND) {
                System.err.println(errorPrefix + ": Ressource nicht gefunden (404).");
                return 1;
            } else {
                System.err.println(errorPrefix + ": HTTP " + response.statusCode());
                System.err.println("Details: " + response.body());
                return 1;
            }
        } catch (ConnectException | HttpConnectTimeoutException e) {
            System.err.println("Verbindungsfehler: Der CDN-Server unter " + serverUrl + " ist nicht erreichbar.");
            return 1;
        } catch (IOException | InterruptedException e) {
            System.err.println("Unerwarteter Fehler: " + e.getMessage());
            Thread.currentThread().interrupt();
            return 1;
        }
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new AdminCli()).execute(args);
        System.exit(exitCode);
    }
}
