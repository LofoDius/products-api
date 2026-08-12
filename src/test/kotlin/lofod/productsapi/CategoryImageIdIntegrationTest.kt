package lofod.productsapi

import lofod.productsapi.model.Image
import lofod.productsapi.support.AbstractApiIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class CategoryImageIdIntegrationTest : AbstractApiIntegrationTest() {

    @Test
    fun `updateCategory preserves imageId when request imageId is null`() {
        val token = registerAndLogin("img-owner")
        val imageId = imageRepository.save(Image(value = "old-image")).imageId.toHexString()
        val categoryId = createCategory(token, "WithImage", imageId = imageId)
            .get("categoryId").asText()

        putJson(
            token,
            "/category/$categoryId",
            """{"parentId":null,"name":"Renamed","imageId":null}""",
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Renamed"))
            .andExpect(jsonPath("$.imageId").value(imageId))
    }

    @Test
    fun `updateCategory replaces imageId when a new one is provided`() {
        val token = registerAndLogin("img-owner2")
        val oldId = imageRepository.save(Image(value = "old")).imageId
        val newId = imageRepository.save(Image(value = "new")).imageId
        val categoryId = createCategory(token, "SwapImage", imageId = oldId.toHexString())
            .get("categoryId").asText()

        putJson(
            token,
            "/category/$categoryId",
            """{"parentId":null,"name":"SwapImage","imageId":"${newId.toHexString()}"}""",
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.imageId").value(newId.toHexString()))

        // Old image document should be deleted by updateCategory
        assert(imageRepository.getImageByImageId(oldId) == null)
        assert(imageRepository.getImageByImageId(newId) != null)
    }

    @Test
    fun `updateCategory rejects invalid imageId`() {
        val token = registerAndLogin("img-owner3")
        val categoryId = createCategory(token, "BadImage").get("categoryId").asText()

        putJson(
            token,
            "/category/$categoryId",
            """{"parentId":null,"name":"BadImage","imageId":"not-an-id"}""",
        ).andExpect(status().isBadRequest)
    }
}
