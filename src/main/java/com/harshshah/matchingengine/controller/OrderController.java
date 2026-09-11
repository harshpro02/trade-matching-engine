package com.harshshah.matchingengine.controller;

import com.harshshah.matchingengine.dto.OrderResponse;
import com.harshshah.matchingengine.dto.SubmitOrderRequest;
import com.harshshah.matchingengine.dto.SubmitOrderResponse;
import com.harshshah.matchingengine.service.MatchingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.UUID;

/**
 * HTTP for orders. Validation, DTO mapping and status codes only - no business logic and
 * no transaction boundaries, both of which live in {@link MatchingService}.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final MatchingService matchingService;

    public OrderController(MatchingService matchingService) {
        this.matchingService = matchingService;
    }

    /**
     * Submit an order. Returns 201 with the order's outcome and every trade it caused.
     *
     * <p>201 even when the order filled completely and is therefore already terminal: the
     * resource was created either way, and the body says what became of it.
     */
    @PostMapping
    public ResponseEntity<SubmitOrderResponse> submit(@Valid @RequestBody SubmitOrderRequest request) {
        SubmitOrderResponse response = matchingService.submitOrder(request);
        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/orders/{id}")
                        .buildAndExpand(response.orderId()).toUri())
                .body(response);
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable UUID id) {
        return matchingService.getOrder(id);
    }

    @GetMapping
    public List<OrderResponse> forAccount(@RequestParam UUID accountId) {
        return matchingService.ordersForAccount(accountId);
    }

    /**
     * Cancel a resting order.
     *
     * <p>200 with the cancelled order, or 409 if it has already filled or been cancelled:
     * a terminal order cannot be un-terminated, and reporting success would be a lie the
     * caller might act on.
     */
    @DeleteMapping("/{id}")
    public OrderResponse cancel(@PathVariable UUID id) {
        return matchingService.cancelOrder(id);
    }
}
