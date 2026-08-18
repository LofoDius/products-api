package lofod.productsapi

import lofod.productsapi.support.AbstractApiIntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteIfExists

@TestPropertySource(
    properties = [
        "app.releases.path=build/test-app-releases-api",
        "app.releases.max-size-bytes=1024",
        "app.releases.deploy-token=test-deploy-token",
    ],
)
class AppReleaseApiTest : AbstractApiIntegrationTest() {

    private val releasesDirectory = Path.of("build/test-app-releases-api")

    @BeforeEach
    fun clearReleases() {
        Files.createDirectories(releasesDirectory)
        releasesDirectory.resolve("latest.apk").deleteIfExists()
        releasesDirectory.resolve("latest.json").deleteIfExists()
    }

    @Test
    fun `latest without release returns 404`() {
        mockMvc.perform(get("/app/latest"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `download without release returns 404`() {
        mockMvc.perform(get("/app/download"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `upload without deploy token returns 401`() {
        mockMvc.perform(
            multipart("/app/releases")
                .file(apkFile())
                .param("versionCode", "12")
                .param("versionName", "1.2.0"),
        ).andExpect(status().isUnauthorized)

        mockMvc.perform(get("/app/latest"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `upload with wrong deploy token returns 401`() {
        mockMvc.perform(
            multipart("/app/releases")
                .file(apkFile())
                .param("versionCode", "12")
                .param("versionName", "1.2.0")
                .header("X-Deploy-Token", "nope"),
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `upload publishes release available without auth`() {
        val apkBytes = "apk-bytes".toByteArray()

        val createResponse = mockMvc.perform(
            multipart("/app/releases")
                .file(MockMultipartFile("file", "products-1.2.0.apk", null, apkBytes))
                .param("versionCode", "12")
                .param("versionName", "1.2.0")
                .header("X-Deploy-Token", "test-deploy-token"),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.versionCode").value(12))
            .andExpect(jsonPath("$.versionName").value("1.2.0"))
            .andExpect(jsonPath("$.downloadPath").value("/app/download"))
            .andReturn()
            .response
            .contentAsString

        val created = objectMapper.readTree(createResponse)
        assertTrue(created.get("releasedAt").asText().isNotBlank())

        val latestResponse = mockMvc.perform(get("/app/latest"))
            .andExpect(status().isOk)
            .andReturn()
            .response
            .contentAsString

        val latest = objectMapper.readTree(latestResponse)
        assertEquals(12, latest.get("versionCode").asInt())
        assertEquals("1.2.0", latest.get("versionName").asText())
        assertEquals("/app/download", latest.get("downloadPath").asText())
        assertEquals(created.get("releasedAt").asText(), latest.get("releasedAt").asText())

        val downloadResponse = mockMvc.perform(get("/app/download"))
            .andExpect(status().isOk)
            .andReturn()
            .response

        assertEquals("application/vnd.android.package-archive", downloadResponse.contentType)
        assertEquals(
            "attachment; filename=\"products-1.2.0.apk\"",
            downloadResponse.getHeader(HttpHeaders.CONTENT_DISPOSITION),
        )
        assertTrue(downloadResponse.contentAsByteArray.contentEquals(apkBytes))
    }

    @Test
    fun `upload overwrites previous release`() {
        mockMvc.perform(
            multipart("/app/releases")
                .file(MockMultipartFile("file", "old.apk", null, "old".toByteArray()))
                .param("versionCode", "12")
                .param("versionName", "1.2.0")
                .header("X-Deploy-Token", "test-deploy-token"),
        ).andExpect(status().isCreated)

        mockMvc.perform(
            multipart("/app/releases")
                .file(MockMultipartFile("file", "new.apk", null, "new".toByteArray()))
                .param("versionCode", "13")
                .param("versionName", "1.3.0")
                .header("X-Deploy-Token", "test-deploy-token"),
        ).andExpect(status().isCreated)

        val latest = objectMapper.readTree(
            mockMvc.perform(get("/app/latest"))
                .andExpect(status().isOk)
                .andReturn()
                .response
                .contentAsString,
        )
        assertEquals(13, latest.get("versionCode").asInt())
        assertEquals("1.3.0", latest.get("versionName").asText())

        val downloaded = mockMvc.perform(get("/app/download"))
            .andExpect(status().isOk)
            .andReturn()
            .response
            .contentAsByteArray
        assertEquals("new", String(downloaded))
    }

    @Test
    fun `upload with invalid versionCode returns 400`() {
        mockMvc.perform(
            multipart("/app/releases")
                .file(apkFile())
                .param("versionCode", "not-a-number")
                .param("versionName", "1.2.0")
                .header("X-Deploy-Token", "test-deploy-token"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
    }

    @Test
    fun `upload too large apk returns 400`() {
        mockMvc.perform(
            multipart("/app/releases")
                .file(MockMultipartFile("file", "big.apk", null, ByteArray(2048)))
                .param("versionCode", "12")
                .param("versionName", "1.2.0")
                .header("X-Deploy-Token", "test-deploy-token"),
        ).andExpect(status().isBadRequest)

        mockMvc.perform(get("/app/latest"))
            .andExpect(status().isNotFound)
    }

    private fun apkFile(): MockMultipartFile =
        MockMultipartFile("file", "app.apk", null, "apk-bytes".toByteArray())
}
