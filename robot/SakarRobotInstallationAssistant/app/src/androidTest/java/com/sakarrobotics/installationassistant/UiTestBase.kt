package com.sakarrobotics.installationassistant

import android.net.Uri
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.sakarrobotics.installation.core.AssistantServices
import com.sakarrobotics.installation.core.storage.InMemoryKeyValueStore
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Before
import org.junit.Rule

/**
 * Stands in for the system file picker (Storage Access Framework). Each launch consumes the next URI
 * from [results] (or reports "cancelled" with null when the list is empty). The app still reads and
 * writes through a real ContentResolver, so the I/O path under test is the production one.
 */
class ScriptedPickerRegistry(private val results: MutableList<Uri?>) : ActivityResultRegistry() {
    val launches = mutableListOf<String>()

    override fun <I : Any?, O : Any?> onLaunch(
        requestCode: Int,
        contract: ActivityResultContract<I, O>,
        input: I,
        options: ActivityOptionsCompat?
    ) {
        launches += contract.javaClass.simpleName
        val uri = if (results.isEmpty()) null else results.removeAt(0)
        @Suppress("UNCHECKED_CAST")
        dispatchResult(requestCode, uri as O)
    }
}

abstract class UiTestBase {

    @get:Rule
    val compose = createEmptyComposeRule()

    protected lateinit var scope: CoroutineScope
    protected lateinit var services: AssistantServices
    protected var scenario: ActivityScenario<MainActivity>? = null

    /** URIs the scripted file picker will return, in order. */
    protected val pickerResults = mutableListOf<Uri?>()
    protected lateinit var picker: ScriptedPickerRegistry

    protected val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    protected val workDir: File get() = File(context.cacheDir, "ui-test-work").apply { mkdirs() }

    protected open fun createServices(scope: CoroutineScope): AssistantServices = AssistantServices(
        store = InMemoryKeyValueStore(),
        scope = scope,
        mappingTickMillis = 100,
        diagnosticsLatencyMillis = 0
    )

    protected open val launchAtStart: Boolean = true

    @Before
    fun baseSetUp() {
        workDir.deleteRecursively()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        services = createServices(scope)
        picker = ScriptedPickerRegistry(pickerResults)
        AssistantApp.servicesOverride = services
        AssistantApp.activityResultRegistryOwnerOverride = object : ActivityResultRegistryOwner {
            override val activityResultRegistry: ActivityResultRegistry = picker
        }
        if (launchAtStart) scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun baseTearDown() {
        scenario?.close()
        services.close()
        AssistantApp.servicesOverride = null
        AssistantApp.shareOverride = null
        AssistantApp.activityResultRegistryOwnerOverride = null
        scope.cancel()
        workDir.deleteRecursively()
    }

    protected fun relaunch() {
        scenario?.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    protected fun tag(t: String): SemanticsNodeInteraction = compose.onNodeWithTag(t, useUnmergedTree = true)

    protected fun scrollTo(t: String) {
        try {
            tag(t).performScrollTo()
        } catch (_: AssertionError) {
            // Not inside a scrollable container: already laid out.
        }
    }

    protected fun click(t: String) {
        scrollTo(t)
        tag(t).performClick()
    }

    protected fun exists(t: String): Boolean =
        compose.onAllNodes(hasTestTag(t), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    protected fun waitForTag(t: String, timeoutMs: Long = 8_000) = compose.waitUntil(timeoutMs) { exists(t) }

    protected fun waitUntilGone(t: String, timeoutMs: Long = 8_000) = compose.waitUntil(timeoutMs) { !exists(t) }

    protected fun waitForText(text: String, timeoutMs: Long = 8_000) = compose.waitUntil(timeoutMs) {
        compose.onAllNodes(hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }

    /** True when the tagged node, or one of its descendants, shows [text]. */
    protected fun tagHasText(t: String, text: String): Boolean = compose.onAllNodes(
        hasTestTag(t) and (hasText(text, substring = true) or hasAnyDescendant(hasText(text, substring = true))),
        useUnmergedTree = true
    ).fetchSemanticsNodes().isNotEmpty()

    protected fun assertTagText(t: String, text: String) {
        check(tagHasText(t, text)) { "Expected '$t' to show '$text'" }
    }

    protected fun waitForTagText(t: String, text: String, timeoutMs: Long = 8_000) =
        compose.waitUntil(timeoutMs) { tagHasText(t, text) }

    protected fun workFile(name: String): File = File(workDir, name)

    /** Polls without touching Compose, so it also works while the activity is stopped. */
    protected fun eventually(timeoutMs: Long = 8_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Condition not met within $timeoutMs ms" }
            Thread.sleep(50)
        }
    }

    /** Home -> New Installation -> start a fresh simulated installation. */
    protected fun startWizardFromHome() {
        click("tile_new_installation")
        waitForTag("wizard_empty")
        click("empty_state_action")
        waitForTag("wizard_installation_id")
    }
}
