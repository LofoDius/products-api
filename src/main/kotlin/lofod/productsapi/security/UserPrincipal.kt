package lofod.productsapi.security

import java.security.Principal

data class UserPrincipal(
    val userId: String,
    val username: String,
) : Principal {
    override fun getName(): String = username
}
