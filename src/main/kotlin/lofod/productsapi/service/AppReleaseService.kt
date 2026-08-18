package lofod.productsapi.service

import com.fasterxml.jackson.databind.ObjectMapper
import lofod.productsapi.config.AppReleaseProperties
import lofod.productsapi.exception.BadRequestException
import lofod.productsapi.exception.NotFoundException
import lofod.productsapi.exception.UnauthorizedException
import lofod.productsapi.model.response.AppReleaseResponse
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Instant

@Service
class AppReleaseService(
    private val appReleaseProperties: AppReleaseProperties,
    private val objectMapper: ObjectMapper,
) {

    fun getLatestRelease(): AppReleaseResponse {
        val metadata = readMetadata() ?: throw NotFoundException("App release not found")
        if (!Files.isRegularFile(apkPath())) {
            throw NotFoundException("App release not found")
        }
        return toDto(metadata)
    }

    fun getLatestApk(): ApkFile {
        val metadata = readMetadata() ?: throw NotFoundException("App release not found")
        val path = apkPath()
        if (!Files.isRegularFile(path)) {
            throw NotFoundException("App release not found")
        }
        return ApkFile(
            bytes = Files.readAllBytes(path),
            fileName = metadata.fileName,
        )
    }

    fun publishRelease(
        deployToken: String?,
        file: MultipartFile?,
        versionCode: String?,
        versionName: String?,
    ): AppReleaseResponse {
        requireDeployToken(deployToken)

        val parsedVersionCode = versionCode?.trim()?.toIntOrNull()
        if (parsedVersionCode == null || parsedVersionCode <= 0) {
            throw BadRequestException("versionCode must be a positive integer")
        }

        val trimmedVersionName = versionName?.trim()
        if (trimmedVersionName.isNullOrBlank()) {
            throw BadRequestException("versionName must not be blank")
        }

        if (file == null || file.isEmpty) {
            throw BadRequestException("file must not be empty")
        }
        if (file.size > appReleaseProperties.maxSizeBytes) {
            throw BadRequestException(
                "file must be at most ${appReleaseProperties.maxSizeBytes} bytes",
            )
        }

        val metadata = ReleaseMetadata(
            versionCode = parsedVersionCode,
            versionName = trimmedVersionName,
            releasedAt = Instant.now().toString(),
            fileName = apkFileName(file.originalFilename, trimmedVersionName),
        )

        val directory = directory()
        Files.createDirectories(directory)

        // APK first, metadata second: a crash in between leaves "no release" rather than a broken one.
        val temp = directory.resolve("$APK_FILE_NAME.tmp")
        file.inputStream.use { input ->
            Files.copy(input, temp, StandardCopyOption.REPLACE_EXISTING)
        }
        Files.move(temp, apkPath(), StandardCopyOption.REPLACE_EXISTING)

        Files.writeString(
            metadataPath(),
            objectMapper.writeValueAsString(
                mapOf(
                    "versionCode" to metadata.versionCode,
                    "versionName" to metadata.versionName,
                    "releasedAt" to metadata.releasedAt,
                    "fileName" to metadata.fileName,
                ),
            ),
            StandardCharsets.UTF_8,
        )

        return toDto(metadata)
    }

    private fun requireDeployToken(deployToken: String?) {
        val expected = appReleaseProperties.deployToken.trim()
        if (expected.isEmpty()) {
            throw UnauthorizedException("Deploy token is not configured")
        }

        val provided = deployToken?.trim().orEmpty()
        val matches = MessageDigest.isEqual(
            provided.toByteArray(StandardCharsets.UTF_8),
            expected.toByteArray(StandardCharsets.UTF_8),
        )
        if (!matches) {
            throw UnauthorizedException("Invalid deploy token")
        }
    }

    private fun readMetadata(): ReleaseMetadata? {
        val path = metadataPath()
        if (!Files.isRegularFile(path)) {
            return null
        }

        return runCatching {
            val node = objectMapper.readTree(Files.readString(path, StandardCharsets.UTF_8))
            val versionName = node.get("versionName")?.asText().orEmpty()
            ReleaseMetadata(
                versionCode = node.get("versionCode")?.asInt() ?: return null,
                versionName = versionName,
                releasedAt = node.get("releasedAt")?.asText().orEmpty(),
                fileName = node.get("fileName")?.asText()?.takeIf { it.isNotBlank() }
                    ?: apkFileName(null, versionName),
            )
        }.getOrNull()
    }

    private fun toDto(metadata: ReleaseMetadata): AppReleaseResponse =
        AppReleaseResponse(
            versionCode = metadata.versionCode,
            versionName = metadata.versionName,
            releasedAt = metadata.releasedAt,
            downloadPath = DOWNLOAD_PATH,
        )

    private fun apkFileName(originalFilename: String?, versionName: String): String {
        val sanitized = originalFilename
            ?.substringAfterLast('/')
            ?.substringAfterLast('\\')
            ?.replace(UNSAFE_FILE_NAME_CHARS, "_")
            ?.takeIf { it.endsWith(".apk", ignoreCase = true) && it.length > ".apk".length }
        return sanitized ?: "products-${versionName.replace(UNSAFE_FILE_NAME_CHARS, "_")}.apk"
    }

    private fun directory(): Path = Path.of(appReleaseProperties.path)

    private fun apkPath(): Path = directory().resolve(APK_FILE_NAME)

    private fun metadataPath(): Path = directory().resolve(METADATA_FILE_NAME)

    data class ApkFile(
        val bytes: ByteArray,
        val fileName: String,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as ApkFile
            if (!bytes.contentEquals(other.bytes)) return false
            if (fileName != other.fileName) return false
            return true
        }

        override fun hashCode(): Int {
            var result = bytes.contentHashCode()
            result = 31 * result + fileName.hashCode()
            return result
        }
    }

    private data class ReleaseMetadata(
        val versionCode: Int,
        val versionName: String,
        val releasedAt: String,
        val fileName: String,
    )

    companion object {
        const val DOWNLOAD_PATH = "/app/download"
        const val APK_CONTENT_TYPE = "application/vnd.android.package-archive"

        private const val APK_FILE_NAME = "latest.apk"
        private const val METADATA_FILE_NAME = "latest.json"
        private val UNSAFE_FILE_NAME_CHARS = Regex("[^A-Za-z0-9._-]")
    }
}
