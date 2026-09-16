package com.harshshah.matchingengine.controller;

import com.harshshah.matchingengine.dto.OrderBookResponse;
import com.harshshah.matchingengine.service.BookService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/book")
public class BookController {
    private final BookService bookService;

    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    @GetMapping("/{symbol}")
    public OrderBookResponse getBook(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "10") int depth) {
        return bookService.book(symbol, depth);
    }
}
