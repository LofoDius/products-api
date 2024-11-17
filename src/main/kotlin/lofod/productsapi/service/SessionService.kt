package lofod.productsapi.service

import lofod.productsapi.model.Password
import lofod.productsapi.model.Session
import lofod.productsapi.repository.PasswordRepository
import lofod.productsapi.repository.SessionRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service

@Service
class SessionService(
    private val passwordRepository: PasswordRepository,
    private val sessionRepository: SessionRepository
) {

    fun createSession(password: String): ResponseEntity<out Any> {
        val expectedPassword = passwordRepository.findAll()[0].password
        if (expectedPassword != password)
            return ResponseEntity.badRequest().body("Неправильный пароль")

        val session = Session()
        sessionRepository.save(session)

        return ResponseEntity(mapOf(Pair("Authorization", "Bearer ${session.id}")), HttpStatus.CREATED)
    }

    fun updatePassword(password: String): ResponseEntity<out Any> {
        val passwords = passwordRepository.findAll()
        if (passwords.size == 1) {
            passwordRepository.delete(passwords[0])
        }

        passwordRepository.save(Password(password = password))

        return ResponseEntity(HttpStatus.OK)
    }
}
