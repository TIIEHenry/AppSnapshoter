package tiiehenry.android.app.snapshotor.config;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


/**
 * 快照配置类
 * 纯数据类，使用 fromJson/toJson 进行序列化/反序列化
 */
public class ShotConfig {
    // 配置字段，直接访问
    public String compressAlgorithm = "";
    public Set<String> compressItems = new HashSet<>(CompressItems.getAll());
    public boolean permission = true;
    public String extraItems = "";
    public boolean autoSnapshot = false;
    public boolean uninstallArchived = false;

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
        return JSON.toJSONString(this);
    }

    /**
     * 获取解析后的额外压缩项列表
     */
    public List<ExtraCompressItem> getExtraItemsList() {
        if (extraItems == null || extraItems.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return JSON.parseObject(extraItems, new TypeReference<List<ExtraCompressItem>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * 设置额外压缩项列表
     */
    public void setExtraItemsList(List<ExtraCompressItem> items) {
        this.extraItems = JSON.toJSONString(items);
    }

    /**
     * 是否有压缩算法配置
     */
    public boolean hasCompressAlgorithm() {
        return compressAlgorithm != null && !compressAlgorithm.isEmpty();
    }

    /**
     * 是否有压缩项配置
     */
    public boolean hasCompressItems() {
        return compressItems != null && !compressItems.isEmpty();
    }
}
