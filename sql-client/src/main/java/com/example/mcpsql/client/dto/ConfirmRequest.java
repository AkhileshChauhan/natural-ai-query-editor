package com.example.mcpsql.client.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConfirmRequest(@NotBlank @Size(max = 20_000) String sql, @NotBlank String previewToken) {
}
