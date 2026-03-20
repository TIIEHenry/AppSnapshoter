package tiiehenry.android.app.snapshot.data;

import com.alibaba.fastjson2.annotation.JSONField;

/**
 * 数据项信息
 */
public class MetaDataItem {
    @JSONField(name = "algorithm")
    private String algorithm;

    @JSONField(name = "name")
    private String name;

    @JSONField(name = "file")
    private String file;

    @JSONField(name = "origin_size")
    private long originSize;

    @JSONField(name = "target_size")
    private long targetSize;

    @JSONField(name = "md5")
    private String md5;

    @JSONField(name = "package_cost")
    private long packageCost;

    @JSONField(name = "compress_level")
    private int compressLevel;

    @JSONField(name = "makeTime")
    private long makeTime;

    // 无参构造函数
    public MetaDataItem() {
    }

    // 全参构造函数
    public MetaDataItem(String algorithm, String name, String file,
                        long originSize, long targetSize, String md5, long packageCost, long makeTime, int compressLevel) {
        this.algorithm = algorithm;
        this.name = name;
        this.file = file;
        this.originSize = originSize;
        this.targetSize = targetSize;
        this.md5 = md5;
        this.packageCost = packageCost;
        this.makeTime = makeTime;
        this.compressLevel = compressLevel;
    }

    // Getter和Setter方法
    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        this.file = file;
    }

    public long getOriginSize() {
        return originSize;
    }

    public void setOriginSize(long originSize) {
        this.originSize = originSize;
    }

    public long getTargetSize() {
        return targetSize;
    }

    public void setTargetSize(long targetSize) {
        this.targetSize = targetSize;
    }

    public String getMd5() {
        return md5;
    }

    public void setMd5(String md5) {
        this.md5 = md5;
    }

    public long getPackageCost() {
        return packageCost;
    }

    public void setPackageCost(long packageCost) {
        this.packageCost = packageCost;
    }

    public int getCompressLevel() {
        return compressLevel;
    }

    public void setCompressLevel(int compressLevel) {
        this.compressLevel = compressLevel;
    }

    @Override
    public String toString() {
        return "MetaDataItem{" +
               "algorithm='" + algorithm + '\'' +
               ", name='" + name + '\'' +
               ", file='" + file + '\'' +
               ", originSize=" + originSize +
               ", targetSize=" + targetSize +
               ", md5='" + md5 + '\'' +
               ", compressCost=" + packageCost +
               ", makeTime=" + makeTime +
               ", compressLevel=" + compressLevel +
               '}';
    }
}