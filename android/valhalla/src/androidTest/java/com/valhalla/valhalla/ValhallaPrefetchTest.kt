package com.valhalla.valhalla

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.valhalla.valhalla.config.ValhallaConfigFactory
import com.valhalla.valhalla.config.ValhallaConfigManager
import com.valhalla.valhalla.files.ValhallaFile
import com.valhalla.valhalla.http.ValhallaHttpResponse
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Finding the tiles under a coordinate, and fetching one ahead of the action that needs it. */
@RunWith(AndroidJUnit4::class)
class ValhallaPrefetchTest {

  private lateinit var context: Context
  private lateinit var tilesDir: File
  private lateinit var client: FakeTileClient
  private val actors = mutableListOf<ValhallaActor>()

  @Before
  fun setUp() {
    context = InstrumentationRegistry.getInstrumentation().targetContext
    tilesDir = File(context.cacheDir, "prefetch-${UUID.randomUUID()}").apply { mkdirs() }
    client = FakeTileClient(context.assets)
  }

  @After
  fun tearDown() {
    actors.forEach { it.close() }
    if (::tilesDir.isInitialized) tilesDir.deleteRecursively()
  }

  private fun actor(): ValhallaActor {
    val config =
        ValhallaConfigFactory.usingTileUrl(
            "${FakeTileClient.BASE_URL}{tilePath}", tilesDir.absolutePath)
    val file = ValhallaFile(context, "prefetch.json")
    ValhallaConfigManager(context, file).writeConfig(config)
    return ValhallaActor(file.absolutePath(), client).also { actors += it }
  }

  private fun message(envelope: String) = JSONObject(envelope).getString("message")

  @Test
  fun testTilesCoveringACoordinate() {
    Valhalla(TestFileUtils.getConfigPath(context)).use { valhalla ->
      assertEquals(
          listOf(
              TileRef(0, 3015, "0/003/015.gph"),
              TileRef(1, 47701, "1/047/701.gph"),
              TileRef(2, 763926, "2/000/763/926.gph")),
          valhalla.tilesCovering(42.5063, 1.5218))
      assertEquals(emptyList<TileRef>(), valhalla.tilesCovering(Double.NaN, 1.5218))
      assertEquals(emptyList<TileRef>(), valhalla.tilesCovering(90.5, 1.5218))
    }
  }

  @Test
  fun testTilesCoveringDoesNotWaitForARunningAction() {
    val fetching = CountDownLatch(1)
    val release = CountDownLatch(1)
    client.answer = {
      fetching.countDown()
      release.await(10, TimeUnit.SECONDS)
      null
    }
    val actor = actor()
    val executor = Executors.newSingleThreadExecutor()
    try {
      val running = executor.submit<String> { actor.ensureTileCached(2, TILE) }
      assertTrue(fetching.await(10, TimeUnit.SECONDS))

      val started = System.nanoTime()
      actor.tilesCovering(42.5063, 1.5218)
      val seconds = (System.nanoTime() - started) / 1e9

      release.countDown()
      assertEquals("true", running.get(10, TimeUnit.SECONDS))
      assertTrue("took $seconds s", seconds < 5)
    } finally {
      executor.shutdown()
    }
  }

  @Test
  fun testFetchesATileTheOriginHas() {
    assertEquals("true", actor().ensureTileCached(2, TILE))

    assertTrue(File(tilesDir, "2/000/762/485.gph").isFile)
  }

  @Test
  fun testFetchesATileAgainWhenItsFileIsGone() {
    val actor = actor()
    val file = File(tilesDir, "2/000/762/485.gph")
    assertEquals("true", actor.ensureTileCached(2, TILE))
    assertTrue(file.delete())

    assertEquals("true", actor.ensureTileCached(2, TILE))

    assertTrue(file.isFile)
    assertEquals(2, client.requests.size)
  }

  @Test
  fun testThrowsWhenTheTileCannotBeWritten() {
    val dir = File(tilesDir, "2/000/762").apply { mkdirs() }
    assertTrue(dir.setWritable(false))
    try {
      assertEquals(NOT_STORED, message(actor().ensureTileCached(2, TILE)))
    } finally {
      dir.setWritable(true)
    }
  }

  @Test
  fun testReturnsFalseForATileTheOriginDoesNotHave() {
    assertEquals("false", actor().ensureTileCached(2, MISSING_TILE))
  }

  @Test
  fun testThrowsOnAFailedFetchAndRetriesItNextTime() {
    client.answer = { ValhallaHttpResponse.failure(503) }
    val actor = actor()

    assertEquals(FETCH_FAILED, message(actor.ensureTileCached(2, TILE)))

    client.answer = { null }
    assertEquals("true", actor.ensureTileCached(2, TILE))
  }


  private companion object {
    const val TILE = 762485
    const val MISSING_TILE = 762484
    const val FETCH_FAILED = "valhalla-mobile: tile fetch failed"
    const val NOT_STORED = "valhalla-mobile: tile not stored"
  }
}
