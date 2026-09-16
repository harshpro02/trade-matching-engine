package com.harshshah.matchingengine.service;

public class SymbolAlreadyExistsException extends RuntimeException {
    public SymbolAlreadyExistsException(String symbol) {
        super("symbol already exists: " + symbol);
    }
}
