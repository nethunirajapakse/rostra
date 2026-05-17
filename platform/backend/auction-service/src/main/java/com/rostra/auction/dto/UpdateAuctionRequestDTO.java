package com.rostra.auction.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public record UpdateAuctionRequestDTO(
        @Size(max = 200) String title,
        @Size(max = 5000) String description,
        @DecimalMin(value = "0.01") BigDecimal startingPrice,
        @DecimalMin(value = "0.01") BigDecimal minIncrement,
        @Future Instant startsAt,
        @Future Instant endsAt
) {}