package de.htwsaar.minicdn.router;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CDNControllerTest {

    // MockMvc erlaubt es uns, HTTP-Anfragen (GET, POST, etc.) zu simulieren,
    // als ob ein Browser oder ein Client sie an unseren Server schicken würde.
    private MockMvc mockMvc;

    // Die Instanz des Controllers, den wir testen wollen.
    private CDNController cdnController;

    /**
     * Diese Methode läuft mit @BeforeEach vor jedem einzelnen Testlauf einmal durch.
     * So stellen wir sicher, dass jeder Test mit einer "sauberen" Umgebung startet.
     */
    @BeforeEach
    void setUp() {
        cdnController = new CDNController();

        // Wir konfigurieren MockMvc so, dass es unseren Controller und die
        // dazugehörige Admin-Schnittstelle (innere Klasse) kennt.
        mockMvc = MockMvcBuilders.standaloneSetup(cdnController, cdnController.new RoutingAdminApi())
                .build();
    }

    @Test
    @DisplayName("Prüfen, ob die Basis-Gesundheits-Check-Endpunkte antworten")
    void testHealthAndReady() throws Exception {
        // Wir führen eine GET-Anfrage auf /api/cdn/health aus
        mockMvc.perform(get("/api/cdn/health"))
                .andExpect(status().isOk()) // Erwarte HTTP 200 (OK)
                .andExpect(content().string("ok")); // Erwarte den Text "ok" im Antwort-Body

        mockMvc.perform(get("/api/cdn/ready"))
                .andExpect(status().isOk())
                .andExpect(content().string("ready"));
    }

    @Test
    @DisplayName("Fehlerfall: Routing ohne Angabe einer Region")
    void testRouteWithoutRegion() throws Exception {
        // Hier schicken wir eine Anfrage an einen Dateipfad, vergessen aber die Region.
        mockMvc.perform(get("/api/cdn/files/mein-bild.jpg"))
                // Der Controller sollte merken, dass die Region fehlt und 400 (Bad Request) liefern.
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Region fehlt")));
    }

    @Test
    @DisplayName("Erfolgsfall: Eine Datei anfragen und zur Edge-Node weitergeleitet werden")
    void testSuccessfulRouting() throws Exception {
        // SCHRITT 1: Wir müssen dem System erst sagen, dass es eine Edge-Node gibt.
        // Das machen wir über die Admin-API (POST-Anfrage).
        mockMvc.perform(post("/api/cdn/routing").param("region", "EU").param("url", "http://edge-server-1.com"))
                .andExpect(status().isCreated()); // Erwarte HTTP 201 (Created)

        // SCHRITT 2: Jetzt fragen wir eine Datei in dieser Region an.
        mockMvc.perform(get("/api/cdn/files/video.mp4").param("region", "EU"))
                // Wir erwarten eine Umleitung (HTTP 307 Temporary Redirect).
                .andExpect(status().isTemporaryRedirect())
                // Die 'Location' im Header muss nun die URL der Edge-Node plus den Dateipfad enthalten.
                .andExpect(header().string("Location", "http://edge-server-1.com/api/edge/files/video.mp4"));
    }

    @Test
    @DisplayName("Lastverteilung: Round-Robin soll zwischen zwei Nodes abwechseln")
    void testRoundRobinRouting() throws Exception {
        // Wir registrieren zwei verschiedene Server in der gleichen Region "US".
        mockMvc.perform(post("/api/cdn/routing").param("region", "US").param("url", "http://node-A.com"));
        mockMvc.perform(post("/api/cdn/routing").param("region", "US").param("url", "http://node-B.com"));

        // Erste Anfrage: Wir holen uns die Location, wohin umgeleitet wurde.
        String ersteLocation = mockMvc.perform(get("/api/cdn/files/test").param("region", "US"))
                .andReturn()
                .getResponse()
                .getHeader("Location");

        // Zweite Anfrage: Der Controller muss jetzt den ANDEREN Server wählen.
        mockMvc.perform(get("/api/cdn/files/test").param("region", "US"))
                .andExpect(status().isTemporaryRedirect())
                // 'not(ersteLocation)' stellt sicher, dass es nicht wieder die gleiche URL ist.
                .andExpect(header().string("Location", not(ersteLocation)));
    }

    @Test
    @DisplayName("Bulk-Update: Mehrere Nodes gleichzeitig über JSON hinzufügen")
    void testBulkUpdate() throws Exception {
        // Ein JSON-String, der zwei Befehle zum Hinzufügen von Nodes enthält.
        String jsonAnfrage =
                """
            [
                {"region": "DE", "url": "http://node-deutschland.com", "action": "add"},
                {"region": "FR", "url": "http://node-frankreich.com", "action": "add"}
            ]
            """;

        mockMvc.perform(post("/api/cdn/routing/bulk")
                        .contentType(MediaType.APPLICATION_JSON) // Wir sagen dem Server: "Hier kommt JSON"
                        .content(jsonAnfrage))
                .andExpect(status().isOk())
                // Wir prüfen im JSON-Ergebnis, ob die Liste 2 Einträge hat.
                .andExpect(jsonPath("$", hasSize(2)))
                // Wir prüfen, ob der erste Eintrag den Status "added" hat.
                .andExpect(jsonPath("$[0].status", is("added")));
    }

    @Test
    @DisplayName("Metriken: Zähler müssen sich bei Anfragen erhöhen")
    void testMetrics() throws Exception {
        // Vorbereitung: Node registrieren
        mockMvc.perform(post("/api/cdn/routing").param("region", "EU").param("url", "http://metrics-edge.com"));

        // Eine Anfrage simulieren
        mockMvc.perform(get("/api/cdn/files/datei.txt").param("region", "EU"));

        // Jetzt rufen wir den Metrik-Endpunkt auf und schauen, ob die Zahlen stimmen.
        mockMvc.perform(get("/api/cdn/routing/metrics"))
                .andExpect(status().isOk())
                // "totalRequests" im JSON sollte jetzt 1 sein.
                .andExpect(jsonPath("$.totalRequests", is(1)))
                // In der Statistik für die Region "EU" sollte auch eine 1 stehen.
                .andExpect(jsonPath("$.requestsByRegion.EU", is(1)));
    }

    @Test
    @DisplayName("Entfernen: Eine Node löschen und prüfen, ob sie weg ist")
    void testDeleteNode() throws Exception {
        // Erst hinzufügen...
        mockMvc.perform(post("/api/cdn/routing").param("region", "EU").param("url", "http://weg-mit-mir.com"));

        // ...dann löschen (DELETE Anfrage)
        mockMvc.perform(delete("/api/cdn/routing").param("region", "EU").param("url", "http://weg-mit-mir.com"))
                .andExpect(status().isOk());

        // Wenn wir jetzt anfragen, darf keine Node mehr gefunden werden (HTTP 404).
        mockMvc.perform(get("/api/cdn/files/test").param("region", "EU"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("Keine verfügbaren Edge-Nodes")));
    }
}
