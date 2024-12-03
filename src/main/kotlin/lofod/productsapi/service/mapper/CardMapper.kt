package lofod.productsapi.service.mapper

import lofod.productsapi.model.Card
import lofod.productsapi.model.response.CardResponse
import org.bson.types.ObjectId
import org.springframework.stereotype.Component

@Component
class CardMapper {
    fun toView(categoryId: ObjectId, card: Card) : CardResponse {
        return CardResponse(
            cardId = card.cardId.toString(),
            categoryId = categoryId.toString(),
            name = card.name,
            imageId = card.imageId?.toString(),
            priceLevel = card.priceLevel,
            qualityLevel = card.qualityLevel,
            description = card.description,
        )
    }
}
