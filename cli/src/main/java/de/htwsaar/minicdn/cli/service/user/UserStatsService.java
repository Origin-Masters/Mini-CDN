package de.htwsaar.minicdn.cli.service.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.htwsaar.minicdn.cli.di.CliContext;
import de.htwsaar.minicdn.cli.dto.HttpCallResult;
import de.htwsaar.minicdn.cli.util.HttpUtils;
import de.htwsaar.minicdn.cli.util.UriUtils;
import java.net.URI;
import java.net.http.HttpRequest;
import java.util.Objects;

/**
 * Service für User-spezifische Statistiken vom Router.
 */
public class UserStatsService {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final CliContext ctx;

    public UserStatsService(CliContext ctx) {
        this.ctx = Objects.requireNonNull(ctx);
    }

    /**
     * Ruft die Statistiken für eine bestimmte Datei des aktuellen Users ab.
     */
    public HttpCallResult fileStatsForCurrentUser(long fileId) {
        URI base = UriUtils.ensureTrailingSlash(ctx.routerBaseUrl());
        URI url = base.resolve("api/cdn/stats/file/" + fileId); // 无userId，后端用token推断
        HttpRequest req = HttpUtils.newAdminRequestBuilder(url, ctx.adminToken()) // 复用admin builder
                .timeout(ctx.defaultRequestTimeout())
                .GET()
                .build();
        return HttpUtils.sendForStringBody(ctx.httpClient(), req);
    }

    /**
     * Ruft die Statistiken für die zuletzt verwendeten Dateien des aktuellen Users ab.
     */
    public HttpCallResult listUserFilesStats(int limit) {
        URI base = UriUtils.ensureTrailingSlash(ctx.routerBaseUrl());
        URI url = base.resolve("api/cdn/stats/files?limit=" + limit);
        HttpRequest req = HttpUtils.newAdminRequestBuilder(url, ctx.adminToken())
                .timeout(ctx.defaultRequestTimeout())
                .GET()
                .build();
        return HttpUtils.sendForStringBody(ctx.httpClient(), req);
    }

    /**
     * Ruft die Gesamtstatistiken für den aktuellen User ab (z.B. Traffic, Anzahl Dateien, ...).
     */
    public HttpCallResult overallStatsForCurrentUser(int windowSec) {
        URI base = UriUtils.ensureTrailingSlash(ctx.routerBaseUrl());
        URI url = base.resolve("api/cdn/stats?windowSec=" + windowSec);
        HttpRequest req = HttpUtils.newAdminRequestBuilder(url, ctx.adminToken())
                .timeout(ctx.defaultRequestTimeout())
                .GET()
                .build();
        return HttpUtils.sendForStringBody(ctx.httpClient(), req);
    }
}
