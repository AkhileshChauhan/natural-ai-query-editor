package com.example.mcpsql.client.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ExecuteRequest(@NotBlank @Size(max = 20_000) String sql) {
}
