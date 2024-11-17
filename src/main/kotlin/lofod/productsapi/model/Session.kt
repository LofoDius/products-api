package lofod.productsapi.model

import org.bson.types.ObjectId
import org.springframework.data.annotation.Id

data class Session (
    @Id
    val id : ObjectId = ObjectId.get(),
)
