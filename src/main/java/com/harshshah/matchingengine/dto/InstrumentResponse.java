package com.harshshah.matchingengine.dto;

import com.harshshah.matchingengine.domain.Instrument;

import java.time.Instant;

public record InstrumentResponse(String symbol, Instant createdAt) {
    public static InstrumentResponse from(Instrument instrument) {
        return new InstrumentResponse(instrument.getSymbol(), instrument.getCreatedAt());
    }
}
