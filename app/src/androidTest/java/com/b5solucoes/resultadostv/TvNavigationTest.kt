package com.b5solucoes.resultadostv

import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class TvNavigationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val fixture = """{"ele":"6258","t":"2","f":"o","tpabr":"br","cdabr":"br","dv":"s","and":"p","tf":"n",
        "dg":"25/10/2026","hg":"18:00:00","dt":"25/10/2026","ht":"17:59:58",
        "s":{"ts":"1000","st":"500","pst":"50,00","pstn":"50"},
        "v":{"vv":"100000","vb":"1100","tvn":"1200"},"e":{"c":"102300","a":"10000"},
        "carg":[{"cd":"1","agr":[{"par":[{"sg":"TESTE","cand":[
        {"n":"98","nm":"CANDIDATURA FICTÍCIA A","nmu":"CANDIDATURA TESTE A","vap":"54000","pvap":"54,00","pvapn":"54","seq":"1","e":"n"},
        {"n":"99","nm":"CANDIDATURA FICTÍCIA B","nmu":"CANDIDATURA TESTE B","vap":"46000","pvap":"46,00","pvapn":"46","seq":"2","e":"n"}
        ]}]}]}]}"""
    @Before fun prepare() {
        context.getSharedPreferences("preferences", 0).edit().clear().commit()
        MainActivity.testResult = fixture
        MainActivity.testOffline = false
        MainActivity.testConfig = """{"pl":[{"c":"ele2026","dt":"25/10/2026","e":[
            {"cd":"6258","nm":"Federal 2026 • TESTE","t":"2","tp":"8","abr":[{"cd":"br","cp":[{"cd":"1","ds":"Presidente","tp":"1"}]}]}
        ]}]}"""
    }
    @After fun cleanup() { MainActivity.testResult = null; MainActivity.testConfig = null; MainActivity.testOffline = false }
    private fun launch() = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java))
    private fun waitForResult() {
        assertTrue(device.wait(Until.hasObject(By.text("54,00%")), 15000))
        device.waitForIdle()
    }
    private fun screenshot(name: String) {
        val dir = File(context.getExternalFilesDir(null), "qa").apply { mkdirs() }
        device.takeScreenshot(File(dir, name))
        device.dumpWindowHierarchy(File(dir, name.removeSuffix(".png") + ".xml"))
    }
    private fun focus(scenario: ActivityScenario<MainActivity>, tag: String) = scenario.onActivity {
        val view = it.findViewById<View>(android.R.id.content).findViewWithTag<View>(tag)
        assertNotNull("Missing focus target $tag", view)
        assertTrue(view.requestFocus())
    }
    @Test fun remoteCanReachCandidateDetailsSelectorsAndFavorites() {
        launch().use { scenario ->
            waitForResult()
            // Starting focus belongs to an actionable menu item. Move into results using the remote.
            focus(scenario, "election")
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
            scenario.onActivity { assertNotNull(it.currentFocus); assertTrue(it.currentFocus!!.isFocusable) }
            // Actual remote enter opens the candidate, then Back returns to the same card.
            focus(scenario, "candidate-98")
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
            assertTrue(device.wait(Until.hasObject(By.text("CANDIDATURA TESTE A")), 3000))
            assertTrue(device.hasObject(By.textContains("Destinação")).not())
            assertTrue(device.hasObject(By.textContains("Situação eleitoral ainda não informada")))
            device.pressBack(); device.waitForIdle()
            focus(scenario, "election")
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
            assertTrue(device.wait(Until.hasObject(By.text("Escolher eleição")), 3000))
            screenshot("02-seletor-eleicao.png"); device.pressBack(); device.waitForIdle()
            focus(scenario, "place")
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
            assertTrue(device.wait(Until.hasObject(By.text("Estado ou Brasil")), 3000))
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
            scenario.onActivity { assertTrue(it.hasWindowFocus().not()) }
            device.pressBack(); device.waitForIdle()
            focus(scenario, "save")
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
            assertTrue(device.wait(Until.hasObject(By.text("★ Local salvo")), 3000))
            focus(scenario, "favorites")
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
            assertTrue(device.wait(Until.hasObject(By.text("Consultas favoritas")), 3000))
            device.pressBack(); device.waitForIdle()
            focus(scenario, "election")
            scenario.onActivity { it.findViewById<View>(android.R.id.content).findViewWithTag<android.widget.ScrollView>("results-scroll").scrollTo(0, 0) }
            screenshot("01-painel-tv-dados-ficticios.png")
        }
    }
    @Test fun offlineIsClearlyIdentifiedAndInterfaceFitsInScreen() {
        MainActivity.testOffline = true
        launch().use { scenario ->
            waitForResult()
            assertTrue(device.hasObject(By.textContains("Exibindo consulta salva")))
            assertTrue(device.hasObject(By.textContains("Consulta salva em")))
            scenario.onActivity { activity ->
                val decor = activity.window.decorView
                for (tag in listOf("election", "office", "place", "refresh")) {
                    val view = decor.findViewWithTag<View>(tag)
                    val rect = Rect(); assertTrue(view.getGlobalVisibleRect(rect))
                    assertTrue(rect.left >= 0 && rect.right <= decor.width)
                    assertTrue(rect.top >= 0 && rect.bottom <= decor.height)
                }
            }
            screenshot("03-sem-conexao-dados-ficticios.png")
        }
    }
    @Test fun automaticUpdatePreservesCandidateFocus() {
        launch().use { scenario ->
            waitForResult(); focus(scenario, "candidate-99")
            // Pause/resume requests an update while keeping the focused card visible.
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.STARTED)
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            SystemClock.sleep(500)
            instrumentation.waitForIdleSync()
            scenario.onActivity { assertEquals("candidate-99", it.currentFocus?.tag) }
        }
    }
    @Test fun everyMainControlIsReachableThroughDirectionalKeys() {
        launch().use { scenario ->
            waitForResult()
            val reachable = mutableSetOf("election")
            val queue = java.util.ArrayDeque<String>().apply { add("election") }
            val directions = listOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)
            while (queue.isNotEmpty() && reachable.size < 30) {
                val origin = queue.removeFirst()
                for (direction in directions) {
                    focus(scenario, origin)
                    instrumentation.sendKeyDownUpSync(direction)
                    instrumentation.waitForIdleSync()
                    scenario.onActivity { activity ->
                        val target = activity.currentFocus?.tag as? String
                        if (target != null && reachable.add(target)) queue.add(target)
                    }
                }
            }
            val expected = setOf("election", "office", "place", "zone", "save", "favorites", "about",
                "refresh", "search", "candidate-98", "candidate-99")
            assertTrue("Controls unreachable by D-pad: ${expected - reachable}", reachable.containsAll(expected))
        }
    }
}
