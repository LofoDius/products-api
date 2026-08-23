package lofod.productsapi.model.response

data class SearchResponse(
    val categories: List<CategorySearchHit>,
    val cards: List<CardResponse>,
)
