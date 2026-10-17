package com.example.measurear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.measurear.ar.ArMeasureSession
import com.example.measurear.ui.camera.MeasureCameraScreen
import com.example.measurear.ui.camera.MeasureViewModel
import com.example.measurear.ui.history.HistoryScreen
import com.example.measurear.ui.theme.MeasureArTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var arMeasureSession: ArMeasureSession

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            MeasureArTheme(darkTheme = true) {
                MeasureArNavigation(arMeasureSession = arMeasureSession)
            }
        }
    }
}

@Composable
private fun MeasureArNavigation(arMeasureSession: ArMeasureSession) {
    val navController = rememberNavController()
    val sharedViewModel: MeasureViewModel = hiltViewModel()

    NavHost(
        navController = navController,
        startDestination = "camera"
    ) {
        composable("camera") {
            MeasureCameraScreen(
                viewModel = sharedViewModel,
                arMeasureSession = arMeasureSession,
                onOpenHistory = { navController.navigate("history") }
            )
        }
        composable("history") {
            HistoryScreen(
                viewModel = sharedViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
