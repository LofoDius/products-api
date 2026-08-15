package lofod.productsapi.service.mapper

import lofod.productsapi.model.CustomFieldDefinition
import lofod.productsapi.model.FullCategory
import lofod.productsapi.model.request.CustomFieldDefinitionDto
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
            customFields = category.customFields.map { toDto(it) },
            customFieldArchive = category.customFieldArchive.map { toDto(it) },
            role = category.role,
        )
    }

    fun toDto(definition: CustomFieldDefinition): CustomFieldDefinitionDto =
        CustomFieldDefinitionDto(
            fieldId = definition.fieldId.toHexString(),
            title = definition.title,
            type = definition.type,
        )
}
