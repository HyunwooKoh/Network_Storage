package tv.formuler.service.gtv.networkstorage;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.text.Html;
import android.text.InputType;
import android.text.Spanned;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.leanback.app.GuidedStepSupportFragment;
import androidx.leanback.widget.GuidanceStylist;
import androidx.leanback.widget.GuidedAction;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;


public class EditNetworkStorage extends GuidedStepSupportFragment {

    public final String TAG = "EditNetworkStorage";

    private NetStorageDbInfo netStorageInfo;
    private String ava_address = null;
    private boolean isMounted = false;
    private NetStorageDbInfo mRequestMountStorageInfo;
    private NetStorageDbInfo mRequestUnmountStorageInfo;
    private long mClickedBtn = ID_INVALID;

    private String mMountFailStr;

    public static final int ID_INVALID = 0x1000; //4096
    public static final int ID_MOUNT_BTN = 0x1002; //4098
    public static final int ID_MOUNT_NEW_BTN = 0x1003; //4099
    public static final int ID_UNMOUNT_BTN = 0x1004; //4010
    public static final int ID_DELETE_BTN = 0x1005; //4011

    private static ArrayList<NetStorageDbInfo> mNetStorageDbInfoList;

    public static void showToast(Context context, String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    public static EditNetworkStorage newInstance() {
        return new EditNetworkStorage();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {

        if (getArguments() != null) {
            ava_address = getArguments().getString("address_info",null);
            if(ava_address == null) {
                netStorageInfo = getArguments().getParcelable("storage_info");
                isMounted = getArguments().getBoolean("mount_info",false);
            }
        }

        mMountFailStr = getString(R.string.network_storage_mount_fail);
        super.onCreate(savedInstanceState);
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
    public void onCreateActions(@NonNull List<GuidedAction> actions, Bundle savedInstanceState) {
        refreshActions(actions);
    }

    @Override
    public void onCreateButtonActions(@NonNull List<GuidedAction> actions, Bundle savedInstanceState) {
        if (netStorageInfo != null) {
            if (isMounted) {
                GuidedAction unmountAction = new GuidedAction.Builder(getContext())
                        .id(ID_UNMOUNT_BTN)
                        .title(getString(R.string.quick_settings_network_storage_unmount))
                        .build();
                actions.add(unmountAction);
            } else {
                GuidedAction unmountAction = new GuidedAction.Builder(getContext())
                        .id(ID_MOUNT_BTN)
                        .title(getString(R.string.quick_settings_network_storage_mount))
                        .build();
                actions.add(unmountAction);
            }

            GuidedAction deleteAction = new GuidedAction.Builder(getContext())
                    .id(ID_DELETE_BTN)
                    .title(getString(R.string.quick_settings_network_storage_delete))
                    .build();
            actions.add(deleteAction);
        } else {
            GuidedAction mountAction = new GuidedAction.Builder(getContext())
                    .id(ID_MOUNT_NEW_BTN)
                    .title(getString(R.string.quick_settings_network_storage_mount))
                    .build();
            actions.add(mountAction);
        }
    }

    @Override
    public void onGuidedActionClicked(GuidedAction action) {
        long id = action.getId();

        if (id == ID_MOUNT_BTN || id == ID_MOUNT_NEW_BTN
                || id == ID_UNMOUNT_BTN || id == ID_DELETE_BTN) {
            List<NetStorageDbInfo> dbInfos = NetworkStorageManager.getInstance().getNetStorageList();
            List<GuidedAction> actions = getActions();
            boolean isSuccess = false;
            String name = actions.get(0).getDescription() != null ? actions.get(0).getDescription().toString() : null;
            String url;
            if(ava_address != null )
                url = ava_address;
            else
                url = actions.get(1).getDescription() != null ? actions.get(1).getDescription().toString() : null;
            String username = actions.get(2).getDescription() != null ? actions.get(2).getDescription().toString() : null;
            String password = actions.get(3).getDescription() != null ? actions.get(3).getDescription().toString() : null;
            boolean isReconnect = actions.get(4).isChecked();

            mClickedBtn = ID_INVALID;

            if (id == ID_MOUNT_BTN) {
                mRequestMountStorageInfo = netStorageInfo;
                isSuccess = NetworkStorageManager.getInstance().mount(mRequestMountStorageInfo);

                if (!isSuccess) {
                    mRequestMountStorageInfo = null;
                    showToast(getActivity(), mMountFailStr);
                } else {
                    SendMountBroadcast(mRequestMountStorageInfo,getActivity());
                    mClickedBtn = id;
                    String message = getString(R.string.network_storage_connect_to, name);
                    showToast(getActivity(), message);
                    mRequestMountStorageInfo.setIsMounted(1);
                    NetworkStorageManager.getInstance().updateNetStorage(mRequestMountStorageInfo);
                    getFragmentManager().popBackStack();
                }

            } else if (id == ID_MOUNT_NEW_BTN) {
                if (TextUtils.isEmpty(name)) {
                    showToast(getActivity(), getString(R.string.quick_settings_network_storage_warning_name_empty));
                } else if (TextUtils.isEmpty(url)) {
                    showToast(getActivity(), getString(R.string.quick_settings_network_storage_warning_path_empty));
                } else {

                    // 이미 등록된 NetworkStorage가 있을 때, 이름 / url이 동일한게 존재하는지 확인
                    if (dbInfos != null && dbInfos.size() > 0) {
                        for (NetStorageDbInfo dbInfo : dbInfos) {
                            if (dbInfo.getName().equals(name)) {
                                showToast(getActivity(), getString(R.string.quick_settings_network_storage_warning_name_exist));
                                return ;
                            } else if (dbInfo.getUrl().equals(url)) {
                                showToast(getActivity(), getString(R.string.quick_settings_network_storage_warning_path_exist));
                                return ;
                            }
                        }
                    }

                    // DB Insert 후 mount 시도
                    NetworkStorageManager.getInstance().insertNetStorage(name, url, username, password, isReconnect, null, null, false);
                    mRequestMountStorageInfo = NetworkStorageManager.getInstance().getNetStorage(name); // db에 insert 됬는지 확인
                    if (mRequestMountStorageInfo == null) {
                        showToast(getActivity(), getString(R.string.quick_settings_network_storage_warning_regist_fail));
                        return ;
                    }

                    // Mount 시도
                    isSuccess = NetworkStorageManager.getInstance().mount(mRequestMountStorageInfo); //NetworkStorageMg로 anapi를 사용하도록 mount 요청 보냄
                    if (!isSuccess) {
                        NetworkStorageManager.getInstance().deleteNetStorage(mRequestMountStorageInfo);
                        showToast(getActivity(), mMountFailStr);
                        mRequestMountStorageInfo = null;
                    } else {
                        SendMountBroadcast(mRequestMountStorageInfo,getActivity());
                        mClickedBtn = id;
                        String message = getString(R.string.network_storage_connect_to, name);
                        mRequestMountStorageInfo.setIsMounted(1);
                        NetworkStorageManager.getInstance().updateNetStorage(mRequestMountStorageInfo);
                        showToast(getActivity(), message);
                        NetworkStorageService.getInstance().Find_Available_Storage(mRequestMountStorageInfo.getUrl(),true);
                        getFragmentManager().popBackStack();
                    }

                }

            } else if (id == ID_UNMOUNT_BTN) {
                mRequestUnmountStorageInfo = NetworkStorageManager.getInstance().getNetStorage(name);

                SharedPreferences.Editor editor = getContext().getSharedPreferences("USER_UNMOUNT",Context.MODE_PRIVATE).edit();
                editor.putBoolean(name,true);
                editor.apply();

                isSuccess = NetworkStorageManager.getInstance().unmount(mRequestUnmountStorageInfo);

                if (!isSuccess) {
                    mRequestUnmountStorageInfo = null;
                    editor.remove(name);
                    editor.apply();
                    showToast(getActivity(), getString(R.string.network_storage_unmount_fail));
                    getFragmentManager().popBackStack();
                } else {
                    mRequestUnmountStorageInfo.setIsMounted(0);
                    NetworkStorageManager.getInstance().updateNetStorage(mRequestUnmountStorageInfo);
                    SendUnMountBroadcast(mRequestUnmountStorageInfo,getActivity());
                    mClickedBtn = id;
                    String message = getString(R.string.network_storage_disconnect_to, name);
                    showToast(getActivity(), message);
                    getFragmentManager().popBackStack();
                }

            } else if (id == ID_DELETE_BTN) {
                mRequestUnmountStorageInfo = NetworkStorageManager.getInstance().getNetStorage(name);

                if (isMounted) {
                    // 만약 mount가 되있는 상태라면 umnount 후 delete 진행
                    SharedPreferences.Editor editor = getContext().getSharedPreferences("USER_UNMOUNT",Context.MODE_PRIVATE).edit();
                    editor.putBoolean(name,true);
                    editor.apply();
                    isSuccess = NetworkStorageManager.getInstance().unmount(mRequestUnmountStorageInfo);

                    if (!isSuccess) {
                        showToast(getActivity(), getString(R.string.quick_settings_network_storage_warning_unmount_fail));
                        editor.remove(name);
                        editor.apply();
                        return ;
                    } else {
                        SendUnMountBroadcast(mRequestUnmountStorageInfo,getActivity());
                        mClickedBtn = id;
                        String message = getString(R.string.network_storage_disconnect_to, name);
                        isSuccess = NetworkStorageManager.getInstance().deleteNetStorage(mRequestUnmountStorageInfo); //delete 진행

                        if(isSuccess) {
                            isMounted = false;
                            message += " and ";
                            message += getString(R.string.quick_settings_network_storage_warning_delete_success);
                            showToast(getActivity(), message);
                            NetworkStorageService.getInstance().Find_Available_Storage(mRequestUnmountStorageInfo.getUrl(),false);
                            getFragmentManager().popBackStack();
                        } else {
                            showToast(getActivity(), message);
                            getFragmentManager().popBackStack();
                        }

                    }

                } else {
                    isMounted = false;
                    isSuccess = NetworkStorageManager.getInstance().deleteNetStorage(mRequestUnmountStorageInfo);

                    if (isSuccess) {
                        showToast(getActivity(), getString(R.string.quick_settings_network_storage_warning_delete_success));
                        getActivity().getSupportFragmentManager().popBackStack();
                    } else {
                        showToast(getActivity(), getString(R.string.quick_settings_network_storage_warning_delete_fail));
                    }

                }

            }
        }
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        Spanned content = Html.fromHtml(getString(R.string.quick_settings_network_storage_desc,
                "<br><font color='#ffff00'>(example:192.168.10.100/share folder)</font>"), Html.FROM_HTML_MODE_COMPACT);
        getGuidanceStylist().getDescriptionView().setText(content);
        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public long onGuidedActionEditedAndProceed(GuidedAction action) {

        if (action.getEditDescription() != null) {
            action.setDescription(action.getEditDescription());
        }
        action.setEditDescription("");
        guidedActionEdited(action);

        return GuidedAction.ACTION_ID_CURRENT;
    }

    @Override
    public void onGuidedActionEditCanceled(GuidedAction action) {

        if (action.getEditDescription() != null) {
            action.setDescription(action.getEditDescription());
        }
        action.setEditDescription("");
        guidedActionEdited(action);
    }

    private void refreshActions(List<GuidedAction> actions) {
        int index = 0;
        GuidedAction nameAction = null;
        GuidedAction urlAction = null;
        GuidedAction usernameAction = null;
        GuidedAction passwordAction = null;
        GuidedAction reconnectAction = null;
        if(ava_address != null) { // Come by Select Available Network Storage
            nameAction = new GuidedAction.Builder(getActivity())
                    .id(index++)
                    .title(getString(R.string.quick_settings_network_storage_name))
                    .editable(false)
                    .descriptionEditable(true)
                    .inputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
                    .build();
            urlAction = new GuidedAction.Builder(getActivity())
                    .id(index++)
                    .title(getString(R.string.quick_settings_network_storage_url))
                    .description(ava_address)
                    .editable(false)
                    .descriptionEditable(false)
                    .inputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
                    .build();
            usernameAction = new GuidedAction.Builder(getActivity())
                    .id(index++)
                    .title(getString(R.string.quick_settings_network_storage_username))
                    .editable(false)
                    .descriptionEditable(true)
                    .inputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
                    .build();
            passwordAction = new GuidedAction.Builder(getActivity())
                    .id(index++)
                    .title(getString(R.string.quick_settings_network_storage_password))
                    .editable(false)
                    .descriptionEditable(true)
                    .inputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
                    .build();
            reconnectAction = new GuidedAction.Builder(getActivity())
                    .id(index++)
                    .title(getString(R.string.quick_settings_network_storage_reconnected_startup))
                    .checkSetId(GuidedAction.CHECKBOX_CHECK_SET_ID)
                    .checked(true)
                    .build();
        }
        else {
            if (netStorageInfo != null) { // Aleady inserted network Storage
                nameAction = new GuidedAction.Builder(getActivity())
                        .id(index++)
                        .title(getString(R.string.quick_settings_network_storage_name))
                        .description(netStorageInfo.getName())
                        .build();
                urlAction = new GuidedAction.Builder(getActivity())
                        .id(index++)
                        .title(getString(R.string.quick_settings_network_storage_url))
                        .description(netStorageInfo.getUrl())
                        .build();
                usernameAction = new GuidedAction.Builder(getActivity())
                        .id(index++)
                        .title(getString(R.string.quick_settings_network_storage_username))
                        .description(netStorageInfo.getUsername())
                        .build();
                passwordAction = new GuidedAction.Builder(getActivity())
                        .id(index++)
                        .title(getString(R.string.quick_settings_network_storage_password))
                        .description(netStorageInfo.getPassword())
                        .build();
                reconnectAction = new GuidedAction.Builder(getActivity())
                        .id(index++)
                        .title(getString(R.string.quick_settings_network_storage_reconnected_startup))
                        .checkSetId(GuidedAction.CHECKBOX_CHECK_SET_ID)
                        .checked(netStorageInfo.isReconnect())
                        .enabled(false)
                        .build();
            } else { // New Network Storage
                nameAction = new GuidedAction.Builder(getActivity())
                        .id(index++)
                        .title(getString(R.string.quick_settings_network_storage_name))
                        .editable(false)
                        .descriptionEditable(true)
                        .inputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
                        .build();
                urlAction = new GuidedAction.Builder(getActivity())
                        .id(index++)
                        .title(getString(R.string.quick_settings_network_storage_url))
                        .editable(false)
                        .descriptionEditable(true)
                        .inputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
                        .build();
                usernameAction = new GuidedAction.Builder(getActivity())
                        .id(index++)
                        .title(getString(R.string.quick_settings_network_storage_username))
                        .editable(false)
                        .descriptionEditable(true)
                        .inputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
                        .build();
                passwordAction = new GuidedAction.Builder(getActivity())
                        .id(index++)
                        .title(getString(R.string.quick_settings_network_storage_password))
                        .editable(false)
                        .descriptionEditable(true)
                        .inputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
                        .build();
                reconnectAction = new GuidedAction.Builder(getActivity())
                        .id(index++)
                        .title(getString(R.string.quick_settings_network_storage_reconnected_startup))
                        .checkSetId(GuidedAction.CHECKBOX_CHECK_SET_ID)
                        .checked(true)
                        .build();
            }
        }
        actions.add(nameAction);
        actions.add(urlAction);
        actions.add(usernameAction);
        actions.add(passwordAction);
        actions.add(reconnectAction);
    }

    private void guidedActionEdited(GuidedAction action) {
        String desc =  action.getDescription() == null ? "" : action.getDescription().toString();

        if (action.getId() == 1 && !TextUtils.isEmpty(desc) && desc.startsWith("//")) {
            desc = desc.replaceAll("//", "");
        }

        action.setDescription(desc);

        int position = getSelectedActionPosition();
        View view = getActionItemView(position);
        EditText editText = (EditText) view.findViewById(R.id.guidedactions_item_description);
        editText.setText(action.getDescription());
    }

    public void updateNetworkStorageNetworkInfo(NetStorageDbInfo storageDbInfo) {
        NetworkInfo activeNetwork = getActiveNetworkInfo(getActivity());
        String networkInfo = null;

        if (activeNetwork.getType() == ConnectivityManager.TYPE_ETHERNET) {
            networkInfo = activeNetwork.getTypeName();
        } else if (activeNetwork.getType() == ConnectivityManager.TYPE_WIFI) {
            networkInfo = activeNetwork.getTypeName();
            WifiManager wifiManager = (WifiManager) getActivity().getSystemService(Context.WIFI_SERVICE);
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            String ssid = wifiInfo.getSSID().replaceAll("\"", "");
            networkInfo += "|" + ssid + "|" + wifiInfo.getMacAddress();
        }

        storageDbInfo.setNetworkInfo(networkInfo);
    }

    public static NetworkInfo getActiveNetworkInfo(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        return networkInfo;
    }

    public void SendMountBroadcast(NetStorageDbInfo mRequestMountStorageInfo, Context context) {
        final String mountpoint = mRequestMountStorageInfo.getMountPath();
        final String device = "//" + mRequestMountStorageInfo.getUrl();
        final String net_storage_name = mRequestMountStorageInfo.getName();

        Intent intent = new Intent("aloys.intent.action.STORAGE_MOUNTED");
        intent.putExtra("mountpoint",mountpoint);
        intent.putExtra("device",device);
        intent.putExtra("net_storage_name",net_storage_name);
        intent.putExtra("fstype","cifs");
        intent.putExtra("fssize",55555555L);
        context.sendBroadcast(intent);
    }

    public void SendUnMountBroadcast(NetStorageDbInfo mRequestMountStorageInfo, Context context) {
        final String mountpoint = mRequestMountStorageInfo.getMountPath();
        final String device = "//" + mRequestMountStorageInfo.getUrl();
        final String net_storage_name = mRequestMountStorageInfo.getName();

        Intent intent = new Intent("aloys.intent.action.STORAGE_BAD_REMOVAL");
        intent.putExtra("mountpoint", mountpoint);
        intent.putExtra("device", device);
        intent.putExtra("net_storage_name",net_storage_name);
        intent.putExtra("fstype", "cifs");
        intent.putExtra("fssize", 55555555L);
        context.sendBroadcast(intent);
    }
}