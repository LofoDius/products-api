package lofod.productsapi.model.response

import lofod.productsapi.model.PriceLevel
import lofod.productsapi.model.QualityLevel

data class CardResponse(
    val categoryId: String,
    val cardId: String,
    val name: String,
    val imageId: String? = null,
    val priceLevel: PriceLevel,
    val qualityLevel: QualityLevel,
    val description: String?,
)
