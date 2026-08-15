package lofod.productsapi.model.request

import org.bson.types.ObjectId

data class UpdateCategoryRequest(
    val parentId: ObjectId?,
    val name: String,
    val imageId: String?,
    /** When null/omitted, existing schema is left unchanged. When present, full active list is reconciled. */
    val customFields: List<CustomFieldDefinitionDto>? = null,
)
