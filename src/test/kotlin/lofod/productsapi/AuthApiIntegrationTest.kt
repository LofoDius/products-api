package lofod.productsapi

import lofod.productsapi.model.Session
import lofod.productsapi.support.AbstractApiIntegrationTest
import org.bson.types.ObjectId
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.time.temporal.ChronoUnit

class AuthApiIntegrationTest : AbstractApiIntegrationTest() {

    @Test
    fun `register login logout and me`() {
        register("alice", "pass").andExpect(status().isCreated)
            .andExpect(jsonPath("$.username").value("alice"))
            .andExpect(jsonPath("$.userId").isNotEmpty)

        val token = login("alice", "pass")
            .andExpect(status().isCreated)
            .andReturn()
            .response
            .getHeader("Authorization")!!

        getWithAuth(token, "/auth/me")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.username").value("alice"))

        mockMvc.perform(
            delete("/auth/logout").header("Authorization", authHeader(token)),
        ).andExpect(status().isOk)

        getWithAuth(token, "/auth/me").andExpect(status().isUnauthorized)
    }

    @Test
    fun `missing authorization returns 401`() {
        mockMvc.perform(get("/auth/me")).andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
    }

    @Test
    fun `expired session returns 401`() {
        register("bob", "pass").andExpect(status().isCreated)
        val userId = userRepository.findByUsername("bob")!!.userId
        val expired = sessionRepository.save(
            Session(
                userId = userId,
                expiresAt = Instant.now().minus(1, ChronoUnit.HOURS),
            ),
        )

        getWithAuth(expired.id.toHexString(), "/auth/me")
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
    }

    @Test
    fun `invalid session token returns 401`() {
        mockMvc.perform(
            get("/auth/me").header("Authorization", "Bearer not-an-object-id"),
        ).andExpect(status().isUnauthorized)

        mockMvc.perform(
            get("/auth/me").header("Authorization", "Bearer ${ObjectId().toHexString()}"),
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `wrong password returns 401`() {
        register("carol", "pass").andExpect(status().isCreated)
        mockMvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"carol","password":"wrong"}"""),
        ).andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.message").value("Неверный логин или пароль"))
    }
}
