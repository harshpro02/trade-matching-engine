package com.harshshah.matchingengine.controller;

import com.harshshah.matchingengine.dto.OrderResponse;
import com.harshshah.matchingengine.dto.PageResponse;
import com.harshshah.matchingengine.dto.SubmitOrderRequest;
import com.harshshah.matchingengine.dto.SubmitOrderResponse;
import com.harshshah.matchingengine.service.MatchingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@Validated
public class OrderController {
    private final MatchingService matchingService;

    public OrderController(MatchingService matchingService) {
        this.matchingService = matchingService;
    }

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
    public PageResponse<OrderResponse> forAccount(
            @RequestParam UUID accountId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return matchingService.ordersForAccount(accountId, page, size);
    }

    @DeleteMapping("/{id}")
    public OrderResponse cancel(@PathVariable UUID id) {
        return matchingService.cancelOrder(id);
    }
}
