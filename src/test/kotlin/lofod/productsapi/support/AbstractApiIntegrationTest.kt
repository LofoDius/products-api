package lofod.productsapi.support

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import lofod.productsapi.repository.CategoryRepository
import lofod.productsapi.repository.ImageRepository
import lofod.productsapi.repository.SessionRepository
import lofod.productsapi.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
abstract class AbstractApiIntegrationTest {

    @Autowired
    protected lateinit var mockMvc: MockMvc

    @Autowired
    protected lateinit var objectMapper: ObjectMapper

    @Autowired
    protected lateinit var userRepository: UserRepository

    @Autowired
    protected lateinit var sessionRepository: SessionRepository

    @Autowired
    protected lateinit var categoryRepository: CategoryRepository

    @Autowired
    protected lateinit var imageRepository: ImageRepository

    @BeforeEach
    fun cleanDatabase() {
        categoryRepository.deleteAll()
        imageRepository.deleteAll()
        sessionRepository.deleteAll()
        userRepository.deleteAll()
    }

    protected fun register(username: String, password: String = "secret"): ResultActions =
        mockMvc.perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(authJson(username, password)),
        )

    protected fun login(username: String, password: String = "secret"): ResultActions =
        mockMvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(authJson(username, password)),
        )

    protected fun registerAndLogin(username: String, password: String = "secret"): String {
        register(username, password).andExpect(status().isCreated)
        val result = login(username, password)
            .andExpect(status().isCreated)
            .andExpect(header().exists("Authorization"))
            .andReturn()
        return result.response.getHeader("Authorization")!!
    }

    protected fun authHeader(token: String): String = "Bearer $token"

    protected fun createCategory(
        token: String,
        name: String,
        parentId: String? = null,
        imageId: String? = null,
    ): JsonNode {
        val body = objectMapper.createObjectNode().apply {
            put("name", name)
            if (parentId == null) putNull("parentId") else put("parentId", parentId)
            if (imageId == null) putNull("imageId") else put("imageId", imageId)
        }
        val result = mockMvc.perform(
            post("/category")
                .header("Authorization", authHeader(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)),
        )
            .andExpect(status().isOk)
            .andReturn()
        return objectMapper.readTree(result.response.contentAsString)
    }

    protected fun inviteMember(token: String, categoryId: String, username: String): ResultActions =
        mockMvc.perform(
            post("/category/$categoryId/members")
                .header("Authorization", authHeader(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"$username"}"""),
        )

    protected fun createCard(
        token: String,
        categoryId: String,
        name: String,
        description: String? = null,
    ): JsonNode {
        val body = """
            {
              "name": "$name",
              "imageId": null,
              "priceLevel": "MEDIUM_PRICE",
              "qualityLevel": "MEDIUM_QUALITY",
              "description": ${description?.let { "\"$it\"" } ?: "null"}
            }
        """.trimIndent()
        val result = mockMvc.perform(
            post("/category/$categoryId/card")
                .header("Authorization", authHeader(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        )
            .andExpect(status().isOk)
            .andReturn()
        return objectMapper.readTree(result.response.contentAsString)
    }

    protected fun putJson(token: String, path: String, json: String): ResultActions =
        mockMvc.perform(
            put(path)
                .header("Authorization", authHeader(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json),
        )

    protected fun deleteWithAuth(token: String, path: String): ResultActions =
        mockMvc.perform(
            delete(path).header("Authorization", authHeader(token)),
        )

    protected fun getWithAuth(token: String, path: String): ResultActions =
        mockMvc.perform(
            get(path).header("Authorization", authHeader(token)),
        )

    private fun authJson(username: String, password: String): String =
        """{"username":"$username","password":"$password"}"""
}
