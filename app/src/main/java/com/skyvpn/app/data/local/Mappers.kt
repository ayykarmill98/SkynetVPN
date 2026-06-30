package com.skyvpn.app.data.local

import com.skyvpn.app.domain.model.LogLevel
import com.skyvpn.app.domain.model.SecurityType
import com.skyvpn.app.domain.model.TransportType
import com.skyvpn.app.domain.model.VPNConfig
import com.skyvpn.app.domain.model.VPNConfigSource
import com.skyvpn.app.domain.model.VPNLog
import com.skyvpn.app.domain.model.VPNProtocol

fun VPNConfig.toEntity(): VPNConfigEntity = VPNConfigEntity(
    id = id, name = name, protocol = protocol.name,
    address = address, port = port, uuid = uuid,
    alterId = alterId, flow = flow, encryption = encryption,
    transportType = transportType.name, host = host, path = path,
    sni = sni, security = security.name, publicKey = publicKey,
    shortId = shortId, serverName = serverName, fingerprint = fingerprint,
    spiderX = spiderX, username = username, password = password,
    method = method, rawConfig = rawConfig, isLocked = isLocked,
    lockPassword = lockPassword, isExpired = isExpired,
    expireDate = expireDate, watermark = watermark,
    dnsRemote = dnsRemote, isPinned = isPinned,
    source = source.name, freeAccountId = freeAccountId,
    createdAt = createdAt, updatedAt = updatedAt
)

fun VPNConfigEntity.toDomain(): VPNConfig = VPNConfig(
    id = id, name = name,
    protocol = runCatching { VPNProtocol.valueOf(protocol) }.getOrDefault(VPNProtocol.VMESS),
    address = address, port = port, uuid = uuid,
    alterId = alterId, flow = flow, encryption = encryption,
    transportType = runCatching { TransportType.valueOf(transportType) }.getOrDefault(TransportType.TCP),
    host = host, path = path, sni = sni,
    security = runCatching { SecurityType.valueOf(security) }.getOrDefault(SecurityType.TLS),
    publicKey = publicKey, shortId = shortId,
    serverName = serverName, fingerprint = fingerprint,
    spiderX = spiderX, username = username, password = password,
    method = method, rawConfig = rawConfig, isLocked = isLocked,
    lockPassword = lockPassword, isExpired = isExpired,
    expireDate = expireDate, watermark = watermark,
    dnsRemote = dnsRemote, isPinned = isPinned,
    source = runCatching { VPNConfigSource.valueOf(source) }.getOrDefault(VPNConfigSource.MANUAL),
    freeAccountId = freeAccountId,
    createdAt = createdAt, updatedAt = updatedAt
)

fun VPNLog.toEntity(): VPNLogEntity = VPNLogEntity(
    id = id, timestamp = timestamp,
    level = level.name, tag = tag, message = message
)

fun VPNLogEntity.toDomain(): VPNLog = VPNLog(
    id = id, timestamp = timestamp,
    level = runCatching { LogLevel.valueOf(level) }.getOrDefault(LogLevel.INFO),
    tag = tag, message = message
)
