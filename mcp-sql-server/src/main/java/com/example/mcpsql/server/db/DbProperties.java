package com.example.mcpsql.server.db;

import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Connection settings. The admin credentials are used only for Flyway at startup. */
@Validated
@ConfigurationProperties("app.db")
public record DbProperties(
        @NotBlank String url,
        @NotBlank String adminUser,
        @NotBlank String adminPassword,
        @NotBlank String appUser,
        @NotBlank String appPassword) {

    private static final String JDBC_PREFIX = "jdbc:";
    private static final Pattern IDENTIFIER = Pattern.compile("[a-z_][a-z0-9_]*");

    /** The database name from the JDBC URL, validated because Flyway substitutes it into V3. */
    public String databaseName() {
        String path = url.startsWith(JDBC_PREFIX) ? URI.create(url.substring(JDBC_PREFIX.length())).getPath() : null;
        String name = path == null || path.isEmpty() ? "" : path.substring(1);
        if (!IDENTIFIER.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid database name in app.db.url: '" + name + "'");
        }
        return name;
    }
}
