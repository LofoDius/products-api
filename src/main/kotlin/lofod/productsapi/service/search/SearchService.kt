package lofod.productsapi.service.search

import lofod.productsapi.model.Card
import lofod.productsapi.model.Category
import lofod.productsapi.model.response.CategorySearchHit
import lofod.productsapi.model.response.SearchResponse
import lofod.productsapi.repository.CategoryRepository
import lofod.productsapi.service.CategoryAccessService
import lofod.productsapi.service.mapper.CardMapper
import org.bson.types.ObjectId
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service

@Service
class SearchService(
    private val searchIndex: SearchIndex,
    private val categoryRepository: CategoryRepository,
    private val categoryAccessService: CategoryAccessService,
    private val cardMapper: CardMapper,
) {

    @EventListener(ApplicationReadyEvent::class)
    fun rebuildOnStartup() {
        searchIndex.rebuild(categoryRepository.findAll())
    }

    fun search(query: String, categoryId: String?): SearchResponse {
        if (query.isBlank()) {
            return SearchResponse(categories = emptyList(), cards = emptyList())
        }

        val userId = categoryAccessService.currentUserId()
        val allCategories = categoryRepository.findAll()
        val accessible = allCategories.filter { categoryAccessService.roleOf(userId, it) != null }
        if (accessible.isEmpty()) {
            return SearchResponse(categories = emptyList(), cards = emptyList())
        }

        val byId = allCategories.associateBy { it.categoryId }
        val accessibleRootIds = accessible
            .filter { it.parentId == null }
            .map { it.categoryId.toHexString() }
            .toSet()

        val categories = searchIndex.searchCategories(query, accessibleRootIds, CATEGORY_FETCH_LIMIT)
            .mapNotNull { hit ->
                val category = byId[parseObjectId(hit.categoryId)] ?: return@mapNotNull null
                val role = categoryAccessService.roleOf(userId, category) ?: return@mapNotNull null
                CategorySearchHit(
                    categoryId = category.categoryId.toHexString(),
                    name = category.name,
                    parentId = category.parentId?.toHexString(),
                    imageId = category.imageId?.toHexString(),
                    role = role,
                )
            }
            .take(MAX_CATEGORY_HITS)

        val scoredCards = searchIndex.searchCards(query, accessibleRootIds, CARD_FETCH_LIMIT)
            .mapNotNull { hit ->
                val category = byId[parseObjectId(hit.categoryId)] ?: return@mapNotNull null
                if (categoryAccessService.roleOf(userId, category) == null) return@mapNotNull null
                val card = category.cards.firstOrNull { it.cardId.toHexString() == hit.cardId }
                    ?: return@mapNotNull null
                ScoredCard(hit.score, category, card)
            }

        val anchor = resolveAnchor(categoryId, userId, byId)
        val cards = if (anchor == null) {
            scoredCards.map { cardMapper.toView(it.category.categoryId, it.card) }
        } else {
            val childrenByParent = accessible.groupBy { it.parentId }
            val accessibleRoots = accessible.filter { it.parentId == null }
            scoredCards
                .sortedWith(
                    compareBy<ScoredCard> {
                        cardTier(it.category.categoryId, anchor, childrenByParent, accessibleRoots)
                    }.thenByDescending { it.score },
                )
                .map { cardMapper.toView(it.category.categoryId, it.card) }
        }

        return SearchResponse(categories = categories, cards = cards)
    }

    private fun resolveAnchor(
        categoryId: String?,
        userId: ObjectId,
        byId: Map<ObjectId, Category>,
    ): Category? {
        val raw = categoryId?.trim() ?: return null
        if (raw.isEmpty() || raw == "-1" || !ObjectId.isValid(raw)) return null
        val category = byId[ObjectId(raw)] ?: return null
        if (categoryAccessService.roleOf(userId, category) == null) return null
        return category
    }

    private fun cardTier(
        cardCategoryId: ObjectId,
        anchor: Category,
        childrenByParent: Map<ObjectId?, List<Category>>,
        accessibleRoots: List<Category>,
    ): Int {
        val anchorId = anchor.categoryId
        if (cardCategoryId == anchorId) return 1
        if (cardCategoryId in descendants(anchorId, childrenByParent)) return 2

        if (anchor.parentId == null) {
            val otherRoots = accessibleRoots.map { it.categoryId }.filter { it != anchorId }
            val siblingTrees = otherRoots.flatMap { rootId ->
                listOf(rootId) + descendants(rootId, childrenByParent)
            }.toSet()
            return if (cardCategoryId in siblingTrees) 4 else 5
        }

        if (cardCategoryId == anchor.parentId) return 3

        val siblings = childrenByParent[anchor.parentId].orEmpty()
            .map { it.categoryId }
            .filter { it != anchorId }
        val siblingTrees = siblings.flatMap { siblingId ->
            listOf(siblingId) + descendants(siblingId, childrenByParent)
        }.toSet()
        return if (cardCategoryId in siblingTrees) 4 else 5
    }

    private fun descendants(
        categoryId: ObjectId,
        childrenByParent: Map<ObjectId?, List<Category>>,
    ): Set<ObjectId> {
        val result = mutableSetOf<ObjectId>()
        val queue = ArrayDeque<ObjectId>()
        childrenByParent[categoryId].orEmpty().forEach { queue.add(it.categoryId) }
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (result.add(current)) {
                childrenByParent[current].orEmpty().forEach { queue.add(it.categoryId) }
            }
        }
        return result
    }

    private fun parseObjectId(value: String): ObjectId? =
        if (ObjectId.isValid(value)) ObjectId(value) else null

    private data class ScoredCard(
        val score: Float,
        val category: Category,
        val card: Card,
    )

    companion object {
        private const val MAX_CATEGORY_HITS = 3
        private const val CATEGORY_FETCH_LIMIT = 20
        private const val CARD_FETCH_LIMIT = 10_000
    }
}
