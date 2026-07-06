package com.retrovault.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.retrovault.data.AuthManager
import com.retrovault.saves.CloudSaveApi
import com.retrovault.saves.CloudSaveClient
import com.retrovault.saves.CloudSaveSync
import com.retrovault.saves.ConflictChoice
import com.retrovault.saves.ConflictResolver
import com.retrovault.saves.LocalSave
import com.retrovault.saves.RemoteSave
import com.retrovault.saves.SyncAction
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/**
 * P16 acceptance: three-way conflict resolution that NEVER clobbers — two devices editing the
 * same save produce a conflict, not silent loss — plus live anonymous auth and a real cloud
 * save round-trip (Storage upload → manifest → download with RLS-scoped tokens).
 */
@RunWith(AndroidJUnit4::class)
class CloudSaveTest {

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun conflictResolverCoversEveryCase() {
        val r = ConflictResolver
        assertEquals(SyncAction.NOTHING, r.resolve(null, null, null))
        assertEquals(SyncAction.IN_SYNC, r.resolve("a", "a", null))
        assertEquals(SyncAction.IN_SYNC, r.resolve("a", "a", "a"))
        assertEquals(SyncAction.PUSH_LOCAL, r.resolve("a", null, null))     // first upload
        assertEquals(SyncAction.PULL_REMOTE, r.resolve(null, "b", null))    // first download
        assertEquals(SyncAction.PUSH_LOCAL, r.resolve("a2", "a", "a"))      // only local moved
        assertEquals(SyncAction.PULL_REMOTE, r.resolve("a", "b2", "a"))     // only cloud moved
        // THE INVARIANT: both diverged from base → conflict, never overwrite.
        assertEquals(SyncAction.CONFLICT, r.resolve("a2", "b2", "a"))
        // Both present, differ, no common base → can't tell → conflict.
        assertEquals(SyncAction.CONFLICT, r.resolve("a", "b", null))
    }

    /** In-memory cloud shared by two simulated devices. */
    private class FakeCloud(val uid: String) : CloudSaveApi {
        val blobs = HashMap<String, ByteArray>()
        val rows = HashMap<String, RemoteSave>()
        override suspend fun userId() = uid
        override suspend fun manifest(gameKey: String) =
            rows.filterKeys { it.startsWith("$gameKey|") }.values.toList()
        override suspend fun upload(gameKey: String, kind: String, slot: Int, file: File, sha256: String): RemoteSave {
            val k = "$gameKey|$kind|$slot"
            val ver = (rows[k]?.version ?: 0) + 1
            val key = "$uid/$gameKey/$kind-$slot-v$ver.bin"
            blobs[key] = file.readBytes()
            return RemoteSave(kind, slot, sha256, file.length(), ver, key).also { rows[k] = it }
        }
        override suspend fun download(remote: RemoteSave, dest: File): Boolean {
            val b = blobs[remote.storageKey] ?: return false
            dest.parentFile?.mkdirs(); dest.writeBytes(b); return true
        }
    }

    private fun sha(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { s -> val buf = ByteArray(4096); while (true) { val n = s.read(buf); if (n <= 0) break; md.update(buf, 0, n) } }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun write(dir: File, name: String, content: String): File =
        File(dir, name).apply { parentFile?.mkdirs(); writeText(content) }

    @Test
    fun twoDevicesEditingSameSaveProduceConflictNotSilentLoss() = runBlocking<Unit> {
        val root = File(ctx.cacheDir, "cloudtest-${UUID.randomUUID()}").apply { mkdirs() }
        val cloud = FakeCloud("user-1")
        val baseA = File(root, "baseA"); val baseB = File(root, "baseB")
        val fsA = File(root, "devA"); val fsB = File(root, "devB")
        val syncA = CloudSaveSync(cloud, baseA)
        val syncB = CloudSaveSync(cloud, baseB)
        val game = "ULUS-10041"

        // Both devices reach a synced state at content "v1".
        val a = write(fsA, "memcard-0.bin", "v1")
        syncA.sync(game, listOf(LocalSave("memcard", 0, a, sha(a))) ) { _, _ -> File(fsA, "pull.bin") }
        val bPull = File(fsB, "memcard-0.bin")
        val rB = syncB.sync(game, listOf(LocalSave("memcard", 0, null, null))) { _, _ -> bPull }
        assertEquals("device B should pull the initial save", 1, rB.pulled)
        assertEquals("v1", bPull.readText())

        // Both edit independently.
        a.writeText("device-A-edit")
        syncA.sync(game, listOf(LocalSave("memcard", 0, a, sha(a)))) { _, _ -> File(fsA, "pull.bin") }
        bPull.writeText("device-B-edit")

        // Device B syncs against a cloud that device A already advanced → CONFLICT.
        val conflictResult = syncB.sync(game, listOf(LocalSave("memcard", 0, bPull, sha(bPull)))) { _, _ -> File(fsB, "pull.bin") }
        assertEquals("device B must see a conflict", 1, conflictResult.conflicts.size)
        assertEquals(0, conflictResult.pushed)     // nothing pushed
        assertEquals(0, conflictResult.pulled)     // nothing pulled — NOT clobbered
        assertEquals("device B's local edit must be intact", "device-B-edit", bPull.readText())
        assertEquals("cloud must still hold device A's edit", "device-A-edit",
            String(cloud.blobs[cloud.rows["$game|memcard|0"]!!.storageKey]!!))

        // Resolve KEEP_BOTH: cloud copy preserved in a sidecar, device copy uploaded.
        val sidecar = File(fsB, "memcard-0.cloud.bin")
        val ok = syncB.resolveConflict(
            game, conflictResult.conflicts[0], ConflictChoice.KEEP_BOTH,
            localFile = bPull, localHash = sha(bPull),
            pullDest = File(fsB, "pull.bin"), cloudSidecar = sidecar,
        )
        assertTrue("resolve should succeed", ok)
        assertEquals("cloud A-copy preserved in sidecar", "device-A-edit", sidecar.readText())
        assertEquals("cloud now holds device B's copy", "device-B-edit",
            String(cloud.blobs[cloud.rows["$game|memcard|0"]!!.storageKey]!!))
        root.deleteRecursively()
    }

    @Test
    fun liveAnonymousAuthAndCloudRoundTrip() = runBlocking<Unit> {
        val auth = AuthManager(ctx)
        val uid = auth.ensureSignedIn()
        assertNotNull("anonymous sign-in must yield a user id", uid)
        assertTrue("anon session should be flagged anonymous", auth.isAnonymous)

        val client = CloudSaveClient(auth, deviceId = "androidTest")
        val game = "TEST-${UUID.randomUUID().toString().take(8)}"
        val dir = File(ctx.cacheDir, "rt-${UUID.randomUUID()}").apply { mkdirs() }
        val src = File(dir, "state.bin").apply { writeText("save-payload-${UUID.randomUUID()}") }
        val hash = sha(src)

        val up = client.upload(game, "state", 1, src, hash)
        assertNotNull("upload should return a remote save", up)
        assertEquals(hash, up!!.sha256)

        val manifest = client.manifest(game)
        assertTrue("manifest must list the uploaded save", manifest.any { it.kind == "state" && it.slot == 1 })

        val dest = File(dir, "downloaded.bin")
        assertTrue("download should succeed", client.download(up, dest))
        assertEquals("round-tripped bytes must match", src.readText(), dest.readText())
        assertEquals("downloaded hash must match", hash, sha(dest))
        dir.deleteRecursively()
    }
}
