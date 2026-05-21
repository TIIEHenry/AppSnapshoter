package tiiehenry.android.app.snapshot.archive.bean;

import com.alibaba.fastjson2.annotation.JSONField;

/**
 * 包信息
 */
public class MetaPackageInfo {
    @JSONField(name = "label")
    private String label;

    @JSONField(name = "packageName")
    private String packageName;

    @JSONField(name = "versionCode")
    private long versionCode;

    @JSONField(name = "versionName")
    private String versionName;

    @JSONField(name = "firstInstallTime")
    private long firstInstallTime;

    @JSONField(name = "flags")
    private int flags;

    @JSONField(name = "lastUpdateTime")
    private long lastUpdateTime;

    @JSONField(name = "size")
    private long size;

    // 无参构造函数
    public MetaPackageInfo() {
    }

    // 全参构造函数
    public MetaPackageInfo(String label, String packageName, long versionCode, String versionName,
                          long firstInstallTime, int flags, long lastUpdateTime, long size) {
        this.label = label;
        this.packageName = packageName;
        this.versionCode = versionCode;
        this.versionName = versionName;
        this.firstInstallTime = firstInstallTime;
        this.flags = flags;
        this.lastUpdateTime = lastUpdateTime;
        this.size = size;
    }

    // Getter和Setter方法
    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public long getVersionCode() {
        return versionCode;
    }

    public void setVersionCode(long versionCode) {
        this.versionCode = versionCode;
    }

    public String getVersionName() {
        return versionName;
    }

    public void setVersionName(String versionName) {
        this.versionName = versionName;
    }

    public long getFirstInstallTime() {
        return firstInstallTime;
    }

    public void setFirstInstallTime(long firstInstallTime) {
        this.firstInstallTime = firstInstallTime;
    }

    public int getFlags() {
        return flags;
    }

    public void setFlags(int flags) {
        this.flags = flags;
    }

    public long getLastUpdateTime() {
        return lastUpdateTime;
    }

    public void setLastUpdateTime(long lastUpdateTime) {
        this.lastUpdateTime = lastUpdateTime;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    @Override
    public String toString() {
        return "MetaPackageInfo{" +
                "label='" + label + '\'' +
                ", packageName='" + packageName + '\'' +
                ", versionCode=" + versionCode +
                ", versionName='" + versionName + '\'' +
                ", firstInstallTime=" + firstInstallTime +
                ", flags=" + flags +
                ", lastUpdateTime=" + lastUpdateTime +
                ", size=" + size +
                '}';
    }
}