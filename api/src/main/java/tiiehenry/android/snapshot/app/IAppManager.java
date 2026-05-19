package tiiehenry.android.snapshot.app;

/**
 * 应用管理接口 - 组合了包管理和权限管理
 *
 * 建议新代码直接使用 {@link IPackageManager} 和 {@link IPermissionManager}，
 * 以获得更清晰的领域分离。
 *
 * @deprecated 考虑按领域使用 {@link IPackageManager} 或 {@link IPermissionManager}
 */
public interface IAppManager extends IPackageManager, IPermissionManager {
}
