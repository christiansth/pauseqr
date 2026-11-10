package de.pauseqr.app

/**
 * Ein persönlicher ScanOff-Code.
 *
 * [version] 0 markiert einen vor diesem Update erzeugten Code (reiner "PAUSEQR:<uuid>"-String
 * ohne strukturierte Metadaten) – er bleibt gültig, bis der Nutzer ausdrücklich einen neuen Code
 * erstellt. [version] 1+ ist das neue, strukturierte Format aus [QrPayload].
 */
data class QrCodeRecord(
    val id: String,
    val username: String,
    val createdAt: Long,
    val version: Int
)
