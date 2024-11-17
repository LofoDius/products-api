package lofod.productsapi.model

import org.bson.types.ObjectId

data class Image(
    val imageId: ObjectId = ObjectId(),
    val value: String
)
