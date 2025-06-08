package com.example.praiseprisonapp.ui.home_tab.adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.praiseprisonapp.R
import com.example.praiseprisonapp.data.model.GroupData
import com.example.praiseprisonapp.ui.group.GroupDetailFragment
import com.google.android.material.card.MaterialCardView

class GroupAdapter(private var groupList: List<GroupData>) : RecyclerView.Adapter<GroupAdapter.GroupViewHolder>() {

    fun updateData(newList: List<GroupData>) {
        groupList = newList
        notifyDataSetChanged()
    }

    private val TAG = "GroupAdapter"


    class GroupViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: MaterialCardView = itemView as MaterialCardView
        val ivGroupImage: ImageView = itemView.findViewById(R.id.ivGroupImage)
        val tvGroupName: TextView = itemView.findViewById(R.id.tvGroupName)
        val tvGroupDescription: TextView = itemView.findViewById(R.id.tvGroupDescription)
        val tvMemberCount: TextView = itemView.findViewById(R.id.tvMemberCount)
        val tvMyGroupBadge: TextView = itemView.findViewById(R.id.tvMyGroupBadge)
        val tvVisibilityBadge: TextView = itemView.findViewById(R.id.tvVisibilityBadge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
        Log.d(TAG, "Creating new ViewHolder")
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_group, parent, false)
        return GroupViewHolder(view)
    }

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        val group = groupList[position]
        Log.d(TAG, "Binding group at position $position: ${group.name}")

        try {
            // 기본 정보 설정
            holder.tvGroupName.text = group.name
            holder.tvGroupDescription.text = group.description
            holder.tvMemberCount.text = "총 ${group.memberCount}명 참여중"

            // 이미지 로딩
            if (group.imageUrl.isNotEmpty()) {
                Glide.with(holder.ivGroupImage.context)
                    .load(group.imageUrl)
                    .placeholder(R.drawable.groupimage_example)
                    .into(holder.ivGroupImage)
            } else {
                holder.ivGroupImage.setImageResource(R.drawable.groupimage_example)
            }

            // 내 그룹 뱃지 표시
            holder.tvMyGroupBadge.visibility = if (group.isMyGroup) View.VISIBLE else View.GONE

            // 공개 여부 뱃지 설정
            holder.tvVisibilityBadge.apply {
                text = group.visibility
                visibility = View.VISIBLE
            }

            // 클릭 피드백 설정
            holder.cardView.apply {
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    // 그룹 상세 화면으로 이동
                    val fragment = GroupDetailFragment.newInstance(group)
                    val activity = context as FragmentActivity
                    activity.supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, fragment)
                        .addToBackStack(null)
                        .commit()
                }
            }

            Log.d(TAG, "Successfully bound group at position $position")
        } catch (e: Exception) {
            Log.e(TAG, "Error binding group at position $position", e)
        }
    }

    override fun getItemCount(): Int = groupList.size.also {
        Log.d(TAG, "getItemCount: $it")
    }
}
