package com.harshshah.matchingengine.controller;

import com.harshshah.matchingengine.dto.OrderBookResponse;
import com.harshshah.matchingengine.service.BookService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only view of an order book, aggregated by price level and ordered best first on
 * both sides.
 */
@RestController
@RequestMapping("/api/book")
public class BookController {

    private final BookService bookService;

    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    @GetMapping("/{symbol}")
    public OrderBookResponse getBook(@PathVariable String symbol) {
        return bookService.book(symbol);
    }
}
