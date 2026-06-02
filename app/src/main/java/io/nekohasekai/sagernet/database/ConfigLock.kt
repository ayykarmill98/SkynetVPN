package io.nekohasekai.sagernet.database

import io.nekohasekai.sagernet.BuildConfig

object ConfigLock {

    private const val LOCKED_PROFILE_IDS = "lockedProfileIds"

    val isAppLocked get() = BuildConfig.CUSTOM_LOCKED_APP

    fun isProfileLocked(profileId: Long): Boolean {
        return lockedProfileIds().contains(profileId.toString())
    }

    fun hasLockedProfiles(profiles: List<ProxyEntity>): Boolean {
        val lockedIds = lockedProfileIds()
        return profiles.any { lockedIds.contains(it.id.toString()) }
    }

    fun lockProfiles(profileIds: List<Long>) {
        if (profileIds.isEmpty()) return
        val lockedIds = lockedProfileIds()
        lockedIds.addAll(profileIds.map { it.toString() })
        DataStore.configurationStore.putStringSet(LOCKED_PROFILE_IDS, lockedIds)
    }

    fun unlockProfiles(profileIds: List<Long>) {
        if (profileIds.isEmpty()) return
        val lockedIds = lockedProfileIds()
        lockedIds.removeAll(profileIds.map { it.toString() }.toSet())
        DataStore.configurationStore.putStringSet(LOCKED_PROFILE_IDS, lockedIds)
    }

    private fun lockedProfileIds(): MutableSet<String> {
        return DataStore.configurationStore
            .getStringSet(LOCKED_PROFILE_IDS, mutableSetOf())
            ?.toMutableSet() ?: mutableSetOf()
    }
}
