package com.harshshah.matchingengine.controller;

import com.harshshah.matchingengine.dto.PositionResponse;
import com.harshshah.matchingengine.service.BookService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/positions")
public class PositionController {
    private final BookService bookService;

    public PositionController(BookService bookService) {
        this.bookService = bookService;
    }

    @GetMapping
    public List<PositionResponse> forAccount(@RequestParam UUID accountId) {
        return bookService.positionsFor(accountId);
    }
}
