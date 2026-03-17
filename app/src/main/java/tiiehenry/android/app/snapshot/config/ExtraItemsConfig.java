package tiiehenry.android.app.snapshot.config;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.annotation.JSONField;
import com.alibaba.fastjson2.JSONWriter;

import java.util.ArrayList;
import java.util.List;

/**
 * 额外压缩项目配置
 */
public class ExtraItemsConfig {
    // 额外压缩项目列表 (JSON 字符串)
    private String itemsJson = "";

    /**
     * 从 JSON 字符串解析配置（静态工厂方法）
     */
    public static ExtraItemsConfig fromJson(String jsonString) {
        return JSON.parseObject(jsonString, ExtraItemsConfig.class);
    }

    /**
     * 将配置转换为 JSON 字符串
     */
    public String toJson() {
        return JSON.toJSONString(this, JSONWriter.Feature.PrettyFormat);
    }

    /**
     * 获取额外压缩项目列表
     */
    public List<ExtraCompressItem> getItems() {
        if (itemsJson == null || itemsJson.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return JSON.parseArray(itemsJson, ExtraCompressItem.class);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * 设置额外压缩项目列表
     */
    public void setItems(List<ExtraCompressItem> items) {
        if (items == null || items.isEmpty()) {
            this.itemsJson = "";
        } else {
            this.itemsJson = JSON.toJSONString(items);
        }
    }

    /**
     * 是否有配置项
     */
    @JSONField(serialize = false, deserialize = false)
    public boolean hasItems() {
        return itemsJson != null && !itemsJson.isEmpty();
    }

    /**
     * 清除所有配置项
     */
    public void clear() {
        this.itemsJson = "";
    }

    /**
     * 获取 itemsJson
     */
    public String getItemsJson() {
        return itemsJson;
    }

    /**
     * 设置 itemsJson
     */
    public void setItemsJson(String itemsJson) {
        this.itemsJson = itemsJson;
    }
}
