package tv.formuler.service.gtv.networkstorage;

import android.content.Context;
import android.os.RemoteException;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import vendor.amlogic.hardware.hwan.V1_0.IHwAnCallback;
import vendor.amlogic.hardware.hwan.V1_0.dataPack;

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
        // 만약 Username와 password가 null이라면 everyone에게 공개되어 있다고 판단.
        // 다음과 같은 option으로 mount시 되는 것 확인 함.
        String option;
        if(info.getUsername() == null) {
            if(info.getPassword() == null)
                option = "username=" + "everyone" + ",password=" + "everyone";
            else
                option = "username=" + "everyone" + ",password=" + info.getPassword();
        }
        else
            option = "username=" + info.getUsername() + ",password=" + info.getPassword();

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

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        final ArrayList<String> vMountedNetStorageInfos = new ArrayList<>();

        Callable<ArrayList<String>> task = new Callable<ArrayList<String>>() {
            @Override
            public ArrayList<String> call() throws Exception {
                try {
                    final File folder = new File("/mnt/cifs");
                    for (final File name : folder.listFiles()) {
                        if (name.isDirectory()) {
                            vMountedNetStorageInfos.add(name.getName());
                        }
                    }
                    return vMountedNetStorageInfos;
                } catch (Error ex) {
                    return null;
                }
            }
        };

        Future<ArrayList<String>> future = executorService.submit(task);

        try {
             ArrayList<String> result = future.get(1500, TimeUnit.MILLISECONDS);
             return result;
        } catch (Exception e) {
            Log.e("NetworkStorageManager","##### FindNetworkStorage Time out error #####");
        } finally {
            executorService.shutdown();
        }

        return null;
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