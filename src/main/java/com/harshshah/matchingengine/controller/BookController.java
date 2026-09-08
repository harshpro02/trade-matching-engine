package com.harshshah.matchingengine.controller;

import com.harshshah.matchingengine.dto.OrderBookResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only view of an order book.
 *
 * <p>Scaffold only: the response shape is final but the data is not yet wired to the
 * repositories. Aggregation over the resting orders lands with the rest of the API layer.
 */
@RestController
@RequestMapping("/api/book")
public class BookController {

    @GetMapping("/{symbol}")
    public ResponseEntity<OrderBookResponse> getBook(@PathVariable String symbol) {
        return ResponseEntity.ok(new OrderBookResponse(symbol, List.of(), List.of()));
    }
}
