package tv.formuler.service.gtv.networkstorage;


import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.net.MalformedURLException;
import java.util.ArrayList;

import jcifs.CIFSContext;
import jcifs.CIFSException;
import jcifs.context.SingletonContext;
import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;
import tv.formuler.service.gtv.networkstorage.search.FindSamba;


public class NetworkStorageService extends Service {

    private final String TAG = "NetworkStorageService";

    private final int MAX_TRY_COUNT = 10;
    private final int SLEEP_TIME = 1000;

    private final int ON_AVAILABLE_WITH_VPN = 0x0001;
    private final int ON_AVAILABLE_WITHOUT_VPN = 0x0002;
    private final int VPN_CHANGE = 0x0003;

    private final int UNMOUNT_VPN_STATUS = 0x0004;
    private final int ONLOST_INTERNET = 0x0005;
    private final int NETWORK_CHANGE = 0x0006;

    private boolean isBootComplete = false;
    private boolean Finding_Storage = false;
    private boolean ReStart = false;
    private static NetworkStorageService mService;
    private ArrayList<String> Mounted_Storage;
    private final ArrayList<String> Found_Server = new ArrayList<String>();

    private NetworkConnectionCheck mNetworkConnectionCheck;
    private IBinder mBinder;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "================================================");
        Log.i(TAG, "####### Network Storage Service started. #######");
        Log.i(TAG, "================================================");
        mBinder = new ServiceBinder();
        mService = this;

        ReStart = true;
        mNetworkConnectionCheck = new NetworkConnectionCheck(getApplicationContext());
        mNetworkConnectionCheck.register();
    }

    public class ServiceBinder extends Binder {
        NetworkStorageService getService() {
            return NetworkStorageService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Found_Server.clear();
        return false;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(intent != null)
            isBootComplete = intent.getBooleanExtra("isBootComplete", false);

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

        Log.i(TAG, "=========================================");
        Log.i(TAG, "### Network Storage Service Destroyed.###");
        Log.i(TAG, "=========================================");

        super.onDestroy();
    }

    public ArrayList<String> Get_Mounted_List() {
        if(Mounted_Storage != null && Mounted_Storage.size() > 0)
            return Mounted_Storage;
        else
            return null;
    }

    private void Send_UI_Update(boolean Show_Avail) {
        if(!Show_Avail)
            Found_Server.clear();
        Intent intent = new Intent("aloys.intent.action.networkstorage_ui_update");
        intent.setPackage("tv.formuler.service.gtv.networkstorage");
        intent.putExtra("UI_Update",true);
        intent.putExtra("Show_Avail",Show_Avail);
        getApplicationContext().sendBroadcast(intent);
    }

    public void Find_Available_Storage(String addr, boolean Mount) {
        if(addr.equals("aloys.init.availablestorage")) {
            ConnectivityManager mConnectivityManager = (ConnectivityManager) getApplicationContext().getSystemService(
                    Context.CONNECTIVITY_SERVICE);
            Network act_net = mConnectivityManager.getActiveNetwork();

            if(act_net != null) {
                NetworkInfo mNetworkInfo = mConnectivityManager.getNetworkInfo(act_net);
                if(mNetworkInfo != null) {
                    if(!Finding_Storage) {
                        Finding_Storage = true;
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                Log.d(TAG,"####### Start Find Available Storages #######");
                                Found_Server.clear();
                                ArrayList<String> AvailableServer = FindSamba.newInstance().start(getApplicationContext());

                                if(AvailableServer != null && AvailableServer.size() > 0 ) {
                                    ArrayList<NetStorageDbInfo> mNetStorageDbInfoList = NetworkStorageManager.getInstance().getNetStorageList();

                                    for (String address : AvailableServer) {
                                        boolean Ismounted = false;
                                        if (mNetStorageDbInfoList != null && mNetStorageDbInfoList.size() > 0) {
                                            for (NetStorageDbInfo dbInfo : mNetStorageDbInfoList) {
                                                if (dbInfo.getUrl().equals(address)) {
                                                    Ismounted = true;
                                                    break;
                                                }
                                            }
                                        }
                                        if(!Ismounted) {
                                            Found_Server.add(address);
                                        }
                                    }
                                }
                                Intent intent = new Intent("aloys.intent.action.networkstorage_ui_update_avaServer");
                                intent.putStringArrayListExtra("available_server",Found_Server);
                                intent.setPackage("tv.formuler.service.gtv.networkstorage");
                                getApplicationContext().sendBroadcast(intent);
                                Finding_Storage = false;
                            }
                        }).start();
                    }
                }
            }
            else {
                Found_Server.clear();
                Intent intent = new Intent("aloys.intent.action.networkstorage_ui_update_avaServer");
                intent.putStringArrayListExtra("available_server",Found_Server);
                intent.setPackage("tv.formuler.service.gtv.networkstorage");
                getApplicationContext().sendBroadcast(intent);
            }
        }
        else {
            if(Mount)
                Found_Server.remove(addr);
            else
                Found_Server.add(addr);

            Intent intent = new Intent("aloys.intent.action.networkstorage_ui_update_avaServer");
            intent.putStringArrayListExtra("available_server",Found_Server);
            intent.setPackage("tv.formuler.service.gtv.networkstorage");
            getApplicationContext().sendBroadcast(intent);
        }
    }

    public static NetworkStorageService getInstance() {
        return mService;
    }

    public class NetworkConnectionCheck extends ConnectivityManager.NetworkCallback {

        private final Context context;
        private final NetworkRequest networkRequest;
        private final ConnectivityManager connectivityManager;
        private final NetworkStorageManager mNetworkStorageManager;
        private final EditNetworkStorage mEditNetworkStorage;
        private final Vpn_Receiver mVpn_Receiver;

        // jcifs library를 사용한 Watcher thread 관리
        private final ArrayList<Thread> Jcifs_Watch_thread;
        private final ArrayList<String> Jcifs_Watch_List;
        private int Jcifs_Count;
        private final Object Jcifs_Count_Lock;

        // jcifs library를 사용하지 못하는 storage를 관리하는 Thread 에서 사용
        private final ArrayList<String> No_Jicfs_Watch_List;

        // Vpn 상태가 변경된 후, Auto_unmount - Auto mount를 진행하기 위한 Flag
        // off -> on으로 변경시 해당 상태가 여려번 변경되는 현상이 발생.
        // 처음의 변경만 처리를 위해 사용하기 위하여 사용.
        private boolean ON_VPN_STATUS, VPN_RECONN;
        private final Object VPN_STATUS_LOCK, VPN_RECONN_LOCK;

        // Vpn 상태가 변경 되는 과정에서 ConnectivityManager.NetworkCallback 에서 연결 / 단절 콜백이
        // 여러번 발생하는 문제가 발생. Auto mount/unmount thread가 돌고 있는지의 Flag를 사용
        private boolean ON_MOUNTING_STATUS,ON_UNMOUNTING_STATUS;
        private final Object MOUNTING_LOCK, UNMOUNTING_LOCK;


        // Unmount함수가 돌았는지를 Flag로 관리해, unmount가 실행됬으면 감시자 thread들을 종료 혹은 exception처리.
        private boolean LAST_ACTION;
        private final Object LAST_ACT_LOCK;

        // OnAvailable call back을 받았을 때, 직전의 Network 상태가 null일 경우만 Auto-mount 진행.
        // 직전의 network 상태를 기억하기 위한 History 변수
        private int Latest_Network_Stat;

        public NetworkConnectionCheck(Context context) {

            this.context=context;
            Jcifs_Count = 0;
            LAST_ACTION = false;
            VPN_RECONN = ON_VPN_STATUS = false;
            ON_MOUNTING_STATUS = ON_UNMOUNTING_STATUS = false;
            Jcifs_Watch_thread = new ArrayList<Thread>();
            No_Jicfs_Watch_List = new ArrayList<String>();
            Jcifs_Watch_List = new ArrayList<String>();

            // Variable For Synchronization
            VPN_STATUS_LOCK = new Object();
            VPN_RECONN_LOCK = new Object();
            MOUNTING_LOCK = new Object();
            UNMOUNTING_LOCK = new Object();
            LAST_ACT_LOCK = new Object();
            Jcifs_Count_Lock = new Object();

            Latest_Network_Stat = -1;

            mEditNetworkStorage = EditNetworkStorage.newInstance();

            mVpn_Receiver = new Vpn_Receiver();
            mVpn_Receiver.init();

            mNetworkStorageManager = NetworkStorageManager.getInstance();
            mNetworkStorageManager.initialize(context);

            Mounted_Storage = mNetworkStorageManager.findNetworkStorage(context);

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
            filter.addAction("aloys.intent.action.STORAGE_MOUNTED");
            filter.addAction("aloys.intent.action.STORAGE_BAD_REMOVAL");
            registerReceiver(mVpn_Receiver,filter);
            this.connectivityManager.registerNetworkCallback(networkRequest, this);
        }

        public void unregister() {
            unregisterReceiver(mVpn_Receiver);
            this.connectivityManager.unregisterNetworkCallback(this);
        }

        public void init_Watcher() {
            if(Mounted_Storage != null) {
                for(String name : Mounted_Storage) {
                    NetStorageDbInfo info = mNetworkStorageManager.getNetStorage(name);

                    if(info.getName() != null && info.getPassword() != null)
                        SMB_File_Watcher(info, true, name, false);
                    else
                        Check_Guest_Available(info);
                }
            }
        }

        @Override
        public void onAvailable(@NonNull Network network) {
            super.onAvailable(network);

            ConnectivityManager mConnectivityManager = (ConnectivityManager) getApplicationContext().getSystemService(
                    Context.CONNECTIVITY_SERVICE);

            /*=======================================================================
                이전의 Network가 존재하는 상태에서 , 새로운 Network가 연결되면
                연결되어 있던 모든 Network Storage들을 Unmount 진행.
                Latest_Network_Stat 에서 History 관리.
            ========================================================================*/

            boolean re_conn = false;
            if(network != null) {
                NetworkInfo networkInfo = mConnectivityManager.getNetworkInfo(network);
                if(networkInfo != null && Latest_Network_Stat == -1) {
                    re_conn = true;
                    Latest_Network_Stat = networkInfo.getType();
                }
            }

            // Network Stroage Service가 죽은 후 다시 살아날 경우
            // Mount 되어있는 Stroage들을 긁어와, Watcher들만 다시 초기화한다.
            if(ReStart) {
                if(isBootComplete) {
                    ReStart = false;
                }
                else {
                    if(Mounted_Storage != null) {
                        Log.d(TAG,"####### Network Storage ReStart!. Init Storage Watcher , " +
                                "Storage count : " + Mounted_Storage.size() + " #######");
                        ReStart = false;
                        init_Watcher();
                        return;
                    }
                }
            }

            if(isBootComplete) {

                //// Boot complete 이후, Vpn 자동 연결 기다린 후, Mount하기 위해서 10초 후 mount 진행. ////

                Set_Mounting_Running(true);
                Set_Vpn_Status(true);

                new Handler(getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        Thread Mounting_Thread = new Thread(new Runnable() {
                            @Override
                            public void run() {
                                Auto_Mount(ON_AVAILABLE_WITHOUT_VPN);
                            }
                        });
                        Mounting_Thread.setPriority(Thread.MAX_PRIORITY);
                        Mounting_Thread.start();
                    }
                },SLEEP_TIME*10);
            }
            else if(mVpn_Receiver.Get_Latest_Vpn_Stat() && !Get_Vpn_Status()) {

            /*=================================================================================
                직전에 Vpn이 켜져 있던 상태에서, 네트워크가 단절되었다가 다시 연결하게 되면
                Onavailable callback이 온 후, Vpn state가 변경되어 unmount - mount를 진행하게 됨.
                mount만 진행하기 위해 해당 케이스는 Onavailable에서 관리. (Vpn 수행상태 true로 수정).
            ==================================================================================*/

                Set_Vpn_Status(true);

                if(re_conn) {
                    Set_Mounting_Running(true);

                    new Handler(getMainLooper()).postDelayed(new Runnable() {
                        @Override
                        public void run() {

                            Thread Mounting_Thread = new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    Auto_Mount(ON_AVAILABLE_WITH_VPN);
                                }
                            });

                            Mounting_Thread.setPriority(Thread.MAX_PRIORITY);
                            Mounting_Thread.start();
                        }
                    },SLEEP_TIME*10);
                }
                else if(!re_conn && !Get_UnMounting_Running()) {

                    Set_UnMounting_Running(true);
                    Set_Last_Action(true);

                    Thread UnMounting_Thread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            Auto_UnMount(NETWORK_CHANGE);
                            Set_Vpn_Status(false);
                        }
                    });

                    UnMounting_Thread.setPriority(Thread.MAX_PRIORITY);
                    UnMounting_Thread.start();
                }

            }
            else if(!Get_Vpn_Status() && !Get_Mounting_Running()) {

                if(re_conn) {
                    Set_Mounting_Running(true);

                    Thread Mounting_Thread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            Auto_Mount(ON_AVAILABLE_WITHOUT_VPN);
                        }
                    });

                    Mounting_Thread.setPriority(Thread.MAX_PRIORITY);
                    Mounting_Thread.start();
                }
                else if(!re_conn && !Get_UnMounting_Running()) {

                    Set_UnMounting_Running(true);
                    Set_Last_Action(true);

                    Thread UnMounting_Thread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            Auto_UnMount(NETWORK_CHANGE);
                        }
                    });

                    UnMounting_Thread.setPriority(Thread.MAX_PRIORITY);
                    UnMounting_Thread.start();
                }
            }
        }

        @Override
        public void onLost(@NonNull Network network) {
            super.onLost(network);

            if(!Get_Vpn_Status() && !Get_UnMounting_Running() && !Get_Last_Action()) {
                Set_UnMounting_Running(true);
                Set_Last_Action(true);

                Thread UnMounting_Thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Latest_Network_Stat = -1;
                        Auto_UnMount(ONLOST_INTERNET);
                    }
                });

                UnMounting_Thread.setPriority(Thread.MAX_PRIORITY);
                UnMounting_Thread.start();
            }
        }

        /*======================Flag Control======================*/
        /*========================================================*/

        private void Set_Mounting_Running (boolean status) {
            synchronized (MOUNTING_LOCK) {
                ON_MOUNTING_STATUS = status;
            }
        }

        private boolean Get_Mounting_Running () {
            synchronized (MOUNTING_LOCK) {
                return ON_MOUNTING_STATUS;
            }
        }

        private void Set_UnMounting_Running(boolean status) {
            synchronized (UNMOUNTING_LOCK) {
                ON_UNMOUNTING_STATUS = status;
            }
        }

        private boolean Get_UnMounting_Running() {
            synchronized (UNMOUNTING_LOCK){
                return ON_UNMOUNTING_STATUS;
            }
        }

        private void Set_Vpn_Status (boolean status) {
            synchronized (VPN_STATUS_LOCK) {
                ON_VPN_STATUS = status;
            }
        }

        private boolean Get_Vpn_Status () {
            synchronized (VPN_STATUS_LOCK) {
                return ON_VPN_STATUS;
            }
        }

        private void Set_Last_Action(boolean action) {
            synchronized (LAST_ACT_LOCK) {
                // true : unmount <-> false : mount //
                if(action)
                    SMB_WATHCER_CHECK(true);
                LAST_ACTION = action;
            }
        }

        private boolean Get_Last_Action() {
            synchronized (LAST_ACT_LOCK) {
                return LAST_ACTION;
            }
        }

        private void Set_Vpn_ReConn(boolean status) {
            synchronized (VPN_RECONN_LOCK) {
                VPN_RECONN = status;
            }
        }

        private boolean Get_Vpn_ReConn() {
            synchronized (VPN_RECONN_LOCK) {
                return VPN_RECONN;
            }
        }

        private boolean SMB_WATHCER_CHECK(boolean Setting) {
            synchronized (Jcifs_Count_Lock) {
                if(Setting) {
                    Jcifs_Count = Jcifs_Watch_List.size();
                    return true;
                }
                else {

                    if(Jcifs_Count == 0)
                        return false;
                    else {
                        Jcifs_Count--;
                        return true;
                    }
                }
            }
        }
        /*========================================================*/
        /*========================================================*/


        /*======================================== Mount - UnMount Functions ========================================*/
        /*===========================================================================================================*/

        public int Boot_Complete() {

            ArrayList<NetStorageDbInfo> mNetStorageDbInfoList = mNetworkStorageManager.getNetStorageList();
            int mount_count = 0;

            if(mNetStorageDbInfoList != null && mNetStorageDbInfoList.size() > 0)
            {
                for (NetStorageDbInfo info : mNetStorageDbInfoList) {
                    info.setIsMounted(0);
                    if (info.isReconnect()) {

                        boolean isSuccess = mNetworkStorageManager.mount(info);

                        if (!isSuccess) {
                            Log.e(TAG, "[BOOT_COMPLETE] Error! Mounting networkstorage name = " + info.getName()
                                    + ", Url = " + info.getUrl() + ", mount path =" + info.getMountPath());
                            mNetworkStorageManager.unmount(info);
                            mNetworkStorageManager.updateNetStorage(info);
                        }
                        else {
                            mount_count++;
                            info.setIsMounted(1);
                            mNetworkStorageManager.updateNetStorage(info);
                            mEditNetworkStorage.SendMountBroadcast(info,context);
                        }
                    }
                }
            }
            Set_Vpn_Status(false);
            isBootComplete = false;

            return mount_count;
        }

        public void Re_UnMount(int mount_type, ArrayList<NetStorageDbInfo> Unmount_List) {

            int Unmount_size = Unmount_List.size();
            int Success_count = 0;

            Log.e(TAG,"Vpn Status Changed while Mounting! Do Unmount " +
                    "- Mount again with Mounted_Storage, Unmount Size : " + Unmount_size);

            for(NetStorageDbInfo remount_info : Unmount_List) {

                int Re_Unmount_Count = 0;
                boolean Re_Unmount_Success = mNetworkStorageManager.unmount(remount_info);

                while(!Re_Unmount_Success) {

                    if(Re_Unmount_Count > MAX_TRY_COUNT)
                        break;
                    Re_Unmount_Count++;
                    Re_Unmount_Success = mNetworkStorageManager.unmount(remount_info);

                }

                if(Re_Unmount_Success)
                    Success_count++;
            }

            if(Unmount_size == Success_count) {

                for(NetStorageDbInfo remount_info : Unmount_List) {
                    mEditNetworkStorage.SendUnMountBroadcast(remount_info, getApplicationContext());
                }

                new Handler(getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        Thread Mounting_Thread = new Thread(new Runnable() {
                            @Override
                            public void run() {
                                Auto_Mount(mount_type);
                            }
                        });
                        Mounting_Thread.setPriority(Thread.MAX_PRIORITY);
                        Mounting_Thread.start();
                    }
                },SLEEP_TIME);
            }
        }

        public void Auto_Mount(int mount_type) {

            /*=================================================================================
                Vpn 상태 변경 혹은 Network 단절등의 이유로 Auto UnMount 진행된 모든 Storage들에 대해
                Auto Mount 진행. Dbinfo.IsMounted 컬럼 값을 보고, Mount할 Storage들을 식별
                Auto Mount하는 중에 Vpn 재 연결이 진행되면, Mount되었던 Storage들에 의해서 오류 발생.
                따라서, Mount하는 도중에 Vpn_Reconn Flag를 확인하면서, 재연결 되었으면 Auto Mount 되었던
                모든 Storage들을 Re_UnMount 함수에 전달하여 다시 Unmount한 후, Auto Mount 재 진행.
            ==================================================================================*/
            if(Get_UnMounting_Running())
                return;

            if(mount_type == VPN_CHANGE || mount_type == ON_AVAILABLE_WITH_VPN)
                Set_Vpn_ReConn(false);

            Log.d(TAG,"####### Start mount #######");
            String msg = "";
            int storage_count = 0;

            if(isBootComplete) {
                msg = getApplicationContext().getString(R.string.network_storage_boot_complete_mounted);
                storage_count = Boot_Complete();
            }
            else {
                msg = getApplicationContext().getString(R.string.reload_network_storage_from_stack);
                ArrayList<NetStorageDbInfo> mNetStorageDbInfoList = mNetworkStorageManager.getNetStorageList();
                ArrayList<NetStorageDbInfo> Auto_Mounted_List = new ArrayList<NetStorageDbInfo >();

                if (mNetStorageDbInfoList != null && mNetStorageDbInfoList.size() > 0) {

                    for (NetStorageDbInfo info : mNetStorageDbInfoList) {

                        if(Get_Vpn_ReConn() && Auto_Mounted_List != null) {
                            if(Auto_Mounted_List.size() > 0) {
                                Re_UnMount(mount_type,Auto_Mounted_List);
                                return;
                            }
                        }

                        if (info.isMounted()) {

                            int mount_count = 0;
                            boolean IsSuccess = mNetworkStorageManager.mount(info);

                            while (!IsSuccess && mount_count < MAX_TRY_COUNT) {

                                if(Get_Vpn_ReConn() && Auto_Mounted_List != null) {
                                    if(Auto_Mounted_List.size() > 0) {
                                        Re_UnMount(mount_type,Auto_Mounted_List);
                                        return;
                                    }
                                }

                                mount_count++;
                                Log.i(TAG,"mount try : " + info.getName() + ", count : " + mount_count);

                                if(mount_count % 10 == 0) {
                                    mNetworkStorageManager.initialize(getApplicationContext());
                                }

                                SystemClock.sleep(SLEEP_TIME);
                                IsSuccess = mNetworkStorageManager.mount(info);
                            }

                            if (IsSuccess) {
                                Auto_Mounted_List.add(info);
                                info.setIsMounted(1);
                                mNetworkStorageManager.updateNetStorage(info);
                                mEditNetworkStorage.SendMountBroadcast(info, getApplicationContext());
                                storage_count++;

                            }
                            else {
                                mNetworkStorageManager.unmount(info);
                                info.setIsMounted(0);
                                mNetworkStorageManager.updateNetStorage(info);
                                Log.e(TAG, "Error! Mounting networkstorage name = " + info.getName()
                                        + " Url = " + info.getUrl() + " mount path =" + info.getMountPath());
                            }
                        }
                    }
                }
            }

            Set_Mounting_Running(false);
            Set_Last_Action(false);

            String finalMsg;
            Handler mHandler;

            switch (mount_type) {

                case VPN_CHANGE :
                    Set_Vpn_Status(false);
                    break;

                case ON_AVAILABLE_WITH_VPN :
                    Set_Vpn_Status(false);
                    if(storage_count > 0) {
                        finalMsg = msg;
                        mHandler = new Handler((getMainLooper()));
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                Send_UI_Update(true);
                                Toast.makeText(getApplicationContext(), finalMsg, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                    break;

                case ON_AVAILABLE_WITHOUT_VPN :
                    if(storage_count > 0) {
                        finalMsg = msg;
                        mHandler = new Handler((getMainLooper()));
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                Send_UI_Update(true);
                                Toast.makeText(getApplicationContext(), finalMsg, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                    break;
            }

            Log.d(TAG,"####### end mount #######");
        }

        public void Auto_UnMount(int unmount_type) {

            /*=================================================================================
                Mount 되어있었던 모든 Storage들을 Unmount 진행.
                해당 Storage들의 Dbinfo.IsMounted 컬럼은 직전에 유저의 의도(Mount / Unmount)를 기억.
                해당 값을 이용하여 Network 재연결시 Auto Mount를 진행.
                해당 함수에서는 이 값들을 변경하지 않고, 연결된 Network가 변경되어 모두 Unmount 시켰을 경우만
                해당 값을 false(0)으로 변경.
            ==================================================================================*/

            Log.d(TAG,"####### start unmount #######");
            ArrayList<NetStorageDbInfo> mNetStorageDbInfoList = mNetworkStorageManager.getNetStorageList();
            ArrayList<NetStorageDbInfo> Unmount_Storage = new ArrayList<NetStorageDbInfo>();
            int storage_count = 0;

            if(mNetStorageDbInfoList != null && mNetStorageDbInfoList.size() > 0 ) {

                for (NetStorageDbInfo info : mNetStorageDbInfoList) {

                    if (info.isMounted()) {

                        int unmount_count = 0;
                        boolean IsSuccess = mNetworkStorageManager.unmount(info);

                        while (!IsSuccess && unmount_count < MAX_TRY_COUNT) {

                            unmount_count++;
                            Log.i(TAG,"unmount try : " + info.getName() + ", count : " + unmount_count);

                            if(unmount_count % 10 == 0) {
                                mNetworkStorageManager.initialize(getApplicationContext());
                            }

                            SystemClock.sleep(SLEEP_TIME);
                            IsSuccess = mNetworkStorageManager.unmount(info);
                        }

                        if (IsSuccess) {
                            storage_count++;
                            Unmount_Storage.add(info);
                            mEditNetworkStorage.SendUnMountBroadcast(info, getApplicationContext());
                        }
                        else {
                            mEditNetworkStorage.SendUnMountBroadcast(info, getApplicationContext());
                            Log.e(TAG, "Error! Unmounting networkstorage name = " + info.getName() + " Url = "
                                    + info.getUrl() + " mount path =" + info.getMountPath());
                        }
                    }
                }
            }

            Set_UnMounting_Running(false);
            Log.d(TAG,"####### end unmount #######");

            switch (unmount_type) {
                case UNMOUNT_VPN_STATUS :
                        if(Get_Vpn_Status() && !Get_Mounting_Running()) {
                            Set_Mounting_Running(true);

                            Auto_Mount(VPN_CHANGE);
                        }
                        break;

                case ONLOST_INTERNET :
                    if(storage_count > 0) {
                        String msg = getApplicationContext().getString(R.string.error_network_storage_from_stack);
                        Handler mHandler = new Handler(getMainLooper());
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                Send_UI_Update(false);
                                Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                    break;

                case NETWORK_CHANGE :
                    if(storage_count > 0) {
                        for (NetStorageDbInfo info : Unmount_Storage) {
                            info.setIsMounted(0);
                            mNetworkStorageManager.updateNetStorage(info);
                        }
                        String msg = getApplicationContext().getString(R.string.network_storage_network_status_change);
                        Handler mHandler = new Handler(getMainLooper());
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                Send_UI_Update(false);
                                Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                    break;
            }
        }
        /*===========================================================================================================*/
        /*===========================================================================================================*/


        /*============================================ Storage Watcher ==============================================*/
        /*===========================================================================================================*/

        public void SMB_File_Watcher(NetStorageDbInfo info, boolean start, String name, boolean IsGuest) {

            /*====================================================================================

                Jcifs Library 를 사용하는 Watcher Thread를 생성하는 함수.
                한개의 Watcher에 대해 한개의 Thread가 필요함.
                유저 ID/Password가 모두 있는 Network Storage들은 디폴트로 해당 함수에서 감시.
                유저 ID/Password가 없는 Network Storage들 중, Guest 접근을 하용하는 파일에 대해서는
                Check_Guest_Available에서 검사한 후 호출 됨.

                Watch에서 발생하는 Exception으로 Host연결 끊김을 감시함.
                Watch함수에서 Socket통신을 사용하기 때문에, Network 단절, Vpn 상태 변경 Host Down등의
                모든 경우에 대해 Exception 발생.

                Auto Mount를 진행하기 위해선, Dbinfo.IsMounted 컬럼 값을 이용하는데, Host Down이 발생한
                Storage는 Auto Mount를 진행할 수 없기 때문에, 일반적인 Auto UnMount와는 처리의 차이가 존재.
                Catch에서 잡히는 Exception 종류도 동일하여, Flag를 사용하여 처리해야함.

                Socket 통신의 Exception이기 때문에, 다른 callback보다 exception 이 빨리 올라옴.
                따라서 Handler.postDelayed를 사용하여, 500ms 뒤에 Flag 확인한 후, Exception Handling 진행

                이 Watcher에서 처리해 줄 부분은 오직 Host Down만 처리

                사용하는 Flag는 SMB_WATHCER_CHECK() 함수의 Jcifs_Count.
                Jcifs_Count는 Auto_Unmount를 진행할 경우, Wather Thread의 수 많큼 초기화.
                해당 값이 0일 경우, 다른 상황이 발생하지 않은것으로 간주 -> Host Down의 경우만 남음.
                이 때만 Host Down Unmount를 진행하도록 함.

                User가 EditNetworkStorage에서 해당 Storage를 Unmount한 경우,
                STORAGE_BAD_REMOVAL intent를 받아, 해당 Storage를 감시하는 Thread에 Interrupt를 발생.
                    Jcifs_Watch_List : 감시하는 Storage들의 Name을 관리
                    Jcifs_Watch_thread : 감시하는 Thread들을 관리
                Jcifs_Watch_List에서 해당 Storage의 Index를 찾은 후, Jcifs_Watch_thread에서 해당 index의
                Thread에 Interrupt를 거는 방식으로 진행.

                이때, User가 진행한 것인지 확인하기 위해, Edit에서 Mount/Unmount/Delete button을 누른 경우
                해당 Storage의 이름으로 SharedPreferences 생성.
                SharedPreferences가 있다면 user가 진행한 것이므로, 별다른 처리 진행하지 않음.

            =====================================================================================*/

            if(start) {
                Thread watcher = new Thread(new Runnable() {

                    @Override
                    public void run() {
                        SmbFile smbFile = null;
                        Log.d(TAG,"####### New JCIFS_WATCHER_THREAD, Watching Thread Num :" + Jcifs_Watch_List.size() + " #######");
                        try {

                            if(IsGuest) {
                                CIFSContext authed = SingletonContext.getInstance().withGuestCrendentials();
                                smbFile = new SmbFile("smb://" + info.getUrl(), authed);
                            }
                            else {
                                CIFSContext authed = SingletonContext.getInstance().withCredentials(
                                        new NtlmPasswordAuthentication(SingletonContext.getInstance(),
                                                "smb://" + info.getUrl(), info.getUsername(), info.getPassword()));
                                smbFile = new SmbFile("smb://" + info.getUrl(), authed);
                            }

                        } catch (MalformedURLException e) {
                            Log.e(TAG, "ERROR MAKING SMB_FILE");
                        }

                        if (smbFile != null) {
                            try {
                                smbFile.watch(1, true).watch();
                            } catch (CIFSException e) {

                                new Handler(getMainLooper()).postDelayed(new Runnable() {
                                    @Override
                                    public void run() {

                                        //user unmount check
                                        SharedPreferences mpref = getApplicationContext().getSharedPreferences("USER_UNMOUNT",MODE_PRIVATE);
                                        mpref.getBoolean(name,false);
                                        if(mpref.getBoolean(name,false)) {
                                            SharedPreferences.Editor editor = mpref.edit();
                                            editor.remove(name);
                                            editor.apply();
                                        }
                                        else if (!SMB_WATHCER_CHECK(false) && !Get_Last_Action()) {

                                            // Host Down Unmounting
                                            boolean IsSuccess = mNetworkStorageManager.unmount(info);
                                            if(IsSuccess) {

                                                // Do unmounting
                                                Log.d(TAG,"####### unmount in smb watcher #####\n ##### db name : " + name + " #######");
                                                info.setIsMounted(0);
                                                mNetworkStorageManager.updateNetStorage(info);
                                                mEditNetworkStorage.SendUnMountBroadcast(info, getApplicationContext());

                                                //show toast msg
                                                Send_UI_Update(true);
                                                String msg = getApplicationContext().getString(R.string.error_network_storage_from_Host, name);
                                                Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();

                                                // Delete info in manage List
                                                int index = Jcifs_Watch_List.indexOf(info.getName());
                                                if (index != -1) {
                                                    Jcifs_Watch_thread.remove(index);
                                                    Jcifs_Watch_List.remove(index);
                                                }
                                            }
                                        }
                                    }
                                },SLEEP_TIME/2);
                            }
                        }
                    }
                });

                Jcifs_Watch_thread.add(watcher);
                Jcifs_Watch_List.add(info.getName());
                watcher.setPriority(Thread.MIN_PRIORITY);
                watcher.start();
            }
            else {
                // user unmount
                int index = Jcifs_Watch_List.indexOf(name);

                if (index != -1) {
                    Thread mThread = Jcifs_Watch_thread.get(index);
                    Jcifs_Watch_thread.remove(index);
                    Jcifs_Watch_List.remove(index);

                    try {
                        mThread.interrupt();
                        mThread.join();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        public void No_SMB_File_Watcher(NetStorageDbInfo info) {

            /*====================================================================================

                User ID / Password가 Null인 경우에는 Jcifs Library 를 사용하지 못함.
                해당 Storage들을 감시하기 위한 Thread. /mnt/cifs/* 의 디렉토리 목록을 읽어와,
                No_Jicfs_Watch_List에 있는 Storage들을 포홤하고 있는지 확인. 반복 시간 3초

                Network 단절, Vpn 상태 변경 등의 이슈로 인하여 해당 디렉토리 목록을 읽어올 때 Block 오류 발생.
                해당 오류 발생시 timeout 처리하여 Timeout되면 빈 리스트(null)이 올라옴.

                이러한 상태를 처리하기 위해 2가지 Flag를 사용.

                1. Get_Last_Action() == true인 경우, 어떠한 문제가 발생하여 Auto Unmount가 진행된 경우임.
                   이때 Mounting Thread가 돌고 있으면, 아무 처리 없이 반복시간 이후 다시 반복.
                   이때 Mounting Thread가 돌고 있지 않으면, 우선 해당 Thread 종료시킴.
                   (Auto Mount 혹은 User Mount시 다시 생성)
                2. 해당 감시 Thread가 if(Get_Last_Action()) 를 통과한 이후, 위의 이슈들이 발생하게 되면
                   빈 List가 생성.(Timeout) 이때, 한번더 Get_Last_Action을 확인하여, true(Unmount가 마지막)
                   이면 1번의 처리와 같이 관리 리스트 clear한 후, 해당 thread 종료.
                   만약 false(Mount가 마지막)인 경우, 한번 더 검사한 후, 그래도 오류 발생된다면
                   모든 Storage Unmount 처리 진행.

                User가 Unmount 한 경우에도 해당 Watcher Thread에도 인식 되는데, 이 경우를 위의 Watcher와 같이
                SharedPreferences를 이용하여 User가 진행한 것인지 확인하도록 설정.

           =====================================================================================*/

            if(No_Jicfs_Watch_List.size() == 0) {

                No_Jicfs_Watch_List.add(info.getName());
                Log.d(TAG,"####### Start new no smb watcher #######");

                Thread Watch_Dog = new Thread(new Runnable() {

                    final Handler mHandler = new Handler(getMainLooper());

                    @Override
                    public void run() {
                        if(No_Jicfs_Watch_List.size() >0) {

                            if(Get_Last_Action()) {

                                if(!Get_Mounting_Running()) {
                                    Log.d(TAG,"####### Die No_SMB_File_Watcher!!! case 1 #######");
                                    No_Jicfs_Watch_List.clear();
                                    return;
                                }
                                else {
                                    mHandler.postDelayed(this,3000);
                                    return;
                                }
                            }

                            if(Finding_Storage) {
                                Log.d(TAG,"####### Skip watching this time #######");
                                mHandler.postDelayed(this,SLEEP_TIME*3);
                            }
                            else {
                                Log.d(TAG,"####### No JCIFS Watch " + No_Jicfs_Watch_List.size() + " Files #######");
                                ArrayList<String> Mounted_Storage_Check = mNetworkStorageManager.findNetworkStorage(getApplicationContext());

                                if(Mounted_Storage_Check != null && Mounted_Storage_Check.size() > 0) {

                                    // /mnt/cifs의 디렉토리 목록과 No_Jicfs_Watch_List 목록을 비교하는 부분,
                                    ArrayList<String> unmount_storage = new ArrayList<String>();

                                    for(String dbname : No_Jicfs_Watch_List) {

                                        String Mount_name = dbname;

                                        if(Mount_name.contains("'"))
                                            Mount_name = Mount_name.replace("'","");
                                        if(dbname.contains("/"))
                                            Mount_name = Mount_name.replace("/","");

                                        if( !Mounted_Storage_Check.contains(Mount_name) && !Get_Last_Action()) {

                                            NetStorageDbInfo dbinfo = NetworkStorageManager.getInstance().getNetStorage(dbname);

                                            SharedPreferences mpref = getApplicationContext().getSharedPreferences("USER_UNMOUNT",MODE_PRIVATE);
                                            if(!mpref.getBoolean(dbname,false)) {

                                                // 유저가 unmount 시킨것이 아닌 경우. Unmounting 진행
                                                Log.d(TAG,"####### UnMounting on Watcher thread! : " + dbname + " #######");
                                                mNetworkStorageManager.unmount(dbinfo);
                                                dbinfo.setIsMounted(0);
                                                mNetworkStorageManager.updateNetStorage(dbinfo);
                                                mEditNetworkStorage.SendUnMountBroadcast(dbinfo, context);

                                                unmount_storage.add(dbname);

                                                //show toast
                                                String msg = getApplicationContext().getString(R.string.error_network_storage_from_Host, dbname);
                                                mHandler.post( new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        Send_UI_Update(true);
                                                        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
                                                    }
                                                });
                                            }
                                            else
                                            {
                                                // 유저가 UnMount 시킨 경우 아무 작업 하지 않음.
                                                unmount_storage.add(dbname);
                                                SharedPreferences.Editor editor = mpref.edit();
                                                editor.remove(dbname);
                                                editor.apply();
                                            }
                                        }
                                    }

                                    if(unmount_storage.size() > 0) {
                                        for( String dbname : unmount_storage) {
                                            boolean remove = No_Jicfs_Watch_List.remove(dbname);
                                            Log.d(TAG,"####### remove : " + remove + ", Watch_Dog_List.size() : "
                                                    + No_Jicfs_Watch_List.size() + ", Watch_Dog_List : " + No_Jicfs_Watch_List + " #######");
                                        }
                                    }

                                    // 해당 Wather Thread 폴링 시키는 부분.
                                    if(No_Jicfs_Watch_List.size() != 0) {
                                        mHandler.postDelayed(this,SLEEP_TIME*3);
                                    }

                                }
                                else {

                                    if(!Get_Last_Action()) {
                                        Log.d(TAG,"####### Die No_SMB_File_Watcher!!! case 2 #######");
                                        final Runnable check_thread = this;
                                        mHandler.postDelayed(new Runnable() {
                                            @Override
                                            public void run() {
                                                new Thread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        ArrayList<String> Mounted_Storage_Check = mNetworkStorageManager.findNetworkStorage(getApplicationContext());
                                                        if(Mounted_Storage_Check == null && !Get_Last_Action() && Mounted_Storage.size() > 0) {
                                                            Log.e(TAG,"####### Long term monitoring error, Unmount All Storage #######");
                                                            No_Jicfs_Watch_List.clear();
                                                            Auto_UnMount(ONLOST_INTERNET);
                                                        }
                                                        else {
                                                            Log.d(TAG,"####### Catch Internal info, Re-Run Watcher #######");
                                                            mHandler.postDelayed(check_thread,SLEEP_TIME*5);
                                                        }
                                                    }
                                                }).start();
                                            }
                                        },SLEEP_TIME*3);
                                    }
                                    else {
                                        Log.d(TAG,"####### Die No_SMB_File_Watcher!!! case 3 #######");
                                        No_Jicfs_Watch_List.clear();
                                    }
                                }
                            }
                        }
                    }
                });
                Watch_Dog.setPriority(Thread.MIN_PRIORITY);
                Watch_Dog.start();
            }
            else {
                String find_name = info.getName();
                boolean find = false;
                for(String dbname : No_Jicfs_Watch_List) {
                    if(dbname.equals(find_name)) {
                        find = true;
                        break;
                    }
                }
                if(!find) {
                    Log.d(TAG,"####### Add No_SMB_File_Watcher, name : " + find_name +" #######");
                    No_Jicfs_Watch_List.add(find_name);
                }
            }
        }

        private void Check_Guest_Available(NetStorageDbInfo info) {

            /*====================================================================================

                STORAGE_MOUNTED Intent를 받은 후, 해당 Storage의 User ID/Password중 하나라도 NUll이면
                이 부분으로 이동. 이 함수에서 Guest권한으로 접근할 수 있는지 확인 진행
                Check_Guest.list(); 이 부분에서 Exception이 발생되지 않는다면 가능, 발생하면 불가능.

                가능하다면, Jcifs Lib을 사용하기 위해 SMB_File_Watcher( , , , true)로 해당 db 정보를 넘겨줌.
                불가능하면, No_SMB_File_Watcher()로 해당 db정보를 넘겨줌.

            =====================================================================================*/

            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        SmbFile Check_Guest = new SmbFile("smb://" + info.getUrl(),
                                        SingletonContext.getInstance().withGuestCrendentials());

                        Check_Guest.list(); // Guest가 안되면 여기서 exception 발생 윈도우 버전은 ex 뜨는중.

                        Log.d(TAG,"####### storage " + info.getName() + " Registed SMB_FILE_WATCHER With Guest Credentials #######");
                        SMB_File_Watcher(info,true,info.getName(),true);
                    } catch (MalformedURLException | SmbException e) {
                        No_SMB_File_Watcher(info);
                    }
                }
            }).start();
        }

        /*===========================================================================================================*/
        /*===========================================================================================================*/


        public class Vpn_Receiver extends BroadcastReceiver {

        /*====================================================================================

    `       Vpn 상태 변화 Storage Mount / UnMount 관련 Intent 수신해서 처리를 지원해줌.

            1 Vpn 상태 변화 수신
                Vpn이 처음 변화 됐을 때, Auto Unmount - Auto Mount 진행.
                Vpn이 처음 변화된 이후, 변화를 감지 했을 때는, Set_Vpn_ReConn을 하여 Auto mount하는 과정에서
                이를 체크하여, 다시 변화 돼었으면, Auto Mount 했던 모든 Storage들을 Re_UnMount한 후,
                다시 Auto Mount진행하도록 함.

            2 Storage Mount 수신
                Mount된 Storage가 network Storage라면, 해당 Storage에 대해
                User ID/Password를 확인 한 후, 하나라도 null이라면, Check_Guest_Available를 호출
                둘 모두 null이 아니라면, SMB_File_Watcher 를 호출한 후, Mounted_Storage list에 추가.

            3 Storage Unmount 수신
                UnMount된 Storage가 network Storage라면, SMB_File_Watcher로 start = false값을 전달
                SMB_File_Watcher에서 false를 받으면, 해당 Storage를 감시하는 Thread가 있다면,
                해당 Thread에 Interrupt를 발생시켜 감시를 중단하도록 하고,
                Mounted_Storage List에서 해당 Storage를 remove.

        =====================================================================================*/

            private boolean Latest_vpn_stat;

            public void init() {
                Latest_vpn_stat = isVpnConnected();
            }

            public boolean Get_Latest_Vpn_Stat() {
                return Latest_vpn_stat;
            }

            @Override
            public void onReceive(Context context, Intent intent) {

                if(intent.getAction() != null) {
                    switch (intent.getAction()) {

                        case "android.net.conn.CONNECTIVITY_CHANGE":
                            if (Latest_vpn_stat != isVpnConnected()) {
                                Latest_vpn_stat = isVpnConnected();

                                if(!Get_Vpn_Status() && !isBootComplete && Mounted_Storage.size() > 0) {

                                    Set_Vpn_Status(true);

                                    if(!Get_UnMounting_Running()) {

                                        Set_UnMounting_Running(true);
                                        Set_Last_Action(true);

                                        Thread Vpn_Chage_Remounting = new Thread(new Runnable() {
                                            @Override
                                            public void run() {
                                                Log.d(TAG,"####### Vpn Status Change Auto Unmount - Mount #######");
                                                Auto_UnMount(UNMOUNT_VPN_STATUS);
                                            }
                                        });
                                        Vpn_Chage_Remounting.setPriority(Thread.MAX_PRIORITY);
                                        Vpn_Chage_Remounting.start();
                                    }
                                }
                                else {
                                    if(Get_Vpn_Status()) {
                                        Set_Vpn_ReConn(true);
                                    }
                                }
                            }
                            break;

                        case "aloys.intent.action.STORAGE_MOUNTED":
                            if(Mounted_Storage == null)
                                Mounted_Storage = new ArrayList<String>();

                            String start_name = intent.getStringExtra("net_storage_name");

                            if( start_name != null) {

                                if(Get_Last_Action()) {
                                    if(!Get_Mounting_Running() && !Get_UnMounting_Running())
                                        Set_Last_Action(false);
                                }

                                Mounted_Storage.add(start_name);

                                NetStorageDbInfo info = mNetworkStorageManager.getNetStorage(start_name);

                                if(info.getUsername() != null && info.getPassword() != null )
                                    SMB_File_Watcher(info, true, start_name, false);
                                else
                                    Check_Guest_Available(info);
                            }

                            break;

                        case "aloys.intent.action.STORAGE_BAD_REMOVAL":
                            String stop_name = intent.getStringExtra("net_storage_name");

                            if(stop_name != null && Mounted_Storage != null) {
                                Mounted_Storage.remove(stop_name);
                                SMB_File_Watcher(null, false, stop_name, false);
                            }
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
