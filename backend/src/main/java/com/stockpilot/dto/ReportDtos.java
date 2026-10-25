package com.stockpilot.dto;

import java.util.List;
import java.util.Map;

public final class ReportDtos {

    private ReportDtos() {
    }

    public record Column(String key, String label, String type) {
    }

    public record Response(String key, String title, List<Column> columns, List<Map<String, Object>> rows,
                           int page, int size, long totalElements, int totalPages) {
    }

    public record Info(String key, String title, String description, boolean dateRange, boolean warehouseFilter) {
    }
}
