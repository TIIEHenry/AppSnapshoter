package tiiehenry.android.app.snapshotor.model

/**
 * 应用包状态枚举
 * 用于表示应用在设备上的安装状态
 */
enum class PackageStatus {
    /**
     * 未安装 - 应用未安装在设备上
     * 显示红色感叹号
     */
    NOT_INSTALLED,

    /**
     * 已安装 - 应用已安装且是最新版本
     * 显示绿色勾号
     */
    INSTALLED,

    /**
     * 可更新 - 应用已安装但有更新版本（存档版本高于已安装版本）
     * 显示蓝色更新图标
     */
    CAN_UPDATE
}
