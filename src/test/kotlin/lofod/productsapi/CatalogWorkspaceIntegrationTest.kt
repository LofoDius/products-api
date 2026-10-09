package lofod.productsapi

import lofod.productsapi.support.AbstractApiIntegrationTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class CatalogWorkspaceIntegrationTest : AbstractApiIntegrationTest() {
    private fun postJson(token: String, path: String, json: String) = mockMvc.perform(
        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path)
            .header("Authorization", authHeader(token)).contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(json))
    @Test fun `child inherits shared field identity and rename affects whole tree`() {
        val token = registerAndLogin("fields-owner")
        val root = createCategory(token, "Root", customFields = listOf("Вес" to "NUMBER"))
        val id = root["categoryId"].asText()
        val fieldId = root["customFields"][0]["fieldId"].asText()
        val child = createCategory(token, "Child", id)
        assertEquals(fieldId, child["customFields"][0]["fieldId"].asText())
        putJson(token, "/category/$id/fields/$fieldId", """{"title":"Масса","type":"NUMBER","revision":0}""")
            .andExpect(status().isOk).andExpect(jsonPath("$.categoryIds.length()").value(2))
        getWithAuth(token, "/category/tree").andExpect(jsonPath("$[0].subcategories[0].customFields[0].title").value("Масса"))
        putJson(token, "/category/$id/fields/$fieldId", """{"title":"Масса","type":"TEXT","revision":1}""")
            .andExpect(status().isBadRequest)
    }

    @Test fun `field catalog is isolated by root and members cannot manage fields`() {
        val owner = registerAndLogin("catalog-owner")
        val member = registerAndLogin("catalog-member")
        val root = createCategory(owner, "Shared", customFields = listOf("Марка" to "TEXT"))
        val rootId = root["categoryId"].asText()
        inviteMember(owner, rootId, "catalog-member").andExpect(status().isCreated)
        val fieldId = root["customFields"][0]["fieldId"].asText()
        getWithAuth(member, "/category/$rootId/fields").andExpect(status().isOk)
        putJson(member, "/category/$rootId/fields/$fieldId", """{"title":"Hacked","type":"TEXT"}""")
            .andExpect(status().isForbidden)
        val other = createCategory(owner, "Other")["categoryId"].asText()
        putJson(owner, "/category/$other", """{"name":"Other","parentId":null,"customFields":[{"fieldId":"$fieldId","title":"Марка","type":"TEXT"}]}""")
            .andExpect(status().isBadRequest)
    }

    @Test fun `archive preserves connections and child default but prevents new connections`() {
        val token = registerAndLogin("archive-owner")
        val root = createCategory(token, "Root", customFields = listOf("Note" to "TEXT"))
        val id = root["categoryId"].asText()
        val fieldId = root["customFields"][0]["fieldId"].asText()
        putJson(token, "/category/$id/fields/$fieldId", """{"title":"Note","type":"TEXT","archived":true,"revision":0}""")
            .andExpect(status().isOk)
        val child = createCategory(token, "Child", id)
        assertEquals(fieldId, child["customFields"][0]["fieldId"].asText())
        val empty = postJson(token, "/category", """{"name":"Empty","parentId":"$id","customFields":[]}""")
            .andExpect(status().isOk).andReturn().response.contentAsString
        val emptyId = objectMapper.readTree(empty)["categoryId"].asText()
        putJson(token, "/category/$emptyId", """{"name":"Empty","parentId":"$id","customFields":[{"fieldId":"$fieldId","title":"Note","type":"TEXT"}]}""")
            .andExpect(status().isBadRequest)
    }

    @Test fun `preferences are personal persistent and reject stale writes`() {
        val owner = registerAndLogin("sort-owner")
        val member = registerAndLogin("sort-member")
        val rootId = createCategory(owner, "Root")["categoryId"].asText()
        inviteMember(owner, rootId, "sort-member").andExpect(status().isCreated)
        val childId = createCategory(owner, "Child", rootId)["categoryId"].asText()
        val body = """{"id":"spoofed","pageId":"$rootId","mode":"DESC","categoryIds":["$childId"],"revision":0}"""
        putJson(owner, "/catalog/preferences/$rootId", body).andExpect(status().isOk).andExpect(jsonPath("$.revision").value(1))
        getWithAuth(owner, "/catalog/preferences/$rootId").andExpect(jsonPath("$.mode").value("DESC"))
        getWithAuth(member, "/catalog/preferences/$rootId").andExpect(jsonPath("$.mode").value("CUSTOM"))
        putJson(owner, "/catalog/preferences/$rootId", body).andExpect(status().isConflict)
    }

    @Test fun `record transfer is atomic preserves hidden values and rejects stale preview`() {
        val token = registerAndLogin("move-owner")
        val root = createCategory(token, "Root", customFields = listOf("Note" to "TEXT"))
        val rootId = root["categoryId"].asText()
        val fieldId = root["customFields"][0]["fieldId"].asText()
        val fromId = createCategory(token, "Source", rootId)["categoryId"].asText()
        val target = postJson(token, "/category", """{"name":"Target","parentId":"$rootId","customFields":[]}""")
            .andExpect(status().isOk).andReturn().response.contentAsString
        val targetId = objectMapper.readTree(target)["categoryId"].asText()
        val cardId = createCard(token, fromId, "Record", customFieldValues = listOf(fieldId to "Keep me"))[0]["cardId"].asText()
        val request = """{"sourceId":"$fromId","targetId":"$targetId","cardId":"$cardId"}"""
        val preview = postJson(token, "/catalog/move/prepare", request).andExpect(status().isOk)
            .andExpect(jsonPath("$.removedFields[0].fieldId").value(fieldId)).andReturn().response.contentAsString
        val moveToken = objectMapper.readTree(preview)["token"].asText()
        postJson(token, "/catalog/move/confirm", """{"sourceId":"$fromId","targetId":"$targetId","cardId":"$cardId","token":"stale"}""")
            .andExpect(status().isConflict)
        postJson(token, "/catalog/move/confirm", """{"sourceId":"$fromId","targetId":"$targetId","cardId":"$cardId","token":"$moveToken","record":{"name":"Record","priceLevel":"MEDIUM_PRICE","qualityLevel":"MEDIUM_QUALITY","description":null,"customFieldValues":[]}}""")
            .andExpect(status().isOk)
        getWithAuth(token, "/category/$fromId/cards").andExpect(jsonPath("$").isEmpty)
        getWithAuth(token, "/category/$targetId/cards").andExpect(jsonPath("$[0].customFieldValues[0].value").value("Keep me"))
    }

    @Test fun `folder transfer rejects descendants and updates parent`() {
        val token = registerAndLogin("folder-owner")
        val rootId = createCategory(token, "Root")["categoryId"].asText()
        val folderId = createCategory(token, "Folder", rootId)["categoryId"].asText()
        val childId = createCategory(token, "Child", folderId)["categoryId"].asText()
        postJson(token, "/catalog/move/prepare", """{"sourceId":"$rootId","targetId":"$childId","categoryId":"$folderId"}""")
            .andExpect(status().isBadRequest)
        val request = """{"sourceId":"$folderId","targetId":"$rootId","categoryId":"$childId"}"""
        val preview = postJson(token, "/catalog/move/prepare", request).andExpect(status().isOk).andReturn().response.contentAsString
        val moveToken = objectMapper.readTree(preview)["token"].asText()
        postJson(token, "/catalog/move/confirm", """{"sourceId":"$folderId","targetId":"$rootId","categoryId":"$childId","token":"$moveToken"}""")
            .andExpect(status().isOk)
        assertEquals(rootId, categoryRepository.getCategoryByCategoryId(org.bson.types.ObjectId(childId))!!.parentId?.toHexString())
    }
}
