package lofod.productsapi.service

import lofod.productsapi.model.*
import lofod.productsapi.model.request.CreateCardRequest
import lofod.productsapi.model.request.CreateCategoryRequest
import lofod.productsapi.model.request.UpdateCardRequest
import lofod.productsapi.model.request.UpdateCategoryRequest
import lofod.productsapi.model.response.CardResponse
import lofod.productsapi.model.response.CategoryResponse
import lofod.productsapi.model.response.ImageIdResponse
import lofod.productsapi.model.response.ImageResponse
import lofod.productsapi.repository.CategoryRepository
import lofod.productsapi.repository.ImageRepository
import lofod.productsapi.service.mapper.CardMapper
import lofod.productsapi.service.mapper.CategoryMapper
import org.bson.types.ObjectId
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.ByteArrayOutputStream
import java.util.*
import net.coobird.thumbnailator.*
import javax.imageio.ImageIO

@Service
class CategoryService(
    private val categoryRepository: CategoryRepository,
    private val categoryMapper: CategoryMapper,
    private val cardMapper: CardMapper,
    private val imageRepository: ImageRepository,
) {
    fun getAllCategories(): ResponseEntity<List<CategoryResponse>> {
        return ResponseEntity(
            categoryRepository.getCategoriesByParentId(null)
                .map { processCategory(it) }
                .map { categoryMapper.toView(it) },
            HttpStatus.OK
        )
    }

    fun createCategory(categoryRequest: CreateCategoryRequest): ResponseEntity<out Any> {
        var category = Category(
            name = categoryRequest.name,
            subcategories = mutableListOf(),
            parentId = categoryRequest.parentId,
            cards = mutableListOf(),
            imageId = null,
        )

        categoryRequest.parentId?.let { parentId ->
            val parent = categoryRepository.getCategoryByCategoryId(parentId)
                ?: return ResponseEntity("Не найдена категория с parentId=${parentId}", HttpStatus.BAD_REQUEST)

            categoryRepository.save(category)
            parent.subcategories.add(
                LiteCategory(
                    categoryId = category.categoryId,
                    subcategories = category.subcategories
                )
            )
            categoryRepository.save(parent)
        } ?: run {
            category = categoryRepository.save(category)
        }

        return ResponseEntity.ok(categoryMapper.toView(processCategory(category)))
    }

    fun updateCategory(id: ObjectId, categoryRequest: UpdateCategoryRequest): ResponseEntity<out Any> {
        val category: Category = categoryRepository.getCategoryByCategoryId(id)
            ?: return ResponseEntity("Категория с id=${id} не найдена", HttpStatus.BAD_REQUEST)

        val updatedCategory = Category(
            categoryId = category.categoryId,
            name = categoryRequest.name,
            subcategories = category.subcategories,
            parentId = categoryRequest.parentId ?: category.parentId,
            cards = category.cards,
            imageId = null,
        )

        return ResponseEntity.ok(categoryMapper.toView(processCategory(categoryRepository.save(updatedCategory))))
    }

    fun deleteCategory(id: ObjectId): ResponseEntity<out Any> {
        val category = categoryRepository.getCategoryByCategoryId(id)
            ?: return ResponseEntity("Не найдена категория с id=${id}", HttpStatus.BAD_REQUEST)

        category.parentId?.let {
            val parent = categoryRepository.getCategoryByCategoryId(category.parentId)
            val liteCategory = parent!!.subcategories.find { it.categoryId == category.categoryId }
            parent.subcategories.remove(liteCategory)
            categoryRepository.save(parent)
        }

        category.imageId?.let { imageRepository.deleteImageByImageId(it) }

        deleteSubcategories(category.subcategories)
        categoryRepository.delete(category)

        return ResponseEntity.ok("Категория удалена")
    }

    fun createCard(categoryId: ObjectId, request: CreateCardRequest): ResponseEntity<out Any> {
        val category = categoryRepository.getCategoryByCategoryId(categoryId)
            ?: return ResponseEntity("Не найдена категория с id=${categoryId}", HttpStatus.BAD_REQUEST)

        val newCard = Card(
            name = request.name,
            imageId = request.imageId?.let { ObjectId(it) },
            priceLevel = request.priceLevel,
            qualityLevel = request.qualityLevel,
            description = request.description,
        )
        category.cards.add(newCard)

        return ResponseEntity.ok(categoryRepository.save(category).cards.map { cardMapper.toView(it) })
    }

    fun getCardsOfCategory(categoryId: ObjectId): ResponseEntity<out Any> {
        val category = categoryRepository.getCategoryByCategoryId(categoryId)
            ?: return ResponseEntity("Не найдена категория с id=${categoryId}", HttpStatus.BAD_REQUEST)

        return ResponseEntity.ok(category.cards.map { cardMapper.toView(it) })
    }

    fun updateCard(categoryId: ObjectId, cardId: ObjectId, request: UpdateCardRequest): ResponseEntity<out Any> {
        val category = categoryRepository.getCategoryByCategoryId(categoryId)
            ?: return ResponseEntity("Не найдено категории с id=${categoryId}", HttpStatus.BAD_REQUEST)

        val card = category.cards.firstOrNull {
            it.cardId == cardId
        } ?: return ResponseEntity(
            "В категории с id=${categoryId} не найдено карточки с id=${cardId}",
            HttpStatus.BAD_REQUEST
        )

        val index = category.cards.indexOf(card)
        val updatedCard = Card(
            cardId = card.cardId,
            name = request.name,
            imageId = request.imageId?.let { ObjectId(it) },
            priceLevel = request.priceLevel,
            qualityLevel = request.qualityLevel,
            description = request.description,
        )
        category.cards.removeAt(index)
        category.cards.add(index, updatedCard)

        categoryRepository.save(category)

        return ResponseEntity.ok(category.cards.map { cardMapper.toView(it) })
    }

    fun createImage(file: MultipartFile): ResponseEntity<out Any> {
        val image = imageRepository.save(Image(value = Base64.getEncoder().encodeToString(compressImage(file))))

        return ResponseEntity.ok(ImageIdResponse(image.imageId.toString()))
    }

    fun getImage(id: ObjectId): ResponseEntity<out Any> {
        val image = imageRepository.getImageByImageId(id)
            ?: return ResponseEntity("Не найдено изображение с id=${id}", HttpStatus.BAD_REQUEST)

        return ResponseEntity.ok(ImageResponse(image.value))
    }

    fun getCard(categoryId: ObjectId, cardId: ObjectId): ResponseEntity<out Any> {
        val category = categoryRepository.getCategoryByCategoryId(categoryId)
            ?: return ResponseEntity("Не найдено категории с id=${categoryId}", HttpStatus.BAD_REQUEST)

        val card = category.cards.firstOrNull {
            it.cardId == cardId
        } ?: return ResponseEntity(
            "В категории с id=${categoryId} не найдено карточки с id=${cardId}",
            HttpStatus.BAD_REQUEST
        )

        return ResponseEntity.ok(cardMapper.toView(card))
    }

    fun deleteCard(categoryId: ObjectId, cardId: ObjectId): ResponseEntity<out Any> {
        val category = categoryRepository.getCategoryByCategoryId(categoryId)
            ?: return ResponseEntity("Не найдено категории с id=${categoryId}", HttpStatus.BAD_REQUEST)

        val card = category.cards.firstOrNull {
            it.cardId == cardId
        } ?: return ResponseEntity(
            "В категории с id=${categoryId} не найдено карточки с id=${cardId}",
            HttpStatus.BAD_REQUEST
        )

        card.imageId?.let { imageRepository.deleteImageByImageId(it) }

        category.cards.remove(card)
        categoryRepository.save(category)

        return ResponseEntity(HttpStatus.OK)
    }

    fun searchCard(query: String): ResponseEntity<out List<CardResponse>> {
        val tokens = query.trim().lowercase().split(" ").filter { it.isNotBlank() }
        val categories = categoryRepository.findAll()
        val resultCards: MutableList<Card> = mutableListOf()
        var matchesInName = mutableMapOf<Int, MutableList<Card>>()
        var matchesInDescription = mutableMapOf<Int, MutableList<Card>>()

        val allCards = categories.filter { it.cards.size > 0 }
            .mapNotNull { it.cards }
            .flatten()

        allCards.forEach { card ->
            if (Regex(query).matches(card.name) || Regex(query.lowercase()).matches(card.name.lowercase()))
                resultCards.add(card)
            else matchesInName = checkTokens(tokens, card, card.name, matchesInName)
        }

        var matchCount = tokens.size
        while (matchCount > 0) {
            matchesInName[matchCount]?.let { resultCards.addAll(it) }
            matchCount--
        }

        allCards.forEach { card ->
            if (Regex(query).matches(card.description ?: "")
                || Regex(query.lowercase()).matches(card.description?.lowercase() ?: "")
            )
                resultCards.add(card)
            else if (card.description != null)
                matchesInDescription = checkTokens(tokens, card, card.description, matchesInDescription)
        }

        matchCount = tokens.size
        while (matchCount > 0) {
            matchesInDescription[matchCount]?.let { cards ->
                resultCards.addAll(cards.filter { !resultCards.contains(it) })
            }
            matchCount--
        }

        return ResponseEntity.ok(resultCards.map { cardMapper.toView(it) })
    }

    private fun processCategory(category: Category): FullCategory {
        val fullCategory = FullCategory(
            categoryId = category.categoryId,
            name = category.name,
            parentId = category.parentId,
            imageId = category.imageId,
            subcategories = processSubcategories(category.subcategories)
        )

        return fullCategory
    }

    private fun processSubcategories(categories: MutableList<LiteCategory>): MutableList<FullCategory> {
        val subcategories = mutableListOf<FullCategory>()
        categories.forEach {
            val category = categoryRepository.getCategoryByCategoryId(it.categoryId) ?: return subcategories
            subcategories.add(
                FullCategory(
                    categoryId = category.categoryId,
                    name = category.name,
                    subcategories = processSubcategories(category.subcategories),
                    parentId = category.parentId,
                    cards = category.cards,
                    imageId = category.imageId,
                )
            )
        }

        return subcategories
    }

    private fun compressImage(multipartFile: MultipartFile): ByteArray {
        val quality = if (multipartFile.size < 2L * 1024 * 1024) 1f else 2f / (multipartFile.size / 1024 / 1024)

        val image = ImageIO.read(multipartFile.inputStream)
        var width = image.width
        var height = image.height
        val aspectRatio = width.toFloat() / height.toFloat()
        if (width > 1024) {
            width = 1024
            height = (width / aspectRatio).toInt()
        }

        val outputStream = ByteArrayOutputStream()
        Thumbnails.of(multipartFile.inputStream)
            .width(width)
            .height(height)
            .outputQuality(quality)
            .toOutputStream(outputStream)

        return outputStream.toByteArray()
    }

    private fun deleteSubcategories(liteCategories: List<LiteCategory>) {
        liteCategories.forEach { category ->
            deleteSubcategories(category.subcategories)
            categoryRepository.deleteCategoryByCategoryId(category.categoryId)
        }
    }

    private fun checkTokens(
        tokens: List<String>,
        card: Card,
        value: String,
        results: MutableMap<Int, MutableList<Card>>
    ): MutableMap<Int, MutableList<Card>> {
        var matchCount = 0
        tokens.forEach { token ->
            if (Regex(".*${token.lowercase()}.*").matches(value))
                matchCount++
        }

        if (matchCount > 0) {
            if (results[matchCount] == null)
                results[matchCount] = mutableListOf(card)
            else
                results[matchCount]!!.add(card)
        }

        return results
    }
}
