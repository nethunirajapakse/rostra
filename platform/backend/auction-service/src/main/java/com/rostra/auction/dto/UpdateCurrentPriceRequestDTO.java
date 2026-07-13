package com.rostra.auction.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UpdateCurrentPriceRequestDTO(
        @NotNull @DecimalMin("0.01") BigDecimal newPrice,
        @NotNull Long expectedVersion
) {}