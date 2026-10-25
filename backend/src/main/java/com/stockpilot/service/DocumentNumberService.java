package com.stockpilot.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Human-readable document numbers backed by PostgreSQL sequences, which are gap-tolerant but
 * never hand out the same value twice, even under concurrency.
 */
@Service
@RequiredArgsConstructor
public class DocumentNumberService {

    public enum DocumentType {
        PURCHASE_ORDER("PO", "po_number_seq"),
        GOODS_RECEIPT("GR", "receipt_number_seq"),
        SALES_ORDER("SO", "so_number_seq"),
        SHIPMENT("SH", "shipment_number_seq"),
        TRANSFER("TR", "transfer_number_seq"),
        SUPPLIER("SUP", "supplier_code_seq"),
        CUSTOMER("CUS", "customer_code_seq");

        private final String prefix;
        private final String sequence;

        DocumentType(String prefix, String sequence) {
            this.prefix = prefix;
            this.sequence = sequence;
        }
    }

    private final JdbcTemplate jdbcTemplate;

    public String next(DocumentType type) {
        Long value = jdbcTemplate.queryForObject("SELECT nextval('" + type.sequence + "')", Long.class);
        int width = (type == DocumentType.SUPPLIER || type == DocumentType.CUSTOMER) ? 4 : 6;
        return type.prefix + "-" + String.format("%0" + width + "d", value);
    }

    /** SKU = first three letters of the category + running number, e.g. ELE-7001. */
    public String nextSku(String categoryName) {
        String letters = categoryName == null ? "" : categoryName.replaceAll("[^A-Za-z]", "").toUpperCase();
        String prefix = letters.length() >= 3 ? letters.substring(0, 3) : (letters + "GEN").substring(0, 3);
        Long value = jdbcTemplate.queryForObject("SELECT nextval('product_sku_seq')", Long.class);
        return prefix + "-" + value;
    }
}
