package com.skyvpn.app.core

import android.content.Context
import android.os.ParcelFileDescriptor
import timber.log.Timber

object TUNManager {

    private var context: Context? = null
    private var tunFd: ParcelFileDescriptor? = null
    private var isActive = false
    private var lastError: String? = null

    fun initialize(context: Context) {
        this.context = context.applicationContext
        lastError = null
    }

    fun start(
        vpnInterface: ParcelFileDescriptor,
        socksHost: String,
        socksPort: Int,
        mtu: Int = 1500
    ): Boolean {
        stop()

        return try {
            val duplicatedTun = ParcelFileDescriptor.dup(vpnInterface.fileDescriptor)
            tunFd = duplicatedTun

            val device = "fd://${duplicatedTun.fd}"
            val proxy = "socks5://$socksHost:$socksPort"
            val key = createEngineKey(
                mtu = mtu,
                device = device,
                proxy = proxy
            )

            invokeEngine("insert", key)
            invokeEngine("start")
            isActive = true
            lastError = null
            Timber.i("tun2socks engine started with $device -> $proxy")
            true
        } catch (e: Throwable) {
            lastError = e.message ?: "Failed to start tun2socks engine"
            Timber.e(e, "Failed to start tun2socks engine")
            isActive = false
            false
        }
    }

    fun stop() {
        if (isActive) {
            runCatching {
                invokeEngine("stop")
            }.onFailure {
                Timber.w(it, "Failed to stop tun2socks engine cleanly")
            }
        }
        tunFd?.close()
        tunFd = null
        isActive = false
        Timber.i("TUN engine stopped")
    }

    fun isTunActive(): Boolean = isActive

    fun getLastError(): String? = lastError

    private fun createEngineKey(mtu: Int, device: String, proxy: String): Any {
        val keyClass = Class.forName("engine.Key")
        val key = keyClass.getDeclaredConstructor().newInstance()
        invokeSetter(key, "setMark", 0)
        invokeSetter(key, "setMTU", mtu)
        invokeSetter(key, "setDevice", device)
        invokeSetter(key, "setProxy", proxy)
        invokeSetter(key, "setRestAPI", "")
        invokeSetter(key, "setTCPSendBufferSize", "")
        invokeSetter(key, "setTCPReceiveBufferSize", "")
        invokeSetter(key, "setTCPModerateReceiveBuffer", false)
        invokeSetter(key, "setLogLevel", "info")
        invokeSetter(key, "setInterface", "")
        return key
    }

    private fun invokeSetter(target: Any, name: String, value: Any) {
        val method = target.javaClass.methods.firstOrNull {
            it.name == name && it.parameterTypes.size == 1
        } ?: error("tun2socks engine method missing: $name")

        val converted = when (method.parameterTypes[0]) {
            java.lang.Long.TYPE, java.lang.Long::class.java -> (value as Number).toLong()
            java.lang.Integer.TYPE, java.lang.Integer::class.java -> (value as Number).toInt()
            java.lang.Boolean.TYPE, java.lang.Boolean::class.java -> value as Boolean
            String::class.java -> value.toString()
            else -> value
        }
        method.invoke(target, converted)
    }

    private fun invokeEngine(name: String, vararg args: Any?) {
        val engineClass = Class.forName("engine.Engine")
        val method = engineClass.methods.firstOrNull {
            it.name == name && it.parameterTypes.size == args.size
        } ?: error("tun2socks engine method missing: $name")
        method.invoke(null, *args)
    }
}
