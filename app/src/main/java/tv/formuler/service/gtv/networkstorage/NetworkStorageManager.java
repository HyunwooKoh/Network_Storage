package tv.formuler.service.gtv.networkstorage;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;

import vendor.amlogic.hardware.hwan.V1_0.IHwAnCallback;
import vendor.amlogic.hardware.hwan.V1_0.dataPack;

import android.os.RemoteException;


public class NetworkStorageManager {

    private Context mContext;
    private NetworkStorageDbHelper mStorageDbHelper;
    private AnApi mAnapi;

    private NetworkStorageManager() {
    }

    private static class Singleton {
        private static final NetworkStorageManager instance = new NetworkStorageManager();
    }

    public static NetworkStorageManager getInstance() {
        return Singleton.instance;
    }

    public void initialize(Context context) {
        mContext = context;
        mAnapi = new AnApi(mContext);
        mStorageDbHelper = new NetworkStorageDbHelper(mContext, null);
        mStorageDbHelper.openDb();
    }

    public void release() {
        mContext = null;
        if (mStorageDbHelper != null) {
            mStorageDbHelper.releaseDb();
        }
    }

    public boolean insertNetStorage(String name, String url, String username, String password, boolean isReconnect, String networkInfo, String reserve, boolean isMounted) {
        if (mStorageDbHelper != null) {

            return mStorageDbHelper.insertNetStorage(name, url, username, password, isReconnect, networkInfo, reserve, isMounted);
        }

        return false;
    }

    public boolean updateNetStorage(NetStorageDbInfo info) {
        if (mStorageDbHelper != null) {
            return mStorageDbHelper.updateNetStorage(info);
        }

        return false;
    }

    public boolean deleteNetStorage(NetStorageDbInfo info) {
        if (mStorageDbHelper != null) {
            return mStorageDbHelper.deleteNetStorage(info);
        }

        return false;
    }

    public ArrayList<NetStorageDbInfo> getNetStorageList() {
        if (mStorageDbHelper != null) {
            return mStorageDbHelper.getNetStorageList();
        }
        Log.e("harold","Db is null");
        return null;
    }

    public NetStorageDbInfo getNetStorage(String name) {
        if (mStorageDbHelper != null) {
            return mStorageDbHelper.getNetStorage(name);
        }

        return null;
    }

    /**
     * mount success 되면 TVService 에서 volume list update
     * TvService addStorageListener 등록하면 onMediaMounted Callback 전달됨
     * @param info
     * @return
     */
    public boolean mount(NetStorageDbInfo info) {
        String netPath = "//" + info.getUrl();
        String localMountPath = info.getMountPath();
        String type = "cifs";
        String option = "username=" + info.getUsername() + ",password=" + info.getPassword();

        if(mAnapi == null)
            mAnapi = new AnApi(mContext);

        int result = mAnapi.appStorageMount( netPath, localMountPath, type, option);
        if( result == 0)
            return true;
        else
            return false;

    }

    /**
     * unmount success 되면 TVService 에서 volume list update
     * TvService addStorageListener 등록하면 onMediaUnMounted Callback 전달됨
     * @param info
     * @return
     */
    public boolean unmount(NetStorageDbInfo info) {
        if(mAnapi == null)
            mAnapi = new AnApi(mContext);

        int result = mAnapi.appStorageUmount( info.getMountPath() );

        if( result == 0)
            return true;
        else
            return false;
    }

    public synchronized ArrayList<String> findNetworkStorage(Context context) {

        ArrayList<String> vMountedNetStorageInfos = new ArrayList<>();
        try {
            final File folder = new File("/mnt/cifs");
            for (final File name : folder.listFiles()) {
                if (name.isDirectory()) {
                    vMountedNetStorageInfos.add(name.getName());
                }
            }
        } catch (Error ex) {
            ex.printStackTrace();
            return null;
        }
        return vMountedNetStorageInfos;
    }

    private IHwAnCallback mAnCallbackImpl = new IHwAnCallback.Stub() {
        @Override
        public void anNotifyEvent(ArrayList<dataPack> arrayList) throws RemoteException {
            if (arrayList == null) {
                return;
            }

            int msg = arrayList.remove(0).iVal;
            Log.v( "jacob", "setting anNotifyEvent msg = " + msg);
            switch( msg )
            {
                case AnApi.NOTIFYMSG_SYSTEM_NETDRIVE_Mount:
                    Log.v( "jacob", "noti mount: " + arrayList.get(1).sVal + ", " + arrayList.get(2).sVal);
                    break;
                case AnApi.NOTIFYMSG_SYSTEM_NETDRIVE_UnMount:
                    Log.v( "jacob", "noti un mount: " + arrayList.remove(1).sVal);
                    break;
                default:
            }
        }
    };
}