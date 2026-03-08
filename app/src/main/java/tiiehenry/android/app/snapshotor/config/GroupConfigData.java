package tiiehenry.android.app.snapshotor.config;

import com.alibaba.fastjson2.JSON;

/**
 * 分组数据配置类
 * 保存在 group.json 文件中
 */
public class GroupConfigData {
    // 用户ID
    public int userId = 0;

    /**
     * 从 JSON 字符串解析配置（静态工厂方法）
     */
    public static GroupConfigData fromJson(String jsonString) {
        return JSON.parseObject(jsonString, GroupConfigData.class);
    }

    /**
     * 将配置转换为 JSON 字符串
     */
    public String toJson() {
        return JSON.toJSONString(this);
    }
}
