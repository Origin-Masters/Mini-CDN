package de.htwsaar.minicdn.e2e;

import static org.junit.jupiter.api.Assertions.*;

import de.htwsaar.minicdn.edge.EdgeApp;
import de.htwsaar.minicdn.origin.OriginApp;
import de.htwsaar.minicdn.router.RouterApp;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * End-to-End-Integrationstest für den Standard-Ablauf eines CDN (Content Delivery Network).
 * Testet die Interaktion zwischen Origin-, Edge- und Router-Server.
 */
public class CdnStandardFlowIT {

    /** Anwendungskontext des Origin-Servers. */
    private static ConfigurableApplicationContext originCtx;

    /** Anwendungskontext des Edge-Servers. */
    private static ConfigurableApplicationContext edgeCtx;

    /** Anwendungskontext des Router-Servers. */
    private static ConfigurableApplicationContext routerCtx;

    /**
     * Startet alle drei CDN-Komponenten (Origin, Edge, Router) vor den Testfällen.
     * Konfiguriert die Server mit entsprechenden Ports und Profilen.
     */
    @BeforeAll
    static void startApps() {
        originCtx = new SpringApplicationBuilder(OriginApp.class)
                .profiles("origin")
                .properties("server.port=8080")
                .run();

        edgeCtx = new SpringApplicationBuilder(EdgeApp.class)
                .profiles("edge")
                .properties("server.port=8081", "origin.base-url=http://localhost:8080")
                .run();

        routerCtx = new SpringApplicationBuilder(RouterApp.class)
                .profiles("cdn")
                .properties("server.port=8082")
                .run();
    }

    /**
     * Beendet alle gestarteten Anwendungen nach Abschluss der Tests.
     * Schließt die Anwendungskontexte in umgekehrter Reihenfolge.
     */
    @AfterAll
    static void stopApps() {
        if (routerCtx != null) routerCtx.close();
        if (edgeCtx != null) edgeCtx.close();
        if (originCtx != null) originCtx.close();
    }

    /**
     * Testet den Standard-Ablauf eines CDN-Systems mit folgenden Schritten:
     * 1. Eine Datei wird zum Origin-Server hochgeladen.
     * 2. Ein Edge-Server wird beim Router für eine bestimmte Region registriert.
     * 3. Eine Anfrage für die Datei wird über den Router gestellt, der mit einer Umleitung (HTTP 307)
     *    zum Edge-Server antwortet.
     * 4. Die umgeleitete Anfrage wird an den Edge-Server gesendet, der den Dateiinhalt korrekt ausliefert.
     *
     * Der Test-Workflow umfasst:
     * - Hochladen einer Datei (`test.txt`) zum Origin-Server.
     * - Registrierung des Edge-Servers beim Router für die Region `eu-west`.
     * - Überprüfung, dass der Router eine HTTP 307-Antwort mit einem `Location`-Header zurückgibt,
     *   der den Client zum Edge-Server weiterleitet.
     * - Folgen der Umleitung vom Router zum Edge-Server und Sicherstellung, dass der Edge die
     *   Dateiinhalte korrekt ausliefert (initial vom Origin abgerufen, falls nötig).
     *
     * Folgende Zusicherungen werden gemacht:
     * - Der Datei-Upload ist erfolgreich (HTTP 201 oder 204 vom Origin-Server).
     * - Der Router registriert den Edge-Server erfolgreich (HTTP 201).
     * - Der Router antwortet mit HTTP 307 und einem gültigen `Location`-Header zum Edge.
     * - Der Edge-Server liefert den Dateiinhalt mit HTTP 200-Status und erwartetem Inhalt.
     *
     * @throws Exception falls ein Netzwerkfehler oder eine unerwartete Antwort während der Testschritte auftritt.
     */
    @Test
    void standardFlow_routerRedirectsToEdge_andFileIsServed() throws Exception {
        var noRedirectClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        var client = HttpClient.newHttpClient();

        // Datei zum Origin-Server hochladen
        var putReq = HttpRequest.newBuilder(URI.create("http://localhost:8080/api/origin/admin/files/test.txt"))
                .PUT(HttpRequest.BodyPublishers.ofString("Hallo vom Origin"))
                .header("Content-Type", "application/octet-stream")
                .build();

        var putResp = client.send(putReq, HttpResponse.BodyHandlers.discarding());
        assertTrue(putResp.statusCode() == 201 || putResp.statusCode() == 204);

        // Edge im Router registrieren: POST /api/cdn/routing?region=...&url=...
        var addEdgeReq = HttpRequest.newBuilder(
                        URI.create("http://localhost:8082/api/cdn/routing?region=eu-west&url=http://localhost:8081"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        var addEdgeResp = noRedirectClient.send(addEdgeReq, HttpResponse.BodyHandlers.discarding());
        assertEquals(201, addEdgeResp.statusCode());

        // Datei über Router anfragen -> erwartet 307 + Location auf Edge
        var routeReq = HttpRequest.newBuilder(URI.create("http://localhost:8082/api/cdn/files/test.txt?region=eu-west"))
                .GET()
                .build();

        var routeResp = noRedirectClient.send(routeReq, HttpResponse.BodyHandlers.discarding());
        assertEquals(307, routeResp.statusCode());

        var location = routeResp.headers().firstValue("location").orElseThrow();
        assertEquals("http://localhost:8081/api/edge/files/test.txt", location);

        // Redirect folgen: Edge sollte die Datei ausliefern (initial ggf. via Origin)
        var followClient = HttpClient.newHttpClient();
        var edgeResp = followClient.send(
                HttpRequest.newBuilder(URI.create(location)).GET().build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(200, edgeResp.statusCode());
        assertTrue(edgeResp.body().contains("Hallo vom Origin"));
    }
}
