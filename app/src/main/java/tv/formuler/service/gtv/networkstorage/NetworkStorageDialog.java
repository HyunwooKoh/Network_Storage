package tv.formuler.service.gtv.networkstorage;

import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import org.jetbrains.annotations.Nullable;
import androidx.leanback.app.GuidedStepSupportFragment;
import androidx.leanback.widget.GuidanceStylist;
import androidx.leanback.widget.GuidedAction;

import java.util.ArrayList;
import java.util.List;

import tv.formuler.service.gtv.networkstorage.search.FindSamba;


public class NetworkStorageDialog extends GuidedStepSupportFragment {

    private static final String TAG = "NetworkStorageDialog";
    public static final int ID_ADD_SERVER = 0x1001;
    public static final int ID_AVAILABLE_SERVER = 0x1006;

    private static ArrayList<NetStorageDbInfo> mNetStorageDbInfoList;
    private static ArrayList<String> connectible_Server;
    private List<GuidedAction> actions; // [ADD] aloys harold - for use in both Task

    public static NetworkStorageDialog newInstance() {
        return new NetworkStorageDialog();
    }

    public  void updateNetStorageDbInfoList() {
        mNetStorageDbInfoList = NetworkStorageManager.getInstance().getNetStorageList();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        NetworkStorageManager.getInstance().initialize(getActivity());
        updateNetStorageDbInfoList();
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
        refreshActions();
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onResume() {
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
    }

    // Add Available Network Storage, Inserted Network Storage and Adding Server action
    private void refreshActions() {
        actions = new ArrayList<>(); // [ADD] aloys harold - initialize actions
        int index = 0;

        // Network Storage list
        updateNetStorageDbInfoList(); // update List
        if (mNetStorageDbInfoList != null && mNetStorageDbInfoList.size() > 0) {
            for (NetStorageDbInfo info : mNetStorageDbInfoList) {
                Drawable drawable = getResources().getDrawable(R.drawable.quick_settings_network_storage, null);

                GuidedAction itemAction = new GuidedAction.Builder(getActivity())
                        .id(index++)
                        .title(info.getName())
                        .description(info.getUrl())
                        .icon(drawable)
                        .build();
                actions.add(itemAction);
            }
        }

        // Add Server
        GuidedAction action_add_server = new GuidedAction.Builder(getActivity())
                .id(ID_ADD_SERVER)
                .title(getString(R.string.quick_settings_network_storage_add_server))
                .build();
        actions.add(action_add_server);

        setActions(actions);

        refreshAvailable_server find_server = new refreshAvailable_server();
        find_server.execute();

    }

    // [ADD] aloys harold - for better performance [
    public class refreshAvailable_server extends AsyncTask<Void,String,Void> {
        private static final String network_storage_Available_Server = "Available NetworkStorage";
        private ArrayList<String> AvailableServer;
        public int count;

        @Override
        protected Void doInBackground(Void... voids){

            AvailableServer = null;
            connectible_Server = new ArrayList<String>();

            AvailableServer = FindSamba.newInstance().start(getActivity());

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
            super.onCancelled();
        }
    }
    // ]
}