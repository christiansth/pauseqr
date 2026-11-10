package de.pauseqr.app

/**
 * Ein gespeicherter ScanOff-Freund – der QR-Code eines anderen ScanOff-Nutzers.
 *
 * Ein QR-Code ist kopierbar: [unlockEnabled] autorisiert den gespeicherten CODE, nicht die
 * tatsächliche Identität der Person. [id]/[createdAt] sind Angaben des Erstellers und kein
 * fälschungssicherer Nachweis.
 */
data class ScanOffFriend(
    val id: String,
    val username: String,
    val createdAt: Long,
    val addedAt: Long,
    val qrPayload: String,
    val unlockEnabled: Boolean = false
)
