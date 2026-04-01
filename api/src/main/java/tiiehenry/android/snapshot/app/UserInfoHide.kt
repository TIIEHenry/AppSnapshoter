package tiiehenry.android.snapshot.app;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * UserInfo 的副本类，用于跨进程传递用户信息
 */
public class UserInfoParcelable implements Parcelable {
    private int id;
    private String name;

    public UserInfoParcelable(int id, String name) {
        this.id = id;
        this.name = name;
    }

    protected UserInfoParcelable(Parcel in) {
        id = in.readInt();
        name = in.readString();
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }


    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(id);
        dest.writeString(name);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<UserInfoParcelable> CREATOR = new Creator<UserInfoParcelable>() {
        @Override
        public UserInfoParcelable createFromParcel(Parcel in) {
            return new UserInfoParcelable(in);
        }

        @Override
        public UserInfoParcelable[] newArray(int size) {
            return new UserInfoParcelable[size];
        }
    };

    @Override
    public String toString() {
        return "UserInfoParcelable{" +
               "id=" + id +
               ", name='" + name + '\'' +
               '}';
    }
}
