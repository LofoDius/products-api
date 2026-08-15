package lofod.productsapi.service

import lofod.productsapi.exception.BadRequestException
import lofod.productsapi.exception.NotFoundException
import lofod.productsapi.model.Card
import lofod.productsapi.model.Category
import lofod.productsapi.model.CategoryRole
import lofod.productsapi.model.CustomFieldDefinition
import lofod.productsapi.model.FullCategory
import lofod.productsapi.model.request.CreateCategoryRequest
import lofod.productsapi.model.request.CustomFieldDefinitionDto
import lofod.productsapi.model.request.UpdateCategoryRequest
import lofod.productsapi.model.response.CategoryResponse
import lofod.productsapi.repository.CategoryRepository
import lofod.productsapi.service.mapper.CategoryMapper
import lofod.productsapi.util.ObjectIds
import org.bson.types.ObjectId
import org.springframework.stereotype.Service

@Service
class CategoryService(
    private val categoryRepository: CategoryRepository,
    private val categoryMapper: CategoryMapper,
    private val imageService: ImageService,
    private val categoryAccessService: CategoryAccessService,
) {

    fun getAllCategories(): List<CategoryResponse> {
        val userId = categoryAccessService.currentUserId()
        return categoryRepository.findByParentIdIsNull()
            .filter { categoryAccessService.isAccessibleRoot(userId, it) }
            .map { processCategory(it, categoryAccessService.requireAccess(userId, it)) }
            .map { categoryMapper.toView(it) }
    }

    fun createCategory(categoryRequest: CreateCategoryRequest): CategoryResponse {
        val userId = categoryAccessService.currentUserId()

        val ownerId: ObjectId
        if (categoryRequest.parentId != null) {
            val parent = categoryRepository.getCategoryByCategoryId(categoryRequest.parentId)
                ?: throw NotFoundException("Не найдена категория с parentId=${categoryRequest.parentId}")
            categoryAccessService.requireOwner(userId, parent)
            ownerId = categoryAccessService.resolveRoot(parent).ownerId
        } else {
            ownerId = userId
        }

        val (customFields, customFieldArchive) = reconcileCustomFields(
            incoming = categoryRequest.customFields,
            currentActive = emptyList(),
            currentArchive = emptyList(),
        )

        val category = categoryRepository.save(
            Category(
                name = categoryRequest.name,
                parentId = categoryRequest.parentId,
                ownerId = ownerId,
                memberIds = mutableListOf(),
                cards = mutableListOf(),
                imageId = ObjectIds.parseOptional(categoryRequest.imageId, "imageId"),
                customFields = customFields,
                customFieldArchive = customFieldArchive,
            )
        )

        val role = categoryAccessService.requireAccess(userId, category)
        return categoryMapper.toView(processCategory(category, role))
    }

    fun updateCategory(id: ObjectId, categoryRequest: UpdateCategoryRequest): CategoryResponse {
        val userId = categoryAccessService.currentUserId()
        val category = categoryRepository.getCategoryByCategoryId(id)
            ?: throw NotFoundException("Категория с id=$id не найдена")

        categoryAccessService.requireOwner(userId, category)

        val newParentId = resolveParentIdForUpdate(category, categoryRequest.parentId)
        val newImageId = resolveImageIdForUpdate(category, categoryRequest.imageId)
        val (customFields, customFieldArchive) = if (categoryRequest.customFields != null) {
            reconcileCustomFields(
                incoming = categoryRequest.customFields,
                currentActive = category.customFields,
                currentArchive = category.customFieldArchive,
            )
        } else {
            category.customFields to category.customFieldArchive
        }

        val updatedCategory = Category(
            categoryId = category.categoryId,
            name = categoryRequest.name,
            parentId = newParentId,
            ownerId = category.ownerId,
            memberIds = category.memberIds,
            cards = cardsOf(category),
            imageId = newImageId,
            customFields = customFields,
            customFieldArchive = customFieldArchive,
        )

        val saved = categoryRepository.save(updatedCategory)
        val role = categoryAccessService.requireAccess(userId, saved)
        return categoryMapper.toView(processCategory(saved, role))
    }

    fun deleteCategory(id: ObjectId) {
        val userId = categoryAccessService.currentUserId()
        val category = categoryRepository.getCategoryByCategoryId(id)
            ?: throw NotFoundException("Не найдена категория с id=$id")

        categoryAccessService.requireOwner(userId, category)
        deleteSubtree(category)
    }

    /**
     * Reconciles the active custom-field schema with archive:
     * - restore by fieldId from archive, or by title+type when fieldId omitted
     * - keep existing active ids; type stays stable on update
     * - fields dropped from the incoming list move to archive (card values are not purged)
     */
    internal fun reconcileCustomFields(
        incoming: List<CustomFieldDefinitionDto>,
        currentActive: List<CustomFieldDefinition>,
        currentArchive: List<CustomFieldDefinition>,
    ): Pair<List<CustomFieldDefinition>, List<CustomFieldDefinition>> {
        if (incoming.size > 10) {
            throw BadRequestException("Допускается не более 10 активных пользовательских полей")
        }

        val activeById = currentActive.associateBy { it.fieldId }.toMutableMap()
        val archiveById = currentArchive.associateBy { it.fieldId }.toMutableMap()
        val keptActiveIds = mutableSetOf<ObjectId>()
        val resultActive = mutableListOf<CustomFieldDefinition>()

        incoming.forEachIndexed { index, dto ->
            val title = dto.title.trim()
            if (title.isEmpty()) {
                throw BadRequestException("Название пользовательского поля не может быть пустым (index=$index)")
            }

            val requestedId = dto.fieldId?.let { ObjectIds.parse(it, "customFields[$index].fieldId") }
            val definition = when {
                requestedId != null && archiveById.containsKey(requestedId) -> {
                    val archived = archiveById.remove(requestedId)!!
                    CustomFieldDefinition(
                        fieldId = archived.fieldId,
                        title = title,
                        type = archived.type,
                    )
                }
                requestedId != null && activeById.containsKey(requestedId) -> {
                    val existing = activeById.getValue(requestedId)
                    CustomFieldDefinition(
                        fieldId = existing.fieldId,
                        title = title,
                        type = existing.type,
                    )
                }
                requestedId == null -> {
                    val archivedMatch = archiveById.values.firstOrNull {
                        it.title == title && it.type == dto.type
                    }
                    if (archivedMatch != null) {
                        archiveById.remove(archivedMatch.fieldId)
                        CustomFieldDefinition(
                            fieldId = archivedMatch.fieldId,
                            title = title,
                            type = archivedMatch.type,
                        )
                    } else {
                        CustomFieldDefinition(
                            fieldId = ObjectId.get(),
                            title = title,
                            type = dto.type,
                        )
                    }
                }
                else -> CustomFieldDefinition(
                    fieldId = ObjectId.get(),
                    title = title,
                    type = dto.type,
                )
            }

            if (!keptActiveIds.add(definition.fieldId)) {
                throw BadRequestException("Дублирующийся fieldId в customFields: ${definition.fieldId.toHexString()}")
            }
            resultActive.add(definition)
        }

        currentActive.forEach { field ->
            if (field.fieldId !in keptActiveIds) {
                archiveById[field.fieldId] = field
            }
        }

        return resultActive to archiveById.values.toList()
    }

    private fun resolveParentIdForUpdate(category: Category, requestedParentId: ObjectId?): ObjectId? {
        if (requestedParentId == null || requestedParentId == category.parentId) {
            return category.parentId
        }

        validateMove(category, requestedParentId)
        return requestedParentId
    }

    private fun validateMove(category: Category, newParentId: ObjectId) {
        if (newParentId == category.categoryId) {
            throw BadRequestException("Категория не может быть родителем самой себе")
        }

        val newParent = categoryRepository.getCategoryByCategoryId(newParentId)
            ?: throw NotFoundException("Не найдена категория с parentId=$newParentId")

        val currentRoot = categoryAccessService.resolveRoot(category)
        val newRoot = categoryAccessService.resolveRoot(newParent)
        if (currentRoot.ownerId != newRoot.ownerId) {
            throw BadRequestException("Перемещение допускается только внутри дерева того же владельца")
        }

        if (wouldCreateCycle(category.categoryId, newParentId)) {
            throw BadRequestException("Перемещение создаёт цикл в дереве категорий")
        }
    }

    private fun wouldCreateCycle(categoryId: ObjectId, newParentId: ObjectId): Boolean {
        var currentId: ObjectId? = newParentId
        val visited = mutableSetOf<ObjectId>()
        while (currentId != null) {
            if (currentId == categoryId) return true
            if (!visited.add(currentId)) return true
            currentId = categoryRepository.getCategoryByCategoryId(currentId)?.parentId
        }
        return false
    }

    private fun resolveImageIdForUpdate(category: Category, requestedImageId: String?): ObjectId? {
        if (requestedImageId == null) {
            return category.imageId
        }

        val parsed = ObjectIds.parse(requestedImageId, "imageId")
        if (category.imageId != null && category.imageId != parsed) {
            imageService.deleteIfPresent(category.imageId)
        }
        return parsed
    }

    private fun processCategory(category: Category, role: CategoryRole): FullCategory {
        val children = categoryRepository.findByParentId(category.categoryId)
            .map { processCategory(it, role) }
            .toMutableList()

        return FullCategory(
            categoryId = category.categoryId,
            name = category.name,
            parentId = category.parentId,
            imageId = category.imageId,
            cards = cardsOf(category),
            customFields = category.customFields,
            customFieldArchive = category.customFieldArchive,
            subcategories = children,
            role = role,
        )
    }

    private fun deleteSubtree(category: Category) {
        categoryRepository.findByParentId(category.categoryId).forEach { child ->
            deleteSubtree(child)
        }
        imageService.deleteIfPresent(category.imageId)
        cardsOf(category).forEach { card ->
            imageService.deleteIfPresent(card.imageId)
        }
        categoryRepository.deleteCategoryByCategoryId(category.categoryId)
    }

    private fun cardsOf(category: Category): MutableList<Card> = category.cards
}
