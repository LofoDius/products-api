package lofod.productsapi.model.request

import org.bson.types.ObjectId

data class CreateCategoryRequest(
    val parentId: ObjectId?,
    val name: String,
    val imageId: String?
)
