package tv.formuler.service.gtv.networkstorage;

import android.os.Parcel;
import android.os.Parcelable;

public class NetStorageDbInfo implements Parcelable {
    public static final String MOUNT_BASE_DIR = "/mnt/cifs/";
    private int id;
    private String name;
    private String url;
    private String username;
    private String password;
    private int isReconnect;
    private String networkInfo;
    private String reserve1;
    private int isMounted;

    public NetStorageDbInfo() {

    }

    protected NetStorageDbInfo(Parcel in) {
        id = in.readInt();
        name = in.readString();
        url = in.readString();
        username = in.readString();
        password = in.readString();
        isReconnect = in.readInt();
        networkInfo = in.readString();
        reserve1 = in.readString();
        isMounted = in.readInt();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(id);
        dest.writeString(name);
        dest.writeString(url);
        dest.writeString(username);
        dest.writeString(password);
        dest.writeInt(isReconnect);
        dest.writeString(networkInfo);
        dest.writeString(reserve1);
        dest.writeInt(isMounted);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<NetStorageDbInfo> CREATOR = new Creator<NetStorageDbInfo>() {
        @Override
        public NetStorageDbInfo createFromParcel(Parcel in) {
            return new NetStorageDbInfo(in);
        }

        @Override
        public NetStorageDbInfo[] newArray(int size) {
            return new NetStorageDbInfo[size];
        }
    };

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

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getMountPath() {
        return (MOUNT_BASE_DIR + name);
    }

    public boolean isReconnect() {
        return isReconnect == 1 ? true : false;
    }

    public void setIsReconnect(int isReconnect) {
        this.isReconnect = isReconnect;
    }

    public String getNetworkInfo() {
        return networkInfo;
    }

    public void setNetworkInfo(String networkInfo) {
        this.networkInfo = networkInfo;
    }

    public String getReserve1() {
        return reserve1;
    }

    public void setReserve1(String reserve1) {
        this.reserve1 = reserve1;
    }

    public void setIsMounted(int isMounted) {this.isMounted = isMounted;}

    public boolean isMounted() {return isMounted == 1 ? true : false ;}
}
