package cn.jlu.schedule.data

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CustomBackgroundStorageTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun cancelledAndEmptyCropsDoNotReplaceExistingBackground() {
        val filesDir = tmp.newFolder("files")
        val cacheDir = tmp.newFolder("cache")
        val existing = File(filesDir, "custom_background.jpg").apply { writeText("old background") }
        val cancelled = CustomBackgroundStorage.createCropFile(cacheDir).apply { writeText("new background") }
        cancelled.delete()
        assertEquals("old background", existing.readText())
        val empty = CustomBackgroundStorage.createCropFile(cacheDir)
        assertThrows(IllegalArgumentException::class.java) { CustomBackgroundStorage.commitCrop(filesDir, empty) }
        assertEquals("old background", existing.readText())
    }

    @Test fun completedCropReplacesBackgroundOnlyAtCommit() {
        val filesDir = tmp.newFolder("files")
        val cacheDir = tmp.newFolder("cache")
        val existing = File(filesDir, "custom_background.jpg").apply { writeText("old background") }
        val crop = CustomBackgroundStorage.createCropFile(cacheDir).apply { writeText("new background") }
        assertEquals("old background", existing.readText())
        assertEquals(existing, CustomBackgroundStorage.commitCrop(filesDir, crop))
        assertEquals("new background", existing.readText())
        assertFalse(crop.exists())
    }
}
