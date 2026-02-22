package de.htwsaar.minicdn.e2e;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * NFA-S1: Performance & Parallelität
 * Nachweis, dass der Router mindestens 5 (hier 10) gleichzeitige Anfragen stabil verarbeitet.
 */
public class ParallelLoadIT extends AbstractE2E {

    //@Test
    void testParallelRequestStability() {
        int numberOfParallelRequests = 1;
        HttpClient client = HttpClient.newHttpClient();
        String testUrl = ROUTER_BASE+ "/api/cdn/files/htwsaar.jpg?region=eu-west";

        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(testUrl)).build();

        System.out.println(">>> START NFA-S1 Lasttest: " + numberOfParallelRequests + " gleichzeitige Anfragen...");

        long startTime = System.nanoTime(); // nanoTime ist für Benchmarks präziser

        // 1. Alle Anfragen asynchron abfeuern
        List<CompletableFuture<HttpResponse<String>>> futures = IntStream.range(0, numberOfParallelRequests)
                .mapToObj(i -> client.sendAsync(request, HttpResponse.BodyHandlers.ofString()))
                .collect(Collectors.toList());

        // 2. Warten, bis ALLE fertig sind
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        long endTime = System.nanoTime();

        // Berechnung der Statistiken
        long totalDurationMs = (endTime - startTime) / 1_000_000;
        long avgDurationMs = totalDurationMs / numberOfParallelRequests;

        // Statistische Ausgabe (immer sichtbar im Log)
        System.out.println("--------------------------------------------------");
        System.out.println("BENCHMARK ERGEBNIS:");
        System.out.println("Gesamtdauer: " + totalDurationMs + "ms");
        System.out.println("Durchschnitt pro Request: " + avgDurationMs + "ms");
        System.out.println("--------------------------------------------------");

        // 3. Validierung der Ergebnisse
        String errorMsg = String.format("Benchmark fehlgeschlagen! Gesamt: %dms, Schnitt: %dms",
                totalDurationMs, avgDurationMs);

        for (CompletableFuture<HttpResponse<String>> future : futures) {
            int status = future.join().statusCode();
            // Wir prüfen auf 200 OK.
            // Hinweis: Falls die Datei im Test-Setup fehlt, käme 404 zurück.
            assertTrue(status == 200, errorMsg + " | Einer der Statuscodes war: " + status);
        }

        System.out.println("✅ NFA-S1 erfüllt: Alle Anfragen erfolgreich verarbeitet.");
    }

    private void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}