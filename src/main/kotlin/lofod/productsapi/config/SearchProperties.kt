package lofod.productsapi.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.search")
data class SearchProperties(
    val indexPath: String = "data/search-index",
)
