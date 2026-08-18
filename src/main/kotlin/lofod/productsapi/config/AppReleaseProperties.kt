package lofod.productsapi.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.releases")
data class AppReleaseProperties(
    val path: String = "data/app-releases",
    val maxSizeBytes: Long = 100L * 1024 * 1024,
    /** Shared secret for POST /app/releases (X-Deploy-Token). Empty disables uploads. */
    val deployToken: String = "",
)
