package com.harshshah.matchingengine.dto;

import java.util.List;

public record OrderBookResponse(String symbol,
                                List<PriceLevelResponse> bids,
                                List<PriceLevelResponse> asks) {
}
