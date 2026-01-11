package tiiehenry.android.shapshotor.app;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

/**
 * 应用权限信息
 */
public class AppPermission implements Parcelable {
    private boolean isGranted;
    private int mode;
    private String name;
    private int op;

    public AppPermission() {
    }

    public AppPermission(boolean isGranted, int mode, String name, int op) {
        this.isGranted = isGranted;
        this.mode = mode;
        this.name = name;
        this.op = op;
    }

    protected AppPermission(Parcel in) {
        isGranted = in.readByte() != 0;
        mode = in.readInt();
        name = in.readString();
        op = in.readInt();
    }

    public static final Creator<AppPermission> CREATOR = new Creator<AppPermission>() {
        @Override
        public AppPermission createFromParcel(Parcel in) {
            return new AppPermission(in);
        }

        @Override
        public AppPermission[] newArray(int size) {
            return new AppPermission[size];
        }
    };

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
    public int describeContents() {
        return 0;
    }


    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeByte((byte) (isGranted ? 1 : 0));
        dest.writeInt(mode);
        dest.writeString(name);
        dest.writeInt(op);
    }

    /**
     * 深度拷贝方法，创建当前对象的副本
     *
     * @return 当前对象的深拷贝副本
     */
    public AppPermission clone() {
        return new AppPermission(this.isGranted, this.mode, this.name, this.op);
    }

}
