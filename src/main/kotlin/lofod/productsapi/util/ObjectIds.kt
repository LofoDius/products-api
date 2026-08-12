package lofod.productsapi.util

import lofod.productsapi.exception.BadRequestException
import org.bson.types.ObjectId

object ObjectIds {

    fun parse(value: String, field: String = "id"): ObjectId {
        if (!ObjectId.isValid(value)) {
            throw BadRequestException("Невалидный ObjectId для $field: $value")
        }
        return ObjectId(value)
    }

    fun parseOptional(value: String?, field: String = "id"): ObjectId? =
        value?.let { parse(it, field) }
}
