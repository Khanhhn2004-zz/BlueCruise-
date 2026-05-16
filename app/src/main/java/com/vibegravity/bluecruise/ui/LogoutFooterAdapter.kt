package com.vibegravity.bluecruise.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.vibegravity.bluecruise.R
import com.vibegravity.bluecruise.databinding.ItemLogoutFooterBinding

class LogoutFooterAdapter(
    private val onLogoutClicked: () -> Unit
) : RecyclerView.Adapter<LogoutFooterAdapter.ViewHolder>() {

    var isLoggingOut: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            notifyItemChanged(0)
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return ViewHolder(ItemLogoutFooterBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(isLoggingOut, onLogoutClicked)
    }

    override fun getItemCount(): Int = 1

    class ViewHolder(private val binding: ItemLogoutFooterBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(isLoggingOut: Boolean, onLogoutClicked: () -> Unit) {
            binding.btnLogout.isEnabled = !isLoggingOut
            binding.btnLogout.text = if (isLoggingOut) {
                binding.root.context.getString(R.string.logging_out)
            } else {
                binding.root.context.getString(R.string.logout)
            }
            binding.btnLogout.setOnClickListener {
                if (!isLoggingOut) onLogoutClicked()
            }
        }
    }
}
