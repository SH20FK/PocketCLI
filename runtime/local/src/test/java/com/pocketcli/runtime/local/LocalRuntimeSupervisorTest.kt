package com.pocketcli.runtime.local

import com.pocketcli.core.security.SecretStore
import com.pocketcli.data.local.db.ConnectionProfileEntity
import com.pocketcli.data.local.db.ProfileDao
import com.pocketcli.runtime.local.installer.RuntimeInstaller
import com.pocketcli.runtime.local.manifest.ManifestParser
import com.pocketcli.runtime.local.proot.ProotEnvironment
import com.pocketcli.runtime.local.supervisor.CircularLogBuffer
import com.pocketcli.runtime.local.supervisor.LocalRuntimeState
import com.pocketcli.runtime.local.supervisor.LocalRuntimeSupervisor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class LocalRuntimeSupervisorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var filesDir: File
    private lateinit var nativeLibDir: File
    private lateinit var rootfsDir: File
    private lateinit var prootEnv: ProotEnvironment
    private lateinit var runtimeInstaller: RuntimeInstaller
    private lateinit var fakeProfileDao: FakeProfileDao
    private lateinit var fakeSecretStore: FakeSecretStore
    private lateinit var logBuffer: CircularLogBuffer
    private lateinit var mockWebServer: MockWebServer
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        filesDir = tempFolder.newFolder("filesDir")
        nativeLibDir = tempFolder.newFolder("nativeLibDir")
        rootfsDir = File(filesDir, "runtime/rootfs").apply { mkdirs() }

        // Setup dummy PRoot binaries
        File(nativeLibDir, "libproot.so").writeText("proot")
        File(nativeLibDir, "libproot-loader.so").writeText("loader")

        prootEnv = ProotEnvironment(
            nativeLibDirProvider = { nativeLibDir },
            filesDirProvider = { filesDir }
        )

        // Setup runtime installer ready marker & binary
        File(rootfsDir, ".pocketcli_ready").writeText("""{"runtimeVersion":"1.2.27"}""")
        File(rootfsDir, "usr/bin").apply { mkdirs() }
        File(rootfsDir, "usr/bin/opencode").writeText("opencode-binary")

        runtimeInstaller = RuntimeInstaller(
            prootEnvironment = prootEnv,
            manifestParser = ManifestParser(),
            okHttpClient = OkHttpClient(),
            ioDispatcher = testDispatcher,
            filesDirProvider = { filesDir },
            supportedAbisProvider = { arrayOf("x86_64") },
            freeSpaceProvider = { 1024L * 1024L * 1024L },
            manifestContentProvider = { "{}" }
        )

        fakeProfileDao = FakeProfileDao()
        fakeSecretStore = FakeSecretStore()
        logBuffer = CircularLogBuffer(capacity = 50)
        mockWebServer = MockWebServer()
        mockWebServer.start()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun testStartFailsWhenNotInstalled() = runTest(testDispatcher) {
        File(rootfsDir, ".pocketcli_ready").delete()

        val supervisor = createSupervisor()
        val result = supervisor.startServer()

        assertTrue(result.isFailure)
        assertTrue(supervisor.state.value is LocalRuntimeState.Failed)
        assertFalse(supervisor.isConnected)
    }

    @Test
    fun testStartFailsWhenProotMissing() = runTest(testDispatcher) {
        File(nativeLibDir, "libproot.so").delete()

        val supervisor = createSupervisor()
        val result = supervisor.startServer()

        assertTrue(result.isFailure)
        assertTrue(supervisor.state.value is LocalRuntimeState.Failed)
    }

    @Test
    fun testSuccessfulStartAndStopLifecycle() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"healthy":true,"version":"1.2.27"}""")
        )

        val fakeProc = FakeProcess()
        val supervisor = createSupervisor(
            port = mockWebServer.port,
            processLauncher = { _, _ -> fakeProc }
        )

        val startResult = supervisor.startServer()
        assertTrue("Start should succeed: ${startResult.exceptionOrNull()?.message}", startResult.isSuccess)
        assertTrue(supervisor.isConnected)
        assertTrue(supervisor.state.value is LocalRuntimeState.Running)

        val runningState = supervisor.state.value as LocalRuntimeState.Running
        assertEquals(mockWebServer.port, runningState.port)
        assertEquals("test-token-123", runningState.token)
        assertEquals("http://127.0.0.1:${mockWebServer.port}", supervisor.baseUrl)

        // Verify profile registered in DB
        val profile = fakeProfileDao.getById(LocalRuntimeSupervisor.LOCAL_PROFILE_ID)
        assertNotNull("Local profile should be saved in DB", profile)
        assertEquals("http://127.0.0.1:${mockWebServer.port}", profile?.url)
        assertEquals("enc_test-token-123", profile?.encryptedPassword)

        // Verify log buffer has messages
        val lines = logBuffer.getLines()
        assertTrue("Logs should contain startup entries", lines.any { it.contains("Сервер успешно запущен") })

        // Test Stop
        val stopResult = supervisor.stopServer()
        assertTrue(stopResult.isSuccess)
        assertFalse(supervisor.isConnected)
        assertTrue(supervisor.state.value is LocalRuntimeState.Stopped)
        assertFalse("Fake process should be destroyed", fakeProc.isAlive)
    }

    @Test
    fun testCircularLogBufferTrimming() {
        val buffer = CircularLogBuffer(capacity = 5)
        for (i in 1..10) {
            buffer.append("line $i")
        }
        val lines = buffer.getLines()
        assertEquals(5, lines.size)
        assertEquals("line 6", lines.first())
        assertEquals("line 10", lines.last())

        buffer.clear()
        assertEquals(0, buffer.getLines().size)
    }

    private fun createSupervisor(
        port: Int = 4096,
        processLauncher: (cmd: List<String>, env: Map<String, String>) -> Process = { _, _ -> FakeProcess() }
    ): LocalRuntimeSupervisor {
        return LocalRuntimeSupervisor(
            prootEnvironment = prootEnv,
            runtimeInstaller = runtimeInstaller,
            profileDao = fakeProfileDao,
            secretStore = fakeSecretStore,
            logBuffer = logBuffer,
            okHttpClient = OkHttpClient.Builder().build(),
            ioDispatcher = testDispatcher,
            workspacesDirProvider = { File(filesDir, "workspaces") },
            portAllocator = { port },
            tokenGenerator = { "test-token-123" },
            processLauncher = processLauncher,
            scope = CoroutineScope(testDispatcher)
        )
    }

    private class FakeProfileDao : ProfileDao {
        private val map = mutableMapOf<String, ConnectionProfileEntity>()
        private val flow = MutableStateFlow<List<ConnectionProfileEntity>>(emptyList())

        override suspend fun upsert(profile: ConnectionProfileEntity) {
            map[profile.id] = profile
            flow.value = map.values.toList()
        }

        override suspend fun update(profile: ConnectionProfileEntity) = upsert(profile)

        override fun getAll(): Flow<List<ConnectionProfileEntity>> = flow

        override suspend fun getAllList(): List<ConnectionProfileEntity> = map.values.toList()

        override suspend fun getById(id: String): ConnectionProfileEntity? = map[id]

        override suspend fun deleteById(id: String) {
            map.remove(id)
            flow.value = map.values.toList()
        }
    }

    private class FakeSecretStore : SecretStore {
        override fun encrypt(plaintext: String): String = "enc_$plaintext"
        override fun decrypt(encryptedPayload: String): String = encryptedPayload.removePrefix("enc_")
    }

    private class FakeProcess(private val exitCode: Int = 0) : Process() {
        private var alive = true
        private val stdout = ByteArrayInputStream("opencode server ready\n".toByteArray())
        private val stderr = ByteArrayInputStream("".toByteArray())
        private val sink = ByteArrayOutputStream()

        override fun getOutputStream(): OutputStream = sink
        override fun getInputStream(): InputStream = stdout
        override fun getErrorStream(): InputStream = stderr

        override fun waitFor(): Int {
            alive = false
            return exitCode
        }

        override fun exitValue(): Int = if (alive) throw IllegalThreadStateException() else exitCode

        override fun destroy() {
            alive = false
        }

        override fun isAlive(): Boolean = alive
    }
}
