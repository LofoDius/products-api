package lofod.productsapi.controller

import lofod.productsapi.model.request.CreateCardRequest
import lofod.productsapi.model.request.CreateCategoryRequest
import lofod.productsapi.model.request.InviteMemberRequest
import lofod.productsapi.model.request.UpdateCardRequest
import lofod.productsapi.model.request.UpdateCategoryRequest
import lofod.productsapi.model.response.CardResponse
import lofod.productsapi.model.response.CategoryResponse
import lofod.productsapi.model.response.ImageIdResponse
import lofod.productsapi.model.response.ImageResponse
import lofod.productsapi.model.response.MemberResponse
import lofod.productsapi.service.CardService
import lofod.productsapi.service.CategoryService
import lofod.productsapi.service.ImageService
import lofod.productsapi.service.MemberService
import lofod.productsapi.util.ObjectIds
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
class CategoryController(
    private val categoryService: CategoryService,
    private val cardService: CardService,
    private val imageService: ImageService,
    private val memberService: MemberService,
) {

    @GetMapping("/category/tree")
    fun getCategoryTree(): List<CategoryResponse> =
        categoryService.getAllCategories()

    @PostMapping("/category")
    fun createCategory(@RequestBody category: CreateCategoryRequest): CategoryResponse =
        categoryService.createCategory(category)

    @PostMapping("/category/image", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun createCategoryImage(@RequestPart("image") image: MultipartFile): ImageIdResponse =
        imageService.createImage(image)

    @GetMapping("/category/image/{id}")
    fun getCategoryImage(@PathVariable id: String): ImageResponse =
        imageService.getImage(ObjectIds.parse(id))

    @PutMapping("/category/{id}")
    fun updateCategory(
        @PathVariable id: String,
        @RequestBody category: UpdateCategoryRequest,
    ): CategoryResponse =
        categoryService.updateCategory(ObjectIds.parse(id), category)

    @GetMapping("/category/{categoryId}/cards")
    fun getCards(@PathVariable categoryId: String): List<CardResponse> =
        cardService.getCardsOfCategory(ObjectIds.parse(categoryId, "categoryId"))

    @PostMapping("/card/image", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun createCardImage(@RequestPart("image") image: MultipartFile): ImageIdResponse =
        imageService.createImage(image)

    @GetMapping("/card/image/{id}")
    fun getCardImage(@PathVariable id: String): ImageResponse =
        imageService.getImage(ObjectIds.parse(id))

    @PostMapping("/category/{categoryId}/card")
    fun createCard(
        @PathVariable categoryId: String,
        @RequestBody card: CreateCardRequest,
    ): List<CardResponse> =
        cardService.createCard(ObjectIds.parse(categoryId, "categoryId"), card)

    @PutMapping("/category/{categoryId}/card/{cardId}")
    fun updateCard(
        @PathVariable categoryId: String,
        @PathVariable cardId: String,
        @RequestBody request: UpdateCardRequest,
    ): List<CardResponse> =
        cardService.updateCard(
            ObjectIds.parse(categoryId, "categoryId"),
            ObjectIds.parse(cardId, "cardId"),
            request,
        )

    @GetMapping("/category/{categoryId}/card/{cardId}")
    fun getCard(
        @PathVariable categoryId: String,
        @PathVariable cardId: String,
    ): CardResponse =
        cardService.getCard(
            ObjectIds.parse(categoryId, "categoryId"),
            ObjectIds.parse(cardId, "cardId"),
        )

    @DeleteMapping("/category/{categoryId}/card/{cardId}")
    fun deleteCard(
        @PathVariable categoryId: String,
        @PathVariable cardId: String,
    ): ResponseEntity<Void> {
        cardService.deleteCard(
            ObjectIds.parse(categoryId, "categoryId"),
            ObjectIds.parse(cardId, "cardId"),
        )
        return ResponseEntity.ok().build()
    }

    @DeleteMapping("/category/{id}")
    fun deleteCategory(@PathVariable id: String): ResponseEntity<Void> {
        categoryService.deleteCategory(ObjectIds.parse(id))
        return ResponseEntity.ok().build()
    }

    @GetMapping("/cards/search/{query}")
    fun search(@PathVariable query: String): List<CardResponse> =
        cardService.searchCard(query)

    @PostMapping("/category/{id}/members")
    fun inviteMember(
        @PathVariable id: String,
        @RequestBody request: InviteMemberRequest,
    ): ResponseEntity<MemberResponse> =
        ResponseEntity.status(HttpStatus.CREATED)
            .body(memberService.inviteMember(ObjectIds.parse(id), request))

    @DeleteMapping("/category/{id}/members/{userId}")
    fun removeMember(
        @PathVariable id: String,
        @PathVariable userId: String,
    ): ResponseEntity<Void> {
        memberService.removeMember(ObjectIds.parse(id), ObjectIds.parse(userId, "userId"))
        return ResponseEntity.ok().build()
    }

    @GetMapping("/category/{id}/members")
    fun listMembers(@PathVariable id: String): List<MemberResponse> =
        memberService.listMembers(ObjectIds.parse(id))
}
