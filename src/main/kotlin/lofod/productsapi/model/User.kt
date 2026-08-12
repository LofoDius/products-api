package lofod.productsapi.model

import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document
data class User(
    @Id
    val userId: ObjectId = ObjectId.get(),
    @Indexed(unique = true)
    val username: String,
    val passwordHash: String,
    val createdAt: Instant = Instant.now(),
)
