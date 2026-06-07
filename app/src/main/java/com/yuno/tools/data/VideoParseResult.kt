package com.yuno.tools.data

import android.os.Parcel
import android.os.Parcelable
import com.google.gson.annotations.SerializedName

data class VideoParseResult(
    val title: String = "",
    val videoUrl: String = "",
    val coverUrl: String = "",
    val musicUrl: String = "",
    val authorName: String = "",
    val authorAvatar: String = "",
    val content: String = "",
    val images: List<String> = emptyList(),
    val isImageSet: Boolean = false
) : Parcelable {
    constructor(parcel: Parcel) : this(
        title = parcel.readString() ?: "",
        videoUrl = parcel.readString() ?: "",
        coverUrl = parcel.readString() ?: "",
        musicUrl = parcel.readString() ?: "",
        authorName = parcel.readString() ?: "",
        authorAvatar = parcel.readString() ?: "",
        content = parcel.readString() ?: "",
        images = parcel.createStringArrayList() ?: emptyList(),
        isImageSet = parcel.readByte() != 0.toByte()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(title)
        parcel.writeString(videoUrl)
        parcel.writeString(coverUrl)
        parcel.writeString(musicUrl)
        parcel.writeString(authorName)
        parcel.writeString(authorAvatar)
        parcel.writeString(content)
        parcel.writeStringList(images)
        parcel.writeByte(if (isImageSet) 1 else 0)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<VideoParseResult> {
        override fun createFromParcel(parcel: Parcel): VideoParseResult = VideoParseResult(parcel)
        override fun newArray(size: Int): Array<VideoParseResult?> = arrayOfNulls(size)
    }
}

data class ApiResponse(
    val code: Int = -1,
    val msg: String = "",
    val data: VideoParseData? = null
)

data class VideoParseData(
    val title: String? = null,
    @SerializedName("video_url") val videoUrl: String? = null,
    @SerializedName("cover_url") val coverUrl: String? = null,
    @SerializedName("music_url") val musicUrl: String? = null,
    val content: String? = null,
    val images: List<com.google.gson.JsonElement>? = null,
    @SerializedName("image_list") val imageList: List<ImageItem>? = null,
    val author: AuthorInfo? = null
)

data class AuthorInfo(
    val uid: String? = null,
    val name: String? = null,
    val avatar: String? = null
)

data class ImageItem(
    val url: String? = null,
    @SerializedName("img_url") val imgUrl: String? = null
)