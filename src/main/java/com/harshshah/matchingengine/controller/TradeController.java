package com.harshshah.matchingengine.controller;

import com.harshshah.matchingengine.dto.ExecutedTradeResponse;
import com.harshshah.matchingengine.dto.PageResponse;
import com.harshshah.matchingengine.service.BookService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trades")
@Validated
public class TradeController {
    private final BookService bookService;

    public TradeController(BookService bookService) {
        this.bookService = bookService;
    }

    @GetMapping
    public PageResponse<ExecutedTradeResponse> forSymbol(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return bookService.tradesFor(symbol, page, size);
    }
}
