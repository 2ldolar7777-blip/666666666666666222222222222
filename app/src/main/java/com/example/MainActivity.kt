package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.example.saqr.engine.SaqrMaestroKernel
import com.example.saqr.ui.screens.SaqrDashboardScreen
import com.example.saqr.ui.theme.SaqrColors
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  private var kernelInstance: SaqrMaestroKernel? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    
    val kernel = SaqrMaestroKernel(applicationContext).also {
      kernelInstance = it
    }

    setContent {
      MyApplicationTheme(darkTheme = true) {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = SaqrColors.VoidBlack
        ) {
          SaqrDashboardScreen(kernel = kernel)
        }
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    kernelInstance?.shutdown()
  }
}

@androidx.compose.runtime.Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
  androidx.compose.material3.Text(
    text = "SAQR OS :: $name",
    modifier = modifier,
    color = SaqrColors.ElectricCyan
  )
}
