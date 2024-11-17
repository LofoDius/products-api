package lofod.productsapi.security

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import lofod.productsapi.repository.SessionRepository
import org.bson.types.ObjectId
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.io.IOException
import javax.security.auth.login.CredentialException

@Component
class SessionRequestFilter(
    private val sessionRepository: SessionRepository
) : OncePerRequestFilter() {

    private val permittedEndpoints =
        listOf("/session", "/category/tree", "/category/image/.*", "/category/.*/cards", "/card/image/.*", "/password")

    @Throws(ServletException::class, IOException::class)
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        if (permittedEndpoints.any {
                val regex = Regex(it)
                regex.matches(request.requestURI)
            }) {
            filterChain.doFilter(request, response)
            return
        }

        val authorizationHeader = request.getHeader("Authorization")
            ?: throw CredentialException("Не передан заголовок Authorization")
        sessionRepository.getSessionById(ObjectId(authorizationHeader.replace("Bearer ", "")))
            ?: throw CredentialException("Сессия c id=${authorizationHeader} не найдена")

        filterChain.doFilter(request, response)
    }
}