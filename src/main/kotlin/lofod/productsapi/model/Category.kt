package lofod.productsapi.model

import org.bson.types.ObjectId
import org.springframework.data.annotation.Id

data class Category(
    @Id
    val categoryId: ObjectId = ObjectId.get(),

    val name: String,
    val parentId: ObjectId?,
    val ownerId: ObjectId,
    /** Populated only on root categories (`parentId == null`); permissions flow down the tree. */
    val memberIds: MutableList<ObjectId> = mutableListOf(),
    val cards: MutableList<Card> = mutableListOf(),
    val imageId: ObjectId?,
)
