package com.readyroom.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.readyroom.app.ui.home.HomeScreen
import com.readyroom.app.ui.home.HomeViewModel
import com.readyroom.app.ui.scan.ScanScreen
import com.readyroom.app.ui.theme.ReadyRoomTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ReadyRoomTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ReadyRoomApp()
                }
            }
        }
    }
}

/**
 * Two screens, no navigation library.
 *
 * The `HomeViewModel` is hoisted here so the scan screen can hand its captured
 * photo back to the conversation the user returns to. A single flag is enough
 * while there are only two destinations — swap this for `androidx.navigation`
 * when a third screen, deep links, or back-stack restoration arrives.
 */
@Composable
private fun ReadyRoomApp() {
    var scanning by rememberSaveable { mutableStateOf(false) }

    // Hoisted: ScanScreen hands its result to the same instance HomeScreen uses.
    val homeViewModel: HomeViewModel = viewModel()

    // Route the system back gesture to the in-app back action.
    BackHandler(enabled = scanning) { scanning = false }

    if (scanning) {
        ScanScreen(
            onBack = { scanning = false },
            onScanAccepted = { scan ->
                homeViewModel.attachScan(scan)
                scanning = false
            },
        )
    } else {
        HomeScreen(
            onStartScan = { scanning = true },
            viewModel = homeViewModel,
        )
    }
}
