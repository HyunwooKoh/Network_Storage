package tv.formuler.service.gtv.networkstorage;


import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;

import java.net.MalformedURLException;
import java.util.ArrayList;

import jcifs.CIFSContext;
import jcifs.CIFSException;
import jcifs.context.SingletonContext;
import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SmbFile;


public class NetworkStatusListener extends Service {
    private final String TAG = "NetworkStatusListener";
    private final int MAX_TRY_COUNT = 20;
    private final int SLEEP_TIME = 1000;
    private boolean isBootComplete = false;
    private NetworkConnectionCheck mNetworkConnectionCheck;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "=========================================");
        Log.i(TAG, "#### Network status checker started. ### ");
        Log.i(TAG, "=========================================");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(intent != null)
            isBootComplete = intent.getBooleanExtra("isBootComplete", false);

        mNetworkConnectionCheck = new NetworkConnectionCheck(getApplicationContext());
        mNetworkConnectionCheck.register();
        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        if(mNetworkConnectionCheck != null)
            mNetworkConnectionCheck.unregister();

        super.onDestroy();
    }

    public class NetworkConnectionCheck extends ConnectivityManager.NetworkCallback {

        private final Context context;
        private final NetworkRequest networkRequest;
        private final ConnectivityManager connectivityManager;
        private final NetworkStorageManager mNetworkStorageManager;
        private final EditNetworkStorage mEditNetworkStorage;
        private final NetworkStorageReceiver mNetworkStorageReceiver;
        private final ArrayList<Thread> Watcher_thread;
        private final ArrayList<String> Watching_SMB_List;
        private int ON_UNMOUNTING;
        private boolean ON_VPN_STATUS;

        public NetworkConnectionCheck(Context context){
            this.context=context;
            mNetworkStorageReceiver = new NetworkStorageReceiver();
            mNetworkStorageReceiver.init();
            mNetworkStorageManager = NetworkStorageManager.getInstance();
            mNetworkStorageManager.initialize(context);
            mEditNetworkStorage = EditNetworkStorage.newInstance();
            Watcher_thread = new ArrayList<Thread>();
            Watching_SMB_List = new ArrayList<String>();
            ON_UNMOUNTING = 0;
            ON_VPN_STATUS = false;

            networkRequest =
                    new NetworkRequest.Builder()                                        // addTransportType : 주어진 전송 요구 사항을 빌더에 추가
                            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)       // TRANSPORT_WIFI : 이 네트워크가 Wi-Fi 전송을 사용함을 나타냅니다.
                            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
                            .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                            .build();
            this.connectivityManager = (ConnectivityManager) this.context.getSystemService(Context.CONNECTIVITY_SERVICE); // CONNECTIVITY_SERVICE : 네트워크 연결 관리 처리를 검색

        }

        public void register() {
            IntentFilter filter = new IntentFilter();
            filter.addAction("android.net.conn.CONNECTIVITY_CHANGE");
            filter.addAction("aloys.intent.action.START_STORAGE_WATCHER");
            filter.addAction("aloys.intent.action.STOP_STORAGE_WATCHER");
            registerReceiver(mNetworkStorageReceiver,filter);
            this.connectivityManager.registerNetworkCallback(networkRequest, this);
        }

        public void unregister() {
            unregisterReceiver(mNetworkStorageReceiver);
            this.connectivityManager.unregisterNetworkCallback(this);
        }

        @Override
        public void onAvailable(@NonNull Network network) {
            super.onAvailable(network);
            if(!ON_VPN_STATUS)
                mount();
        }

        @Override
        public void onLost(@NonNull Network network) {
            super.onLost(network);
            if(!ON_VPN_STATUS)
                unmount();
        }

        // mount storage which has re_mount option.
        public void Boot_Complete() {
            ArrayList<NetStorageDbInfo> mNetStorageDbInfoList = mNetworkStorageManager.getNetStorageList();

            if(mNetStorageDbInfoList != null && mNetStorageDbInfoList.size() > 0)
            {
                for (NetStorageDbInfo info : mNetStorageDbInfoList) {

                    if (info.isReconnect()) {

                        int mount_count = 0;
                        boolean isSuccess = mNetworkStorageManager.mount(info);

                        while (!isSuccess) {
                            if(mount_count++ >= MAX_TRY_COUNT)
                                break;
                            SystemClock.sleep(SLEEP_TIME);
                            isSuccess = mNetworkStorageManager.mount(info);
                        }

                        if (!isSuccess) {
                            Log.e(TAG, "[BOOT_COMPLETE] Error! Mounting networkstorage name = " + info.getName() + ", Url = " + info.getUrl() + ", mount path =" + info.getMountPath());
                            mNetworkStorageManager.unmount(info);
                            info.setIsMounted(0);
                            mNetworkStorageManager.updateNetStorage(info);
                        }
                        else {
                            info.setIsMounted(1);
                            mNetworkStorageManager.updateNetStorage(info);
                            mEditNetworkStorage.SendMountBroadcast(info,context);
                        }
                    }
                }
            }
        }

        // mount storage when Internet reconnected
        public void mount() {

            if(!isBootComplete) {
                ArrayList<NetStorageDbInfo> mNetStorageDbInfoList = mNetworkStorageManager.getNetStorageList();

                if (mNetStorageDbInfoList != null && mNetStorageDbInfoList.size() > 0) {

                    for (NetStorageDbInfo info : mNetStorageDbInfoList) {

                        if (info.isMounted() && !mEditNetworkStorage.isMounted(info)) {

                            int mount_count = 0;
                            boolean IsSuccess = mNetworkStorageManager.mount(info);

                            while (!IsSuccess) {
                                if (mount_count++ >= MAX_TRY_COUNT)
                                    break;
                                SystemClock.sleep(SLEEP_TIME);
                                IsSuccess = mNetworkStorageManager.mount(info);
                            }

                            if (IsSuccess) {
                                info.setIsMounted(1);
                                mNetworkStorageManager.updateNetStorage(info);
                                mEditNetworkStorage.SendMountBroadcast(info, context);
                            }
                            else {
                                mNetworkStorageManager.unmount(info);
                                info.setIsMounted(0);
                                mNetworkStorageManager.updateNetStorage(info);
                                mEditNetworkStorage.SendUnMountBroadcast(info, context);
                            }
                        }
                    }
                }
            }
            else {
                isBootComplete = false;
                Boot_Complete();
            }

            if(ON_VPN_STATUS) {
                ON_VPN_STATUS = false;
            }
        }

        // unmount storage when Internet disconnected
        public void unmount() {
            ON_UNMOUNTING = Watching_SMB_List.size(); // for Watcher thread control , If the value > 0 Indicate that
                                                      // Unmount function is started, so the watcher thread doesn't need to unmount it again.
            ArrayList<NetStorageDbInfo> mNetStorageDbInfoList = mNetworkStorageManager.getNetStorageList();

            if(mNetStorageDbInfoList != null && mNetStorageDbInfoList.size() > 0 ) {

                for (NetStorageDbInfo info : mNetStorageDbInfoList) {

                    // mount the storages which is mounted , but do not change the IsMounted value, It only changed when user mount / unmount it by userself or
                    // the server's host is down
                    if (info.isMounted()) {

                        int unmount_count = 0;
                        boolean IsSuccess = mNetworkStorageManager.unmount(info);

                        while (!IsSuccess) {
                            if(unmount_count++ >= MAX_TRY_COUNT)
                                break;
                            SystemClock.sleep(SLEEP_TIME);
                            IsSuccess = mNetworkStorageManager.unmount(info);
                        }

                        if (IsSuccess) {
                            mEditNetworkStorage.SendUnMountBroadcast(info, context);
                        }
                        else {
                            Log.e(TAG, "Error! Unmounting networkstorage name = " + info.getName() + " Url = " + info.getUrl() + " mount path =" + info.getMountPath());
                        }
                    }
                }
            }
        }

        // This is Watcher which is watching the connection between samba host server
        // start == true : Making new thread which is watching the info's server
        // start == false : Destroy thread which is watching the info's server
        public void SMB_Host_watcher(NetStorageDbInfo info, boolean start) {

            // making watcher thread and detect error
            if (start) {
                Thread watcher = new Thread(new Runnable() {

                    @Override
                    public void run() {

                        SmbFile smbFile = null;

                        try {
                            CIFSContext authed = SingletonContext.getInstance().withCredentials(
                                                                new NtlmPasswordAuthentication(SingletonContext.getInstance(),
                                                            "smb://" + info.getUrl(), info.getUsername(), info.getPassword()));
                            smbFile = new SmbFile("smb://" + info.getUrl(), authed);
                        } catch (MalformedURLException e) {
                            Log.e(TAG, "ERROR MAKING SMB_FILE");
                        }

                        if (smbFile != null) {

                            try {
                                smbFile.watch(1, true).watch();
                            } catch (CIFSException e) {

                                /* [Aloys harold]
                                **** There is 2 case of exception 1 : Disconnect Internet / 2 : samba host server is down
                                **** 1 : Unmount function will do unmount actions , at Unmount function set ON_UNMOUNTING value to skip
                                **** 2 : In this case, unmount the server which host is down
                                */
                                SystemClock.sleep(SLEEP_TIME); // Wait for check which is the case

                                if (ON_UNMOUNTING == 0) { // case 2

                                    // Unmounting
                                    mNetworkStorageManager.unmount(info);
                                    info.setIsMounted(0);
                                    mNetworkStorageManager.updateNetStorage(info);
                                    mEditNetworkStorage.SendUnMountBroadcast(info, context);

                                    // Delete info in manage List
                                    int index = Watching_SMB_List.indexOf(info.getName());
                                    if (index != -1) {
                                        Thread mThread = Watcher_thread.get(index);
                                        Watcher_thread.remove(index);
                                        Watching_SMB_List.remove(index);
                                    }
                                }
                                else
                                    ON_UNMOUNTING--; // case 1

                            }
                        }
                    }
                });

                Watcher_thread.add(watcher);
                Watching_SMB_List.add(info.getName());
                watcher.start();
            }
            // Deleting watcher thread
            else {
                int index = Watching_SMB_List.indexOf(info.getName());

                if (index != -1) {
                    Thread mThread = Watcher_thread.get(index);
                    Watcher_thread.remove(index);
                    Watching_SMB_List.remove(index);

                    try {
                        mThread.interrupt();
                        mThread.join();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                }
            }
        }

        // Vpn status and Mount / UnMount change Receiver
        public class NetworkStorageReceiver extends BroadcastReceiver {
            private boolean Latest_vpn_stat;

            public void init() {
                Latest_vpn_stat = isVpnConnected();
            }

            @Override
            public void onReceive(Context context, Intent intent) {

                if(intent.getAction() != null) {
                    switch (intent.getAction()) {

                        case "android.net.conn.CONNECTIVITY_CHANGE":
                            if (Latest_vpn_stat != isVpnConnected()) {
                                ON_VPN_STATUS = true;
                                Latest_vpn_stat = isVpnConnected();
                                unmount();
                                mount();
                            }
                            break;

                        case "aloys.intent.action.START_STORAGE_WATCHER":
                            SMB_Host_watcher(mNetworkStorageManager.getNetStorage(intent.getStringExtra("name")), true);
                            break;

                        case "aloys.intent.action.STOP_STORAGE_WATCHER":
                            SMB_Host_watcher(mNetworkStorageManager.getNetStorage(intent.getStringExtra("name")), false);
                            break;
                    }
                }
            }

            public boolean isVpnConnected() {
                ConnectivityManager mConnectivityManager;
                mConnectivityManager =
                        (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
                Network[] allNetworks = mConnectivityManager.getAllNetworks();

                for (Network network : allNetworks) {

                    NetworkInfo networkInfo = mConnectivityManager.getNetworkInfo(network);

                    if (networkInfo != null && networkInfo.getType() == ConnectivityManager.TYPE_VPN
                            && networkInfo.isAvailable() && networkInfo.isConnected()) {
                        return true;
                    }
                }
                return false;
            }
        }
    }

}