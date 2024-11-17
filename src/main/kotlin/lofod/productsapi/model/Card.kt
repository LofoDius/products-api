package lofod.productsapi.model

import org.bson.types.ObjectId
import org.springframework.data.annotation.Id

data class Card(
    @Id
    val cardId: ObjectId = ObjectId.get(),

    val name: String,
    val imageId: ObjectId? = null,
    val priceLevel: PriceLevel,
    val qualityLevel: QualityLevel,
    val description: String?,
)
