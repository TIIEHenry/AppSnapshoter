package tiiehenry.android.app.snapshot.archive.bean;

import com.alibaba.fastjson2.annotation.JSONField;

import tiiehenry.android.snapshot.app.AppPermission;

/**
 * 权限信息
 */
public class MetaPermission {
    @JSONField(name = "isGranted")
    private boolean isGranted;

    @JSONField(name = "mode")
    private int mode;

    @JSONField(name = "name")
    private String name;

    @JSONField(name = "op")
    private int op;

    // 无参构造函数
    public MetaPermission() {
    }

    // 全参构造函数
    public MetaPermission(boolean isGranted, int mode, String name, int op) {
        this.isGranted = isGranted;
        this.mode = mode;
        this.name = name;
        this.op = op;
    }

    // Getter和Setter方法
    public boolean isGranted() {
        return isGranted;
    }

    public void setGranted(boolean granted) {
        isGranted = granted;
    }

    public int getMode() {
        return mode;
    }

    public void setMode(int mode) {
        this.mode = mode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getOp() {
        return op;
    }

    public void setOp(int op) {
        this.op = op;
    }

    @Override
    public String toString() {
        return "MetaPermission{" +
                "isGranted=" + isGranted +
                ", mode=" + mode +
                ", name='" + name + '\'' +
                ", op=" + op +
                '}';
    }

    /**
     * 从 AppPermission 转换为 MetaPermission
     * 注意：这里需要AppPermission类的Java版本才能实现完整的转换逻辑
     */
    public static MetaPermission fromAppPermission(AppPermission appPermission) {
        return new MetaPermission(
            appPermission.isGranted(),
            appPermission.getMode(),
            appPermission.getName(),
            appPermission.getOp()
        );
    }
}