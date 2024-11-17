package lofod.productsapi.model.request

import lofod.productsapi.model.PriceLevel
import lofod.productsapi.model.QualityLevel

data class UpdateCardRequest(
    val name: String,
    val imageId: String?,
    val priceLevel: PriceLevel,
    val qualityLevel: QualityLevel,
    val description: String?
)
