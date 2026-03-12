package de.htwsaar.minicdn.router.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.htwsaar.minicdn.router.domain.EdgeGateway;
import de.htwsaar.minicdn.router.domain.FileRouteLocationResolver;
import de.htwsaar.minicdn.router.dto.EdgeNode;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests für den fachlichen Retry- und Kandidatenablauf im {@link CdnRoutingService}.
 */
class CdnRoutingServiceLoadBalancingTest {

    /**
     * Prüft, dass pro Routing-Aufruf nur eindeutige Kandidaten verarbeitet werden.
     */
    @Test
    void shouldProbeUniqueCandidatesWithoutDuplicateRetries() {
        RoutingIndex routingIndex = mock(RoutingIndex.class);
        RouterStatsService routerStatsService = mock(RouterStatsService.class);
        EdgeGateway edgeGateway = mock(EdgeGateway.class);
        FileRouteLocationResolver resolver = mock(FileRouteLocationResolver.class);

        EdgeNode edgeOne = new EdgeNode("http://edge-1/");
        EdgeNode edgeTwo = new EdgeNode("http://edge-2/");

        when(routingIndex.getNodeCount("eu")).thenReturn(2);
        when(routingIndex.getNextNodes("eu", 2)).thenReturn(List.of(edgeOne, edgeTwo));
        when(edgeGateway.isNodeResponsive(any(EdgeNode.class), any(Duration.class)))
                .thenReturn(false);
        when(resolver.resolveOriginFileLocation("/a.txt")).thenReturn(URI.create("http://origin/a.txt"));

        CdnRoutingService service =
                new CdnRoutingService(routingIndex, routerStatsService, edgeGateway, resolver, 10, 5, 0);

        service.route("/a.txt", "eu", "c1");

        verify(edgeGateway, times(2)).isNodeResponsive(any(EdgeNode.class), any(Duration.class));
        verify(routingIndex).getNextNodes("eu", 2);
    }

    /**
     * Bei maxRetries kleiner als Knotenzahl wird die Kandidatenmenge begrenzt.
     */
    @Test
    void shouldRespectConfiguredMaxRetriesUpperBound() {
        RoutingIndex routingIndex = mock(RoutingIndex.class);
        RouterStatsService routerStatsService = mock(RouterStatsService.class);
        EdgeGateway edgeGateway = mock(EdgeGateway.class);
        FileRouteLocationResolver resolver = mock(FileRouteLocationResolver.class);

        when(routingIndex.getNodeCount("eu")).thenReturn(5);
        when(routingIndex.getNextNodes("eu", 3)).thenReturn(List.of());
        when(resolver.resolveOriginFileLocation("/a.txt")).thenReturn(URI.create("http://origin/a.txt"));

        CdnRoutingService service =
                new CdnRoutingService(routingIndex, routerStatsService, edgeGateway, resolver, 10, 3, 0);

        service.route("/a.txt", "eu", "c1");

        verify(routingIndex).getNodeCount("eu");
        verify(routingIndex).getNextNodes("eu", 3);
    }
}
