package de.htwsaar.minicdn.router.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.htwsaar.minicdn.router.domain.EdgeGateway;
import de.htwsaar.minicdn.router.domain.EdgeRegistry;
import de.htwsaar.minicdn.router.domain.FileRouteLocationResolver;
import de.htwsaar.minicdn.router.domain.RouteFileResult;
import de.htwsaar.minicdn.router.domain.RouteStatus;
import de.htwsaar.minicdn.router.dto.EdgeNode;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests für den fachlichen Kandidatenablauf im {@link CdnRoutingService}.
 */
class CdnRoutingServiceLoadBalancingTest {

    @Test
    void shouldRetryNextCandidateWhenFirstNodeIsDead() {
        EdgeRegistry edgeRegistry = mock(EdgeRegistry.class);
        RouterStatsService routerStatsService = mock(RouterStatsService.class);
        EdgeGateway edgeGateway = mock(EdgeGateway.class);
        FileRouteLocationResolver resolver = mock(FileRouteLocationResolver.class);

        EdgeNode dead = new EdgeNode("http://edge-dead/");
        EdgeNode healthy = new EdgeNode("http://edge-healthy/");

        when(edgeRegistry.getNodeCount("eu")).thenReturn(2);
        when(edgeRegistry.getNextNodes("eu", 2)).thenReturn(List.of(dead, healthy));
        when(edgeGateway.isNodeResponsive(dead, Duration.ofMillis(500))).thenReturn(false);
        when(edgeGateway.isNodeResponsive(healthy, Duration.ofMillis(500))).thenReturn(true);
        when(resolver.resolveEdgeFileLocation(healthy, "/a.txt"))
                .thenReturn(URI.create("http://edge-healthy/api/edge/files/a.txt"));

        CdnRoutingService service =
                new CdnRoutingService(edgeRegistry, routerStatsService, edgeGateway, resolver, 500, 5, 0);

        RouteFileResult result = service.route("/a.txt", "eu", "c1");

        assertEquals(RouteStatus.REDIRECT, result.status());
        assertEquals(URI.create("http://edge-healthy/api/edge/files/a.txt"), result.location());
        verify(edgeRegistry).markUnhealthy("eu", dead);
    }

    @Test
    void shouldFallbackToOriginWhenAllCandidatesAreDead() {
        EdgeRegistry edgeRegistry = mock(EdgeRegistry.class);
        RouterStatsService routerStatsService = mock(RouterStatsService.class);
        EdgeGateway edgeGateway = mock(EdgeGateway.class);
        FileRouteLocationResolver resolver = mock(FileRouteLocationResolver.class);

        EdgeNode deadOne = new EdgeNode("http://edge-1/");
        EdgeNode deadTwo = new EdgeNode("http://edge-2/");

        when(edgeRegistry.getNodeCount("eu")).thenReturn(2);
        when(edgeRegistry.getNextNodes("eu", 2)).thenReturn(List.of(deadOne, deadTwo));
        when(edgeGateway.isNodeResponsive(deadOne, Duration.ofMillis(500))).thenReturn(false);
        when(edgeGateway.isNodeResponsive(deadTwo, Duration.ofMillis(500))).thenReturn(false);
        when(resolver.resolveOriginFileLocation("/a.txt"))
                .thenReturn(URI.create("http://origin/api/origin/files/a.txt"));

        CdnRoutingService service =
                new CdnRoutingService(edgeRegistry, routerStatsService, edgeGateway, resolver, 500, 5, 0);

        RouteFileResult result = service.route("/a.txt", "eu", "c1");

        assertEquals(RouteStatus.REDIRECT, result.status());
        assertEquals(URI.create("http://origin/api/origin/files/a.txt"), result.location());
        verify(edgeRegistry).markUnhealthy("eu", deadOne);
        verify(edgeRegistry).markUnhealthy("eu", deadTwo);
    }

    @Test
    void shouldRespectConfiguredMaxRetriesUpperBound() {
        EdgeRegistry edgeRegistry = mock(EdgeRegistry.class);
        RouterStatsService routerStatsService = mock(RouterStatsService.class);
        EdgeGateway edgeGateway = mock(EdgeGateway.class);
        FileRouteLocationResolver resolver = mock(FileRouteLocationResolver.class);

        when(edgeRegistry.getNodeCount("eu")).thenReturn(5);
        when(edgeRegistry.getNextNodes("eu", 3)).thenReturn(List.of());
        when(resolver.resolveOriginFileLocation("/a.txt"))
                .thenReturn(URI.create("http://origin/api/origin/files/a.txt"));

        CdnRoutingService service =
                new CdnRoutingService(edgeRegistry, routerStatsService, edgeGateway, resolver, 500, 3, 0);

        service.route("/a.txt", "eu", "c1");

        verify(edgeRegistry).getNextNodes("eu", 3);
    }
}
