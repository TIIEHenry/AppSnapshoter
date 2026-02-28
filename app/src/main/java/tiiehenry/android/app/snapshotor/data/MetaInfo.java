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
    private List<String> dataItems;

    @JSONField(name = "permissions", serialize = false)
    private List<MetaPermission> permissions;

    @JSONField(name = "makeTime")
    private long makeTime;

    // 无参构造函数
    public MetaInfo() {
    }

    // 全参构造函数
    public MetaInfo(MetaPackageInfo packageInfo, int userId, List<String> dataItems,
                    List<MetaPermission> permissions, long makeTime) {
        this.packageInfo = packageInfo;
        this.userId = userId;
        this.dataItems = dataItems;
        this.permissions = permissions;
        this.makeTime = makeTime;
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

    public List<String> getDataItems() {
        return dataItems;
    }

    public void setDataItems(List<String> dataItems) {
        this.dataItems = dataItems;
    }

    public List<MetaPermission> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<MetaPermission> permissions) {
        this.permissions = permissions;
    }

    public long getMakeTime() {
        return makeTime;
    }

    public void setMakeTime(long makeTime) {
        this.makeTime = makeTime;
    }

    @Override
    public String toString() {
        return "MetaInfo{" +
               "packageInfo=" + packageInfo +
               ", userId=" + userId +
               ", dataItems=" + dataItems +
               ", permissions=" + permissions +
               ", makeTime=" + makeTime +
               '}';
    }
}