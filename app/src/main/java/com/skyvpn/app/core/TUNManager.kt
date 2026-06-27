package com.skyvpn.app.core

import android.content.Context
import android.os.Build
import android.os.ParcelFileDescriptor
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
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
        binaryFile = extractExecutable(
            context = context,
            assetName = "tun2socks",
            targetName = "tun2socks"
        )
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
            lastError = "tun2socks binary missing. Add assets/tun2socks/<abi>/tun2socks"
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

    private fun extractExecutable(context: Context, assetName: String, targetName: String): File? {
        val abiAsset = Build.SUPPORTED_ABIS
            .asSequence()
            .map { "$assetName/$it/$targetName" }
            .firstOrNull { assetPath ->
                runCatching { context.assets.open(assetPath).close() }.isSuccess
            }
            ?: return null

        val target = File(context.filesDir, "$targetName-${abiAsset.substringAfter("$assetName/").substringBefore("/")}")
        runCatching {
            context.assets.open(abiAsset).use { input ->
                FileOutputStream(target).use { output ->
                    input.copyTo(output)
                }
            }
            target.setExecutable(true, true)
        }.onFailure {
            Timber.e(it, "Failed to extract $abiAsset")
            return null
        }
        return target
    }
}
