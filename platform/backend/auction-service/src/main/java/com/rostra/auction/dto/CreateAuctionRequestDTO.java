package com.rostra.auction.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;

public record CreateAuctionRequestDTO(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 5000) String description,
        @NotNull @DecimalMin(value = "0.01") BigDecimal startingPrice,
        @NotNull @DecimalMin(value = "0.01") BigDecimal minIncrement,
        @NotNull @Future Instant startsAt,
        @NotNull @Future Instant endsAt
) {}