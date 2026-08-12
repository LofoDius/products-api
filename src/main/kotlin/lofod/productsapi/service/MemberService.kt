package lofod.productsapi.service

import lofod.productsapi.exception.BadRequestException
import lofod.productsapi.exception.ConflictException
import lofod.productsapi.exception.NotFoundException
import lofod.productsapi.model.request.InviteMemberRequest
import lofod.productsapi.model.response.MemberResponse
import lofod.productsapi.repository.CategoryRepository
import lofod.productsapi.repository.UserRepository
import org.bson.types.ObjectId
import org.springframework.stereotype.Service

@Service
class MemberService(
    private val categoryRepository: CategoryRepository,
    private val userRepository: UserRepository,
    private val categoryAccessService: CategoryAccessService,
) {

    fun inviteMember(categoryId: ObjectId, request: InviteMemberRequest): MemberResponse {
        val userId = categoryAccessService.currentUserId()
        val category = categoryRepository.getCategoryByCategoryId(categoryId)
            ?: throw NotFoundException("Категория с id=$categoryId не найдена")

        categoryAccessService.requireOwner(userId, category)
        val root = categoryAccessService.resolveRoot(category)

        val username = request.username.trim()
        if (username.isBlank()) {
            throw BadRequestException("username обязателен")
        }

        val invitee = userRepository.findByUsername(username)
            ?: throw NotFoundException("Пользователь с username=$username не найден")

        if (invitee.userId == root.ownerId) {
            throw ConflictException("Владелец уже имеет доступ к категории")
        }
        if (root.memberIds.contains(invitee.userId)) {
            throw ConflictException("Пользователь уже является участником")
        }

        root.memberIds.add(invitee.userId)
        categoryRepository.save(root)

        return MemberResponse(
            userId = invitee.userId.toHexString(),
            username = invitee.username,
        )
    }

    fun removeMember(categoryId: ObjectId, memberUserId: ObjectId) {
        val userId = categoryAccessService.currentUserId()
        val category = categoryRepository.getCategoryByCategoryId(categoryId)
            ?: throw NotFoundException("Категория с id=$categoryId не найдена")

        categoryAccessService.requireOwner(userId, category)
        val root = categoryAccessService.resolveRoot(category)

        if (memberUserId == root.ownerId) {
            throw BadRequestException("Нельзя удалить владельца из участников")
        }
        if (!root.memberIds.remove(memberUserId)) {
            throw NotFoundException("Участник с id=$memberUserId не найден")
        }

        categoryRepository.save(root)
    }

    fun listMembers(categoryId: ObjectId): List<MemberResponse> {
        val userId = categoryAccessService.currentUserId()
        val category = categoryRepository.getCategoryByCategoryId(categoryId)
            ?: throw NotFoundException("Категория с id=$categoryId не найдена")

        categoryAccessService.requireAccess(userId, category)
        val root = categoryAccessService.resolveRoot(category)

        return root.memberIds.mapNotNull { memberId ->
            val user = userRepository.findByUserId(memberId) ?: return@mapNotNull null
            MemberResponse(
                userId = user.userId.toHexString(),
                username = user.username,
            )
        }
    }
}
