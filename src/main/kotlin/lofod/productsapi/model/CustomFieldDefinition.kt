package lofod.productsapi.model

import org.bson.types.ObjectId

data class CustomFieldDefinition(
    val fieldId: ObjectId = ObjectId.get(),
    val title: String,
    val type: CustomFieldType,
)
