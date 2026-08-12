package lofod.productsapi.service

import lofod.productsapi.exception.ForbiddenException
import lofod.productsapi.exception.UnauthorizedException
import lofod.productsapi.model.Category
import lofod.productsapi.model.CategoryRole
import lofod.productsapi.repository.CategoryRepository
import lofod.productsapi.security.UserPrincipal
import org.bson.types.ObjectId
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service

@Service
class CategoryAccessService(
    private val categoryRepository: CategoryRepository,
) {

    fun currentPrincipal(): UserPrincipal {
        return SecurityContextHolder.getContext().authentication?.principal as? UserPrincipal
            ?: throw UnauthorizedException("Не авторизован")
    }

    fun currentUserId(): ObjectId = ObjectId(currentPrincipal().userId)

    fun resolveRoot(category: Category): Category {
        var current = category
        val visited = mutableSetOf<ObjectId>()
        while (current.parentId != null) {
            if (!visited.add(current.categoryId)) {
                throw IllegalStateException("Цикл в дереве категорий")
            }
            current = categoryRepository.getCategoryByCategoryId(current.parentId!!)
                ?: throw IllegalStateException("Не найден родитель категории ${current.categoryId}")
        }
        return current
    }

    fun roleOf(userId: ObjectId, category: Category): CategoryRole? {
        val root = resolveRoot(category)
        return when {
            root.ownerId == userId -> CategoryRole.OWNER
            root.memberIds.contains(userId) -> CategoryRole.MEMBER
            else -> null
        }
    }

    fun requireAccess(userId: ObjectId, category: Category): CategoryRole {
        return roleOf(userId, category)
            ?: throw ForbiddenException("Нет доступа к категории")
    }

    fun requireOwner(userId: ObjectId, category: Category) {
        val role = requireAccess(userId, category)
        if (role != CategoryRole.OWNER) {
            throw ForbiddenException("Только владелец может выполнить эту операцию")
        }
    }

    fun isAccessibleRoot(userId: ObjectId, root: Category): Boolean {
        if (root.parentId != null) return false
        return root.ownerId == userId || root.memberIds.contains(userId)
    }
}
