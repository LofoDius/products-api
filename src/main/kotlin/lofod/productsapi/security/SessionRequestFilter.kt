package lofod.productsapi.security

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import lofod.productsapi.exception.ErrorResponse
import lofod.productsapi.service.AuthService
import org.bson.types.ObjectId
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.io.IOException

@Component
class SessionRequestFilter(
    private val authService: AuthService,
    private val objectMapper: ObjectMapper,
) : OncePerRequestFilter() {

    private val publicExactPaths = setOf(
        "/auth/register",
        "/auth/login",
    )

    @Throws(ServletException::class, IOException::class)
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        if (HttpMethod.OPTIONS.matches(request.method) || isPublicPath(request.requestURI)) {
            filterChain.doFilter(request, response)
            return
        }

        val authorizationHeader = request.getHeader("Authorization")
        if (authorizationHeader.isNullOrBlank()) {
            writeUnauthorized(response, "Не передан заголовок Authorization")
            return
        }

        val token = authorizationHeader.removePrefix("Bearer ").trim()
        val sessionId = try {
            ObjectId(token)
        } catch (_: IllegalArgumentException) {
            writeUnauthorized(response, "Невалидный токен сессии")
            return
        }

        val principal = authService.resolvePrincipal(sessionId)
        if (principal == null) {
            writeUnauthorized(response, "Сессия не найдена или истекла")
            return
        }

        val authentication = UsernamePasswordAuthenticationToken(principal, null, emptyList())
        SecurityContextHolder.getContext().authentication = authentication

        try {
            filterChain.doFilter(request, response)
        } finally {
            SecurityContextHolder.clearContext()
        }
    }

    private fun isPublicPath(uri: String): Boolean = publicExactPaths.contains(uri)

    private fun writeUnauthorized(response: HttpServletResponse, message: String) {
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        objectMapper.writeValue(
            response.outputStream,
            ErrorResponse(code = "UNAUTHORIZED", message = message),
        )
    }
}
