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
    // 额外压缩项目列表
    private List<ExtraCompressItem> items = new ArrayList<>();


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
        if (items == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(items);
    }

    /**
     * 设置额外压缩项目列表
     */
    public void setItems(List<ExtraCompressItem> items) {
        if (items == null || items.isEmpty()) {
            this.items = new ArrayList<>();
        } else {
            this.items = new ArrayList<>(items);
        }
    }

    /**
     * 是否有配置项
     */
    @JSONField(serialize = false, deserialize = false)
    public boolean hasItems() {
        return items != null && !items.isEmpty();
    }

    /**
     * 清除所有配置项
     */
    public void clear() {
        this.items = new ArrayList<>();
    }
}
