package com.stockpilot.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockpilot.config.AppProperties;
import com.stockpilot.dto.StockStatus;
import com.stockpilot.security.RateLimitFilter;
import com.stockpilot.validation.HttpUrlValidator;
import com.stockpilot.validation.StrongPasswordValidator;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ValidationAndUtilityTest {

    @Test
    void strongPasswordRules() {
        var v = new StrongPasswordValidator();
        assertThat(v.isValid("Password1", null)).isTrue();
        assertThat(v.isValid("short1", null)).isFalse();
        assertThat(v.isValid("onlyletters", null)).isFalse();
        assertThat(v.isValid("12345678", null)).isFalse();
        assertThat(v.isValid("a1".repeat(37), null)).isFalse(); // over BCrypt's 72-byte limit
    }

    @Test
    void httpUrlRules() {
        var v = new HttpUrlValidator();
        assertThat(v.isValid(null, null)).isTrue();
        assertThat(v.isValid("https://cdn.example.com/a.png", null)).isTrue();
        assertThat(v.isValid("javascript:alert(1)", null)).isFalse();
        assertThat(v.isValid("data:image/png;base64,AAA", null)).isFalse();
        assertThat(v.isValid("ftp://example.com/a.png", null)).isFalse();
        assertThat(v.isValid("not a url", null)).isFalse();
    }

    @Test
    void stockStatusClassification() {
        assertThat(StockStatus.of(0, 10)).isEqualTo(StockStatus.OUT_OF_STOCK);
        assertThat(StockStatus.of(10, 10)).isEqualTo(StockStatus.LOW_STOCK);
        assertThat(StockStatus.of(11, 10)).isEqualTo(StockStatus.IN_STOCK);
        assertThat(StockStatus.of(1, 0)).isEqualTo(StockStatus.IN_STOCK);
    }

    @Test
    void csvEscapingQuotesAndNeutralisesFormulas() throws Exception {
        Method csv = com.stockpilot.service.ReportService.class.getDeclaredMethod("csv", Object.class);
        csv.setAccessible(true);
        assertThat(csv.invoke(null, "plain")).isEqualTo("plain");
        assertThat(csv.invoke(null, "a,b")).isEqualTo("\"a,b\"");
        assertThat(csv.invoke(null, "say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
        assertThat(csv.invoke(null, "=SUM(A1)")).isEqualTo("'=SUM(A1)");
        assertThat(csv.invoke(null, "@cmd")).isEqualTo("'@cmd");
        assertThat(csv.invoke(null, -5)).isEqualTo("-5"); // numbers are never prefixed
        assertThat(csv.invoke(null, new java.math.BigDecimal("1E+3"))).isEqualTo("1000");
        assertThat(csv.invoke(null, (Object) null)).isEqualTo("");
    }

    @Test
    void rateLimiterBlocksAfterConfiguredAttempts() throws Exception {
        var props = new AppProperties(null, new AppProperties.Cors(List.of()), new AppProperties.Security(false, 3), null);
        RateLimitFilter filter = new RateLimitFilter(props, new ObjectMapper().findAndRegisterModules());
        int[] statuses = new int[5];
        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
            request.setRemoteAddr("10.0.0.1");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            statuses[i] = response.getStatus();
        }
        assertThat(statuses).containsExactly(200, 200, 200, 429, 429);

        MockHttpServletRequest other = new MockHttpServletRequest("GET", "/api/v1/products");
        other.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(other, response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
