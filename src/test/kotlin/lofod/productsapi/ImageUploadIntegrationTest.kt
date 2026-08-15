package lofod.productsapi

import lofod.productsapi.support.AbstractApiIntegrationTest
import org.bson.types.ObjectId
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class ImageUploadIntegrationTest : AbstractApiIntegrationTest() {

    @ParameterizedTest
    @ValueSource(strings = ["/category/image", "/card/image"])
    fun `upload jpeg image returns imageId`(path: String) {
        val token = registerAndLogin("img-jpeg")
        val part = imagePart("photo.jpg", "image/jpeg", "jpg")

        mockMvc.perform(
            multipart(path)
                .file(part)
                .header("Authorization", authHeader(token)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.imageId").isNotEmpty)
            .andExpect { result ->
                val imageId = objectMapper.readTree(result.response.contentAsString)
                    .get("imageId").asText()
                assertTrue(ObjectId.isValid(imageId))
                assertTrue(imageRepository.getImageByImageId(ObjectId(imageId)) != null)
            }
    }

    @ParameterizedTest
    @ValueSource(strings = ["/category/image", "/card/image"])
    fun `upload png image is stored as jpeg without output-format error`(path: String) {
        val token = registerAndLogin("img-png")
        val part = imagePart("photo.png", "image/png", "png")

        mockMvc.perform(
            multipart(path)
                .file(part)
                .header("Authorization", authHeader(token)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.imageId").isNotEmpty)
    }

    @Test
    fun `upload non-image returns 400`() {
        val token = registerAndLogin("img-bad")
        val part = MockMultipartFile(
            "image",
            "not-image.txt",
            MediaType.TEXT_PLAIN_VALUE,
            "not an image".toByteArray(),
        )

        mockMvc.perform(
            multipart("/category/image")
                .file(part)
                .header("Authorization", authHeader(token)),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
    }

    private fun imagePart(filename: String, contentType: String, format: String): MockMultipartFile {
        val image = BufferedImage(32, 24, BufferedImage.TYPE_INT_RGB).apply {
            val g = createGraphics()
            g.color = Color.BLUE
            g.fillRect(0, 0, width, height)
            g.dispose()
        }
        val bytes = ByteArrayOutputStream().use { out ->
            ImageIO.write(image, format, out)
            out.toByteArray()
        }
        return MockMultipartFile("image", filename, contentType, bytes)
    }
}
