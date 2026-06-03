package com.rostra.bidding.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record PlaceBidRequestDTO(
        @NotNull UUID auctionId,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount
) {}
