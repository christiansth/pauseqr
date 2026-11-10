package de.pauseqr.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import com.google.android.material.materialswitch.MaterialSwitch
import java.text.DateFormat
import java.util.Date

/** „ScanOff Freunde“: Übersicht der gespeicherten Freunde, sortiert nach Hinzufügezeitpunkt. */
class FriendsFragment : Fragment(R.layout.fragment_friends) {

    private lateinit var btnAddFriend: Button
    private lateinit var tvFriendsEmpty: TextView
    private lateinit var listFriends: ListView
    private lateinit var adapter: FriendsAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btnAddFriend = view.findViewById(R.id.btnAddFriend)
        tvFriendsEmpty = view.findViewById(R.id.tvFriendsEmpty)
        listFriends = view.findViewById(R.id.listFriends)

        adapter = FriendsAdapter()
        listFriends.adapter = adapter

        btnAddFriend.setOnClickListener {
            (activity as? MainActivity)?.pushFragment(FriendAddFragment())
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val context = context ?: return
        adapter.friends = FriendsStore.getFriends(context)
        adapter.notifyDataSetChanged()
        tvFriendsEmpty.visibility = if (adapter.friends.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openDetail(friend: ScanOffFriend) {
        (activity as? MainActivity)?.pushFragment(
            FriendDetailFragment().apply { arguments = bundleOf(FriendDetailFragment.ARG_FRIEND_ID to friend.id) }
        )
    }

    private fun confirmRemove(friend: ScanOffFriend) {
        val context = context ?: return
        AlertDialog.Builder(context)
            .setTitle(R.string.remove_friend_title)
            .setMessage(R.string.remove_friend_message)
            .setPositiveButton(R.string.remove_friend_confirm) { _, _ ->
                FriendsStore.removeFriend(context, friend.id)
                refresh()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun formatDate(millis: Long): String =
        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))

    private inner class FriendsAdapter : BaseAdapter() {
        var friends: List<ScanOffFriend> = emptyList()

        override fun getCount() = friends.size
        override fun getItem(position: Int) = friends[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val context = parent.context
            val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_friend, parent, false)
            val friend = friends[position]

            view.findViewById<TextView>(R.id.tvFriendUsername).text = friend.username
            view.findViewById<TextView>(R.id.tvFriendCreatedAt).text =
                getString(R.string.created_at_label, formatDate(friend.createdAt))

            val switch = view.findViewById<MaterialSwitch>(R.id.switchUnlock)
            switch.setOnCheckedChangeListener(null)
            switch.isChecked = friend.unlockEnabled
            switch.setOnCheckedChangeListener { _, isChecked ->
                FriendsStore.setUnlockEnabled(context, friend.id, isChecked)
                friends = friends.map { if (it.id == friend.id) it.copy(unlockEnabled = isChecked) else it }
            }

            view.findViewById<ImageButton>(R.id.btnFriendMenu).setOnClickListener { anchor ->
                showFriendMenu(anchor, friend)
            }
            return view
        }
    }

    private fun showFriendMenu(anchor: View, friend: ScanOffFriend) {
        val popup = PopupMenu(anchor.context, anchor)
        popup.menu.add(0, 1, 0, R.string.menu_view_qr)
        popup.menu.add(0, 2, 1, R.string.menu_change_unlock)
        popup.menu.add(0, 3, 2, R.string.menu_remove_friend)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> openDetail(friend)
                2 -> {
                    val context = context ?: return@setOnMenuItemClickListener true
                    FriendsStore.setUnlockEnabled(context, friend.id, !friend.unlockEnabled)
                    refresh()
                }
                3 -> confirmRemove(friend)
            }
            true
        }
        popup.show()
    }
}
