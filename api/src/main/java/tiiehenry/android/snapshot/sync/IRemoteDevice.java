package tiiehenry.android.snapshot.sync;

import android.os.Parcel;
import android.os.Parcelable;

public class IRemoteDevice implements Parcelable {
    private String deviceId;
    private int sdkInt;
    private String osType;
    private int osVersion;
    private String ip;
    private int port;

    public IRemoteDevice() {
    }

    public IRemoteDevice(String deviceId, int sdkInt, String osType, int osVersion, String ip, int port) {
        this.deviceId = deviceId;
        this.sdkInt = sdkInt;
        this.osType = osType;
        this.osVersion = osVersion;
        this.ip = ip;
        this.port = port;
    }

    protected IRemoteDevice(Parcel in) {
        deviceId = in.readString();
        sdkInt = in.readInt();
        osType = in.readString();
        osVersion = in.readInt();
        ip = in.readString();
        port = in.readInt();
    }

    public static final Creator<IRemoteDevice> CREATOR = new Creator<IRemoteDevice>() {
        @Override
        public IRemoteDevice createFromParcel(Parcel in) {
            return new IRemoteDevice(in);
        }

        @Override
        public IRemoteDevice[] newArray(int size) {
            return new IRemoteDevice[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(deviceId);
        dest.writeInt(sdkInt);
        dest.writeString(osType);
        dest.writeInt(osVersion);
        dest.writeString(ip);
        dest.writeInt(port);
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public int getSdkInt() {
        return sdkInt;
    }

    public void setSdkInt(int sdkInt) {
        this.sdkInt = sdkInt;
    }

    public String getOsType() {
        return osType;
    }

    public void setOsType(String osType) {
        this.osType = osType;
    }

    public int getOsVersion() {
        return osVersion;
    }

    public void setOsVersion(int osVersion) {
        this.osVersion = osVersion;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }
}
