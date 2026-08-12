package lofod.productsapi.repository

import lofod.productsapi.model.User
import org.bson.types.ObjectId
import org.springframework.data.mongodb.repository.MongoRepository

interface UserRepository : MongoRepository<User, String> {
    fun findByUsername(username: String): User?
    fun findByUserId(userId: ObjectId): User?
}
