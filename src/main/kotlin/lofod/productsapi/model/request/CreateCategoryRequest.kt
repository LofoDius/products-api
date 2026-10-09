package lofod.productsapi.model.request

import org.bson.types.ObjectId

data class CreateCategoryRequest(
    val parentId: ObjectId?,
    val name: String,
    val imageId: String?,
    /** Omitted means inherit the parent's connected fields; explicit [] means none. */
    val customFields: List<CustomFieldDefinitionDto>? = null,
)
