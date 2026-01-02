package tiieherny.android.app.snapshotor.main.apps

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import tiieherny.android.app.snapshotor.app.AppInfo

class AppsViewModel : ViewModel() {

    val filteredAppList = MutableLiveData<List<AppInfo>>()
    private var allApps: List<AppInfo> = emptyList()

    fun setAppList(apps: List<AppInfo>) {
        allApps = apps
        filteredAppList.value = apps
    }

    fun filterApps(query: String) {
        if (query.isEmpty()) {
            filteredAppList.value = allApps
        } else {
            filteredAppList.value = allApps.filter { 
                it.label.contains(query, ignoreCase = true) || 
                it.packageName.contains(query, ignoreCase = true)
            }
        }
    }
}
