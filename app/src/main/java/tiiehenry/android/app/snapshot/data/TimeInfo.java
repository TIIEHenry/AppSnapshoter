package tiiehenry.android.app.snapshot.data;

import com.alibaba.fastjson2.annotation.JSONField;

/**
 * 时间信息
 */
public class TimeInfo {
    @JSONField(name = "compressCost")
    private long compressCost;

    @JSONField(name = "makeTime")
    private long makeTime;

    // 无参构造函数
    public TimeInfo() {
    }

    // 全参构造函数
    public TimeInfo(long compressCost, long makeTime) {
        this.compressCost = compressCost;
        this.makeTime = makeTime;
    }

    // Getter和Setter方法
    public long getCompressCost() {
        return compressCost;
    }

    public void setCompressCost(long compressCost) {
        this.compressCost = compressCost;
    }

    public long getMakeTime() {
        return makeTime;
    }

    public void setMakeTime(long makeTime) {
        this.makeTime = makeTime;
    }

    @Override
    public String toString() {
        return "TimeInfo{" +
                "compressCost=" + compressCost +
                ", makeTime=" + makeTime +
                '}';
    }
}