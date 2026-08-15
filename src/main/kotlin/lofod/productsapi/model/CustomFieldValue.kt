package lofod.productsapi.model

import org.bson.types.ObjectId

data class CustomFieldValue(
    val fieldId: ObjectId,
    val value: String?,
)
