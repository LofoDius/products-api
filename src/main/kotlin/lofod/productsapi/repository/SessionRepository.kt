package lofod.productsapi.repository

import lofod.productsapi.model.Session
import org.bson.types.ObjectId
import org.springframework.data.mongodb.repository.MongoRepository

interface SessionRepository: MongoRepository<Session, String> {
    fun getSessionById(id: ObjectId): Session?
}
