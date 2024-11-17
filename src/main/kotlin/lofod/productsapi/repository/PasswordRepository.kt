package lofod.productsapi.repository

import lofod.productsapi.model.Password
import org.springframework.data.mongodb.repository.MongoRepository

interface PasswordRepository: MongoRepository<Password, String> {
}
