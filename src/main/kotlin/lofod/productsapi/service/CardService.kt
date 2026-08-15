package lofod.productsapi.service

import lofod.productsapi.exception.BadRequestException
import lofod.productsapi.exception.NotFoundException
import lofod.productsapi.model.Card
import lofod.productsapi.model.CustomFieldDefinition
import lofod.productsapi.model.CustomFieldType
import lofod.productsapi.model.CustomFieldValue
import lofod.productsapi.model.request.CreateCardRequest
import lofod.productsapi.model.request.CustomFieldValueDto
import lofod.productsapi.model.request.UpdateCardRequest
import lofod.productsapi.model.response.CardResponse
import lofod.productsapi.repository.CategoryRepository
import lofod.productsapi.service.mapper.CardMapper
import lofod.productsapi.util.ObjectIds
import org.bson.types.ObjectId
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.format.DateTimeParseException

@Service
class CardService(
    private val categoryRepository: CategoryRepository,
    private val cardMapper: CardMapper,
    private val imageService: ImageService,
    private val categoryAccessService: CategoryAccessService,
) {

    fun createCard(categoryId: ObjectId, request: CreateCardRequest): List<CardResponse> {
        val userId = categoryAccessService.currentUserId()
        val category = categoryRepository.getCategoryByCategoryId(categoryId)
            ?: throw NotFoundException("Не найдена категория с id=$categoryId")

        categoryAccessService.requireAccess(userId, category)
        requireValidRating(request.rating)
        val customFieldValues = mergeCustomFieldValues(
            existing = emptyList(),
            incoming = request.customFieldValues,
            activeFields = category.customFields,
        )

        category.cards.add(
            Card(
                name = request.name,
                imageId = ObjectIds.parseOptional(request.imageId, "imageId"),
                priceLevel = request.priceLevel,
                qualityLevel = request.qualityLevel,
                rating = request.rating,
                description = request.description,
                customFieldValues = customFieldValues,
            )
        )

        return categoryRepository.save(category).cards.map { cardMapper.toView(categoryId, it) }
    }

    fun getCardsOfCategory(categoryId: ObjectId): List<CardResponse> {
        val userId = categoryAccessService.currentUserId()
        val category = categoryRepository.getCategoryByCategoryId(categoryId)
            ?: throw NotFoundException("Не найдена категория с id=$categoryId")

        categoryAccessService.requireAccess(userId, category)
        return category.cards.map { cardMapper.toView(categoryId, it) }
    }

    fun updateCard(categoryId: ObjectId, cardId: ObjectId, request: UpdateCardRequest): List<CardResponse> {
        val userId = categoryAccessService.currentUserId()
        val category = categoryRepository.getCategoryByCategoryId(categoryId)
            ?: throw NotFoundException("Не найдено категории с id=$categoryId")

        categoryAccessService.requireAccess(userId, category)
        requireValidRating(request.rating)

        val index = category.cards.indexOfFirst { it.cardId == cardId }
        if (index < 0) {
            throw NotFoundException("В категории с id=$categoryId не найдено карточки с id=$cardId")
        }

        val existing = category.cards[index]
        val newImageId = ObjectIds.parseOptional(request.imageId, "imageId")
        if (existing.imageId != null && existing.imageId != newImageId) {
            imageService.deleteIfPresent(existing.imageId)
        }

        val customFieldValues = mergeCustomFieldValues(
            existing = existing.customFieldValues,
            incoming = request.customFieldValues,
            activeFields = category.customFields,
        )

        category.cards[index] = Card(
            cardId = existing.cardId,
            name = request.name,
            imageId = newImageId,
            priceLevel = request.priceLevel,
            qualityLevel = request.qualityLevel,
            rating = request.rating,
            description = request.description,
            customFieldValues = customFieldValues,
        )

        categoryRepository.save(category)
        return category.cards.map { cardMapper.toView(categoryId, it) }
    }

    fun getCard(categoryId: ObjectId, cardId: ObjectId): CardResponse {
        val userId = categoryAccessService.currentUserId()
        val category = categoryRepository.getCategoryByCategoryId(categoryId)
            ?: throw NotFoundException("Не найдено категории с id=$categoryId")

        categoryAccessService.requireAccess(userId, category)

        val card = category.cards.firstOrNull { it.cardId == cardId }
            ?: throw NotFoundException("В категории с id=$categoryId не найдено карточки с id=$cardId")

        return cardMapper.toView(categoryId, card)
    }

    fun deleteCard(categoryId: ObjectId, cardId: ObjectId) {
        val userId = categoryAccessService.currentUserId()
        val category = categoryRepository.getCategoryByCategoryId(categoryId)
            ?: throw NotFoundException("Не найдено категории с id=$categoryId")

        categoryAccessService.requireAccess(userId, category)

        val card = category.cards.firstOrNull { it.cardId == cardId }
            ?: throw NotFoundException("В категории с id=$categoryId не найдено карточки с id=$cardId")

        imageService.deleteIfPresent(card.imageId)
        category.cards.remove(card)
        categoryRepository.save(category)
    }

    fun searchCard(query: String): List<CardResponse> {
        val userId = categoryAccessService.currentUserId()
        val normalizedQuery = query.trim()
        val tokens = normalizedQuery.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }

        val accessible = categoryRepository.findAll()
            .filter { categoryAccessService.roleOf(userId, it) != null }

        val cardsWithCategory = accessible.flatMap { category ->
            category.cards.map { card -> category.categoryId to card }
        }

        val result = linkedSetOf<Pair<ObjectId, Card>>()
        val matchesInName = mutableMapOf<Int, MutableList<Pair<ObjectId, Card>>>()
        val matchesInDescription = mutableMapOf<Int, MutableList<Pair<ObjectId, Card>>>()

        cardsWithCategory.forEach { pair ->
            val card = pair.second
            if (literalEquals(card.name, normalizedQuery)) {
                result.add(pair)
            } else {
                accumulateTokenMatches(tokens, card.name, pair, matchesInName)
            }
        }
        appendByMatchCount(matchesInName, tokens.size, result)

        cardsWithCategory.forEach { pair ->
            val description = pair.second.description ?: return@forEach
            if (literalEquals(description, normalizedQuery)) {
                result.add(pair)
            } else {
                accumulateTokenMatches(tokens, description, pair, matchesInDescription)
            }
        }
        appendByMatchCount(matchesInDescription, tokens.size, result)

        return result.map { (categoryId, card) -> cardMapper.toView(categoryId, card) }
    }

    /**
     * Validates incoming values against the active schema, updates those fieldIds,
     * and keeps orphan values whose fieldId is not in the active schema.
     */
    internal fun mergeCustomFieldValues(
        existing: List<CustomFieldValue>,
        incoming: List<CustomFieldValueDto>,
        activeFields: List<CustomFieldDefinition>,
    ): List<CustomFieldValue> {
        val activeById = activeFields.associateBy { it.fieldId }
        val merged = existing.associateBy { it.fieldId }.toMutableMap()
        val seenIncoming = mutableSetOf<ObjectId>()

        incoming.forEachIndexed { index, dto ->
            val fieldId = ObjectIds.parse(dto.fieldId, "customFieldValues[$index].fieldId")
            val definition = activeById[fieldId]
                ?: throw BadRequestException(
                    "customFieldValues[$index].fieldId=${dto.fieldId} не входит в активную схему категории",
                )
            if (!seenIncoming.add(fieldId)) {
                throw BadRequestException("Дублирующийся fieldId в customFieldValues: ${dto.fieldId}")
            }
            validateCustomFieldValue(definition.type, dto.value, index)
            merged[fieldId] = CustomFieldValue(fieldId = fieldId, value = dto.value)
        }

        return merged.values.toList()
    }

    private fun validateCustomFieldValue(type: CustomFieldType, value: String?, index: Int) {
        if (value == null) return
        when (type) {
            CustomFieldType.TEXT -> Unit
            CustomFieldType.NUMBER -> {
                val parsed = value.toDoubleOrNull()
                if (parsed == null || parsed.isNaN() || parsed.isInfinite()) {
                    throw BadRequestException(
                        "customFieldValues[$index].value должен быть числом (NUMBER)",
                    )
                }
            }
            CustomFieldType.BOOLEAN -> {
                if (value != "true" && value != "false") {
                    throw BadRequestException(
                        "customFieldValues[$index].value должен быть \"true\" или \"false\" (BOOLEAN)",
                    )
                }
            }
            CustomFieldType.DATE -> {
                try {
                    LocalDate.parse(value)
                } catch (_: DateTimeParseException) {
                    throw BadRequestException(
                        "customFieldValues[$index].value должен быть датой yyyy-MM-dd (DATE)",
                    )
                }
            }
            CustomFieldType.COUNTER -> {
                if (value.toIntOrNull() == null) {
                    throw BadRequestException(
                        "customFieldValues[$index].value должен быть целым числом (COUNTER)",
                    )
                }
            }
        }
    }

    private fun requireValidRating(rating: Int) {
        if (rating !in 0..10) {
            throw BadRequestException("rating должен быть в диапазоне 0..10")
        }
    }

    private fun literalEquals(value: String, query: String): Boolean =
        value.equals(query, ignoreCase = true)

    private fun containsLiteral(value: String, token: String): Boolean =
        value.lowercase().contains(token.lowercase())

    private fun accumulateTokenMatches(
        tokens: List<String>,
        value: String,
        pair: Pair<ObjectId, Card>,
        results: MutableMap<Int, MutableList<Pair<ObjectId, Card>>>,
    ) {
        if (tokens.isEmpty()) return
        val matchCount = tokens.count { containsLiteral(value, it) }
        if (matchCount > 0) {
            results.getOrPut(matchCount) { mutableListOf() }.add(pair)
        }
    }

    private fun appendByMatchCount(
        matches: Map<Int, MutableList<Pair<ObjectId, Card>>>,
        maxCount: Int,
        result: LinkedHashSet<Pair<ObjectId, Card>>,
    ) {
        var matchCount = maxCount
        while (matchCount > 0) {
            matches[matchCount]?.forEach { result.add(it) }
            matchCount--
        }
    }
}
