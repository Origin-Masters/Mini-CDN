package de.htwsaar.minicdn.cli;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import picocli.CommandLine;

@CommandLine.Command(name = "ping", description = "mini-Cdn Health Check (/health)")
public class PingCommand implements Runnable {

    @CommandLine.Option(
            names = {"-h", "--host"},
            defaultValue = "http://localhost:8080",
            description = "Basis-Url des mini-CDN Server")
    String host;

    @Override
    public void run() {
        String url = host + "/health";
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request =
                    HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("Status: " + response.statusCode());
            System.out.println("Body: " + response.body());
        } catch (Exception ignored) {
            System.out.println("Ping fehlgeschlagen: " + url);
        }
    }
}
