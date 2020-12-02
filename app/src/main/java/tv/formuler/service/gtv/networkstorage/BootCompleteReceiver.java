package tv.formuler.service.gtv.networkstorage;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.ArrayList;

public class BootCompleteReceiver extends BroadcastReceiver {

    @Override
    public void onReceive (Context context, Intent intent) {

        if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
            Intent i = new Intent(context, NetworkStatusListener.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            i.putExtra("isBootComplete",true);
            context.startService(i);
        }
    }
}
