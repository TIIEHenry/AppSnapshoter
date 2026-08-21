package tiiehenry.android.app.snapshot.config;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * 分组集配置，保存在 groupset.json。
 * groupOrder 存直接子目录 basename（可同步），禁止写入本机 UUID。
 */
public class GroupSetConfigData {
    public String name;

    public List<String> groupOrder = new ArrayList<>();

    /** ARGB hex，如 #FF0078D4；缺省时本机按 setId 选默认色 */
    public String accentColor;

    public static GroupSetConfigData fromJson(String jsonString) {
        GroupSetConfigData data = JSON.parseObject(jsonString, GroupSetConfigData.class);
        if (data == null) {
            return new GroupSetConfigData();
        }
        if (data.groupOrder == null) {
            data.groupOrder = new ArrayList<>();
        }
        return data;
    }

    public String toJson() {
        return JSON.toJSONString(this, JSONWriter.Feature.PrettyFormat);
    }
}
