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

    fun initialize(context: Context) {
        this.context = context.applicationContext
        binaryFile = extractExecutable(
            context = context,
            assetName = "tun2socks",
            targetName = "tun2socks"
        )
    }

    fun start(
        vpnInterface: ParcelFileDescriptor,
        socksHost: String,
        socksPort: Int,
        mtu: Int = 1500
    ): Boolean {
        stop()
        val executable = binaryFile ?: run {
            Timber.e("tun2socks binary is missing. Add assets/tun2socks/<abi>/tun2socks")
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
                Timber.e("tun2socks process exited immediately")
            }
            isActive
        } catch (e: Exception) {
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
