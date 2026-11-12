package de.pauseqr.app

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import java.text.DateFormat
import java.util.Date

/** Detailansicht eines gespeicherten ScanOff-Freundes. */
class FriendDetailFragment : Fragment(R.layout.fragment_friend_detail) {

    companion object {
        const val ARG_FRIEND_ID = "friend_id"
    }

    private lateinit var tvUsername: TextView
    private lateinit var ivQr: ImageView
    private lateinit var tvCreatedAt: TextView
    private lateinit var tvAddedAt: TextView
    private lateinit var ivUnlockIcon: ImageView
    private lateinit var tvUnlockStatus: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvUsername = view.findViewById(R.id.tvDetailUsername)
        ivQr = view.findViewById(R.id.ivDetailQr)
        tvCreatedAt = view.findViewById(R.id.tvDetailCreatedAt)
        tvAddedAt = view.findViewById(R.id.tvDetailAddedAt)
        ivUnlockIcon = view.findViewById(R.id.ivDetailUnlockIcon)
        tvUnlockStatus = view.findViewById(R.id.tvDetailUnlockStatus)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val context = context ?: return
        val friendId = arguments?.getString(ARG_FRIEND_ID) ?: return
        val friend = FriendsStore.findById(context, friendId) ?: return

        tvUsername.text = friend.username
        // Den ursprünglich gescannten Payload exakt decodieren statt neu zusammenzusetzen – ein
        // neu erzeugter Record wäre nicht dieselbe Identität (z. B. fehlende Passwortprüfdaten
        // bei einer passwortgeschützten Version-2-Identität des Freundes).
        val record = QrPayload.decode(friend.qrPayload)
        if (record != null) {
            ivQr.setImageBitmap(QrUtil.makeQr(record, 900))
        }
        tvCreatedAt.text = getString(R.string.created_at_label, formatDate(friend.createdAt))
        tvAddedAt.text = getString(R.string.added_at_label, formatDate(friend.addedAt))

        if (friend.unlockEnabled) {
            ivUnlockIcon.setImageResource(R.drawable.ic_check)
            ivUnlockIcon.setColorFilter(resources.getColor(R.color.green, null))
            tvUnlockStatus.text = getString(R.string.friend_detail_unlock_on)
            tvUnlockStatus.setTextColor(resources.getColor(R.color.green, null))
        } else {
            ivUnlockIcon.setImageResource(R.drawable.ic_lock_closed)
            ivUnlockIcon.setColorFilter(resources.getColor(R.color.text_secondary, null))
            tvUnlockStatus.text = getString(R.string.friend_detail_unlock_off)
            tvUnlockStatus.setTextColor(resources.getColor(R.color.text_secondary, null))
        }
    }

    private fun formatDate(millis: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))
}
