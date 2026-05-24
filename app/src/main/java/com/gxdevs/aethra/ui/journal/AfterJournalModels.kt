
package com.gxdevs.aethra.ui.journal

import android.net.Uri

data class Emotion(
    val id: String,
    val label: String,
    var value: Int? = null,
    var isActive: Boolean = false
)

data class AttachedFile(
    val id: Long, 
    val type: FileType, 
    val uri: Uri, 
    val name: String
)

enum class FileType {
    IMAGE,
    VIDEO,
    FILE
}


