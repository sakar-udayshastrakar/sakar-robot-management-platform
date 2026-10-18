package com.sakarrobotics.installationassistant.data

import com.sakarrobotics.installation.core.AssistantServices
import com.sakarrobotics.installation.core.model.ChecklistState
import com.sakarrobotics.installation.core.storage.SequentialIdGenerator
import com.sakarrobotics.installation.core.storage.StorageException
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileKeyValueStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun services(dir: File) = AssistantServices(
        store = FileKeyValueStore(dir),
        scope = CoroutineScope(Job()),
        ids = SequentialIdGenerator(),
        mappingTickMillis = 0,
        diagnosticsLatencyMillis = 0
    )

    @Test
    fun `values survive creating a new store on the same folder`() {
        val dir = File(tmp.root, "data")
        FileKeyValueStore(dir).put("k", "hello")
        assertEquals("hello", FileKeyValueStore(dir).get("k"))
    }

    @Test
    fun `remove deletes the value and removing a missing key is fine`() {
        val store = FileKeyValueStore(File(tmp.root, "data"))
        store.put("k", "v")
        store.remove("k")
        assertNull(store.get("k"))
        store.remove("never-existed")
    }

    @Test
    fun `an interrupted write leaves the previous value intact`() {
        val dir = File(tmp.root, "data")
        val store = FileKeyValueStore(dir)
        store.put("k", "good")
        // Simulate a crash after the temp file was written but before the rename.
        File(dir, "k.json.tmp").writeText("half-written garbage")
        assertEquals("good", FileKeyValueStore(dir).get("k"))
    }

    @Test
    fun `keys cannot escape the data folder`() {
        val dir = File(tmp.root, "data")
        FileKeyValueStore(dir).put("../../evil", "x")
        assertFalse(File(tmp.root, "evil.json").exists())
        assertTrue(dir.listFiles()!!.any { it.name.endsWith("evil.json") })
    }

    @Test
    fun `a write that cannot complete throws StorageException instead of silently losing data`() {
        val dir = File(tmp.root, "data")
        val store = FileKeyValueStore(dir)
        // A non-empty directory where the value file should be makes the final rename impossible.
        val blocker = File(dir, "k.json").apply { mkdirs() }
        File(blocker, "occupied").writeText("x")
        try {
            store.put("k", "v")
            fail("expected StorageException")
        } catch (e: StorageException) {
            assertTrue(e.message!!.contains("k.json"))
        }
        assertFalse("no temp file is left behind", File(dir, "k.json.tmp").exists())
    }

    @Test
    fun `workflow progress persists across a restart through the real file store`() {
        val dir = File(tmp.root, "data")
        val first = services(dir)
        val inst = first.installations.startNew()
        first.installations.acknowledgeOverview()
        first.installations.setSelectedFloors(listOf("floor-ground"))
        first.installations.updateChecklist("charger", ChecklistState.DONE, "By the lobby")
        first.floors.addFloor("Mezzanine")

        val second = services(dir)
        val restored = second.installations.current.value!!
        assertEquals(inst.id, restored.id)
        assertTrue(restored.overviewAcknowledged)
        assertEquals(listOf("floor-ground"), restored.selectedFloorIds)
        assertEquals("By the lobby", restored.checklist.single { it.id == "charger" }.note)
        assertTrue(second.floors.floors.value.any { it.name == "Mezzanine" })
    }

    @Test
    fun `damaged files fall back to defaults instead of crashing the app`() {
        val dir = File(tmp.root, "data")
        dir.mkdirs()
        listOf("floors.v1", "sim.maps.v1", "installation.v1", "reports.v1").forEach {
            File(dir, "$it.json").writeText("\u0000\u0000 not json {{{")
        }
        val s = services(dir)
        assertEquals(2, s.floors.floors.value.size)
        assertTrue(s.mapping.maps.value.isEmpty())
        assertNull(s.installations.current.value)
        assertTrue(s.reports.reports.value.isEmpty())
    }

    @Test
    fun `a storage failure reaches the persistence monitor and keeps the app usable`() {
        val dir = File(tmp.root, "data")
        val s = services(dir)
        val blocker = File(dir, "floors.v1.json").apply { delete(); mkdirs() }
        File(blocker, "occupied").writeText("x")

        val result = s.floors.addFloor("Roof")

        assertTrue(result.isSuccess)
        assertTrue(s.floors.floors.value.any { it.name == "Roof" })
        assertNotNull(s.persistence.error.value)
        assertTrue(s.persistence.error.value!!.contains("may be lost"))
    }
}
