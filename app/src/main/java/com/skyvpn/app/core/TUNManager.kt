package com.skyvpn.app.core

import android.content.Context
import android.os.ParcelFileDescriptor
import timber.log.Timber
import java.io.File
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
            tunFd = ParcelFileDescriptor.dup(vpnInterface.fileDescriptor)
            val fd = tunFd!!.detachFd()
            tunProcess = ProcessBuilder(
                executable.absolutePath,
                "--tunfd", fd.toString(),
                "--tunmtu", mtu.toString(),
                "--socks-server-addr", "$socksHost:$socksPort",
                "--netif-ipaddr", "10.10.10.2",
                "--netif-netmask", "255.255.255.0"
            )
                .redirectErrorStream(true)
                .start()

            Thread.sleep(500)
            isActive = tunProcess?.isAlive == true
            if (!isActive) {
                lastError = "tun2socks process exited immediately"
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
}
