package cn.jlu.schedule.data

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** A cancelled or failed crop must never overwrite the current background. */
object CustomBackgroundStorage {
    fun createCropFile(cacheDir: File): File {
        val directory = File(cacheDir, "background_crops")
        check(directory.isDirectory || directory.mkdirs()) { "无法创建背景裁剪目录" }
        return File.createTempFile("background_", ".jpg", directory)
    }

    fun commitCrop(filesDir: File, croppedFile: File): File {
        require(croppedFile.isFile && croppedFile.length() > 0) { "裁剪未生成图片" }
        val destination = File(filesDir, "custom_background.jpg")
        // Both directories are in the app's internal storage; replace only after a complete crop.
        Files.move(croppedFile.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING)
        return destination
    }
}
