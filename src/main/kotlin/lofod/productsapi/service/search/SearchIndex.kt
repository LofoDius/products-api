package lofod.productsapi.service.search

import jakarta.annotation.PreDestroy
import lofod.productsapi.config.SearchProperties
import lofod.productsapi.model.Card
import lofod.productsapi.model.Category
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.LowerCaseFilter
import org.apache.lucene.analysis.TokenStream
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper
import org.apache.lucene.analysis.ngram.NGramTokenFilter
import org.apache.lucene.analysis.ru.RussianAnalyzer
import org.apache.lucene.analysis.standard.StandardTokenizer
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.StringField
import org.apache.lucene.document.TextField
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.index.Term
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser
import org.apache.lucene.queryparser.classic.ParseException
import org.apache.lucene.queryparser.classic.QueryParser
import org.apache.lucene.search.BooleanClause
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.Query
import org.apache.lucene.search.SearcherFactory
import org.apache.lucene.search.SearcherManager
import org.apache.lucene.search.TermQuery
import org.apache.lucene.store.FSDirectory
import org.bson.types.ObjectId
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path

@Component
class SearchIndex(
    searchProperties: SearchProperties,
) {
    private val analyzer: Analyzer = createAnalyzer()
    private val directory = FSDirectory.open(prepareIndexPath(searchProperties.indexPath))
    private val writer: IndexWriter = IndexWriter(
        directory,
        IndexWriterConfig(analyzer).apply {
            openMode = IndexWriterConfig.OpenMode.CREATE_OR_APPEND
        },
    )
    private val searcherManager = SearcherManager(writer, SearcherFactory())

    @Synchronized
    fun rebuild(categories: Collection<Category>) {
        writer.deleteAll()
        val byId = categories.associateBy { it.categoryId }
        for (category in categories) {
            val rootId = resolveRootId(category, byId)
            writer.addDocument(categoryDocument(category, rootId))
            for (card in category.cards) {
                writer.addDocument(cardDocument(card, category, rootId))
            }
        }
        commitAndRefresh()
    }

    @Synchronized
    fun indexCategory(category: Category, rootCategoryId: ObjectId) {
        writer.updateDocument(
            Term(F_ID, categoryDocId(category.categoryId)),
            categoryDocument(category, rootCategoryId.toHexString()),
        )
        commitAndRefresh()
    }

    @Synchronized
    fun indexCard(card: Card, category: Category, rootCategoryId: ObjectId) {
        writer.updateDocument(
            Term(F_ID, cardDocId(card.cardId)),
            cardDocument(card, category, rootCategoryId.toHexString()),
        )
        commitAndRefresh()
    }

    @Synchronized
    fun deleteCategory(categoryId: ObjectId) {
        writer.deleteDocuments(Term(F_ID, categoryDocId(categoryId)))
        commitAndRefresh()
    }

    @Synchronized
    fun deleteCard(cardId: ObjectId) {
        writer.deleteDocuments(Term(F_ID, cardDocId(cardId)))
        commitAndRefresh()
    }

    @Synchronized
    fun searchCategories(query: String, accessibleRootIds: Set<String>, limit: Int): List<CategoryHit> {
        if (accessibleRootIds.isEmpty() || limit <= 0) return emptyList()
        val textQuery = parseTextQuery(query, CATEGORY_FIELDS, CATEGORY_BOOSTS) ?: return emptyList()
        val luceneQuery = filterQuery(textQuery, DOC_TYPE_CATEGORY, accessibleRootIds)
        return search(luceneQuery, limit) { doc, score ->
            val categoryId = doc.get(F_CATEGORY_ID) ?: return@search null
            CategoryHit(categoryId = categoryId, score = score)
        }
    }

    @Synchronized
    fun searchCards(query: String, accessibleRootIds: Set<String>, limit: Int): List<CardHit> {
        if (accessibleRootIds.isEmpty() || limit <= 0) return emptyList()
        val textQuery = parseTextQuery(query, CARD_FIELDS, CARD_BOOSTS) ?: return emptyList()
        val luceneQuery = filterQuery(textQuery, DOC_TYPE_CARD, accessibleRootIds)
        return search(luceneQuery, limit) { doc, score ->
            val cardId = doc.get(F_CARD_ID) ?: return@search null
            val categoryId = doc.get(F_CATEGORY_ID) ?: return@search null
            CardHit(cardId = cardId, categoryId = categoryId, score = score)
        }
    }

    @PreDestroy
    fun shutdown() {
        searcherManager.close()
        writer.close()
        directory.close()
    }

    private fun <T> search(query: Query, limit: Int, mapper: (Document, Float) -> T?): List<T> {
        if (!DirectoryReader.indexExists(directory)) return emptyList()
        searcherManager.maybeRefreshBlocking()
        val searcher = searcherManager.acquire()
        try {
            val topDocs = searcher.search(query, limit)
            val storedFields = searcher.storedFields()
            return topDocs.scoreDocs.mapNotNull { scoreDoc ->
                mapper(storedFields.document(scoreDoc.doc), scoreDoc.score)
            }
        } finally {
            searcherManager.release(searcher)
        }
    }

    private fun parseTextQuery(query: String, fields: Array<String>, boosts: Map<String, Float>): Query? {
        val escaped = QueryParser.escape(query.trim())
        if (escaped.isBlank()) return null
        return try {
            val parser = MultiFieldQueryParser(fields, analyzer, boosts)
            parser.defaultOperator = QueryParser.Operator.OR
            parser.parse(escaped)
        } catch (_: ParseException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun filterQuery(textQuery: Query, docType: String, accessibleRootIds: Set<String>): Query {
        val roots = BooleanQuery.Builder()
        accessibleRootIds.forEach { rootId ->
            roots.add(TermQuery(Term(F_ROOT_CATEGORY_ID, rootId)), BooleanClause.Occur.SHOULD)
        }
        return BooleanQuery.Builder()
            .add(textQuery, BooleanClause.Occur.MUST)
            .add(TermQuery(Term(F_DOC_TYPE, docType)), BooleanClause.Occur.FILTER)
            .add(roots.build(), BooleanClause.Occur.FILTER)
            .build()
    }

    private fun categoryDocument(category: Category, rootCategoryId: String): Document =
        Document().apply {
            add(StringField(F_ID, categoryDocId(category.categoryId), Field.Store.NO))
            add(StringField(F_DOC_TYPE, DOC_TYPE_CATEGORY, Field.Store.YES))
            add(StringField(F_CATEGORY_ID, category.categoryId.toHexString(), Field.Store.YES))
            add(StringField(F_ROOT_CATEGORY_ID, rootCategoryId, Field.Store.YES))
            add(TextField(F_NAME_MORPH, category.name, Field.Store.NO))
            add(TextField(F_NAME_NGRAM, category.name, Field.Store.NO))
        }

    private fun cardDocument(card: Card, category: Category, rootCategoryId: String): Document =
        Document().apply {
            add(StringField(F_ID, cardDocId(card.cardId), Field.Store.NO))
            add(StringField(F_DOC_TYPE, DOC_TYPE_CARD, Field.Store.YES))
            add(StringField(F_CARD_ID, card.cardId.toHexString(), Field.Store.YES))
            add(StringField(F_CATEGORY_ID, category.categoryId.toHexString(), Field.Store.YES))
            add(StringField(F_ROOT_CATEGORY_ID, rootCategoryId, Field.Store.YES))
            add(TextField(F_NAME_MORPH, card.name, Field.Store.NO))
            add(TextField(F_NAME_NGRAM, card.name, Field.Store.NO))
            add(TextField(F_CATNAME_MORPH, category.name, Field.Store.NO))
            add(TextField(F_CATNAME_NGRAM, category.name, Field.Store.NO))
            val description = card.description
            if (!description.isNullOrBlank()) {
                add(TextField(F_DESC_MORPH, description, Field.Store.NO))
                add(TextField(F_DESC_NGRAM, description, Field.Store.NO))
            }
            val custom = card.customFieldValues.mapNotNull { it.value }.filter { it.isNotBlank() }.joinToString(" ")
            if (custom.isNotBlank()) {
                add(TextField(F_CUSTOM_MORPH, custom, Field.Store.NO))
                add(TextField(F_CUSTOM_NGRAM, custom, Field.Store.NO))
            }
        }

    private fun commitAndRefresh() {
        writer.commit()
        searcherManager.maybeRefreshBlocking()
    }

    private fun resolveRootId(category: Category, byId: Map<ObjectId, Category>): String {
        var current = category
        val visited = mutableSetOf<ObjectId>()
        while (current.parentId != null && visited.add(current.categoryId)) {
            current = byId[current.parentId] ?: break
        }
        return current.categoryId.toHexString()
    }

    data class CategoryHit(val categoryId: String, val score: Float)
    data class CardHit(val cardId: String, val categoryId: String, val score: Float)

    private class RussianNGramAnalyzer : Analyzer() {
        override fun createComponents(fieldName: String): TokenStreamComponents {
            val tokenizer = StandardTokenizer()
            var tokens: TokenStream = LowerCaseFilter(tokenizer)
            tokens = NGramTokenFilter(tokens, MIN_NGRAM, MAX_NGRAM, true)
            return TokenStreamComponents(tokenizer, tokens)
        }
    }

    companion object {
        private const val MIN_NGRAM = 3
        private const val MAX_NGRAM = 20

        private const val F_ID = "id"
        private const val F_DOC_TYPE = "docType"
        private const val F_CATEGORY_ID = "categoryId"
        private const val F_CARD_ID = "cardId"
        private const val F_ROOT_CATEGORY_ID = "rootCategoryId"
        private const val F_NAME_MORPH = "name_morph"
        private const val F_NAME_NGRAM = "name_ngram"
        private const val F_DESC_MORPH = "description_morph"
        private const val F_DESC_NGRAM = "description_ngram"
        private const val F_CUSTOM_MORPH = "custom_morph"
        private const val F_CUSTOM_NGRAM = "custom_ngram"
        private const val F_CATNAME_MORPH = "categoryName_morph"
        private const val F_CATNAME_NGRAM = "categoryName_ngram"

        private const val DOC_TYPE_CATEGORY = "category"
        private const val DOC_TYPE_CARD = "card"

        private val CATEGORY_FIELDS = arrayOf(F_NAME_MORPH, F_NAME_NGRAM)
        private val CATEGORY_BOOSTS = mapOf(
            F_NAME_MORPH to 4f,
            F_NAME_NGRAM to 3f,
        )

        private val CARD_FIELDS = arrayOf(
            F_NAME_MORPH, F_NAME_NGRAM,
            F_CATNAME_MORPH, F_CATNAME_NGRAM,
            F_DESC_MORPH, F_DESC_NGRAM,
            F_CUSTOM_MORPH, F_CUSTOM_NGRAM,
        )
        private val CARD_BOOSTS = mapOf(
            F_NAME_MORPH to 8f,
            F_NAME_NGRAM to 6f,
            F_CATNAME_MORPH to 4f,
            F_CATNAME_NGRAM to 3f,
            F_DESC_MORPH to 2f,
            F_DESC_NGRAM to 1.5f,
            F_CUSTOM_MORPH to 1f,
            F_CUSTOM_NGRAM to 0.8f,
        )

        private fun createAnalyzer(): Analyzer {
            val ngram = RussianNGramAnalyzer()
            return PerFieldAnalyzerWrapper(
                RussianAnalyzer(),
                mapOf(
                    F_NAME_NGRAM to ngram,
                    F_DESC_NGRAM to ngram,
                    F_CUSTOM_NGRAM to ngram,
                    F_CATNAME_NGRAM to ngram,
                ),
            )
        }

        private fun prepareIndexPath(indexPath: String): Path {
            val path = Path.of(indexPath)
            Files.createDirectories(path)
            return path
        }

        private fun categoryDocId(categoryId: ObjectId) = "category:${categoryId.toHexString()}"
        private fun cardDocId(cardId: ObjectId) = "card:${cardId.toHexString()}"
    }
}
