package tiieherny.android.app.snapshotor.config

import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.TypeReference
import com.tencent.mmkv.MMKV

class AppConfig(val packageName: String) {
    private val mmkv: MMKV = MMKV.mmkvWithID("app:$packageName")

    val shotConfig = ShotConfig(mmkv)

    val syncConfig = SyncConfig(mmkv)

    fun getMMKV(): MMKV = mmkv
}
