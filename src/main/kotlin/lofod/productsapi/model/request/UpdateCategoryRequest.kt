package lofod.productsapi.model.request

import org.bson.types.ObjectId

data class UpdateCategoryRequest(
    val parentId: ObjectId?,
    val name: String,
    val imageId: ObjectId?,
)
