package com.nla.AIscanerPDF.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.nla.AIscanerPDF.presentation.ai.AiScreen
import com.nla.AIscanerPDF.presentation.camera.CameraScreen
import com.nla.AIscanerPDF.presentation.crop.CropScreen
import com.nla.AIscanerPDF.presentation.document.DocumentScreen
import com.nla.AIscanerPDF.presentation.editor.PageEditorScreen
import com.nla.AIscanerPDF.presentation.home.HomeScreen
import com.nla.AIscanerPDF.presentation.ocr.OcrScreen
import com.nla.AIscanerPDF.presentation.premium.PremiumScreen
import com.nla.AIscanerPDF.presentation.settings.SettingsScreen

/**
 * Между экранами передаются только идентификаторы (п. 16 ТЗ),
 * изображения загружаются по путям из БД.
 */
object Routes {
    const val HOME = "home"
    const val CAMERA = "camera?documentId={documentId}"
    const val CROP = "crop/{pageId}"
    const val EDITOR = "editor/{pageId}"
    const val DOCUMENT = "document/{documentId}"
    const val OCR = "ocr/{documentId}"
    const val AI = "ai/{documentId}"
    const val SETTINGS = "settings"
    const val PREMIUM = "premium"

    fun camera(documentId: String? = null) =
        if (documentId == null) "camera" else "camera?documentId=$documentId"

    fun crop(pageId: String) = "crop/$pageId"
    fun editor(pageId: String) = "editor/$pageId"
    fun document(documentId: String) = "document/$documentId"
    fun ocr(documentId: String) = "ocr/$documentId"
    fun ai(documentId: String) = "ai/$documentId"
}

@Composable
fun AppNavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) { HomeScreen(navController) }
        composable(
            route = Routes.CAMERA,
            arguments = listOf(
                navArgument("documentId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { CameraScreen(navController) }
        composable(
            route = Routes.CROP,
            arguments = listOf(navArgument("pageId") { type = NavType.StringType }),
        ) { CropScreen(navController) }
        composable(
            route = Routes.EDITOR,
            arguments = listOf(navArgument("pageId") { type = NavType.StringType }),
        ) { PageEditorScreen(navController) }
        composable(
            route = Routes.DOCUMENT,
            arguments = listOf(navArgument("documentId") { type = NavType.StringType }),
        ) { DocumentScreen(navController) }
        composable(
            route = Routes.OCR,
            arguments = listOf(navArgument("documentId") { type = NavType.StringType }),
        ) { OcrScreen(navController) }
        composable(
            route = Routes.AI,
            arguments = listOf(navArgument("documentId") { type = NavType.StringType }),
        ) { AiScreen(navController) }
        composable(Routes.SETTINGS) { SettingsScreen(navController) }
        composable(Routes.PREMIUM) { PremiumScreen(navController) }
    }
}
