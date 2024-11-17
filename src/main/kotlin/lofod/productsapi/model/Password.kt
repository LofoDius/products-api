package lofod.productsapi.model

import org.bson.types.ObjectId
import org.springframework.data.annotation.Id

data class Password(
    @Id
    val id: ObjectId = ObjectId.get(),
    val password: String,
)
