package com.example.praiseprisonapp.ui.home_tab.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.praiseprisonapp.R
import com.example.praiseprisonapp.data.model.GroupData

class GroupAdapter(private val groupList: List<GroupData>) : RecyclerView.Adapter<GroupAdapter.GroupViewHolder>() {

    class GroupViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivGroupImage: ImageView = itemView.findViewById(R.id.ivGroupImage)
        val tvGroupName: TextView = itemView.findViewById(R.id.tvGroupName)
        val tvGroupDescription: TextView = itemView.findViewById(R.id.tvGroupDescription)
        val tvMemberCount: TextView = itemView.findViewById(R.id.tvMemberCount)
        val tvMyGroupBadge: TextView = itemView.findViewById(R.id.tvMyGroupBadge)
        val tvVisibilityBadge: TextView = itemView.findViewById(R.id.tvVisibilityBadge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_group, parent, false)
        return GroupViewHolder(view)
    }

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        val group = groupList[position]

        holder.tvGroupName.text = group.name
        holder.tvGroupDescription.text = group.description
        holder.tvMemberCount.text = "총 ${group.memberCount}명 참여중"

        Glide.with(holder.ivGroupImage.context)
            .load(group.imageUrl)
            .placeholder(R.drawable.groupimage_example) // 없으면 기본 이미지 추가 가능
            .into(holder.ivGroupImage)

        holder.tvMyGroupBadge.visibility = if (group.isMyGroup) View.VISIBLE else View.GONE
        holder.tvVisibilityBadge.text = group.visibility
    }

    override fun getItemCount(): Int = groupList.size
}
