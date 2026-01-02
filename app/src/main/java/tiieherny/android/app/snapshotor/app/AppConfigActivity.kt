package tiieherny.android.app.snapshotor.app

import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import tiieherny.android.app.snapshotor.R
import tiieherny.android.app.snapshotor.config.AppConfig

class AppConfigActivity : AppCompatActivity() {

    private lateinit var appConfig: AppConfig
    private lateinit var packageName: String

    private lateinit var cbApk: CheckBox
    private lateinit var cbData: CheckBox
    private lateinit var cbUser: CheckBox
    private lateinit var cbCache: CheckBox
    private lateinit var cbUserDe: CheckBox
    private lateinit var cbObb: CheckBox
    private lateinit var cbExternalData: CheckBox
    private lateinit var cbPermission: CheckBox
    private lateinit var cbEnableSyncToTarget: CheckBox
    private lateinit var cbEnableSyncToSystem: CheckBox
    private lateinit var etCompressAlgorithm: EditText
    private lateinit var etSyncType: EditText
    private lateinit var btnSave: Button
    private lateinit var btnReset: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_config)

        packageName = intent.getStringExtra("packageName") ?: ""
        if (packageName.isEmpty()) {
            finish()
            return
        }

        appConfig = AppConfig(packageName)
        
        title = "配置 - $packageName"

        initViews()
        loadConfig()
        setupListeners()
    }

    private fun initViews() {
        cbApk = findViewById(R.id.cb_apk)
        cbData = findViewById(R.id.cb_data)
        cbUser = findViewById(R.id.cb_user)
        cbCache = findViewById(R.id.cb_cache)
        cbUserDe = findViewById(R.id.cb_user_de)
        cbObb = findViewById(R.id.cb_obb)
        cbExternalData = findViewById(R.id.cb_external_data)
        cbPermission = findViewById(R.id.cb_permission)
        cbEnableSyncToTarget = findViewById(R.id.cb_enable_sync_to_target)
        cbEnableSyncToSystem = findViewById(R.id.cb_enable_sync_to_system)
        etCompressAlgorithm = findViewById(R.id.et_compress_algorithm)
        etSyncType = findViewById(R.id.et_sync_type)
        btnSave = findViewById(R.id.btn_save)
        btnReset = findViewById(R.id.btn_reset)
    }

    private fun loadConfig() {
        // 加载压缩项
        val compressItems = appConfig.compressItems ?: setOf()
        cbApk.isChecked = compressItems.contains("apk")
        cbData.isChecked = compressItems.contains("data")
        cbUser.isChecked = compressItems.contains("user")
        cbCache.isChecked = compressItems.contains("cache")
        cbUserDe.isChecked = compressItems.contains("user_de")
        cbObb.isChecked = compressItems.contains("obb")
        cbExternalData.isChecked = compressItems.contains("external_data")

        // 加载其他配置
        etCompressAlgorithm.setText(appConfig.compressAlgorithm ?: "")
        cbPermission.isChecked = appConfig.permission ?: false
        cbEnableSyncToTarget.isChecked = appConfig.enableSyncToTarget ?: false
        cbEnableSyncToSystem.isChecked = appConfig.enableSyncToSystem ?: false
        etSyncType.setText(appConfig.syncType?.toString() ?: "")
    }

    private fun setupListeners() {
        btnSave.setOnClickListener {
            saveConfig()
        }

        btnReset.setOnClickListener {
            resetConfig()
        }
    }

    private fun saveConfig() {
        // 保存压缩项
        val compressItems = mutableSetOf<String>()
        if (cbApk.isChecked) compressItems.add("apk")
        if (cbData.isChecked) compressItems.add("data")
        if (cbUser.isChecked) compressItems.add("user")
        if (cbCache.isChecked) compressItems.add("cache")
        if (cbUserDe.isChecked) compressItems.add("user_de")
        if (cbObb.isChecked) compressItems.add("obb")
        if (cbExternalData.isChecked) compressItems.add("external_data")
        
        appConfig.compressItems = if (compressItems.isNotEmpty()) compressItems else null

        // 保存其他配置
        val algorithm = etCompressAlgorithm.text.toString()
        appConfig.compressAlgorithm = if (algorithm.isNotEmpty()) algorithm else null

        appConfig.permission = if (cbPermission.isChecked) true else null
        appConfig.enableSyncToTarget = if (cbEnableSyncToTarget.isChecked) true else null
        appConfig.enableSyncToSystem = if (cbEnableSyncToSystem.isChecked) true else null

        val syncTypeText = etSyncType.text.toString()
        appConfig.syncType = if (syncTypeText.isNotEmpty()) {
            try {
                syncTypeText.toInt()
            } catch (e: NumberFormatException) {
                null
            }
        } else {
            null
        }

        Toast.makeText(this, "配置已保存", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun resetConfig() {
        // 清除所有配置
        appConfig.compressItems = null
        appConfig.compressAlgorithm = null
        appConfig.permission = null
        appConfig.enableSyncToTarget = null
        appConfig.enableSyncToSystem = null
        appConfig.syncType = null

        Toast.makeText(this, "配置已重置", Toast.LENGTH_SHORT).show()
        loadConfig()
    }
}
