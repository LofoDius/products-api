package lofod.productsapi

import lofod.productsapi.support.AbstractApiIntegrationTest
import org.bson.types.ObjectId
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class CardRatingIntegrationTest : AbstractApiIntegrationTest() {

    @Autowired
    private lateinit var mongoTemplate: MongoTemplate

    @Test
    fun `create and update card with rating`() {
        val token = registerAndLogin("ratingOwner")
        val categoryId = createCategory(token, "Rated").get("categoryId").asText()

        val created = mockMvc.perform(
            post("/category/$categoryId/card")
                .header("Authorization", authHeader(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Cheese",
                      "imageId": null,
                      "priceLevel": "MEDIUM_PRICE",
                      "qualityLevel": "HIGH_QUALITY",
                      "rating": 7,
                      "description": null
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].rating").value(7))
            .andReturn()

        val cardId = objectMapper.readTree(created.response.contentAsString)[0].get("cardId").asText()

        putJson(
            token,
            "/category/$categoryId/card/$cardId",
            """
            {
              "name": "Cheese",
              "imageId": null,
              "priceLevel": "MEDIUM_PRICE",
              "qualityLevel": "HIGH_QUALITY",
              "rating": 10,
              "description": null
            }
            """.trimIndent(),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].rating").value(10))

        getWithAuth(token, "/category/$categoryId/card/$cardId")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.rating").value(10))
    }

    @Test
    fun `rejects rating outside 0 to 10`() {
        val token = registerAndLogin("ratingReject")
        val categoryId = createCategory(token, "Bounds").get("categoryId").asText()

        mockMvc.perform(
            post("/category/$categoryId/card")
                .header("Authorization", authHeader(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cardJson(rating = 11)),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"))

        mockMvc.perform(
            post("/category/$categoryId/card")
                .header("Authorization", authHeader(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cardJson(rating = -1)),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"))

        val cards = createCard(token, categoryId, "Plain")
        val cardId = cards[0].get("cardId").asText()

        putJson(
            token,
            "/category/$categoryId/card/$cardId",
            cardJson(name = "Plain", rating = 11),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"))

        putJson(
            token,
            "/category/$categoryId/card/$cardId",
            cardJson(name = "Plain", rating = -1),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
    }

    @Test
    fun `legacy card document without rating field reads as 0`() {
        val token = registerAndLogin("ratingLegacy")
        val categoryId = createCategory(token, "Legacy").get("categoryId").asText()
        val cards = createCard(token, categoryId, "OldCard")
        val cardId = cards[0].get("cardId").asText()

        mongoTemplate.updateFirst(
            Query(Criteria.where("_id").`is`(ObjectId(categoryId))),
            Update().unset("cards.0.rating"),
            "category",
        )

        getWithAuth(token, "/category/$categoryId/card/$cardId")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("OldCard"))
            .andExpect(jsonPath("$.rating").value(0))

        getWithAuth(token, "/category/$categoryId/cards")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].rating").value(0))
    }

    @Test
    fun `create without rating defaults to 0`() {
        val token = registerAndLogin("ratingDefault")
        val categoryId = createCategory(token, "Default").get("categoryId").asText()

        createCard(token, categoryId, "NoRatingField")
        getWithAuth(token, "/category/$categoryId/cards")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].rating").value(0))
    }

    private fun cardJson(name: String = "Item", rating: Int): String =
        """
        {
          "name": "$name",
          "imageId": null,
          "priceLevel": "MEDIUM_PRICE",
          "qualityLevel": "MEDIUM_QUALITY",
          "rating": $rating,
          "description": null
        }
        """.trimIndent()
}
