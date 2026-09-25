package com.expensetracker.ui.screens.ocr

import android.Manifest
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrPermissionTest {

    @Test
    fun cameraPermissionConstantExists() {
        assertTrue(Manifest.permission.CAMERA.isNotEmpty())
    }
}
