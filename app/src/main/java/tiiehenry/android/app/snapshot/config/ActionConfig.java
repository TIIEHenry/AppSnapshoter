package tiiehenry.android.app.snapshot.config;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;

/**
 * 动作配置类
 * 管理自动存档和打包算法配置
 */
public class ActionConfig {
    public boolean enabled = false; // 是否启用应用单独配置
    // 配置字段
    private boolean autoSnapshot = false;
    private boolean uninstallArchived = false;
    private String compressAlgorithm = "";
    private int compressLevel = 5; // 压缩级别：1 极快，3 快，5 平衡，7 略慢，9 极慢

    /**
     * 默认构造函数
     */
    public ActionConfig() {
    }

    /**
     * 从 JSON 字符串解析配置（静态工厂方法）
     */
    public static ActionConfig fromJson(String jsonString) {
        return JSON.parseObject(jsonString, ActionConfig.class);
    }

    /**
     * 将配置转换为 JSON 字符串
     */
    public String toJson() {
        return JSON.toJSONString(this, JSONWriter.Feature.PrettyFormat);
    }

    /**
     * 是否有压缩算法配置
     */
    public boolean hasCompressAlgorithm() {
        return compressAlgorithm != null && !compressAlgorithm.isEmpty();
    }

    // Getter 和 Setter 方法
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isAutoSnapshot() {
        return autoSnapshot;
    }

    public void setAutoSnapshot(boolean autoSnapshot) {
        this.autoSnapshot = autoSnapshot;
    }

    public boolean isUninstallArchived() {
        return uninstallArchived;
    }

    public void setUninstallArchived(boolean uninstallArchived) {
        this.uninstallArchived = uninstallArchived;
    }

    public String getCompressAlgorithm() {
        return compressAlgorithm;
    }

    public void setCompressAlgorithm(String compressAlgorithm) {
        this.compressAlgorithm = compressAlgorithm;
    }

    public int getCompressLevel() {
        return compressLevel;
    }

    public void setCompressLevel(int compressLevel) {
        this.compressLevel = compressLevel;
    }
}
