package de.htwsaar.minicdn.e2e;

import de.htwsaar.minicdn.edge.EdgeApp;
import de.htwsaar.minicdn.origin.OriginApp;
import de.htwsaar.minicdn.router.RouterApp;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

public abstract class AbstractE2E {

    protected static final String ADMIN_TOKEN = System.getProperty(
            "minicdn.admin.token", System.getenv().getOrDefault("MINICDN_ADMIN_TOKEN", "secret-token"));

    protected static int ORIGIN_PORT;
    protected static int EDGE_PORT;
    protected static int ROUTER_PORT;

    protected static String ORIGIN_BASE;
    protected static String EDGE_BASE;
    protected static String ROUTER_BASE;

    private static ConfigurableApplicationContext originCtx;
    private static ConfigurableApplicationContext edgeCtx;
    private static ConfigurableApplicationContext routerCtx;

    @BeforeAll
    static void startAppsOnce() {

        if (originCtx != null) return;

        originCtx = new SpringApplicationBuilder(OriginApp.class)
                .profiles("origin")
                .run("--server.port=0", "--minicdn.admin.token=" + ADMIN_TOKEN);

        ORIGIN_PORT = localPort(originCtx);
        ORIGIN_BASE = "http://localhost:" + ORIGIN_PORT;

        edgeCtx = new SpringApplicationBuilder(EdgeApp.class)
                .profiles("edge")
                .run(
                        "--server.port=0",
                        "--origin.base-url=" + ORIGIN_BASE,
                        "--edge.cache.ttl-ms=60000",
                        "--edge.cache.max-entries=100",
                        "--minicdn.admin.token=" + ADMIN_TOKEN);

        EDGE_PORT = localPort(edgeCtx);
        EDGE_BASE = "http://localhost:" + EDGE_PORT;

        routerCtx = new SpringApplicationBuilder(RouterApp.class)
                .profiles("cdn")
                .run("--server.port=0", "--minicdn.admin.token=" + ADMIN_TOKEN);

        ROUTER_PORT = localPort(routerCtx);
        ROUTER_BASE = "http://localhost:" + ROUTER_PORT;
    }

    @AfterAll
    static void stopAppsOnce() {
        if (edgeCtx != null) {
            edgeCtx.close();
            edgeCtx = null;
        }
        if (routerCtx != null) {
            routerCtx.close();
            routerCtx = null;
        }
        if (originCtx != null) {
            originCtx.close();
            originCtx = null;
        }
    }

    protected static int localPort(ConfigurableApplicationContext ctx) {
        return ((WebServerApplicationContext) ctx).getWebServer().getPort();
    }
}
