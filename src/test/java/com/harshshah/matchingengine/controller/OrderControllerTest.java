package com.harshshah.matchingengine.controller;

import com.harshshah.matchingengine.domain.OrderStatus;
import com.harshshah.matchingengine.dto.OrderResponse;
import com.harshshah.matchingengine.dto.PageResponse;
import com.harshshah.matchingengine.dto.SubmitOrderResponse;
import com.harshshah.matchingengine.service.BookService;
import com.harshshah.matchingengine.service.MatchingService;
import com.harshshah.matchingengine.service.OrderNotFoundException;
import com.harshshah.matchingengine.service.UnknownSymbolException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {OrderController.class, BookController.class})
@DisplayName("order HTTP contract")
class OrderControllerTest {

    private static final UUID ACCOUNT = UUID.fromString("3f1b8c2e-0000-4000-8000-000000000001");
    private static final UUID ORDER_ID = UUID.fromString("9a2c4d10-0000-4000-8000-000000000002");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MatchingService matchingService;

    @MockitoBean
    private BookService bookService;

    private static String body(String side, String type, String priceField, long quantity) {
        return """
                {"accountId":"%s","symbol":"AAPL","side":"%s","type":"%s"%s,"quantity":%d}
                """.formatted(ACCOUNT, side, type, priceField, quantity);
    }

    @Test
    void aValidSubmissionIsCreatedAndCarriesALocationHeader() throws Exception {
        given(matchingService.submitOrder(any())).willReturn(
                new SubmitOrderResponse(ORDER_ID, OrderStatus.OPEN, 0, 100, List.of()));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("BUY", "LIMIT", ",\"price\":\"150.25\"", 100)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/orders/" + ORDER_ID))
                .andExpect(jsonPath("$.orderId").value(ORDER_ID.toString()))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void aLimitOrderWithoutAPriceIsRejectedWithTheOffendingField() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("BUY", "LIMIT", "", 100)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.priceConsistentWithType").exists());
    }

    @Test
    void aMarketOrderCarryingAPriceIsRejected() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("BUY", "MARKET", ",\"price\":\"150.25\"", 100)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.priceConsistentWithType").exists());
    }

    @Test
    void aNonPositiveQuantityIsRejected() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("BUY", "LIMIT", ",\"price\":\"150.25\"", 0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.quantity").exists());
    }

    @Test
    void anUnknownSymbolIsNotFound() throws Exception {
        willThrow(new UnknownSymbolException("NOPE")).given(matchingService).submitOrder(any());

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("BUY", "LIMIT", ",\"price\":\"150.25\"", 100)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("unknown symbol: NOPE"));
    }

    @Test
    void anUnknownOrderIdIsNotFound() throws Exception {
        willThrow(new OrderNotFoundException(ORDER_ID)).given(matchingService).getOrder(ORDER_ID);

        mockMvc.perform(get("/api/orders/{id}", ORDER_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancellingAnOrderThatIsAlreadyTerminalConflicts() throws Exception {
        willThrow(new IllegalStateException("cannot cancel an order in status FILLED"))
                .given(matchingService).cancelOrder(ORDER_ID);

        mockMvc.perform(delete("/api/orders/{id}", ORDER_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void listingOrdersForAnAccountIsPagedAndDefaultsAreApplied() throws Exception {
        given(matchingService.ordersForAccount(eq(ACCOUNT), anyInt(), anyInt()))
                .willReturn(new PageResponse<>(List.of(sampleOrder()), 0, 50, 1, 1, false));

        mockMvc.perform(get("/api/orders").param("accountId", ACCOUNT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(50))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void anOversizedPageRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/orders")
                        .param("accountId", ACCOUNT.toString())
                        .param("size", "5000"))
                .andExpect(status().isBadRequest());
    }

    private static OrderResponse sampleOrder() {
        return new OrderResponse(ORDER_ID, ACCOUNT, "AAPL",
                com.harshshah.matchingengine.domain.Side.BUY,
                com.harshshah.matchingengine.domain.OrderType.LIMIT,
                new java.math.BigDecimal("150.25"), 100, 0, 100,
                OrderStatus.OPEN, Instant.parse("2026-09-16T14:30:00Z"), 1L);
    }
}
