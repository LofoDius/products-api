package lofod.productsapi.model

import org.bson.types.ObjectId

data class LiteCategory (
    val categoryId: ObjectId,
    val subcategories: List<LiteCategory>
)
