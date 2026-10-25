package com.stockpilot.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockpilot.entity.RoleName;
import com.stockpilot.entity.User;
import com.stockpilot.repository.RoleRepository;
import com.stockpilot.repository.UserRepository;
import com.stockpilot.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Boots the full application against a real PostgreSQL 16 container (one per test JVM) with the Flyway
 * schema, and wipes all business data before every test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected UserRepository userRepository;
    @Autowired protected RoleRepository roleRepository;
    @Autowired protected PasswordEncoder passwordEncoder;
    @Autowired protected JwtService jwtService;

    protected String admin;
    protected String inventory;
    protected String sales;
    protected String viewer;

    @BeforeEach
    void resetDatabase() {
        jdbc.execute("""
                TRUNCATE notifications, audit_logs, shipments, stock_reservations, sales_order_items, sales_orders,
                         customers, goods_receipt_items, goods_receipts, purchase_order_items, purchase_orders,
                         supplier_products, suppliers, inventory_movements, stock_transfers, inventory_balances,
                         warehouses, products, categories, refresh_tokens, users RESTART IDENTITY CASCADE
                """);
        admin = token(createUser("admin", RoleName.ADMIN));
        inventory = token(createUser("inventory", RoleName.INVENTORY_MANAGER));
        sales = token(createUser("sales", RoleName.SALES_MANAGER));
        viewer = token(createUser("viewer", RoleName.VIEWER));
    }

    protected User createUser(String prefix, RoleName role) {
        User user = new User();
        user.setEmail(prefix + "@test.local");
        user.setFullName(prefix + " user");
        user.setPasswordHash(passwordEncoder.encode("Password123!"));
        user.setRole(roleRepository.findByName(role).orElseThrow());
        return userRepository.save(user);
    }

    protected String token(User user) {
        return jwtService.generateAccessToken(user);
    }

    // ------------------------------------------------------------------ HTTP helpers

    protected ResultActions call(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        return mvc.perform(request);
    }

    protected ResultActions doGet(String url, String token) throws Exception {
        return call(get(url), token, null);
    }

    protected ResultActions doPost(String url, String token, Object body) throws Exception {
        return call(post(url), token, body);
    }

    protected ResultActions doPut(String url, String token, Object body) throws Exception {
        return call(put(url), token, body);
    }

    protected JsonNode body(ResultActions actions) throws Exception {
        MvcResult result = actions.andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }

    // ------------------------------------------------------------------ fixtures (created through the API)

    protected long createCategory(String name) throws Exception {
        return body(doPost("/api/v1/categories", inventory, Map.of("name", name))).get("id").asLong();
    }

    protected long createProduct(long categoryId, String sku, double unitPrice, double costPrice, int threshold) throws Exception {
        var req = new java.util.HashMap<String, Object>();
        req.put("sku", sku);
        req.put("name", "Product " + sku);
        req.put("categoryId", categoryId);
        req.put("unitPrice", unitPrice);
        req.put("costPrice", costPrice);
        req.put("reorderThreshold", threshold);
        return body(doPost("/api/v1/products", inventory, req)).get("id").asLong();
    }

    protected long createWarehouse(String code) throws Exception {
        return body(doPost("/api/v1/warehouses", inventory, Map.of("code", code, "name", "Warehouse " + code)))
                .get("id").asLong();
    }

    protected long createSupplier(String name) throws Exception {
        return body(doPost("/api/v1/suppliers", inventory, Map.of("name", name))).get("id").asLong();
    }

    protected long createCustomer(String name) throws Exception {
        return body(doPost("/api/v1/customers", sales, Map.of("name", name, "address", "1 Main St"))).get("id").asLong();
    }

    protected void receiveStock(long productId, long warehouseId, int quantity) throws Exception {
        doPost("/api/v1/inventory/receipts", inventory,
                Map.of("productId", productId, "warehouseId", warehouseId, "quantity", quantity));
    }

    protected int onHand(long productId, long warehouseId) {
        Integer value = jdbc.queryForObject("SELECT COALESCE((SELECT quantity_on_hand FROM inventory_balances "
                + "WHERE product_id = ? AND warehouse_id = ?), 0)", Integer.class, productId, warehouseId);
        return value == null ? 0 : value;
    }

    protected int reserved(long productId, long warehouseId) {
        Integer value = jdbc.queryForObject("SELECT COALESCE((SELECT quantity_reserved FROM inventory_balances "
                + "WHERE product_id = ? AND warehouse_id = ?), 0)", Integer.class, productId, warehouseId);
        return value == null ? 0 : value;
    }

    protected int movementCount(long productId) {
        Integer value = jdbc.queryForObject("SELECT COUNT(*) FROM inventory_movements WHERE product_id = ?",
                Integer.class, productId);
        return value == null ? 0 : value;
    }

    protected static String unique(String prefix) {
        return prefix + SEQ.incrementAndGet();
    }
}
