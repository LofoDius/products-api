package lofod.productsapi.model

import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document
data class Session(
    @Id
    val id: ObjectId = ObjectId.get(),
    val userId: ObjectId,
    /** Mongo TTL index: document is removed when this time is reached (expireAfterSeconds = 0). */
    @Indexed(name = "session_expires_at_ttl", expireAfterSeconds = 0)
    val expiresAt: Instant,
)
