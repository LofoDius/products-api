package lofod.productsapi.service

import lofod.productsapi.exception.BadRequestException
import lofod.productsapi.exception.NotFoundException
import lofod.productsapi.model.Image
import lofod.productsapi.model.response.ImageIdResponse
import lofod.productsapi.model.response.ImageResponse
import lofod.productsapi.repository.ImageRepository
import net.coobird.thumbnailator.Thumbnails
import org.bson.types.ObjectId
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO

@Service
class ImageService(
    private val imageRepository: ImageRepository,
    private val categoryAccessService: CategoryAccessService,
) {

    fun createImage(file: MultipartFile): ImageIdResponse {
        categoryAccessService.currentUserId()
        val image = imageRepository.save(
            Image(value = Base64.getEncoder().encodeToString(compressImage(file)))
        )
        return ImageIdResponse(image.imageId.toString())
    }

    fun getImage(id: ObjectId): ImageResponse {
        categoryAccessService.currentUserId()
        val image = imageRepository.getImageByImageId(id)
            ?: throw NotFoundException("Не найдено изображение с id=$id")
        return ImageResponse(image.value)
    }

    fun deleteIfPresent(imageId: ObjectId?) {
        imageId?.let { imageRepository.deleteImageByImageId(it) }
    }

    private fun compressImage(multipartFile: MultipartFile): ByteArray {
        val quality = if (multipartFile.size < 2L * 1024 * 1024) {
            1f
        } else {
            2f / (multipartFile.size / 1024 / 1024)
        }

        val buffered = ImageIO.read(multipartFile.inputStream)
            ?: throw BadRequestException("Файл не является изображением или повреждён")

        var width = buffered.width
        var height = buffered.height
        val aspectRatio = width.toFloat() / height.toFloat()
        if (width > 1024) {
            width = 1024
            height = (width / aspectRatio).toInt()
        }

        val outputStream = ByteArrayOutputStream()
        Thumbnails.of(buffered)
            .width(width)
            .height(height)
            .outputQuality(quality)
            .toOutputStream(outputStream)

        return outputStream.toByteArray()
    }
}
