package com.harshshah.matchingengine.controller;

import com.harshshah.matchingengine.dto.CreateInstrumentRequest;
import com.harshshah.matchingengine.dto.InstrumentResponse;
import com.harshshah.matchingengine.service.BookService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

@RestController
@RequestMapping("/api/instruments")
public class InstrumentController {
    private final BookService bookService;

    public InstrumentController(BookService bookService) {
        this.bookService = bookService;
    }

    @GetMapping
    public List<InstrumentResponse> list() {
        return bookService.instruments();
    }

    @PostMapping
    public ResponseEntity<InstrumentResponse> create(@Valid @RequestBody CreateInstrumentRequest request) {
        InstrumentResponse created = bookService.createInstrument(request.symbol());
        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/book/{symbol}")
                        .buildAndExpand(created.symbol()).toUri())
                .body(created);
    }
}
