package lofod.productsapi.model.response

import lofod.productsapi.model.CategoryRole
import lofod.productsapi.model.request.CustomFieldDefinitionDto

data class CategoryResponse(
    val name: String,
    val categoryId: String,
    val parentId: String? = null,
    val subcategoriesAmount: Int,
    val cardsAmount: Int,
    val subcategories: List<CategoryResponse>,
    val imageId: String?,
    val customFields: List<CustomFieldDefinitionDto> = emptyList(),
    val customFieldArchive: List<CustomFieldDefinitionDto> = emptyList(),
    val role: CategoryRole,
)
