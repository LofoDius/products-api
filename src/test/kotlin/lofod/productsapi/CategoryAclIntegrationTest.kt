package lofod.productsapi

import lofod.productsapi.support.AbstractApiIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class CategoryAclIntegrationTest : AbstractApiIntegrationTest() {

    @Test
    fun `member cannot update delete category or invite`() {
        val ownerToken = registerAndLogin("owner1")
        register("member1")
        val memberToken = login("member1").andReturn().response.getHeader("Authorization")!!

        val categoryId = createCategory(ownerToken, "Shared").get("categoryId").asText()
        inviteMember(ownerToken, categoryId, "member1").andExpect(status().isCreated)

        putJson(
            memberToken,
            "/category/$categoryId",
            """{"parentId":null,"name":"Hacked","imageId":null}""",
        ).andExpect(status().isForbidden)

        deleteWithAuth(memberToken, "/category/$categoryId")
            .andExpect(status().isForbidden)

        inviteMember(memberToken, categoryId, "someone")
            .andExpect(status().isForbidden)
    }

    @Test
    fun `member can create update and delete card in shared category`() {
        val ownerToken = registerAndLogin("owner2")
        register("member2")
        val memberToken = login("member2").andReturn().response.getHeader("Authorization")!!

        val categoryId = createCategory(ownerToken, "Cards").get("categoryId").asText()
        inviteMember(ownerToken, categoryId, "member2").andExpect(status().isCreated)

        val cards = createCard(memberToken, categoryId, "Milk")
        val cardId = cards[0].get("cardId").asText()

        putJson(
            memberToken,
            "/category/$categoryId/card/$cardId",
            """
            {
              "name": "Milk 2%",
              "imageId": null,
              "priceLevel": "LOW_PRICE",
              "qualityLevel": "HIGH_QUALITY",
              "description": "updated"
            }
            """.trimIndent(),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].name").value("Milk 2%"))

        deleteWithAuth(memberToken, "/category/$categoryId/card/$cardId")
            .andExpect(status().isOk)

        getWithAuth(memberToken, "/category/$categoryId/cards")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isEmpty)
    }

    @Test
    fun `owner invite by username then remove denies access`() {
        val ownerToken = registerAndLogin("owner3")
        register("member3")
        val memberToken = login("member3").andReturn().response.getHeader("Authorization")!!
        val memberUserId = userRepository.findByUsername("member3")!!.userId.toHexString()

        val categoryId = createCategory(ownerToken, "InviteTree").get("categoryId").asText()
        inviteMember(ownerToken, categoryId, "member3")
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.username").value("member3"))

        getWithAuth(memberToken, "/category/$categoryId/cards")
            .andExpect(status().isOk)

        deleteWithAuth(ownerToken, "/category/$categoryId/members/$memberUserId")
            .andExpect(status().isOk)

        getWithAuth(memberToken, "/category/$categoryId/cards")
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("FORBIDDEN"))

        getWithAuth(memberToken, "/category/tree")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isEmpty)
    }
}
