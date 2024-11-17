package lofod.productsapi.controller

import lofod.productsapi.model.request.CreateCardRequest
import lofod.productsapi.model.request.CreateCategoryRequest
import lofod.productsapi.model.request.UpdateCardRequest
import lofod.productsapi.model.request.UpdateCategoryRequest
import lofod.productsapi.model.response.CategoryResponse
import lofod.productsapi.service.CategoryService
import org.bson.types.ObjectId
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
class CategoryController(private val service: CategoryService) {

    @GetMapping("/category/tree")
    fun getCategoryTree(): ResponseEntity<List<CategoryResponse>> {
        return service.getAllCategories()
    }

    @PostMapping("/category")
    fun createCategory(@RequestBody category: CreateCategoryRequest): ResponseEntity<out Any> {
        return service.createCategory(category)
    }

    @PostMapping("/category/image")
    fun createCategoryImage(@RequestBody image: MultipartFile): ResponseEntity<out Any> {
        return service.createImage(image)
    }

    @GetMapping("/category/image/{id}")
    fun getCategoryImage(@PathVariable id: String): ResponseEntity<out Any> {
        return service.getImage(ObjectId(id))
    }

    @PutMapping("/category/{id}")
    fun updateCategory(@PathVariable id: String, @RequestBody category: UpdateCategoryRequest)
            : ResponseEntity<out Any> {
        return service.updateCategory(ObjectId(id), category)
    }

    @DeleteMapping("/category/{id}")
    fun deleteCategory(@PathVariable id: String): ResponseEntity<out Any> {
        return service.deleteCategory(ObjectId(id))
    }

    @GetMapping("/category/{categoryId}/cards")
    fun getCards(@PathVariable categoryId: String): ResponseEntity<out Any> {
        return service.getCardsOfCategory(ObjectId(categoryId))
    }

    @PostMapping("/card/image")
    fun createCardImage(@RequestBody image: MultipartFile): ResponseEntity<out Any> {
        return service.createImage(image)
    }

    @GetMapping("/card/image/{id}")
    fun getCardImage(@PathVariable id: String): ResponseEntity<out Any> {
        return service.getImage(ObjectId(id))
    }

    @PostMapping("/category/{categoryId}/card")
    fun createCard(@PathVariable categoryId: String, @RequestBody card: CreateCardRequest): ResponseEntity<out Any> {
        return service.createCard(ObjectId(categoryId), card)
    }

    @PutMapping("/category/{categoryId}/card/{cardId}")
    fun updateCard(
        @PathVariable categoryId: String,
        @PathVariable cardId: String,
        @RequestBody request: UpdateCardRequest
    ): ResponseEntity<out Any> {
        return service.updateCard(ObjectId(categoryId), ObjectId(cardId), request)
    }

    @GetMapping("/category/{categoryId}/card/{cardId}")
    fun getCard(@PathVariable categoryId: String, @PathVariable cardId: String): ResponseEntity<out Any> {
        return service.getCard(ObjectId(categoryId), ObjectId(cardId))
    }

    @DeleteMapping("/category/{categoryId}/card/{cardId}")
    fun deleteCard(@PathVariable categoryId: String, @PathVariable cardId: String): ResponseEntity<out Any> {
        return service.deleteCard(ObjectId(categoryId), ObjectId(cardId))
    }
}
