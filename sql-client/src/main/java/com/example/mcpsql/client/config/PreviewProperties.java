package com.example.mcpsql.client.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Signs preview tokens. The secret is long enough to be a full-strength HMAC-SHA256 key. */
@Validated
@ConfigurationProperties("app.preview")
public record PreviewProperties(@Size(min = 32) @NotNull String secret, @NotNull Duration ttl) {
}
