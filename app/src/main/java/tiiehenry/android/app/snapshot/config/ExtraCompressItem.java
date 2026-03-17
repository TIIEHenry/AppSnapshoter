package tiiehenry.android.app.snapshot.config;

import com.alibaba.fastjson2.annotation.JSONField;

import java.util.ArrayList;
import java.util.List;

/**
 * 额外压缩项目
 */
public class ExtraCompressItem {
    private String name;
    private String path;
    private List<String> excludePatterns;
    private boolean enabled;

    /**
     * 默认构造函数
     */
    public ExtraCompressItem() {
        this.name = "";
        this.path = "";
        this.excludePatterns = new ArrayList<>();
        this.enabled = true;
    }

    /**
     * 带参构造函数
     */
    public ExtraCompressItem(String name, String path, List<String> excludePatterns, boolean enabled) {
        this.name = name;
        this.path = path;
        this.excludePatterns = excludePatterns != null ? new ArrayList<>(excludePatterns) : new ArrayList<>();
        this.enabled = enabled;
    }

    /**
     * 获取 name
     */
    public String getName() {
        return name;
    }

    /**
     * 设置 name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * 获取 path
     */
    public String getPath() {
        return path;
    }

    /**
     * 设置 path
     */
    public void setPath(String path) {
        this.path = path;
    }

    /**
     * 获取 excludePatterns
     */
    public List<String> getExcludePatterns() {
        if (excludePatterns == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(excludePatterns);
    }

    /**
     * 设置 excludePatterns
     */
    public void setExcludePatterns(List<String> excludePatterns) {
        if (excludePatterns == null || excludePatterns.isEmpty()) {
            this.excludePatterns = new ArrayList<>();
        } else {
            this.excludePatterns = new ArrayList<>(excludePatterns);
        }
    }

    /**
     * 获取 enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置 enabled
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 复制当前对象
     */
    public ExtraCompressItem copy() {
        return new ExtraCompressItem(
            this.name,
            this.path,
            this.excludePatterns != null ? new ArrayList<>(this.excludePatterns) : new ArrayList<>(),
            this.enabled
        );
    }
}
