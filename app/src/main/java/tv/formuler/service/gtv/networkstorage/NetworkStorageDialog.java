package tv.formuler.service.gtv.networkstorage;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.leanback.app.GuidedStepSupportFragment;
import androidx.leanback.widget.GuidanceStylist;
import androidx.leanback.widget.GuidedAction;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;


public class NetworkStorageDialog extends GuidedStepSupportFragment {

    private static final String TAG = "NetworkStorageDialog";
    public static final int ID_ADD_SERVER = 0x1001;
    public static final int ID_AVAILABLE_SERVER = 0x1006;

    private static ArrayList<NetStorageDbInfo> mNetStorageDbInfoList;
    private static ArrayList<String> connectible_Server;
    private ArrayList<GuidedAction> actions; // [ADD] aloys harold - for use in both Task
    private static ArrayList<String> Mounted_List;

    public NetworkStorageService mService;
    private UI_Update_Receiver mUI_Update_Receiver;

    private boolean bound = false;
    private boolean UI_Update = false;
    private boolean Show_Avail = true;

    public static NetworkStorageDialog newInstance() {
        return new NetworkStorageDialog();
    }

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            NetworkStorageService.ServiceBinder binder = (NetworkStorageService.ServiceBinder) service;
            mService = binder.getService();
            if(mService != null) {
                bound = true;
                Update_Mount_List();
                mService.Find_Available_Storage("aloys.init.availablestorage",false);
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            mService = null;
            bound = false;
        }
    };

    private class UI_Update_Receiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.getAction().equals("aloys.intent.action.networkstorage_ui_update")) {
                if(mService != null && bound) {
                    UI_Update = intent.getBooleanExtra("UI_Update",false);
                    Show_Avail = intent.getBooleanExtra("Show_Avail",true);
                    Update_Mount_List();
                    if(UI_Update && Show_Avail)
                        mService.Find_Available_Storage("aloys.init.availablestorage",false);
                }
            }
            else if(intent.getAction().equals("aloys.intent.action.networkstorage_ui_update_avaServer")) {
                connectible_Server = intent.getStringArrayListExtra("available_server");
                refreshActions();
            }
        }
    }

    private void register() {
        mUI_Update_Receiver = new UI_Update_Receiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction("aloys.intent.action.networkstorage_ui_update");
        filter.addAction("aloys.intent.action.networkstorage_ui_update_avaServer");
        getActivity().registerReceiver(mUI_Update_Receiver, filter);
    }

    private void unregister() {
        getActivity().unregisterReceiver(mUI_Update_Receiver);
    }

    public  void updateNetStorageDbInfoList() {
        mNetStorageDbInfoList = NetworkStorageManager.getInstance().getNetStorageList();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        NetworkStorageManager.getInstance().initialize(getActivity());
        updateNetStorageDbInfoList();
        actions = new ArrayList<GuidedAction>();
        register();
    }

    @NonNull
    @Override
    public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
        String title = getString(R.string.quick_settings_network_storage);
        String content = getString(R.string.quick_settings_network_storage_desc, "");
        int iconResId = R.drawable.quick_settings_network_storage;
        Drawable drawable = null;
        if (iconResId != -1) {
            drawable = getActivity().getDrawable(iconResId);
        }
        GuidanceStylist.Guidance guidance = new GuidanceStylist.Guidance(title, content, null, drawable);
        return guidance;
    }

    @Override
    public GuidanceStylist onCreateGuidanceStylist() {
        return super.onCreateGuidanceStylist();
    }

    @Override
    public void onGuidedActionClicked(GuidedAction action) {
        int id = (int) action.getId();
        // Add New Network Storage
        if (id == ID_ADD_SERVER) {
            EditNetworkStorage fragment = new EditNetworkStorage();
            GuidedStepSupportFragment.add(getFragmentManager(), fragment, android.R.id.content);
        }
        // Select Available Network Storage
        else if(id >= ID_AVAILABLE_SERVER) {
            EditNetworkStorage fragment = new EditNetworkStorage();
            Bundle bundle = new Bundle();
            bundle.putString("address_info", connectible_Server.get(id - ID_AVAILABLE_SERVER));
            fragment.setArguments(bundle);
            GuidedStepSupportFragment.add(getFragmentManager(), fragment, android.R.id.content);
        }
        // Select Inserted Network Storage
        else {
            EditNetworkStorage fragment = new EditNetworkStorage();
            Bundle bundle = new Bundle();
            bundle.putParcelable("storage_info", mNetStorageDbInfoList.get(id));
            bundle.putBoolean("mount_info", IsMounted(mNetStorageDbInfoList.get(id)));
            fragment.setArguments(bundle);
            GuidedStepSupportFragment.add(getFragmentManager(), fragment, android.R.id.content);
        }
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        Intent service = new Intent(getContext(), NetworkStorageService.class);
        getActivity().bindService(service, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onResume() {
        if(bound) {
            Update_Mount_List();
        }
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if(bound) {
            getActivity().unbindService(mConnection);
            bound = false;
        }

        unregister();
    }

    private void Update_Mount_List() {

        if(mService != null && bound) {
            Mounted_List = mService.Get_Mounted_List();
        }
        else {
            Mounted_List = null;
        }

        refreshActions();
    }

    private boolean IsMounted(NetStorageDbInfo info) {

        if(Mounted_List != null && Mounted_List.size() > 0) {
            return Mounted_List.contains(info.getName());
        }

        return false;
    }

    // Add Available Network Storage, Inserted Network Storage and Adding Server action
    private void refreshActions() {
        actions.clear(); // [ADD] aloys harold - initialize actions
        int index = 0;

        // Network Storage list
        updateNetStorageDbInfoList(); // update List
        if (mNetStorageDbInfoList != null && mNetStorageDbInfoList.size() > 0) {
            for (NetStorageDbInfo info : mNetStorageDbInfoList) {
                Drawable drawable = IsMounted(info) ?
                        getResources().getDrawable(R.drawable.ic_mounted, null) : getResources().getDrawable(R.drawable.ic_unmounted, null);

                GuidedAction itemAction = new GuidedAction.Builder(getActivity())
                        .id(index++)
                        .title(info.getName())
                        .description(info.getUrl())
                        .icon(drawable)
                        .build();
                actions.add(itemAction);

                if(UI_Update)
                    notifyActionChanged(index-1);
            }
        }

        // Add Server
        GuidedAction action_add_server = new GuidedAction.Builder(getActivity())
                .id(ID_ADD_SERVER)
                .title(getString(R.string.quick_settings_network_storage_add_server))
                .build();
        actions.add(action_add_server);

        if(Show_Avail) {
            if(connectible_Server != null && connectible_Server.size() > 0) {
                int count = 0;

                for (String address : connectible_Server) {
                    GuidedAction itemAction = new GuidedAction.Builder(getActivity())
                            .id(ID_AVAILABLE_SERVER + count++)
                            .title("Available NetworkStorage")
                            .description(address)
                            .build();
                    actions.add(itemAction);
                }
            }
        }

        setActions(actions);

        //find_server = new refreshAvailable_server();
        //find_server.execute();
    }

    /* [ADD] aloys harold - for better performance [
    public class refreshAvailable_server extends AsyncTask<Void,String,Void> {
        private static final String network_storage_Available_Server = "Available NetworkStorage";
        private ArrayList<String> AvailableServer;

        private Thread Find_Thread;
        private boolean Find_Thread_Running;
        public int count;

        @Override
        protected Void doInBackground(Void... voids){

            AvailableServer = null;
            connectible_Server = new ArrayList<String>();

            Find_Thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    Find_Thread_Running = true;
                    AvailableServer = FindSamba.newInstance().start(getContext());
                }
            });

            Find_Thread.start();
            try {
                Find_Thread.join();
            } catch (InterruptedException e) {
                //e.printStackTrace();
            }

            Find_Thread_Running = false;
            if(AvailableServer != null && AvailableServer.size() > 0 ) {
                count = 0;
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
                        publishProgress(address);
                        connectible_Server.add(address);
                    }
                }
            }
            return null;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected void onPostExecute(Void voids) {
            super.onPostExecute(voids);

            if(UI_Update)
                UI_Update = false;
        }

        @Override
        protected void onProgressUpdate(String... server) {
            GuidedAction temp = new GuidedAction.Builder(getActivity())
                    .id(ID_AVAILABLE_SERVER + count++)
                    .title(network_storage_Available_Server)
                    .description(server[0])
                    .build();
            actions.add(temp);
            setActions(actions);
            super.onProgressUpdate(server);
        }

        @Override
        protected void onCancelled() {

            if(Find_Thread_Running) {
                Find_Thread.interrupt();
            }

            if(UI_Update) {
                UI_Update = false;
            }

            super.onCancelled();
        }
    }
    */
}