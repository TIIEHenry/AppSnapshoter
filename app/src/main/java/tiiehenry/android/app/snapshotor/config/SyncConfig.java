package tiiehenry.android.app.snapshotor.config;

import com.alibaba.fastjson2.JSON;

import java.util.HashSet;
import java.util.Set;

/**
 * 同步配置类
 * 纯数据类，使用 fromJson/toJson 进行序列化/反序列化
 */
public class SyncConfig {
    // 配置字段，直接访问
    public boolean enableSyncToTarget = false;
    public Set<String> syncTargets = new HashSet<>();
    public boolean enableSyncToSystem = false;
    public Set<String> syncSystems = new HashSet<>();
    public int syncType = 0;

    /**
     * 从 JSON 字符串解析配置（静态工厂方法）
     */
    public static SyncConfig fromJson(String jsonString) {
        return JSON.parseObject(jsonString, SyncConfig.class);
    }

    /**
     * 将配置转换为 JSON 字符串
     */
    public String toJson() {
        return JSON.toJSONString(this);
    }
}
