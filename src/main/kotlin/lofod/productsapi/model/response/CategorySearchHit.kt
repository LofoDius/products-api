package lofod.productsapi.model.response

import lofod.productsapi.model.CategoryRole

data class CategorySearchHit(
    val categoryId: String,
    val name: String,
    val parentId: String?,
    val imageId: String?,
    val role: CategoryRole,
)
