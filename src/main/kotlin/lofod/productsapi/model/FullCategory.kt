package lofod.productsapi.model

import org.bson.types.ObjectId

data class FullCategory(
    val categoryId: ObjectId,
    val name: String,
    val subcategories: MutableList<FullCategory> = mutableListOf(),
    val parentId: ObjectId?,
    val cards: MutableList<Card> = mutableListOf(),
    val imageId: ObjectId?,
    val customFields: List<CustomFieldDefinition> = emptyList(),
    val customFieldArchive: List<CustomFieldDefinition> = emptyList(),
    val role: CategoryRole,
)
