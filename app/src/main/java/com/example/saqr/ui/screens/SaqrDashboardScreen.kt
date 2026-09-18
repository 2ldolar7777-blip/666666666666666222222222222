package com.example.saqr.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.saqr.engine.SaqrMaestroKernel
import com.example.saqr.engine.SaqrSystemState
import com.example.saqr.model.*
import com.example.saqr.ui.components.*
import com.example.saqr.ui.theme.SaqrColors
import kotlinx.coroutines.launch

enum class SaqrDashboardTab(val label: String) {
    COMMAND("Maestro DAG"),
    HARDWARE("Hardware Profiler"),
    AST_STUDIO("AST Self-Repair"),
    PLUGINS("Sandbox Plugins"),
    MEMORY_LOGS("Memory & Logs")
}

@Composable
fun SaqrDashboardScreen(
    kernel: SaqrMaestroKernel,
    modifier: Modifier = Modifier
) {
    val systemState by kernel.systemState.collectAsState()
    var selectedTab by remember { mutableStateOf(SaqrDashboardTab.COMMAND) }
    var promptInput by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()

    Box(modifier = modifier.fillMaxSize()) {
        // Dynamic Glassmorphism Ambient Canvas adapting to hardware tier
        AmbientReactiveMeshBackground(tier = systemState.hardwareProfile.tier)

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                SaqrTopAppBar(
                    state = systemState,
                    onThermalToggle = { force ->
                        kernel.triggerThermalSpikeSimulation(force)
                    }
                )
            },
            bottomBar = {
                SaqrBottomTabBar(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Crossfade(
                    targetState = selectedTab,
                    label = "tabCrossfade"
                ) { tab ->
                    when (tab) {
                        SaqrDashboardTab.COMMAND -> CommandAndDagView(
                            state = systemState,
                            promptInput = promptInput,
                            onPromptChange = { promptInput = it },
                            onExecuteGoal = { prompt, simFail ->
                                kernel.executeGoal(prompt, simFail)
                                focusManager.clearFocus()
                            },
                            onRunCurrent = { simFail ->
                                kernel.runCurrentPlan(simFail)
                            },
                            onCancel = { kernel.cancelExecution() }
                        )
                        SaqrDashboardTab.HARDWARE -> HardwareProfilerView(
                            hardware = systemState.hardwareProfile,
                            onSimulateThermal = { force ->
                                kernel.triggerThermalSpikeSimulation(force)
                            }
                        )
                        SaqrDashboardTab.AST_STUDIO -> AstSelfRepairView(
                            patches = systemState.astPatches,
                            checkpoints = systemState.checkpoints,
                            onApplyPatch = { kernel.autonomousExtension.applyPatch(it) },
                            onRollback = { kernel.autonomousExtension.rollbackToCheckpoint(it) },
                            onRunScript = { script ->
                                kernel.autonomousExtension.executeAutonomousScript(script)
                            }
                        )
                        SaqrDashboardTab.PLUGINS -> PluginsView(
                            plugins = systemState.plugins,
                            onTogglePlugin = { kernel.pluginManager.togglePluginEnabled(it) },
                            onExecuteTool = { pluginId, action, params ->
                                coroutineScope.launch {
                                    try {
                                        kernel.pluginManager.executeTool(pluginId, action, params)
                                    } catch (_: Exception) {}
                                }
                            }
                        )
                        SaqrDashboardTab.MEMORY_LOGS -> MemoryAndLogsView(
                            nodes = systemState.memoryNodes,
                            logs = systemState.telemetryLogs,
                            health = systemState.systemHealth,
                            onSearchMemory = { query ->
                                kernel.memoryEngine.querySimilar(query)
                            },
                            onClearLogs = { kernel.telemetry.clearLogs() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SaqrTopAppBar(
    state: SaqrSystemState,
    onThermalToggle: (Boolean) -> Unit
) {
    val tier = state.hardwareProfile.tier
    val thermal = state.hardwareProfile.thermal
    val isThrottling = thermal.isThrottling

    FloatingGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        backgroundColor = SaqrColors.SurfaceGlassNavy
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(SaqrColors.CyanVioletGradient),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = "SAQR Logo",
                        tint = SaqrColors.VoidBlack,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "SAQR OS",
                            color = SaqrColors.TextPrimary,
                            fontWeight = FontWeight.Black,
                            fontSize = 17.sp,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        GlowingBadge(
                            text = "v3.0 KERNEL",
                            glowColor = SaqrColors.ElectricCyan
                        )
                    }
                    Text(
                        text = "Adaptive Autonomous Agent Engine",
                        color = SaqrColors.TextTertiary,
                        fontSize = 10.sp
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Dynamic Hardware Tier Indicator
                GlowingBadge(
                    text = tier.displayName,
                    glowColor = when (tier) {
                        PerformanceTier.ULTRA -> SaqrColors.ElectricCyan
                        PerformanceTier.HIGH -> SaqrColors.EmeraldPulse
                        PerformanceTier.MEDIUM -> SaqrColors.CyberAmber
                        PerformanceTier.LOW -> SaqrColors.CrimsonFlare
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                // Quick Thermal Spike Simulation Toggle
                IconButton(
                    onClick = { onThermalToggle(!isThrottling) },
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(
                            if (isThrottling) SaqrColors.CrimsonFlare.copy(alpha = 0.25f)
                            else Color(0x18FFFFFF)
                        )
                ) {
                    Icon(
                        imageVector = if (isThrottling) Icons.Default.LocalFireDepartment else Icons.Default.Thermostat,
                        contentDescription = "Thermal State",
                        tint = if (isThrottling) SaqrColors.CrimsonFlare else SaqrColors.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SaqrBottomTabBar(
    selectedTab: SaqrDashboardTab,
    onTabSelected: (SaqrDashboardTab) -> Unit
) {
    FloatingGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        shape = RoundedCornerShape(22.dp),
        backgroundColor = SaqrColors.SurfaceGlassNavy
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SaqrDashboardTab.values().forEach { tab ->
                val isSelected = selectedTab == tab
                val icon = when (tab) {
                    SaqrDashboardTab.COMMAND -> Icons.Default.AccountTree
                    SaqrDashboardTab.HARDWARE -> Icons.Default.Memory
                    SaqrDashboardTab.AST_STUDIO -> Icons.Default.Build
                    SaqrDashboardTab.PLUGINS -> Icons.Default.Extension
                    SaqrDashboardTab.MEMORY_LOGS -> Icons.Default.Psychology
                }

                val activeColor = if (isSelected) SaqrColors.ElectricCyan else SaqrColors.TextTertiary

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onTabSelected(tab) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = tab.label,
                        tint = activeColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = tab.label.split(" ").first(),
                        fontSize = 9.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = activeColor
                    )
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(width = 12.dp, height = 2.dp)
                                .clip(CircleShape)
                                .background(SaqrColors.ElectricCyan)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CommandAndDagView(
    state: SaqrSystemState,
    promptInput: String,
    onPromptChange: (String) -> Unit,
    onExecuteGoal: (String, String?) -> Unit,
    onRunCurrent: (String?) -> Unit,
    onCancel: () -> Unit
) {
    val plan = state.currentPlan
    val hardware = state.hardwareProfile

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Quick Hardware HUD Strip
        item {
            HardwareQuickHudStrip(hardware = hardware, state = state)
        }

        // Autonomous Goal Input Box
        item {
            FloatingGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Autonomous Agent Goal Prompt",
                            color = SaqrColors.TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        GlowingBadge(
                            text = "DYNAMIC DAG",
                            glowColor = SaqrColors.PlasmaViolet
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = promptInput,
                        onValueChange = onPromptChange,
                        placeholder = {
                            Text(
                                "e.g. Audit security, fix mutated selectors, crawl DOM",
                                color = SaqrColors.TextTertiary,
                                fontSize = 12.sp
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SaqrColors.ElectricCyan,
                            unfocusedBorderColor = SaqrColors.GlassBorderLight,
                            focusedTextColor = SaqrColors.TextPrimary,
                            unfocusedTextColor = SaqrColors.TextPrimary,
                            cursorColor = SaqrColors.ElectricCyan
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (promptInput.isNotBlank()) {
                                    onExecuteGoal(promptInput, null)
                                }
                            }
                        ),
                        trailingIcon = {
                            if (promptInput.isNotBlank()) {
                                IconButton(
                                    onClick = { onExecuteGoal(promptInput, null) }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Send,
                                        contentDescription = "Send",
                                        tint = SaqrColors.ElectricCyan
                                    )
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    // Quick Action Template Chips
                    Text(
                        text = "Quick Action Scenarios:",
                        color = SaqrColors.TextTertiary,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QuickGoalChip(
                            label = "Real-Time System Audit",
                            onClick = {
                                onPromptChange("System Audit & Hardware Verification")
                                onExecuteGoal("System Audit & Hardware Verification", null)
                            }
                        )
                        QuickGoalChip(
                            label = "Self-Repair AST Patch",
                            onClick = {
                                onPromptChange("Synthesize AST Patch & Self-Repair")
                                onExecuteGoal("Synthesize AST Patch & Self-Repair", null)
                            }
                        )
                        QuickGoalChip(
                            label = "Simulate Error & Self-Heal",
                            onClick = {
                                onPromptChange("Perceive Target View & Execute")
                                onExecuteGoal("Perceive Target View & Execute", "step_auto_2")
                            }
                        )
                        QuickGoalChip(
                            label = "DOM Crawl & Extract",
                            onClick = {
                                onPromptChange("Crawl DOM & Store Knowledge")
                                onExecuteGoal("Crawl DOM & Store Knowledge", null)
                            }
                        )
                    }
                }
            }
        }

        // Active DAG Execution Status Card
        if (plan != null) {
            item {
                FloatingGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = SaqrColors.SurfaceGlassNavy
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Execution Plan: ${plan.planId}",
                                    color = SaqrColors.ElectricCyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = plan.goalPrompt,
                                    color = SaqrColors.TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            GlowingBadge(
                                text = if (plan.isRunning) "RUNNING" else if (plan.isCompleted) "COMPLETED" else "READY",
                                glowColor = if (plan.isRunning) SaqrColors.ElectricCyan else if (plan.isCompleted) SaqrColors.EmeraldPulse else SaqrColors.CyberAmber
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        LiveMetricBar(
                            progress = plan.progress,
                            barColor = if (plan.failureCount > 0 && plan.healedCount > 0) SaqrColors.EmeraldPulse else SaqrColors.ElectricCyan,
                            height = 6.dp
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Progress: ${(plan.progress * 100).toInt()}% • ${plan.steps.count { it.status == StepStatus.SUCCESS }}/${plan.steps.size} Steps",
                                color = SaqrColors.TextSecondary,
                                fontSize = 11.sp
                            )
                            if (plan.healedCount > 0) {
                                Text(
                                    text = "Healed: ${plan.healedCount} incidents",
                                    color = SaqrColors.EmeraldPulse,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            InteractiveGlassButton(
                                text = if (plan.isRunning) "Running..." else "Execute DAG",
                                onClick = { onRunCurrent(null) },
                                modifier = Modifier.weight(1f),
                                glowColor = SaqrColors.ElectricCyan,
                                icon = {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = SaqrColors.TextPrimary, modifier = Modifier.size(16.dp))
                                }
                            )
                            InteractiveGlassButton(
                                text = "Simulate Failure",
                                onClick = {
                                    val targetStepId = plan.steps.firstOrNull { it.status != StepStatus.SUCCESS }?.id ?: plan.steps.firstOrNull()?.id
                                    onRunCurrent(targetStepId)
                                },
                                modifier = Modifier.weight(1f),
                                glowColor = SaqrColors.CyberAmber,
                                icon = {
                                    Icon(Icons.Default.BugReport, contentDescription = null, tint = SaqrColors.TextPrimary, modifier = Modifier.size(16.dp))
                                }
                            )
                            if (plan.isRunning) {
                                InteractiveGlassButton(
                                    text = "Cancel",
                                    onClick = onCancel,
                                    glowColor = SaqrColors.CrimsonFlare
                                )
                            }
                        }
                    }
                }
            }

            // Steps Timeline
            items(plan.steps) { step ->
                StepItemCard(step = step)
            }
        }
    }
}

@Composable
fun HardwareQuickHudStrip(
    hardware: HardwareProfile,
    state: SaqrSystemState
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        QuickStatMiniCard(
            title = "CPU",
            value = "${hardware.cpu.coreCount} Cores",
            subValue = "${hardware.cpu.estimatedLoadPercent}% Load",
            glowColor = SaqrColors.ElectricCyan,
            modifier = Modifier.weight(1f)
        )
        QuickStatMiniCard(
            title = "RAM",
            value = "${hardware.memory.availableRamMb} MB",
            subValue = "${hardware.memory.ramUsagePercent}% Used",
            glowColor = SaqrColors.PlasmaViolet,
            modifier = Modifier.weight(1f)
        )
        QuickStatMiniCard(
            title = "HEALTH",
            value = "${state.systemHealth.overallHealthScore}%",
            subValue = "${state.systemHealth.healedIncidentsCount} Healed",
            glowColor = SaqrColors.EmeraldPulse,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun QuickStatMiniCard(
    title: String,
    value: String,
    subValue: String,
    glowColor: Color,
    modifier: Modifier = Modifier
) {
    FloatingGlassCard(
        modifier = modifier,
        backgroundColor = SaqrColors.SurfaceGlassNavy
    ) {
        Column {
            Text(
                text = title,
                color = SaqrColors.TextTertiary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = value,
                color = SaqrColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = subValue,
                color = glowColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun QuickGoalChip(
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0x22FFFFFF))
            .border(BorderStroke(1.dp, Color(0x3300F0FF)), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = SaqrColors.ElectricCyan,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun StepItemCard(step: ExecutionStep) {
    val statusColor = when (step.status) {
        StepStatus.SUCCESS -> SaqrColors.EmeraldPulse
        StepStatus.RUNNING -> SaqrColors.ElectricCyan
        StepStatus.HEALING -> SaqrColors.PlasmaViolet
        StepStatus.FAILED -> SaqrColors.CrimsonFlare
        StepStatus.PENDING -> SaqrColors.TextTertiary
        StepStatus.ROLLED_BACK -> SaqrColors.CyberAmber
        StepStatus.SKIPPED -> SaqrColors.TextTertiary
    }

    val statusIcon = when (step.status) {
        StepStatus.SUCCESS -> Icons.Default.CheckCircle
        StepStatus.RUNNING -> Icons.Default.HourglassTop
        StepStatus.HEALING -> Icons.Default.AutoFixHigh
        StepStatus.FAILED -> Icons.Default.Error
        StepStatus.PENDING -> Icons.Default.Schedule
        StepStatus.ROLLED_BACK -> Icons.Default.Undo
        StepStatus.SKIPPED -> Icons.Default.SkipNext
    }

    FloatingGlassCard(
        modifier = Modifier.fillMaxWidth(),
        borderGradient = Brush.horizontalGradient(listOf(statusColor.copy(alpha = 0.5f), Color(0x20FFFFFF)))
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = step.status.label,
                        tint = statusColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = step.title,
                            color = SaqrColors.TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = step.category.label,
                            color = SaqrColors.TextTertiary,
                            fontSize = 10.sp
                        )
                    }
                }

                GlowingBadge(
                    text = step.status.label,
                    glowColor = statusColor
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = step.description,
                color = SaqrColors.TextSecondary,
                fontSize = 12.sp
            )

            if (step.executionTimeMs > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Latency: ${step.executionTimeMs}ms",
                    color = SaqrColors.TextTertiary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            if (step.errorDetails != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SaqrColors.CrimsonFlare.copy(alpha = 0.15f))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "Incident: ${step.errorDetails}",
                        color = SaqrColors.CrimsonFlare,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            if (step.patchDiff != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SaqrColors.EmeraldPulse.copy(alpha = 0.12f))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "AST Self-Repair Patch:\n${step.patchDiff}",
                        color = SaqrColors.EmeraldPulse,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun HardwareProfilerView(
    hardware: HardwareProfile,
    onSimulateThermal: (Boolean) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            FloatingGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Adaptive Hardware Profiler",
                            color = SaqrColors.TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        GlowingBadge(
                            text = hardware.tier.displayName,
                            glowColor = SaqrColors.ElectricCyan
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Senses CPU topology, available RAM, and thermal throttle telemetry to dynamically regulate worker concurrency and visual shader blur depth.",
                        color = SaqrColors.TextSecondary,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Max Concurrency", color = SaqrColors.TextTertiary, fontSize = 11.sp)
                            Text("${hardware.tier.maxConcurrentWorkers} Parallel Workers", color = SaqrColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Column {
                            Text("Frosted Blur Depth", color = SaqrColors.TextTertiary, fontSize = 11.sp)
                            Text("${hardware.tier.blurRadiusDp.toInt()} dp Radius", color = SaqrColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Column {
                            Text("Target Refresh", color = SaqrColors.TextTertiary, fontSize = 11.sp)
                            Text("${hardware.tier.refreshRateTargetFps} FPS", color = SaqrColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        // Live Thermal Simulation Toggle Card
        item {
            FloatingGlassCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = if (hardware.thermal.isThrottling) SaqrColors.CrimsonFlare.copy(alpha = 0.15f) else SaqrColors.SurfaceGlassNavy
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Thermal Throttle State",
                            color = SaqrColors.TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Status: ${hardware.thermal.label} (${hardware.battery.temperatureCelsius}°C)",
                            color = if (hardware.thermal.isThrottling) SaqrColors.CrimsonFlare else SaqrColors.EmeraldPulse,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Button(
                        onClick = { onSimulateThermal(!hardware.thermal.isThrottling) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (hardware.thermal.isThrottling) SaqrColors.CrimsonFlare else SaqrColors.CyberAmber
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (hardware.thermal.isThrottling) "Clear Throttle" else "Simulate Spike",
                            color = SaqrColors.VoidBlack,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // CPU Breakdown
        item {
            FloatingGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("CPU Topology & Compute", color = SaqrColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    MetricRow(label = "Physical Cores", value = "${hardware.cpu.coreCount} Cores")
                    MetricRow(label = "Architecture", value = hardware.cpu.architecture)
                    MetricRow(label = "Max Clock Speed", value = "${hardware.cpu.maxFrequencyMhz} MHz")
                    MetricRow(label = "Estimated System Load", value = "${hardware.cpu.estimatedLoadPercent}%")
                    Spacer(modifier = Modifier.height(8.dp))
                    LiveMetricBar(progress = hardware.cpu.estimatedLoadPercent / 100f, barColor = SaqrColors.ElectricCyan)
                }
            }
        }

        // Memory Breakdown
        item {
            FloatingGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("RAM Memory Architecture", color = SaqrColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    MetricRow(label = "Total System RAM", value = "${hardware.memory.totalRamMb} MB")
                    MetricRow(label = "Available Free RAM", value = "${hardware.memory.availableRamMb} MB")
                    MetricRow(label = "Allocated Memory", value = "${hardware.memory.usedRamMb} MB")
                    MetricRow(label = "Low Memory Critical", value = if (hardware.memory.isLowMemory) "ACTIVE" else "NO")
                    Spacer(modifier = Modifier.height(8.dp))
                    LiveMetricBar(progress = hardware.memory.ramUsagePercent / 100f, barColor = SaqrColors.PlasmaViolet)
                }
            }
        }
    }
}

@Composable
fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = SaqrColors.TextSecondary, fontSize = 12.sp)
        Text(text = value, color = SaqrColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun AstSelfRepairView(
    patches: List<AstPatch>,
    checkpoints: List<StateCheckpoint>,
    onApplyPatch: (String) -> Unit,
    onRollback: (String) -> Unit,
    onRunScript: (String) -> ScriptExecutionResult
) {
    var scriptInput by remember { mutableStateOf("fun adaptive_recovery() {\n  patch(target=\"view_locator_v2\");\n  log(\"AST Patch Applied\");\n}") }
    var scriptOutput by remember { mutableStateOf<ScriptExecutionResult?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            FloatingGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Self-Modification & AST Engine",
                            color = SaqrColors.TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        GlowingBadge(
                            text = "${patches.size} Patches",
                            glowColor = SaqrColors.EmeraldPulse
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Synthesizes live AST patches to heal mutated selectors, adjusts coroutine deadlines, and manages state rollback checkpoints.",
                        color = SaqrColors.TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Live AST Patch list
        item {
            Text(
                text = "Live Synthesized AST Patches",
                color = SaqrColors.ElectricCyan,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }

        items(patches) { patch ->
            FloatingGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = patch.targetModule,
                            color = SaqrColors.TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        GlowingBadge(
                            text = patch.status.label,
                            glowColor = if (patch.status == PatchStatus.APPLIED) SaqrColors.EmeraldPulse else SaqrColors.CyberAmber
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Rationale: ${patch.rationale}",
                        color = SaqrColors.TextSecondary,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Visual Code Diff Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF04060A))
                            .border(BorderStroke(1.dp, Color(0x30FFFFFF)), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Text(
                                text = "- ${patch.originalSyntax}",
                                color = SaqrColors.CrimsonFlare,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "+ ${patch.replacementSyntax}",
                                color = SaqrColors.EmeraldPulse,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Confidence: ${(patch.confidenceScore * 100).toInt()}%",
                            color = SaqrColors.TextTertiary,
                            fontSize = 11.sp
                        )
                        if (patch.status != PatchStatus.APPLIED) {
                            Button(
                                onClick = { onApplyPatch(patch.patchId) },
                                colors = ButtonDefaults.buttonColors(containerColor = SaqrColors.ElectricCyan),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Apply Patch", color = SaqrColors.VoidBlack, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Script Execution Sandbox Tester
        item {
            FloatingGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("Autonomous Script Sandbox Evaluator", color = SaqrColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = scriptInput,
                        onValueChange = { scriptInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SaqrColors.ElectricCyan,
                            unfocusedBorderColor = SaqrColors.GlassBorderLight,
                            focusedTextColor = SaqrColors.TextPrimary,
                            unfocusedTextColor = SaqrColors.TextPrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        maxLines = 6
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    InteractiveGlassButton(
                        text = "Evaluate in Sandbox",
                        onClick = {
                            scriptOutput = onRunScript(scriptInput)
                        },
                        glowColor = SaqrColors.ElectricCyan
                    )

                    if (scriptOutput != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF04060A))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = scriptOutput!!.output,
                                color = if (scriptOutput!!.isSuccess) SaqrColors.EmeraldPulse else SaqrColors.CrimsonFlare,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        // Rollback Checkpoints
        item {
            Text("State Rollback Checkpoints", color = SaqrColors.ElectricCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        items(checkpoints) { checkpoint ->
            FloatingGlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(checkpoint.checkpointId, color = SaqrColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        Text(checkpoint.description, color = SaqrColors.TextSecondary, fontSize = 11.sp)
                    }
                    Button(
                        onClick = { onRollback(checkpoint.checkpointId) },
                        colors = ButtonDefaults.buttonColors(containerColor = SaqrColors.PlasmaViolet),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Rollback", color = SaqrColors.TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun PluginsView(
    plugins: List<PluginManifest>,
    onTogglePlugin: (String) -> Unit,
    onExecuteTool: (String, String, Map<String, Any?>) -> Unit
) {
    var selectedPluginForRun by remember { mutableStateOf<PluginManifest?>(null) }
    var runActionResult by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            FloatingGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Modular Plugin Sandbox", color = SaqrColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        GlowingBadge(text = "${plugins.size} Installed", glowColor = SaqrColors.ElectricCyan)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "All tool executions are isolated within security boundary wrappers with strict capability checks and latency tracking.",
                        color = SaqrColors.TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }

        items(plugins) { plugin ->
            FloatingGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = plugin.name,
                                color = SaqrColors.TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "v${plugin.version} • ${plugin.capabilities.firstOrNull()?.label ?: "Utility"}",
                                color = SaqrColors.ElectricCyan,
                                fontSize = 10.sp
                            )
                        }
                        Switch(
                            checked = plugin.isEnabled,
                            onCheckedChange = { onTogglePlugin(plugin.id) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = SaqrColors.ElectricCyan,
                                checkedTrackColor = SaqrColors.SurfaceGlassNavy
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = plugin.description,
                        color = SaqrColors.TextSecondary,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Exec: ${plugin.executionCount} calls • ${plugin.avgLatencyMs}ms avg",
                            color = SaqrColors.TextTertiary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Button(
                            onClick = {
                                selectedPluginForRun = plugin
                                onExecuteTool(plugin.id, "test_action", mapOf("target" to "main_view"))
                                runActionResult = "Dispatched test action to ${plugin.name} successfully."
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SaqrColors.SurfaceGlassElevated),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Test Tool", color = SaqrColors.TextPrimary, fontSize = 10.sp)
                        }
                    }
                }
            }
        }

        if (runActionResult != null) {
            item {
                FloatingGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = SaqrColors.SurfaceGlassNavy
                ) {
                    Column {
                        Text("Live Tool Sandbox Output", color = SaqrColors.EmeraldPulse, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(runActionResult!!, color = SaqrColors.TextPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

@Composable
fun MemoryAndLogsView(
    nodes: List<MemoryNode>,
    logs: List<TelemetryLog>,
    health: SystemHealth,
    onSearchMemory: (String) -> List<Pair<MemoryNode, Float>>,
    onClearLogs: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<Pair<MemoryNode, Float>>>(emptyList()) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Vector Search Bar
        item {
            FloatingGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("Vector Memory Associative Recall", color = SaqrColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            if (it.isNotBlank()) {
                                searchResults = onSearchMemory(it)
                            } else {
                                searchResults = emptyList()
                            }
                        },
                        placeholder = { Text("Query semantic memory by concept...", color = SaqrColors.TextTertiary, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SaqrColors.ElectricCyan,
                            unfocusedBorderColor = SaqrColors.GlassBorderLight,
                            focusedTextColor = SaqrColors.TextPrimary,
                            unfocusedTextColor = SaqrColors.TextPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = SaqrColors.ElectricCyan) }
                    )
                }
            }
        }

        // Memory Search Results or Default Nodes
        val displayNodes = if (searchResults.isNotEmpty()) searchResults.map { it.first } else nodes.take(4)
        items(displayNodes) { node ->
            FloatingGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(node.title, color = SaqrColors.ElectricCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        GlowingBadge(text = "Recall: ${node.accessCount}", glowColor = SaqrColors.PlasmaViolet)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(node.content, color = SaqrColors.TextSecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        node.tags.forEach { tag ->
                            Text("#$tag", color = SaqrColors.TextTertiary, fontSize = 10.sp)
                        }
                    }
                }
            }
        }

        // Zero-Lock Telemetry Log Stream
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Zero-Lock Telemetry Stream", color = SaqrColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                IconButton(onClick = onClearLogs) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Clear Logs", tint = SaqrColors.TextSecondary, modifier = Modifier.size(20.dp))
                }
            }
        }

        items(logs.take(15)) { log ->
            val logColor = when (log.level) {
                LogLevel.INFO -> SaqrColors.ElectricCyan
                LogLevel.DEBUG -> SaqrColors.TextSecondary
                LogLevel.WARN -> SaqrColors.CyberAmber
                LogLevel.ERROR, LogLevel.CRITICAL -> SaqrColors.CrimsonFlare
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF070B14))
                    .border(BorderStroke(1.dp, Color(0x18FFFFFF)), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = "[${log.level.name}]",
                        color = logColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = log.tag,
                        color = SaqrColors.TextTertiary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = log.message,
                        color = SaqrColors.TextPrimary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
