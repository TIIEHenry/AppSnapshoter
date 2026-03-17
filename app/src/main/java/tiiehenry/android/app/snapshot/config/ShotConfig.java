package tiiehenry.android.app.snapshot.config;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;

import java.util.HashSet;
import java.util.Set;


/**
 * 快照配置类
 * 纯数据类，使用 fromJson/toJson 进行序列化/反序列化
 */
public class ShotConfig {
    // 配置字段，直接访问
    public boolean enabled = false; // 是否启用应用单独配置
    public Set<String> compressItems = new HashSet<>(CompressItems.getAll());
    public boolean permission = true;

    // 版本保留配置
    public VersionRetentionConfig versionRetention = new VersionRetentionConfig();

    /**
     * 从 JSON 字符串解析配置（静态工厂方法）
     */
    public static ShotConfig fromJson(String jsonString) {
        return JSON.parseObject(jsonString, ShotConfig.class);
    }

    /**
     * 将配置转换为 JSON 字符串
     */
    public String toJson() {
        return JSON.toJSONString(this, JSONWriter.Feature.PrettyFormat);
    }

    /**
     * 是否有压缩项配置
     */
    public boolean hasCompressItems() {
        return compressItems != null && !compressItems.isEmpty();
    }

    /**
     * 获取版本保留配置对象 (兼容旧代码，直接返回对象)
     */
    public VersionRetentionConfig getVersionRetentionConfig() {
        return versionRetention != null ? versionRetention : new VersionRetentionConfig();
    }

    /**
     * 设置版本保留配置对象
     */
    public void setVersionRetentionConfig(VersionRetentionConfig config) {
        this.versionRetention = config != null ? config : new VersionRetentionConfig();
    }
}
