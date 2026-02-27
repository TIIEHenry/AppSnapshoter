package tiiehenry.android.app.snapshotor.data;

import com.alibaba.fastjson2.annotation.JSONField;
import java.util.List;

/**
 * 存档元信息数据类
 * 对应 meta-info.json 文件结构
 */
public class MetaInfo {
    @JSONField(name = "packageInfo")
    private MetaPackageInfo packageInfo;

    @JSONField(name = "userId")
    private int userId;

    @JSONField(name = "dataItems")
    private List<MetaDataItem> dataItems;

    @JSONField(name = "permissions")
    private List<MetaPermission> permissions;

    @JSONField(name = "time")
    private TimeInfo time;

    // 无参构造函数
    public MetaInfo() {
    }

    // 全参构造函数
    public MetaInfo(MetaPackageInfo packageInfo, int userId, List<MetaDataItem> dataItems,
                    List<MetaPermission> permissions, TimeInfo time) {
        this.packageInfo = packageInfo;
        this.userId = userId;
        this.dataItems = dataItems;
        this.permissions = permissions;
        this.time = time;
    }

    // Getter和Setter方法
    public MetaPackageInfo getPackageInfo() {
        return packageInfo;
    }

    public void setPackageInfo(MetaPackageInfo packageInfo) {
        this.packageInfo = packageInfo;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public List<MetaDataItem> getDataItems() {
        return dataItems;
    }

    public void setDataItems(List<MetaDataItem> dataItems) {
        this.dataItems = dataItems;
    }

    public List<MetaPermission> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<MetaPermission> permissions) {
        this.permissions = permissions;
    }

    public TimeInfo getTime() {
        return time;
    }

    public void setTime(TimeInfo time) {
        this.time = time;
    }

    @Override
    public String toString() {
        return "MetaInfo{" +
                "packageInfo=" + packageInfo +
                ", userId=" + userId +
                ", dataItems=" + dataItems +
                ", permissions=" + permissions +
                ", time=" + time +
                '}';
    }
}