package de.htwsaar.minicdn.e2e;

import de.htwsaar.minicdn.edge.EdgeApp;
import de.htwsaar.minicdn.origin.OriginApp;
import de.htwsaar.minicdn.router.RouterApp;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

public abstract class AbstractE2E {

    protected static final int ORIGIN_PORT = 8080;
    protected static final int EDGE_PORT = 8081;
    protected static final int ROUTER_PORT = 8082;

    protected static final String ORIGIN_BASE = "http://localhost:" + ORIGIN_PORT;
    protected static final String EDGE_BASE = "http://localhost:" + EDGE_PORT;
    protected static final String ROUTER_BASE = "http://localhost:" + ROUTER_PORT;

    private static ConfigurableApplicationContext originCtx;
    private static ConfigurableApplicationContext edgeCtx;
    private static ConfigurableApplicationContext routerCtx;

    @BeforeAll
    static void startAppsOnce() {

        if (originCtx != null) return;

        originCtx = new SpringApplicationBuilder(OriginApp.class)
                .profiles("origin")
                .properties("server.port=" + ORIGIN_PORT, "minicdn.admin.token=secret-token")
                .run();

        edgeCtx = new SpringApplicationBuilder(EdgeApp.class)
                .profiles("edge")
                .properties(
                        "server.port=" + EDGE_PORT,
                        "origin.base-url=" + ORIGIN_BASE,
                        "edge.cache.ttl-ms=60000",
                        "edge.cache.max-entries=100",
                        "minicdn.admin.token=secret-token")
                .run();

        routerCtx = new SpringApplicationBuilder(RouterApp.class)
                .profiles("cdn")
                .properties("server.port=" + ROUTER_PORT, "minicdn.admin.token=secret-token")
                .run();
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
}
