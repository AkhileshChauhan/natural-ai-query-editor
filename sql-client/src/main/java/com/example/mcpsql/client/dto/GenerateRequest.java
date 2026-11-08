package com.example.mcpsql.client.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GenerateRequest(@NotBlank @Size(max = 2_000) String question) {
}
