package lofod.productsapi.controller

import lofod.productsapi.model.request.AuthRequest
import lofod.productsapi.model.response.UserResponse
import lofod.productsapi.service.AuthService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/auth")
class AuthController(private val authService: AuthService) {

    @PostMapping("/register")
    fun register(@RequestBody request: AuthRequest): ResponseEntity<UserResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request))

    @PostMapping("/login")
    fun login(@RequestBody request: AuthRequest): ResponseEntity<UserResponse> {
        val result = authService.login(request)
        return ResponseEntity.status(HttpStatus.CREATED)
            .header("Authorization", result.sessionToken)
            .body(result.user)
    }

    @DeleteMapping("/logout")
    fun logout(
        @RequestHeader(value = "Authorization", required = false) authorization: String?,
    ): ResponseEntity<Void> {
        authService.logout(authorization)
        return ResponseEntity.ok().build()
    }

    @GetMapping("/me")
    fun me(): UserResponse = authService.me()
}
