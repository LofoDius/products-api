package lofod.productsapi.controller

import lofod.productsapi.model.request.CreateSessionRequest
import lofod.productsapi.service.SessionService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
class SessionController(private val sessionService: SessionService) {

    @PostMapping("/session")
    fun createSession(@RequestBody session: CreateSessionRequest): ResponseEntity<out Any> {
        return sessionService.createSession(session.password)
    }

    @PutMapping("/password")
    fun updatePassword(@RequestBody password: CreateSessionRequest): ResponseEntity<out Any> {
        return sessionService.updatePassword(password.password)
    }
}
