package lofod.productsapi.service.mapper

import lofod.productsapi.model.FullCategory
import lofod.productsapi.model.response.CategoryResponse
import org.springframework.stereotype.Component

@Component
class CategoryMapper {
    fun toView(category: FullCategory): CategoryResponse {
        val cards = category.cards
        return CategoryResponse(
            categoryId = category.categoryId.toString(),
            parentId = category.parentId?.toString(),
            name = category.name,
            subcategoriesAmount = category.subcategories.size,
            cardsAmount = cards.size,
            subcategories = category.subcategories.map { toView(it) },
            imageId = category.imageId?.toString(),
            role = category.role,
        )
    }
}
