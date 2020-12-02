package tv.formuler.service.gtv.networkstorage;


import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.leanback.app.GuidedStepSupportFragment;

import android.os.Bundle;


public class MainActivity extends FragmentActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            GuidedStepSupportFragment.addAsRoot(this, NetworkStorageDialog.newInstance(),
                    android.R.id.content);
        }
    }

}