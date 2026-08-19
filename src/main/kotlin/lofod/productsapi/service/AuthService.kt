package lofod.productsapi.service

import lofod.productsapi.exception.BadRequestException
import lofod.productsapi.exception.ConflictException
import lofod.productsapi.exception.UnauthorizedException
import lofod.productsapi.model.Session
import lofod.productsapi.model.User
import lofod.productsapi.model.request.AuthRequest
import lofod.productsapi.model.response.UserResponse
import lofod.productsapi.repository.SessionRepository
import lofod.productsapi.repository.UserRepository
import lofod.productsapi.security.UserPrincipal
import org.bson.types.ObjectId
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DuplicateKeyException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit

data class LoginResult(
    val user: UserResponse,
    val sessionToken: String,
)

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val sessionRepository: SessionRepository,
    private val passwordEncoder: BCryptPasswordEncoder,
    @Value("\${app.session.ttl-days:30}")
    private val sessionTtlDays: Long,
) {

    fun register(request: AuthRequest): UserResponse {
        if (request.username.isBlank() || request.password.isBlank()) {
            throw BadRequestException("логин и пароль обязательны")
        }
        if (userRepository.findByUsername(request.username) != null) {
            throw ConflictException("Пользователь с таким username уже существует")
        }

        val user = User(
            username = request.username,
            passwordHash = passwordEncoder.encode(request.password),
        )

        return try {
            val saved = userRepository.save(user)
            UserResponse(
                userId = saved.userId.toHexString(),
                username = saved.username,
            )
        } catch (_: DuplicateKeyException) {
            throw ConflictException("Пользователь с таким username уже существует")
        }
    }

    fun login(request: AuthRequest): LoginResult {
        val user = userRepository.findByUsername(request.username)
            ?: throw UnauthorizedException("Неверный логин или пароль")

        if (!passwordEncoder.matches(request.password, user.passwordHash)) {
            throw UnauthorizedException("Неверный логин или пароль")
        }

        val session = Session(
            userId = user.userId,
            expiresAt = Instant.now().plus(sessionTtlDays, ChronoUnit.DAYS),
        )
        sessionRepository.save(session)

        return LoginResult(
            user = UserResponse(
                userId = user.userId.toHexString(),
                username = user.username,
            ),
            sessionToken = session.id.toHexString(),
        )
    }

    fun logout(authorizationHeader: String?) {
        val sessionId = extractSessionId(authorizationHeader)
            ?: throw UnauthorizedException("Не передан заголовок Authorization")

        val session = findValidSession(sessionId)
            ?: throw UnauthorizedException("Сессия не найдена или истекла")

        sessionRepository.delete(session)
        SecurityContextHolder.clearContext()
    }

    fun me(): UserResponse {
        val principal = SecurityContextHolder.getContext().authentication?.principal as? UserPrincipal
            ?: throw UnauthorizedException("Не авторизован")

        return UserResponse(
            userId = principal.userId,
            username = principal.username,
        )
    }

    fun resolvePrincipal(sessionId: ObjectId): UserPrincipal? {
        val session = findValidSession(sessionId) ?: return null
        val user = userRepository.findByUserId(session.userId) ?: return null
        return UserPrincipal(
            userId = user.userId.toHexString(),
            username = user.username,
        )
    }

    private fun findValidSession(sessionId: ObjectId): Session? {
        val session = sessionRepository.getSessionById(sessionId) ?: return null
        if (session.expiresAt.isBefore(Instant.now())) {
            sessionRepository.delete(session)
            return null
        }
        return session
    }

    private fun extractSessionId(authorizationHeader: String?): ObjectId? {
        if (authorizationHeader.isNullOrBlank()) return null
        val token = authorizationHeader.removePrefix("Bearer ").trim()
        return try {
            ObjectId(token)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
