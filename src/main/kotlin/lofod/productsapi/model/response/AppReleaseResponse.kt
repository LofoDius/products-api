package lofod.productsapi.model.response

data class AppReleaseResponse(
    val versionCode: Int,
    val versionName: String,
    val releasedAt: String,
    val downloadPath: String,
)
