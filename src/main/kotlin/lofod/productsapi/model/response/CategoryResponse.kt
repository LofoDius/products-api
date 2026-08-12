package lofod.productsapi.model.response

import lofod.productsapi.model.CategoryRole

data class CategoryResponse(
    val name: String,
    val categoryId: String,
    val parentId: String? = null,
    val subcategoriesAmount: Int,
    val cardsAmount: Int,
    val subcategories: List<CategoryResponse>,
    val imageId: String?,
    val role: CategoryRole,
)
