package com.harshshah.matchingengine.controller;

import com.harshshah.matchingengine.dto.ExecutedTradeResponse;
import com.harshshah.matchingengine.service.BookService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The tape: executed trades for one symbol, most recent first. */
@RestController
@RequestMapping("/api/trades")
public class TradeController {

    private final BookService bookService;

    public TradeController(BookService bookService) {
        this.bookService = bookService;
    }

    @GetMapping
    public List<ExecutedTradeResponse> forSymbol(@RequestParam String symbol) {
        return bookService.tradesFor(symbol);
    }
}
