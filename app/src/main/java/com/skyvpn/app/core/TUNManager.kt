package com.skyvpn.app.core

import android.content.Context
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import timber.log.Timber
import java.io.File
import java.io.FileDescriptor
import java.util.concurrent.TimeUnit

object TUNManager {

    private var context: Context? = null
    private var tunFd: ParcelFileDescriptor? = null
    private var tunProcess: Process? = null
    private var binaryFile: File? = null
    private var isActive = false
    private var lastError: String? = null

    fun initialize(context: Context) {
        this.context = context.applicationContext
        binaryFile = findNativeExecutable(context, "libtun2socks.so")
        lastError = if (binaryFile == null) "tun2socks binary missing for this device ABI" else null
    }

    fun start(
        vpnInterface: ParcelFileDescriptor,
        socksHost: String,
        socksPort: Int,
        mtu: Int = 1500
    ): Boolean {
        stop()
        val executable = binaryFile ?: run {
            lastError = "tun2socks binary missing. Add jniLibs/<abi>/libtun2socks.so"
            Timber.e(lastError)
            return false
        }

        return try {
            val duplicatedTun = ParcelFileDescriptor.dup(vpnInterface.fileDescriptor)
            clearCloseOnExec(duplicatedTun.fileDescriptor)
            tunFd = duplicatedTun
            val fd = duplicatedTun.detachFd()
            tunProcess = ProcessBuilder(
                executable.absolutePath,
                "--device", "fd://$fd",
                "--proxy", "socks5://$socksHost:$socksPort",
                "--mtu", mtu.toString(),
                "--loglevel", "info"
            )
                .redirectErrorStream(true)
                .start()

            Thread.sleep(500)
            isActive = tunProcess?.isAlive == true
            if (!isActive) {
                val output = readProcessOutput(tunProcess)
                lastError = if (output.isNotBlank()) {
                    "tun2socks exited: ${output.takeLast(240)}"
                } else {
                    "tun2socks process exited immediately"
                }
                Timber.e(lastError)
            } else {
                lastError = null
            }
            isActive
        } catch (e: Exception) {
            lastError = e.message ?: "Failed to start tun2socks"
            Timber.e(e, "Failed to start tun2socks")
            false
        }
    }

    fun stop() {
        try {
            tunProcess?.let { process ->
                process.destroy()
                if (!process.waitFor(2, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to stop tun2socks cleanly")
        }
        tunProcess = null
        tunFd?.close()
        tunFd = null
        isActive = false
        Timber.i("TUN interface stopped")
    }

    fun isTunActive(): Boolean = isActive

    fun getLastError(): String? = lastError

    private fun findNativeExecutable(context: Context, libraryName: String): File? {
        val nativeDir = context.applicationInfo.nativeLibraryDir ?: return null
        return File(nativeDir, libraryName).takeIf { it.exists() }
    }

    private fun clearCloseOnExec(fileDescriptor: FileDescriptor) {
        runCatching {
            val flags = Os.fcntlInt(fileDescriptor, OsConstants.F_GETFD, 0)
            Os.fcntlInt(fileDescriptor, OsConstants.F_SETFD, flags and OsConstants.FD_CLOEXEC.inv())
        }.onFailure {
            Timber.w(it, "Failed to clear close-on-exec for TUN fd")
        }
    }

    private fun readProcessOutput(process: Process?): String {
        if (process == null) return ""
        return runCatching {
            process.inputStream.bufferedReader().use { reader ->
                reader.readText()
                    .lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .joinToString(" ")
            }
        }.getOrDefault("")
    }
}
