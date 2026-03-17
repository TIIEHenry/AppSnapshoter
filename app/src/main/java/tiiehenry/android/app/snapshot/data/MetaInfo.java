package tiiehenry.android.app.snapshot.data;

import com.alibaba.fastjson2.annotation.JSONField;

import java.util.List;
import java.util.Map;

/**
 * 存档元信息数据类
 * 对应 meta-info.json 文件结构
 */
public class MetaInfo {
    @JSONField(name = "packageInfo")
    private MetaPackageInfo packageInfo;

    @JSONField(name = "userId")
    private int userId;

    @JSONField(name = "ssaid")
    private String ssaid;

    @JSONField(name = "dataItems")
    private List<String> dataItems;


    @JSONField(name = "extraItems")
    private Map<String, String> extraItems;

    @JSONField(name = "permissions", serialize = false)
    private List<MetaPermission> permissions;

    @JSONField(name = "makeTime")
    private long makeTime;

    @JSONField(name = "locked")
    private boolean locked;

    // 无参构造函数
    public MetaInfo() {
    }

    // 全参构造函数
    public MetaInfo(MetaPackageInfo packageInfo, int userId, String ssaid, List<String> dataItems,
                    Map<String, String> extraItems, List<MetaPermission> permissions, long makeTime, boolean locked) {
        this.packageInfo = packageInfo;
        this.userId = userId;
        this.ssaid = ssaid;
        this.dataItems = dataItems;
        this.extraItems = extraItems;
        this.permissions = permissions;
        this.makeTime = makeTime;
        this.locked = locked;
    }

    // 兼容旧的构造函数（不含 locked）
    public MetaInfo(MetaPackageInfo packageInfo, int userId, String ssaid, List<String> dataItems,
                    List<MetaPermission> permissions, long makeTime) {
        this(packageInfo, userId, ssaid, dataItems, null, permissions, makeTime, false);
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

    public String getSsaid() {
        return ssaid;
    }

    public void setSsaid(String ssaid) {
        this.ssaid = ssaid;
    }

    public List<String> getDataItems() {
        return dataItems;
    }

    public void setDataItems(List<String> dataItems) {
        this.dataItems = dataItems;
    }

    public Map<String, String> getExtraItems() {
        return extraItems;
    }

    public void setExtraItems(Map<String, String> extraItems) {
        this.extraItems = extraItems;
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

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    @Override
    public String toString() {
        return "MetaInfo{" +
               "packageInfo=" + packageInfo +
               ", userId=" + userId +
               ", ssaid='" + ssaid + '\'' +
               ", dataItems=" + dataItems +
               ", extraItems=" + extraItems +
               ", permissions=" + permissions +
               ", makeTime=" + makeTime +
               ", locked=" + locked +
               '}';
    }
}