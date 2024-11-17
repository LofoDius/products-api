package lofod.productsapi.service.mapper

import lofod.productsapi.model.FullCategory
import lofod.productsapi.model.response.CategoryResponse
import org.springframework.stereotype.Component

@Component
class CategoryMapper {
    fun toView(category: FullCategory): CategoryResponse {
        return CategoryResponse(
            categoryId = category.categoryId.toString(),
            name = category.name,
            subcategoriesAmount = category.subcategories.size,
            cardsAmount = category.cards.size,
            subcategories = processSubcategories(category.subcategories),
            imageId = category.imageId?.toString()
        )
    }

    private fun processSubcategories(categories: MutableList<FullCategory>): List<CategoryResponse> {
        val categoryResponses = mutableListOf<CategoryResponse>()
        categories.forEach { category ->
            categoryResponses.add(
                CategoryResponse(
                    categoryId = category.categoryId.toString(),
                    name = category.name,
                    subcategoriesAmount = category.subcategories.size,
                    cardsAmount = category.cards.size,
                    subcategories = processSubcategories(category.subcategories),
                    imageId = category.imageId?.toString()
                )
            )
        }

        return categoryResponses
    }
}
