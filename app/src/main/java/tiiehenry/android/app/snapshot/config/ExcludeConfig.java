package tiiehenry.android.app.snapshot.config;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.TypeReference;
import com.alibaba.fastjson2.annotation.JSONField;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 排除模式配置类
 * 管理按压缩项目分类的排除模式
 */
public class ExcludeConfig {
    // 按压缩项目分类的排除模式映射
    // key: 压缩项目类型 (如 "data", "user" 等)
    // value: 该压缩项目的排除模式列表
    private Map<String, List<String>> itemExcludePatterns = new HashMap<>();

    /**
     * 从 JSON 字符串解析配置（静态工厂方法）
     */
    public static ExcludeConfig fromJson(String jsonString) {
        Map<String, List<String>> patterns = JSON.parseObject(jsonString, new TypeReference<Map<String, List<String>>>() {});
        ExcludeConfig config = new ExcludeConfig();
        config.setItemExcludePatternsMap(patterns);
        return config;
    }

    /**
     * 将配置转换为 JSON 字符串
     */
    public String toJson() {
        return JSON.toJSONString(itemExcludePatterns, JSONWriter.Feature.PrettyFormat);
    }

    /**
     * 获取按压缩项目分类的排除模式映射
     * @return Map<压缩项目类型，排除模式列表>
     */
    public Map<String, List<String>> getItemExcludePatternsMap() {
        if (itemExcludePatterns == null) {
            return new HashMap<>();
        }
        return new HashMap<>(itemExcludePatterns);
    }

    /**
     * 设置按压缩项目分类的排除模式映射
     * @param patterns Map<压缩项目类型，排除模式列表>
     */
    public void setItemExcludePatternsMap(Map<String, List<String>> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            this.itemExcludePatterns = new HashMap<>();
        } else {
            this.itemExcludePatterns = new HashMap<>(patterns);
        }
    }

    /**
     * 获取指定压缩项目的排除模式列表
     * @param compressItem 压缩项目类型 (如 CompressItems.COMPRESS_ITEM_DATA)
     * @return 该压缩项目的排除模式列表
     */
    public List<String> getExcludePatternsForItem(String compressItem) {
        Map<String, List<String>> map = getItemExcludePatternsMap();
        return map.getOrDefault(compressItem, new ArrayList<>());
    }

    /**
     * 设置指定压缩项目的排除模式列表
     * @param compressItem 压缩项目类型 (如 CompressItems.COMPRESS_ITEM_DATA)
     * @param patterns 排除模式列表
     */
    public void setExcludePatternsForItem(String compressItem, List<String> patterns) {
        Map<String, List<String>> map = getItemExcludePatternsMap();
        if (patterns == null || patterns.isEmpty()) {
            map.remove(compressItem);
        } else {
            map.put(compressItem, new ArrayList<>(patterns));
        }
        setItemExcludePatternsMap(map);
    }

    /**
     * 获取所有包含排除模式的压缩项目类型
     * @return 压缩项目类型集合
     */
    @JSONField(serialize = false, deserialize = false)
    public Set<String> getCompressItemsWithExcludes() {
        return getItemExcludePatternsMap().keySet();
    }

    /**
     * 获取所有排除模式（合并所有压缩项目的排除模式）
     * @return 所有排除模式的列表
     */
    @JSONField(serialize = false, deserialize = false)
    public List<String> getAllExcludePatterns() {
        Map<String, List<String>> map = getItemExcludePatternsMap();
        List<String> allPatterns = new ArrayList<>();
        for (List<String> patterns : map.values()) {
            allPatterns.addAll(patterns);
        }
        return allPatterns;
    }

    /**
     * 是否有排除模式配置
     */
    @JSONField(serialize = false, deserialize = false)
    public boolean hasExcludePatterns() {
        return !getItemExcludePatternsMap().isEmpty();
    }

    /**
     * 清除所有排除模式
     */
    public void clearExcludePatterns() {
        this.itemExcludePatterns = new HashMap<>();
    }

    /**
     * 复制当前对象
     */
    public ExcludeConfig copy() {
        ExcludeConfig copy = new ExcludeConfig();
        copy.setItemExcludePatternsMap(this.getItemExcludePatternsMap());
        return copy;
    }
}
