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
    /** Active custom field schema; at most 10. Missing in old docs → empty. */
    val customFields: List<CustomFieldDefinition> = emptyList(),
    /** Removed fields kept for restore; card values for archived ids are never purged. */
    val customFieldArchive: List<CustomFieldDefinition> = emptyList(),
)
