package lofod.productsapi

import lofod.productsapi.support.AbstractApiIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class SearchApiIntegrationTest : AbstractApiIntegrationTest() {

    @Test
    fun `search does not return other users categories cards`() {
        val aliceToken = registerAndLogin("alice-search")
        val bobToken = registerAndLogin("bob-search")

        val aliceCat = createCategory(aliceToken, "AliceCat").get("categoryId").asText()
        val bobCat = createCategory(bobToken, "BobCat").get("categoryId").asText()
        createCard(aliceToken, aliceCat, "AliceOnlyWidget")
        createCard(bobToken, bobCat, "BobOnlyWidget")

        getWithAuth(aliceToken, "/cards/search/BobOnlyWidget")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isEmpty)

        getWithAuth(aliceToken, "/cards/search/AliceOnlyWidget")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].name").value("AliceOnlyWidget"))

        getWithAuth(bobToken, "/cards/search/AliceOnlyWidget")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isEmpty)
    }

    @Test
    fun `search with regex metacharacters does not return 500`() {
        val token = registerAndLogin("regex-user")
        val categoryId = createCategory(token, "RegexCat").get("categoryId").asText()
        createCard(token, categoryId, "PlainName", description = "desc")

        // Characters that would break Pattern.compile if search used unescaped regex
        val dangerous = ".*+?^$()[]|"

        mockMvc.perform(
            get("/cards/search/{query}", dangerous)
                .header("Authorization", authHeader(token)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
    }
}
