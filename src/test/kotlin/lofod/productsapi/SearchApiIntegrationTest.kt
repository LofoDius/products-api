package lofod.productsapi

import lofod.productsapi.support.AbstractApiIntegrationTest
import org.hamcrest.Matchers.containsInAnyOrder
import org.hamcrest.Matchers.lessThanOrEqualTo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class SearchApiIntegrationTest : AbstractApiIntegrationTest() {

    @Test
    fun `search does not return other users categories cards`() {
        val aliceToken = registerAndLogin("alice-search")
        val bobToken = registerAndLogin("bob-search")

        val aliceCat = createCategory(aliceToken, "AliceCat").get("categoryId").asText()
        val bobCat = createCategory(bobToken, "BobCat").get("categoryId").asText()
        createCard(aliceToken, aliceCat, "AlicesFlamingo")
        createCard(bobToken, bobCat, "BobsNarwhal")

        searchWithAuth(aliceToken, "BobsNarwhal")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.cards").isEmpty)
            .andExpect(jsonPath("$.categories").isEmpty)

        searchWithAuth(aliceToken, "AlicesFlamingo")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.cards.length()").value(1))
            .andExpect(jsonPath("$.cards[0].name").value("AlicesFlamingo"))

        searchWithAuth(bobToken, "AlicesFlamingo")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.cards").isEmpty)
    }

    @Test
    fun `search with regex metacharacters does not return 500`() {
        val token = registerAndLogin("regex-user")
        val categoryId = createCategory(token, "RegexCat").get("categoryId").asText()
        createCard(token, categoryId, "PlainName", description = "desc")

        val dangerous = ".*+?^$()[]|"

        searchWithAuth(token, dangerous)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.cards").isArray)
            .andExpect(jsonPath("$.categories").isArray)
    }

    @Test
    fun `search matches russian morphology and ngrams`() {
        val token = registerAndLogin("morph-user")
        val categoryId = createCategory(token, "MorphRoot").get("categoryId").asText()
        createCard(token, categoryId, "шапка")
        createCard(token, categoryId, "ДругаяКарточка", description = "там лежат шапки и шапочка")
        createCard(token, categoryId, "ботинок")

        searchWithAuth(token, "шапочки")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.cards.length()").value(2))
            .andExpect(jsonPath("$.cards[*].name", containsInAnyOrder("шапка", "ДругаяКарточка")))
    }

    @Test
    fun `search matches text custom field value`() {
        val token = registerAndLogin("custom-field-search")
        val category = createCategory(
            token,
            "CustomRoot",
            customFields = listOf("Заметка" to "TEXT"),
        )
        val categoryId = category.get("categoryId").asText()
        val fieldId = category.get("customFields")[0].get("fieldId").asText()
        createCard(
            token,
            categoryId,
            "ОбычноеИмяКарточки",
            customFieldValues = listOf(fieldId to "крендель"),
        )
        createCard(token, categoryId, "БезКастома")

        searchWithAuth(token, "кренделя")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.cards.length()").value(1))
            .andExpect(jsonPath("$.cards[0].name").value("ОбычноеИмяКарточки"))
    }

    @Test
    fun `search matches category name word form and returns at most three`() {
        val token = registerAndLogin("cat-morph-user")
        repeat(5) { index ->
            createCategory(token, "Лукморилка$index")
        }

        searchWithAuth(token, "лукморилки")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.categories.length()").value(lessThanOrEqualTo(3)))
            .andExpect(jsonPath("$.categories.length()").value(3))
    }

    @Test
    fun `search ranks cards by proximity when categoryId is set`() {
        val token = registerAndLogin("proximity-user")
        val name = "ProximitySameNameZZZXQ"
        val rootA = createCategory(token, "ProxA").get("categoryId").asText()
        val catC = createCategory(token, "ProxC", parentId = rootA).get("categoryId").asText()
        val catC1 = createCategory(token, "ProxC1", parentId = catC).get("categoryId").asText()
        val catS = createCategory(token, "ProxS", parentId = rootA).get("categoryId").asText()
        val catS1 = createCategory(token, "ProxS1", parentId = catS).get("categoryId").asText()
        val rootU = createCategory(token, "ProxU").get("categoryId").asText()

        createCard(token, catC, name)
        createCard(token, catC1, name)
        createCard(token, rootA, name)
        createCard(token, catS, name)
        createCard(token, catS1, name)
        createCard(token, rootU, name)

        val withAnchor = searchWithAuth(token, name, categoryId = catC)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.cards.length()").value(6))
            .andReturn()
        val ids = objectMapper.readTree(withAnchor.response.contentAsString)
            .get("cards")
            .map { it.get("categoryId").asText() }
        assertEquals(listOf(catC, catC1, rootA), ids.take(3))
        assertEquals(setOf(catS, catS1), ids.subList(3, 5).toSet())
        assertEquals(rootU, ids[5])

        searchWithAuth(token, name)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.cards.length()").value(6))
    }

    @Test
    fun `blank search query returns empty arrays`() {
        val token = registerAndLogin("empty-query-user")
        val categoryId = createCategory(token, "EmptyQCat").get("categoryId").asText()
        createCard(token, categoryId, "Something")

        searchWithAuth(token, "   ")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.cards").isEmpty)
            .andExpect(jsonPath("$.categories").isEmpty)
    }
}
