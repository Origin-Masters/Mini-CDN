package de.htwsaar.minicdn.common.dto;

import java.util.Map;

public record MetricsInfoDto(
        long totalRequests,
        long routingErrors,
        Map<String, Long> requestsByRegion,
        Map<String, Long> selectionsByNode) {}
