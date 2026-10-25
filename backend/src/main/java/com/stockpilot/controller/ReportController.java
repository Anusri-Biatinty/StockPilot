package com.stockpilot.controller;

import com.stockpilot.dto.ReportDtos;
import com.stockpilot.security.Permissions;
import com.stockpilot.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@Tag(name = "Reports")
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@PreAuthorize(Permissions.AUTHENTICATED)
public class ReportController {

    private final ReportService reportService;

    @GetMapping
    public List<ReportDtos.Info> catalogue() {
        return reportService.catalogue();
    }

    @Operation(summary = "Run a report. Dates default to the last 90 days.")
    @GetMapping("/{key}")
    public ReportDtos.Response run(@PathVariable String key,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                   @RequestParam(required = false) Long warehouseId,
                                   @RequestParam(required = false) String sort,
                                   @RequestParam(required = false) String direction,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "25") int size) {
        return reportService.run(key, from, to, warehouseId, sort, direction, page, size);
    }

    @Operation(summary = "Download the full report as CSV")
    @GetMapping("/{key}/export")
    public void export(@PathVariable String key,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       @RequestParam(required = false) Long warehouseId,
                       @RequestParam(required = false) String sort,
                       @RequestParam(required = false) String direction,
                       HttpServletResponse response) throws IOException {
        String fileName = reportService.fileName(key);
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(fileName).build().toString());
        Writer writer = new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8);
        reportService.exportCsv(key, from, to, warehouseId, sort, direction, writer);
    }
}
