package lofod.productsapi.model

import org.bson.types.ObjectId
import org.springframework.data.annotation.Id

data class Category(
    @Id
    val categoryId: ObjectId = ObjectId.get(),

    val name: String,
    val subcategories: MutableList<LiteCategory> = mutableListOf(),
    val parentId: ObjectId?,
    val cards: MutableList<Card> = mutableListOf(),
    val imageId: ObjectId?,
)
