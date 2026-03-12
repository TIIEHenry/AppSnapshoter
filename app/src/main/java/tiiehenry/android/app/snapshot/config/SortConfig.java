package tiiehenry.android.app.snapshot.config;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;

import java.util.ArrayList;
import java.util.List;

/**
 * 排序配置类
 * 纯数据类，使用 fromJson/toJson 进行序列化/反序列化
 */
public class SortConfig {
    // 排序类型常量
    public static final int SORT_TYPE_DEFAULT = 0;      // 默认排序
    public static final int SORT_TYPE_NAME_ASC = 1;     // 按名称排序（升序）
    public static final int SORT_TYPE_NAME_DESC = 2;    // 按名称排序（降序）
    public static final int SORT_TYPE_CUSTOM = 3;       // 自定义排序
    public static final int SORT_TYPE_INSTALL_TIME_ASC = 4;  // 按安装时间排序（升序）
    public static final int SORT_TYPE_INSTALL_TIME_DESC = 5; // 按安装时间排序（降序）

    // 配置字段，直接访问
    public int sortType = SORT_TYPE_DEFAULT;
    public List<String> sortOrder = new ArrayList<>();

    /**
     * 从 JSON 字符串解析配置（静态工厂方法）
     */
    public static SortConfig fromJson(String jsonString) {
        return JSON.parseObject(jsonString, SortConfig.class);
    }

    /**
     * 将配置转换为 JSON 字符串
     */
    public String toJson() {
        return JSON.toJSONString(this, JSONWriter.Feature.PrettyFormat);
    }
}
