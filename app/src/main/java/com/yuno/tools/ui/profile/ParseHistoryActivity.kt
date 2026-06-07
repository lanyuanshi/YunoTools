package com.yuno.tools.ui.profile
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.yuno.tools.R
import com.yuno.tools.data.ParseHistoryStore
import com.yuno.tools.data.ParseHistoryStore.ParseHistoryItem
import com.yuno.tools.ui.video.ImageSetResultActivity
import com.yuno.tools.ui.video.ParseResultActivity
import com.yuno.tools.util.ThemeApplier
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ParseHistoryActivity : AppCompatActivity() {
    private lateinit var adapter: HistoryAdapter
    private lateinit var emptyView: View
    private lateinit var recyclerView: RecyclerView
    private val timeFormat = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_parse_history)
        ThemeApplier.apply(this)
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.btnClearHistory).setOnClickListener { confirmClear() }
        emptyView = findViewById(R.id.emptyHistory)
        recyclerView = findViewById(R.id.rvHistory)
        adapter = HistoryAdapter(timeFormat) { openHistory(it) }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        loadHistory()
    }
    override fun onResume() { super.onResume(); ThemeApplier.apply(this); loadHistory() }
    private fun loadHistory() {
        val items = ParseHistoryStore.getAll(this)
        adapter.submit(items)
        emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        recyclerView.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        findViewById<TextView>(R.id.tvHistoryCount).text = "共 ${items.size} 条"
    }
    private fun confirmClear() {
        if (adapter.itemCount == 0) return
        AlertDialog.Builder(this).setTitle("清空历史记录").setMessage("确定要清空所有解析历史吗？")
            .setNegativeButton("取消", null).setPositiveButton("清空") { _, _ -> ParseHistoryStore.clear(this); loadHistory() }.show()
    }
    private fun openHistory(item: ParseHistoryItem) {
        val intent = if (item.result.isImageSet) Intent(this, ImageSetResultActivity::class.java) else Intent(this, ParseResultActivity::class.java)
        intent.putExtra("result", item.result)
        startActivity(intent)
    }
    private class HistoryAdapter(private val timeFormat: SimpleDateFormat, private val onClick: (ParseHistoryItem) -> Unit) : RecyclerView.Adapter<HistoryAdapter.VH>() {
        private val items = mutableListOf<ParseHistoryItem>()
        fun submit(newItems: List<ParseHistoryItem>) { items.clear(); items.addAll(newItems); notifyDataSetChanged() }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH = VH(LayoutInflater.from(parent.context).inflate(R.layout.item_parse_history, parent, false))
        override fun onBindViewHolder(holder: VH, position: Int) { holder.bind(items[position], timeFormat, onClick) }
        override fun getItemCount(): Int = items.size
        class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val cover: ImageView = itemView.findViewById(R.id.ivCover)
            private val title: TextView = itemView.findViewById(R.id.tvTitle)
            private val type: TextView = itemView.findViewById(R.id.tvType)
            private val time: TextView = itemView.findViewById(R.id.tvTime)
            private val source: TextView = itemView.findViewById(R.id.tvSource)
            fun bind(item: ParseHistoryItem, timeFormat: SimpleDateFormat, onClick: (ParseHistoryItem) -> Unit) {
                title.text = item.title.ifBlank { if (item.result.isImageSet) "图集解析" else "视频解析" }
                type.text = if (item.result.isImageSet) "图集 · ${item.result.images.size} 张" else "视频"
                time.text = timeFormat.format(Date(item.parsedAt))
                source.text = item.sourceUrl.ifBlank { "点击查看解析结果" }
                Glide.with(cover).load(item.coverUrl.ifBlank { item.result.images.firstOrNull() }).placeholder(R.drawable.bg_circle_blue_light).error(R.drawable.bg_circle_blue_light).centerCrop().into(cover)
                itemView.setOnClickListener { onClick(item) }
            }
        }
    }
}
