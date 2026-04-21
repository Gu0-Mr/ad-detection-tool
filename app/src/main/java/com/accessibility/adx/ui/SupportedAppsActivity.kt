package com.accessibility.adx.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.accessibility.adx.Constants
import com.accessibility.adx.R
import com.accessibility.adx.databinding.ActivitySupportedAppsBinding

/**
 * 适配名单Activity
 * 显示已适配的APP列表
 * 作者：古封
 */
class SupportedAppsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySupportedAppsBinding
    
    private val appList = mutableListOf<AppItem>()
    private lateinit var adapter: AppListAdapter

    data class AppItem(
        val packageName: String,
        val appName: String,
        val description: String,
        var icon: Drawable?,
        var isInstalled: Boolean,
        var status: AppStatus
    )
    
    enum class AppStatus {
        SUPPORTED_INSTALLED,  // 已适配且已安装
        SUPPORTED_NOT_INSTALLED, // 已适配但未安装
        NOT_SUPPORTED
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySupportedAppsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        loadAppList()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = getString(R.string.title_supported_apps)
            setDisplayHomeAsUpEnabled(true)
        }
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupRecyclerView() {
        adapter = AppListAdapter(appList)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun loadAppList() {
        binding.progressBar.visibility = View.VISIBLE
        
        Thread {
            appList.clear()
            
            for (supportedApp in Constants.SUPPORTED_APPS) {
                val appItem = AppItem(
                    packageName = supportedApp.packageName,
                    appName = supportedApp.appName,
                    description = supportedApp.description,
                    icon = null,
                    isInstalled = isAppInstalled(supportedApp.packageName),
                    status = if (isAppInstalled(supportedApp.packageName)) {
                        AppStatus.SUPPORTED_INSTALLED
                    } else {
                        AppStatus.SUPPORTED_NOT_INSTALLED
                    }
                )
                
                // 尝试获取APP图标
                if (appItem.isInstalled) {
                    appItem.icon = getAppIcon(supportedApp.packageName)
                }
                
                appList.add(appItem)
            }
            
            runOnUiThread {
                binding.progressBar.visibility = View.GONE
                adapter.notifyDataSetChanged()
                updateEmptyState()
            }
        }.start()
    }

    private fun isAppInstalled(packageName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(packageName, 0)
            }
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun getAppIcon(packageName: String): Drawable? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationIcon(packageName)
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationIcon(packageName)
            }
        } catch (e: Exception) {
            ContextCompat.getDrawable(this, R.drawable.ic_accessibility)
        }
    }

    private fun openAppStore(packageName: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            // 如果市场应用不可用，尝试打开应用详情页
            try {
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.parse("package:$packageName")
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } catch (e2: Exception) {
                // 忽略
            }
        }
    }

    private fun updateEmptyState() {
        binding.tvEmpty.visibility = if (appList.isEmpty()) View.VISIBLE else View.GONE
    }

    /**
     * RecyclerView适配器
     */
    inner class AppListAdapter(private val items: List<AppItem>) : 
        RecyclerView.Adapter<AppListAdapter.ViewHolder>() {
        
        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val ivIcon: ImageView = itemView.findViewById(R.id.ivAppIcon)
            val tvAppName: TextView = itemView.findViewById(R.id.tvAppName)
            val tvPackageName: TextView = itemView.findViewById(R.id.tvPackageName)
            val tvDescription: TextView = itemView.findViewById(R.id.tvDescription)
            val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = layoutInflater.inflate(R.layout.item_supported_app, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            
            holder.ivIcon.setImageDrawable(item.icon ?: ContextCompat.getDrawable(this@SupportedAppsActivity, R.drawable.ic_accessibility))
            holder.tvAppName.text = item.appName
            holder.tvPackageName.text = item.packageName
            holder.tvDescription.text = item.description
            
            when (item.status) {
                AppStatus.SUPPORTED_INSTALLED -> {
                    holder.tvStatus.text = getString(R.string.status_app_installed)
                    holder.tvStatus.setTextColor(ContextCompat.getColor(this@SupportedAppsActivity, R.color.status_supported))
                    holder.itemView.alpha = 1.0f
                }
                AppStatus.SUPPORTED_NOT_INSTALLED -> {
                    holder.tvStatus.text = getString(R.string.status_app_not_installed)
                    holder.tvStatus.setTextColor(ContextCompat.getColor(this@SupportedAppsActivity, R.color.status_not_installed))
                    holder.itemView.alpha = 0.6f
                }
                else -> {}
            }
            
            // 点击事件
            holder.itemView.setOnClickListener {
                if (item.isInstalled) {
                    // 打开应用
                    val launchIntent = packageManager.getLaunchIntentForPackage(item.packageName)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(launchIntent)
                    }
                } else {
                    // 跳转到应用商店
                    openAppStore(item.packageName)
                }
            }
            
            holder.itemView.setOnLongClickListener {
                openAppStore(item.packageName)
                true
            }
        }

        override fun getItemCount() = items.size
    }
}
