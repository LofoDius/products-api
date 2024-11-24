package lofod.productsapi.model.response

data class CategoryResponse(
    val name : String,
    val categoryId: String,
    val parentId: String? = null,
    val subcategoriesAmount: Int,
    val cardsAmount: Int,
    val subcategories: List<CategoryResponse>,
    val imageId: String?,
)
