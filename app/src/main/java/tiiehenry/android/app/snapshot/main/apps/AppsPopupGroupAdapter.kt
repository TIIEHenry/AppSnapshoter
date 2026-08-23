package tiiehenry.android.app.snapshot.main.apps

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import tiiehenry.android.app.snapshot.R
import tiiehenry.android.app.snapshot.databinding.ItemAppsPopupGroupBinding
import tiiehenry.android.app.snapshot.group.AppsPopupGroupRow

class AppsPopupGroupAdapter(
    private val onClick: (AppsPopupGroupRow) -> Unit,
) : RecyclerView.Adapter<AppsPopupGroupAdapter.Holder>() {

    private var rows: List<AppsPopupGroupRow> = emptyList()

    fun submit(list: List<AppsPopupGroupRow>) {
        rows = list
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = rows.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemAppsPopupGroupBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val row = rows[position]
        val res = if (row.exclusive) R.string.app_membership_exclusive_item
        else R.string.app_membership_shared_item
        holder.binding.groupLabel.text = holder.itemView.context.getString(res, row.group.name)
        holder.itemView.setOnClickListener { onClick(row) }
    }

    class Holder(val binding: ItemAppsPopupGroupBinding) : RecyclerView.ViewHolder(binding.root)
}
