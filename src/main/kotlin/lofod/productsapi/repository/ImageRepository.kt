package lofod.productsapi.repository

import lofod.productsapi.model.Image
import org.bson.types.ObjectId
import org.springframework.data.mongodb.repository.MongoRepository

interface ImageRepository: MongoRepository<Image, String> {
    fun getImageByImageId(id: ObjectId): Image?
    fun deleteImageByImageId(id: ObjectId)
}
