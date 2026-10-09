package lofod.productsapi.service

import lofod.productsapi.exception.BadRequestException
import lofod.productsapi.exception.NotFoundException
import lofod.productsapi.model.*
import lofod.productsapi.model.request.CustomFieldDefinitionDto
import lofod.productsapi.repository.CategoryRepository
import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria.where
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import org.springframework.http.HttpStatus

data class CatalogField(
    @Id val id: String,
    val rootCategoryId: String,
    val fieldId: String,
    val title: String,
    val type: CustomFieldType,
    val archived: Boolean = false,
    val revision: Long = 0,
)
data class FieldView(val fieldId: String, val title: String, val type: CustomFieldType,
    val archived: Boolean, val revision: Long, val categoryIds: List<String>)
data class FieldChange(val title: String, val type: CustomFieldType, val archived: Boolean = false,
    val revision: Long = 0)
data class CatalogPreference(@Id val id: String, val pageId: String, val mode: String = "CUSTOM",
    val categoryIds: List<String> = emptyList(), val cardIds: List<String> = emptyList(), val revision: Long = 0)

@Service
class CatalogWorkspaceService(
    private val mongo: MongoTemplate,
    private val categories: CategoryRepository,
    private val access: CategoryAccessService,
) {
    fun category(id: String): Category = categories.getCategoryByCategoryId(ObjectId(id))
        ?: throw NotFoundException("Категория не найдена")

    fun root(id: String): Category = access.resolveRoot(category(id))

    private fun tree(category: Category): List<Category> = listOf(category) +
        categories.findByParentId(category.categoryId).flatMap { tree(it) }

    /** Additive, idempotent import: original definitions/values are never rewritten. */
    fun definitions(category: Category): List<CatalogField> {
        val root = access.resolveRoot(category)
        val rootId = root.categoryId.toHexString()
        val existing = mongo.find(Query(where("rootCategoryId").`is`(rootId)), CatalogField::class.java)
        val byId = existing.associateBy { it.fieldId }.toMutableMap()
        val nodes = tree(root)
        val legacy = nodes.flatMap { it.customFields + it.customFieldArchive }.groupBy { it.fieldId }
        legacy.forEach { (id, variants) ->
            val key = id.toHexString()
            if (key !in byId) {
                if (variants.map { it.title to it.type }.distinct().size != 1)
                    throw BadRequestException("Конфликт определений поля $key; требуется проверка миграции")
                val field = variants.first()
                val archived = nodes.none { node -> (node.fieldIds ?: node.customFields.map { it.fieldId }).contains(id) }
                val document = CatalogField("$rootId:$key", rootId, key, field.title, field.type, archived)
                try { mongo.insert(document) } catch (_: org.springframework.dao.DuplicateKeyException) { }
                byId[key] = mongo.findById(document.id, CatalogField::class.java) ?: document
            }
        }
        return byId.values.toList()
    }

    fun active(category: Category, shared: List<CatalogField>? = null): List<CustomFieldDefinition> {
        val catalog = (shared ?: definitions(category)).associateBy { it.fieldId }
        return (category.fieldIds ?: category.customFields.map { it.fieldId }).map { id ->
            val definition = catalog[id.toHexString()] ?: throw BadRequestException("Поле отсутствует в каталоге")
            CustomFieldDefinition(id, definition.title, definition.type)
        }
    }

    fun fields(categoryId: String): List<FieldView> {
        val category = category(categoryId)
        access.requireAccess(access.currentUserId(), category)
        val nodes = tree(access.resolveRoot(category))
        return definitions(category).map { field -> FieldView(field.fieldId, field.title, field.type,
            field.archived, field.revision, nodes.filter { node ->
                (node.fieldIds ?: node.customFields.map { it.fieldId }).any { it.toHexString() == field.fieldId }
            }.map { it.categoryId.toHexString() }) }
    }

    fun changeField(categoryId: String, fieldId: String?, change: FieldChange): FieldView {
        val category = category(categoryId)
        access.requireOwner(access.currentUserId(), category)
        val rootId = access.resolveRoot(category).categoryId.toHexString()
        definitions(category)
        val title = change.title.trim()
        if (title.isEmpty()) throw BadRequestException("Введите название поля")
        val key = fieldId ?: ObjectId.get().toHexString()
        val id = "$rootId:$key"
        if (fieldId == null) {
            mongo.insert(CatalogField(id, rootId, key, title, change.type))
        } else {
            val old = mongo.findById(id, CatalogField::class.java) ?: throw NotFoundException("Поле не найдено")
            if (old.type != change.type) throw BadRequestException("Тип поля нельзя изменить. Создайте новое поле")
            val result = mongo.updateFirst(Query(where("_id").`is`(id).and("revision").`is`(change.revision)),
                Update().set("title", title).set("archived", change.archived).inc("revision", 1), CatalogField::class.java)
            if (result.matchedCount == 0L) conflict()
        }
        return fields(categoryId).first { it.fieldId == key }
    }

    fun schema(category: Category, incoming: List<CustomFieldDefinitionDto>,
        inherited: List<CustomFieldDefinition> = emptyList()): Pair<List<CustomFieldDefinition>, List<CustomFieldDefinition>> {
        if (incoming.size > 10) throw BadRequestException("Максимум 10 полей")
        val catalog = definitions(category).associateBy { it.fieldId }.toMutableMap()
        val seen = mutableSetOf<String>()
        val result = incoming.map { dto ->
            val id = dto.fieldId
            val field = if (id == null) {
                changeField(category.categoryId.toHexString(), null, FieldChange(dto.title, dto.type)).also {
                    catalog[it.fieldId] = CatalogField("", "", it.fieldId, it.title, it.type)
                }.let { catalog.getValue(it.fieldId) }
            } else catalog[id] ?: throw BadRequestException("Поле не относится к каталогу корневой категории")
            if (!seen.add(field.fieldId)) throw BadRequestException("Поле уже подключено")
            if (field.archived && (category.customFields + inherited).none { it.fieldId.toHexString() == field.fieldId })
                throw BadRequestException("Архивное поле нельзя подключить заново")
            CustomFieldDefinition(ObjectId(field.fieldId), field.title, field.type)
        }
        val archived = (category.customFieldArchive + category.customFields).distinctBy { it.fieldId }
            .filter { it.fieldId.toHexString() !in seen }
        return result to archived
    }

    fun preference(pageId: String): CatalogPreference {
        checkPage(pageId)
        val id = "${access.currentUserId().toHexString()}:$pageId"
        return mongo.findById(id, CatalogPreference::class.java) ?: CatalogPreference(id, pageId)
    }

    fun savePreference(pageId: String, request: CatalogPreference): CatalogPreference {
        checkPage(pageId)
        if (request.mode !in setOf("CUSTOM", "ASC", "DESC")) throw BadRequestException("Неизвестная сортировка")
        val children = if (pageId == "-1") categories.findByParentIdIsNull()
            .filter { access.isAccessibleRoot(access.currentUserId(), it) } else categories.findByParentId(ObjectId(pageId))
        val cards = if (pageId == "-1") emptyList() else category(pageId).cards
        if (request.categoryIds.distinct().size != request.categoryIds.size ||
            request.cardIds.distinct().size != request.cardIds.size ||
            request.categoryIds.any { id -> children.none { it.categoryId.toHexString() == id } } ||
            request.cardIds.any { id -> cards.none { it.cardId.toHexString() == id } })
            throw BadRequestException("Порядок содержит недоступные или повторяющиеся элементы")
        val old = preference(pageId)
        val next = request.copy(id = old.id, pageId = pageId, revision = old.revision + 1)
        if (request.revision != old.revision) conflict()
        if (mongo.exists(Query(where("_id").`is`(old.id)), CatalogPreference::class.java)) {
            val result = mongo.updateFirst(Query(where("_id").`is`(old.id).and("revision").`is`(old.revision)),
                Update().set("mode", next.mode).set("categoryIds", next.categoryIds)
                    .set("cardIds", next.cardIds).set("revision", next.revision), CatalogPreference::class.java)
            if (result.matchedCount == 0L) conflict()
        } else try { mongo.insert(next) } catch (_: org.springframework.dao.DuplicateKeyException) { conflict() }
        return next
    }
    private fun checkPage(id: String) { if (id != "-1") access.requireAccess(access.currentUserId(), category(id)) }
    private fun conflict(): Nothing = throw lofod.productsapi.exception.ConflictException("Данные изменились. Обновите страницу")
}
