package lofod.productsapi.service.mapper

import lofod.productsapi.model.Card
import lofod.productsapi.model.response.CardResponse
import org.springframework.stereotype.Component

@Component
class CardMapper {
    fun toView(card: Card) : CardResponse {
        return CardResponse(
            cardId = card.cardId.toString(),
            name = card.name,
            imageId = card.imageId?.toString(),
            priceLevel = card.priceLevel,
            qualityLevel = card.qualityLevel,
            description = card.description,
        )
    }
}
