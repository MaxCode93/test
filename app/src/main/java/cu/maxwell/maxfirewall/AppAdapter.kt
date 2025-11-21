package cu.maxwell.maxfirewall

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class AppAdapter(
    private var appList: List<AppInfo>,
    val onItemClick: (AppInfo) -> Unit,
    val onItemLongClick: (AppInfo) -> Unit,
    val onWifiClick: (AppInfo) -> Unit,
    val onDataClick: (AppInfo) -> Unit
) : RecyclerView.Adapter<AppAdapter.AppViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val appInfo = appList[position]
        holder.bind(appInfo)
        // holder.itemView.isActivated = appInfo.isSelected
    }

    override fun getItemCount(): Int = appList.size
    
    fun getAppList(): List<AppInfo> {
        return appList
    }

    fun updateApps(newAppList: List<AppInfo>) {
        // Use a more efficient DiffUtil later if performance becomes an issue
        this.appList = newAppList
        notifyDataSetChanged()
    }

    inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val card: MaterialCardView = itemView as MaterialCardView
        private val appIcon: ImageView = itemView.findViewById(R.id.image_app_icon)
        private val appName: TextView = itemView.findViewById(R.id.text_app_name)
        private val appPackage: TextView = itemView.findViewById(R.id.text_app_package)
        private val wifiIcon: ImageView = itemView.findViewById(R.id.icon_wifi)
        private val dataIcon: ImageView = itemView.findViewById(R.id.icon_data)

        // Color cache
        private val colorBlue = ContextCompat.getColor(itemView.context, R.color.dark_blue)
        private val colorGrey = ContextCompat.getColor(itemView.context, R.color.icon_grey_disabled)
        private val colorOnSurface = ContextCompat.getColor(itemView.context, R.color.light_grey)
        private val colorSelected = ContextCompat.getColor(itemView.context, R.color.selected_grey)
        private val colorCard = ContextCompat.getColor(itemView.context, R.color.card_surface)
        private val colorAccent = ContextCompat.getColor(itemView.context, R.color.neon_cyan)


        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(appList[position])
                }
            }
            itemView.setOnLongClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemLongClick(appList[position])
                }
                true // Consume the long click
            }
            wifiIcon.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onWifiClick(appList[position])
                }
            }
            dataIcon.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onDataClick(appList[position])
                }
            }
        }

        fun bind(appInfo: AppInfo) {
            appIcon.setImageDrawable(appInfo.appIcon)
            appName.text = appInfo.appName
            appPackage.text = appInfo.packageName

            if (appInfo.isSelected) {
                card.setCardBackgroundColor(colorSelected)
                card.strokeWidth = itemView.resources.getDimensionPixelSize(R.dimen.card_stroke_width)
                card.strokeColor = colorAccent
                appName.setTextColor(colorOnSurface)
                appPackage.setTextColor(colorOnSurface)

                wifiIcon.setImageResource(R.drawable.ic_wifi)
                wifiIcon.setColorFilter(colorOnSurface)
                DrawableCompat.setTint(DrawableCompat.wrap(wifiIcon.background), colorAccent)

                dataIcon.setImageResource(R.drawable.ic_data)
                dataIcon.setColorFilter(colorOnSurface)
                DrawableCompat.setTint(DrawableCompat.wrap(dataIcon.background), colorAccent)

            } else {
                card.setCardBackgroundColor(colorCard)
                card.strokeWidth = 0
                appName.setTextColor(colorOnSurface)
                appPackage.setTextColor(colorGrey)

                wifiIcon.setImageResource(R.drawable.ic_wifi)
                if (appInfo.isWifiBlocked) {
                    wifiIcon.setColorFilter(colorGrey)
                    DrawableCompat.setTint(DrawableCompat.wrap(wifiIcon.background), colorSelected)
                } else {
                    wifiIcon.setColorFilter(colorBlue)
                    DrawableCompat.setTint(DrawableCompat.wrap(wifiIcon.background), colorAccent)
                }

                dataIcon.setImageResource(R.drawable.ic_data)
                if (appInfo.isDataBlocked) {
                    dataIcon.setColorFilter(colorGrey)
                    DrawableCompat.setTint(DrawableCompat.wrap(dataIcon.background), colorSelected)
                } else {
                    dataIcon.setColorFilter(colorBlue)
                    DrawableCompat.setTint(DrawableCompat.wrap(dataIcon.background), colorAccent)
                }
            }
        }
    }
}

