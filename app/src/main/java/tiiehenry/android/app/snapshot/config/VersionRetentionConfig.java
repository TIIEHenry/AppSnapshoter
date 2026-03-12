package tiiehenry.android.app.snapshot.config;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;

/**
 * 版本保留配置类
 * 用于控制存档版本的保留策略
 */
public class VersionRetentionConfig {
    // 是否启用版本保留单独控制
    public boolean enabled = false;

    // 条件A: 最大保留版本数 (null 或 0 表示不限制)
    public Integer maxVersionCount = null;
    
    // 条件B: 同版本最低保留数 (null 或 0 表示不限制)
    public Integer minSameVersionCount = null;
    
    // 条件C: 额外保留数量
    public Integer extraRetentionCount = null;
    
    // 条件C: 额外保留过期天数 (0 表示不过期)
    public Integer extraRetentionDays = 0;

    /**
     * 从 JSON 字符串解析配置
     */
    public static VersionRetentionConfig fromJson(String jsonString) {
        if (jsonString == null || jsonString.isEmpty()) {
            return new VersionRetentionConfig();
        }
        return JSON.parseObject(jsonString, VersionRetentionConfig.class);
    }

    /**
     * 将配置转换为 JSON 字符串
     */
    public String toJson() {
        return JSON.toJSONString(this, JSONWriter.Feature.PrettyFormat);
    }

    /**
     * 是否启用最大版本数限制
     */
    public boolean isMaxVersionCountEnabled() {
        return maxVersionCount != null && maxVersionCount > 0;
    }

    /**
     * 是否启用同版本最低保留限制
     */
    public boolean isMinSameVersionCountEnabled() {
        return minSameVersionCount != null && minSameVersionCount > 0;
    }

    /**
     * 是否启用额外保留
     */
    public boolean isExtraRetentionEnabled() {
        return extraRetentionCount != null && extraRetentionCount > 0;
    }

    /**
     * 额外保留是否有过期时间
     */
    public boolean hasExtraRetentionExpiry() {
        return extraRetentionDays != null && extraRetentionDays > 0;
    }

    /**
     * 创建默认配置
     */
    public static VersionRetentionConfig createDefault() {
        return new VersionRetentionConfig();
    }

    /**
     * 重置为默认配置
     */
    public void reset() {
        enabled = false;
        maxVersionCount = null;
        minSameVersionCount = null;
        extraRetentionCount = null;
        extraRetentionDays = 0;
    }
}
