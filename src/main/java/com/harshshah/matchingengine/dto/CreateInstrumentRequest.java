package com.harshshah.matchingengine.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateInstrumentRequest(
        @NotBlank
        @Size(max = 32)
        @Pattern(regexp = "[A-Z0-9.\\-]+", message = "symbol must be upper case letters, digits, dot or dash")
        String symbol) {
}
