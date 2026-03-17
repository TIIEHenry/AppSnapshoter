package tiiehenry.android.app.snapshot.config;

/**
 * 配置文件名常量类
 * 统一管理所有配置文件的文件名
 */
public class ConfigFiles {
    private ConfigFiles() {
        // 私有构造函数，防止实例化
    }

    public static final String SHOT_CONFIG_FILE = "items.json";
    public static final String EXCLUDE_CONFIG_FILE = "excludes.json";
    public static final String ACTION_CONFIG_FILE = "action.json";
    public static final String EXTRA_CONFIG_FILE = "extra.json";

    // GroupConfig 特有的配置文件名
    public static final String GROUP_CONFIG_FILE = "group.json";
}
