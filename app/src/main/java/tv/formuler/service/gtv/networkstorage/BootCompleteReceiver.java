package tv.formuler.service.gtv.networkstorage;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemProperties;
import java.util.Objects;

public class BootCompleteReceiver extends BroadcastReceiver {

    @Override
    public void onReceive (Context context, Intent intent) {

        if (Objects.requireNonNull(intent.getAction()).equals(Intent.ACTION_BOOT_COMPLETED)) {

            if( !SystemProperties.getBoolean("persist.sys.aloys.networkstorage",false)) {
                SystemProperties.set("persist.sys.aloys.networkstorage", String.valueOf(true));
            }

            Intent i = new Intent(context, NetworkStorageService.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            i.putExtra("isBootComplete",true);
            context.startService(i);
        }
    }
}
