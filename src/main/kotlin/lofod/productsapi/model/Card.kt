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
    /** Numeric rating 0..10 (half-star UI maps ½ star = 1). Missing in old docs → 0. */
    val rating: Int = 0,
    val description: String?,
    /** Values keyed by fieldId; orphans for archived fields are retained. Missing in old docs → empty. */
    val customFieldValues: List<CustomFieldValue> = emptyList(),
)
