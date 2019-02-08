package mattecarra.accapp.adapters

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import mattecarra.accapp.R
import java.util.ArrayList
import kotlin.math.log

class LogRecyclerViewAdapter(val lines: ArrayList<String>, private val listener: (String) -> Unit) : RecyclerView.Adapter<LogRecyclerViewAdapter.LogViewHolder>() {

    fun saveState(bundle: Bundle) {
        bundle.putStringArrayList("lines", lines)
    }

    fun restoreState(bundle: Bundle) {
        bundle.getStringArrayList("lines")?.let {
            lines.addAll(it)
        }
    }

    fun add(line: String) {
        lines.add(line)
        notifyItemInserted(lines.size - 1)
    }

    fun setList(logs: List<String>) {
        this.lines.clear()
        this.lines.addAll(logs)
        notifyDataSetChanged()
    }

    fun getItem(index: Int): String {
        return lines[index]
    }

    fun size(): Int {
        return lines.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogRecyclerViewAdapter.LogViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.log_row, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogRecyclerViewAdapter.LogViewHolder, position: Int) {
        holder.bind(getItem(position), listener)
    }

    override fun getItemCount(): Int {
        return lines.size
    }

    inner class LogViewHolder(internal var view: View) : RecyclerView.ViewHolder(view) {
        fun bind(line: String, listener: (String) -> Unit) = with(itemView) {
            view.findViewById<TextView>(R.id.line_text_view).text = line
            setOnClickListener { listener(line) }
        }
    }
}