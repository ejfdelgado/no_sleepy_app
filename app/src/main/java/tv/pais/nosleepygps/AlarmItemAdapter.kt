package tv.pais.nosleepygps

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlarmItemAdapter(
    private var items: List<AlarmItem>,
    private val onEditClick: (AlarmItem) -> Unit,
    private val onDeleteClick: (AlarmItem) -> Unit,
    private val onToggleClick: (AlarmItem, Boolean) -> Unit
) : RecyclerView.Adapter<AlarmItemAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleTextView: TextView = view.findViewById(R.id.tv_alarm_title)
        val datesTextView: TextView = view.findViewById(R.id.tv_alarm_dates)
        val editButton: Button = view.findViewById(R.id.btn_edit_alarm)
        val deleteButton: Button = view.findViewById(R.id.btn_delete_alarm)
        val enabledSwitch: android.widget.Switch = view.findViewById(R.id.switch_alarm_enabled)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_alarm, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.titleTextView.text = item.title
        
        val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        val createdStr = dateFormat.format(Date(item.created))
        val updatedStr = dateFormat.format(Date(item.updated))
        
        holder.datesTextView.text = "Created: $createdStr | Updated: $updatedStr"

        holder.editButton.setOnClickListener { onEditClick(item) }
        holder.deleteButton.setOnClickListener { onDeleteClick(item) }

        holder.enabledSwitch.setOnCheckedChangeListener(null)
        holder.enabledSwitch.isChecked = item.enabled
        holder.enabledSwitch.setOnCheckedChangeListener { _, isChecked ->
            onToggleClick(item, isChecked)
        }
    }

    override fun getItemCount() = items.size
    
    fun getItems(): List<AlarmItem> = items

    fun updateData(newItems: List<AlarmItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}
