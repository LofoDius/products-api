package lofod.productsapi.service

import lofod.productsapi.exception.*
import lofod.productsapi.model.*
import lofod.productsapi.model.request.CustomFieldValueDto
import lofod.productsapi.model.request.UpdateCardRequest
import lofod.productsapi.repository.CategoryRepository
import lofod.productsapi.service.search.SearchIndex
import org.bson.types.ObjectId
import org.springframework.data.mongodb.MongoDatabaseFactory
import org.springframework.data.mongodb.MongoTransactionManager
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.security.MessageDigest

data class MoveRequest(val sourceId: String, val targetId: String, val categoryId: String? = null,
    val cardId: String? = null, val token: String? = null, val record: UpdateCardRequest? = null)
data class MovePreview(val sourcePath: String, val targetPath: String, val name: String,
    val removedFields: List<lofod.productsapi.model.request.CustomFieldDefinitionDto>,
    val addedFields: List<lofod.productsapi.model.request.CustomFieldDefinitionDto>, val token: String)

@Service
class CatalogMoveService(
    private val workspace: CatalogWorkspaceService,
    private val categories: CategoryRepository,
    private val access: CategoryAccessService,
    private val cards: CardService,
    private val search: SearchIndex,
    private val database: MongoDatabaseFactory,
) {
    private val transactions = TransactionTemplate(MongoTransactionManager(database))

    private fun path(category: Category): String {
        val names = mutableListOf(category.name)
        var parent = category.parentId
        val visited = mutableSetOf(category.categoryId)
        while (parent != null && visited.add(parent)) {
            val node = workspace.category(parent.toHexString())
            names.add(node.name); parent = node.parentId
        }
        return (listOf("Каталог") + names.reversed()).joinToString(" › ")
    }

    fun prepare(request: MoveRequest): MovePreview {
        if ((request.cardId == null) == (request.categoryId == null)) throw BadRequestException("Выберите один элемент")
        if (request.targetId == "-1") throw BadRequestException("Выберите категорию назначения")
        val target = workspace.category(request.targetId)
        val source = if (request.sourceId == "-1") null else workspace.category(request.sourceId)
        val user = access.currentUserId()
        access.requireAccess(user, target)
        source?.let { access.requireAccess(user, it) }
        val moved = request.categoryId?.let { workspace.category(it) }
        if (moved != null) {
            access.requireOwner(user, moved); access.requireOwner(user, target)
            if (moved.parentId?.toHexString() != source?.categoryId?.toHexString()) throw ConflictException("Папка уже перемещена")
            var node: Category? = target
            val seen = mutableSetOf<ObjectId>()
            while (node != null && seen.add(node.categoryId)) {
                if (node.categoryId == moved.categoryId) throw BadRequestException("Нельзя переместить папку в себя или потомка")
                node = node.parentId?.let { workspace.category(it.toHexString()) }
            }
            if (access.resolveRoot(moved).categoryId != access.resolveRoot(target).categoryId)
                throw BadRequestException("Перенос между корневыми каталогами требует настройки соответствия полей")
        }
        if (source?.categoryId == target.categoryId) throw BadRequestException("Элемент уже находится здесь")
        val card = request.cardId?.let { id -> source?.cards?.firstOrNull { it.cardId.toHexString() == id }
            ?: throw NotFoundException("Запись уже перемещена или удалена") }
        if (card != null && access.resolveRoot(source!!).categoryId != access.resolveRoot(target).categoryId)
            throw BadRequestException("Перенос между корневыми каталогами требует настройки соответствия полей")
        val from = if (card != null) workspace.active(source!!) else emptyList()
        val to = if (card != null) workspace.active(target) else emptyList()
        fun dto(field: CustomFieldDefinition) = lofod.productsapi.model.request.CustomFieldDefinitionDto(
            field.fieldId.toHexString(), field.title, field.type)
        val raw = listOf(source?.toString(), target.toString(), moved?.toString(), card?.toString(), from.toString(), to.toString()).joinToString("|")
        val token = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
        return MovePreview(source?.let { path(it) } ?: "Каталог", path(target), moved?.name ?: card!!.name,
            from.filter { old -> to.none { it.fieldId == old.fieldId } }.map { dto(it) },
            to.filter { new -> from.none { it.fieldId == new.fieldId } }.map { dto(it) }, token)
    }

    fun confirm(request: MoveRequest) {
        val preview = prepare(request)
        if (request.token != preview.token) throw ConflictException("Запись или поля изменились. Проверьте перенос заново")
        val hello = database.mongoDatabase.runCommand(org.bson.Document("hello", 1))
        if (hello["setName"] == null && hello["msg"] != "isdbgrid")
            throw BadRequestException("Для безопасного переноса серверу требуется MongoDB replica set")
        val categoryId = request.categoryId
        if (categoryId != null) {
            val node = workspace.category(categoryId)
            val target = workspace.category(request.targetId)
            transactions.executeWithoutResult {
                if (prepare(request).token != request.token) throw ConflictException("Данные изменились")
                categories.save(node.copy(parentId = target.categoryId))
            }
            search.indexCategory(workspace.category(categoryId), access.resolveRoot(target).categoryId)
        } else {
            val source = workspace.category(request.sourceId)
            val target = workspace.category(request.targetId)
            val existing = source.cards.first { it.cardId.toHexString() == request.cardId }
            val active = workspace.active(target)
            if ((preview.removedFields.isNotEmpty() || preview.addedFields.isNotEmpty()) && request.record == null)
                throw BadRequestException("Проверьте поля в редакторе перед переносом")
            val edited = request.record?.let { record ->
                if (record.name.isBlank() || record.rating !in 0..10) throw BadRequestException("Проверьте название и рейтинг")
                existing.copy(name = record.name.trim(), priceLevel = record.priceLevel,
                    qualityLevel = record.qualityLevel, rating = record.rating, description = record.description,
                    imageId = record.imageId?.let { ObjectId(it) }, customFieldValues = cards.mergeCustomFieldValues(
                        existing.customFieldValues, record.customFieldValues, active))
            } ?: existing
            transactions.executeWithoutResult {
                if (prepare(request).token != request.token) throw ConflictException("Данные изменились")
                val from = workspace.category(request.sourceId)
                val to = workspace.category(request.targetId)
                categories.save(from.copy(cards = from.cards.filterNot { it.cardId == existing.cardId }.toMutableList()))
                categories.save(to.copy(cards = (to.cards + edited).toMutableList()))
            }
            search.indexCard(edited, target, access.resolveRoot(target).categoryId)
        }
    }
}
