package lofod.productsapi.repository

import lofod.productsapi.model.Category
import org.bson.types.ObjectId
import org.springframework.data.mongodb.repository.MongoRepository

interface CategoryRepository: MongoRepository<Category, String> {
    fun getCategoryByCategoryId(categoryId: ObjectId): Category?
    fun getCategoriesByParentId(parentId: ObjectId?): List<Category>
    fun deleteCategoryByCategoryId(categoryId: ObjectId)
}
