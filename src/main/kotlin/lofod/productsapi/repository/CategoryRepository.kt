package lofod.productsapi.repository

import lofod.productsapi.model.Category
import org.bson.types.ObjectId
import org.springframework.data.mongodb.repository.MongoRepository

interface CategoryRepository : MongoRepository<Category, String> {
    fun getCategoryByCategoryId(categoryId: ObjectId): Category?
    fun findByParentId(parentId: ObjectId): List<Category>
    fun findByParentIdIsNull(): List<Category>
    fun deleteCategoryByCategoryId(categoryId: ObjectId)
}
