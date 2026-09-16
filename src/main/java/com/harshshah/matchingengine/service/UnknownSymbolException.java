package com.harshshah.matchingengine.service;

public class UnknownSymbolException extends RuntimeException {
    public UnknownSymbolException(String symbol) {
        super("unknown symbol: " + symbol);
    }
}
