package io.nekohasekai.sagernet.database

import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.fmt.parseUniversal
import io.nekohasekai.sagernet.fmt.toUniversalLink
import moe.matsuri.nb4a.utils.Util
import org.json.JSONArray
import org.json.JSONObject

data class ManagedConfigPayload(
    val locked: Boolean,
    val profiles: List<AbstractBean>,
)

object ManagedConfig {

    private const val PREFIX = "sn://managed-config?"

    fun isManagedConfig(text: String): Boolean {
        return text.trim().startsWith(PREFIX)
    }

    fun exportProfiles(profiles: List<ProxyEntity>, locked: Boolean): String {
        val payload = JSONObject().apply {
            put("version", 1)
            put("locked", locked)
            put("profiles", JSONArray().apply {
                profiles.forEach {
                    put(it.requireBean().toUniversalLink())
                }
            })
        }
        val compressed = Util.zlibCompress(payload.toString().toByteArray(), 9)
        return PREFIX + Util.b64EncodeUrlSafe(compressed)
    }

    fun parse(link: String): ManagedConfigPayload {
        val data = link.trim().substringAfter(PREFIX)
        val payload = JSONObject(String(Util.zlibDecompress(Util.b64Decode(data))))
        val version = payload.optInt("version", 0)
        require(version == 1) { "Unsupported managed config version" }

        val profilesJson = payload.getJSONArray("profiles")
        val profiles = ArrayList<AbstractBean>()
        for (i in 0 until profilesJson.length()) {
            profiles.add(parseUniversal(profilesJson.getString(i)))
        }
        return ManagedConfigPayload(
            locked = payload.optBoolean("locked", true),
            profiles = profiles,
        )
    }
}
