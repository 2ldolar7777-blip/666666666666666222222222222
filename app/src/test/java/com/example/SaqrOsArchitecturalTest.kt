package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.saqr.engine.*
import com.example.saqr.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SaqrOsArchitecturalTest {

    private val context: Context by lazy { ApplicationProvider.getApplicationContext() }

    @Test
    fun `test vector memory semantic cosine similarity ranking`() {
        val telemetry = TelemetryEngine()
        val memoryEngine = VectorMemoryEngine(telemetry)

        memoryEngine.storeNode(
            title = "Coroutine Cancellation Policy",
            content = "Always handle CancellationException and avoid swallowing it in try-catch.",
            tags = listOf("coroutines", "concurrency", "cancellation")
        )

        val results = memoryEngine.querySimilar("Coroutine Cancellation Policy", topK = 3)
        assertFalse("Vector memory should recall matches", results.isEmpty())
        assertTrue("Top recall score should be positive", results.first().second > 0.3f)
        assertTrue(
            "Results should contain stored or related node",
            results.any { it.first.title.contains("Cancellation") || it.first.title.contains("Coroutines") }
        )
    }

    @Test
    fun `test autonomous extension script sandbox violations`() {
        val telemetry = TelemetryEngine()
        val autoExtension = AutonomousExtensionEngine(telemetry)

        // Test forbidden command
        val forbiddenScript = "System.exit(1);"
        val result = autoExtension.executeAutonomousScript(forbiddenScript)
        assertFalse(result.isSuccess)
        assertTrue(result.sandboxViolations.any { it.contains("System.exit") })

        // Test valid script
        val validScript = "fun heal() {\n  patch(target=\"nav\");\n  log(\"OK\");\n}"
        val validResult = autoExtension.executeAutonomousScript(validScript)
        assertTrue(validResult.isSuccess)
        assertTrue(validResult.modifiedAstNodes.isNotEmpty())
    }

    @Test
    fun `test ast patch synthesis and rollback checkpoints`() {
        val telemetry = TelemetryEngine()
        val autoExtension = AutonomousExtensionEngine(telemetry)

        val checkpoint = autoExtension.createCheckpoint("Pre-Upgrade Baseline", emptyMap())
        assertNotNull(checkpoint.checkpointId)

        val patch = autoExtension.generateAstPatch(
            targetModule = "TestModule",
            originalSyntax = "oldSyntax()",
            replacementSyntax = "newSyntax()",
            rationale = "Bug fix"
        )
        assertEquals(PatchStatus.VALIDATED, patch.status)

        val applied = autoExtension.applyPatch(patch.patchId)
        assertTrue(applied)

        val rollbackSuccess = autoExtension.rollbackToCheckpoint(checkpoint.checkpointId)
        assertTrue(rollbackSuccess)
    }

    @Test
    fun `test plugin sandbox manager tool execution`() = runTest {
        val telemetry = TelemetryEngine()
        val memory = VectorMemoryEngine(telemetry)
        val autoExtension = AutonomousExtensionEngine(telemetry)
        val pluginManager = PluginManagerEngine(telemetry, memory, autoExtension)

        val plugins = pluginManager.pluginsState.value
        assertEquals(8, plugins.size)

        // Execute Spatial Vision tool
        val output = pluginManager.executeTool("plugin-spatial-vision", "inspect", emptyMap())
        assertTrue(output.contains("bounding box", ignoreCase = true))

        // Toggle disabled
        pluginManager.togglePluginEnabled("plugin-spatial-vision")
        assertFalse(pluginManager.pluginsState.value.first { it.id == "plugin-spatial-vision" }.isEnabled)
    }

    @Test
    fun `test dag planner synthesis and step dependencies`() = runTest {
        val telemetry = TelemetryEngine()
        val memory = VectorMemoryEngine(telemetry)
        val autoExtension = AutonomousExtensionEngine(telemetry)
        val pluginManager = PluginManagerEngine(telemetry, memory, autoExtension)
        val profiler = HardwareProfilerEngine(context, this)
        val healing = SelfHealingEngine(telemetry, autoExtension)

        val dagPlanner = DAGPlannerEngine(telemetry, healing, pluginManager, profiler, autoExtension)
        val plan = dagPlanner.generatePlan("System Audit")
        assertNotNull(plan)
        assertTrue(plan.steps.size >= 4)
        assertEquals("step_1", plan.steps.first().id)
        assertTrue(plan.steps[1].dependsOn.contains("step_1"))
        profiler.stopProfiling()
    }
}
