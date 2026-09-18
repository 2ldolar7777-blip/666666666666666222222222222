package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.saqr.engine.SaqrMaestroKernel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("SAQR OS", appName)
  }

  @Test
  fun `saqr kernel initializes with default plan and hardware tier`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val kernel = SaqrMaestroKernel(context)
    val state = kernel.systemState.value
    assertNotNull(state.hardwareProfile)
    kernel.executeGoal("System Diagnostic Verification")
    assertNotNull(kernel.dagPlanner.currentPlan.value)
    kernel.shutdown()
  }
}
