package lofod.productsapi.service

import lofod.productsapi.exception.BadRequestException
import lofod.productsapi.exception.NotFoundException
import lofod.productsapi.model.Card
import lofod.productsapi.model.Category
import lofod.productsapi.model.CategoryRole
import lofod.productsapi.model.FullCategory
import lofod.productsapi.model.request.CreateCategoryRequest
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

        val category = categoryRepository.save(
            Category(
                name = categoryRequest.name,
                parentId = categoryRequest.parentId,
                ownerId = ownerId,
                memberIds = mutableListOf(),
                cards = mutableListOf(),
                imageId = ObjectIds.parseOptional(categoryRequest.imageId, "imageId"),
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

        val updatedCategory = Category(
            categoryId = category.categoryId,
            name = categoryRequest.name,
            parentId = newParentId,
            ownerId = category.ownerId,
            memberIds = category.memberIds,
            cards = cardsOf(category),
            imageId = newImageId,
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
