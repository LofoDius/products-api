package lofod.productsapi.controller

import lofod.productsapi.model.response.AppReleaseResponse
import lofod.productsapi.service.AppReleaseService
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
class AppReleaseController(
    private val appReleaseService: AppReleaseService,
) {

    @GetMapping("/app/latest")
    fun getLatestRelease(): AppReleaseResponse =
        appReleaseService.getLatestRelease()

    @GetMapping("/app/download")
    fun downloadLatestApk(): ResponseEntity<ByteArray> {
        val apk = appReleaseService.getLatestApk()
        return ResponseEntity.ok()
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(apk.fileName).build().toString(),
            )
            .contentType(MediaType.parseMediaType(AppReleaseService.APK_CONTENT_TYPE))
            .body(apk.bytes)
    }

    @PostMapping("/app/releases", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun publishRelease(
        @RequestHeader(name = DEPLOY_TOKEN_HEADER, required = false) deployToken: String?,
        @RequestParam("file", required = false) file: MultipartFile?,
        @RequestParam("versionCode", required = false) versionCode: String?,
        @RequestParam("versionName", required = false) versionName: String?,
    ): ResponseEntity<AppReleaseResponse> {
        val release = appReleaseService.publishRelease(deployToken, file, versionCode, versionName)
        return ResponseEntity.status(HttpStatus.CREATED).body(release)
    }

    companion object {
        private const val DEPLOY_TOKEN_HEADER = "X-Deploy-Token"
    }
}
