package tv.formuler.service.gtv.networkstorage;

import android.content.Context;
import android.os.RemoteException;

import java.util.ArrayList;

import vendor.amlogic.hardware.hwan.V1_0.IHwAn;
import vendor.amlogic.hardware.hwan.V1_0.IHwAnCallback;
import vendor.amlogic.hardware.hwan.V1_0.dataPack;

public class AnApi
{
    private IHwAn mHwan;
    private Context mAppContext;

    public final static int NOTIFYMSG_SYSTEM_BASE           = 0x0900;
    public final static int NOTIFYMSG_SYSTEM_NETDRIVE_Mount = NOTIFYMSG_SYSTEM_BASE | 0x16;
    public final static int NOTIFYMSG_SYSTEM_NETDRIVE_UnMount = NOTIFYMSG_SYSTEM_BASE | 0x17;

    public AnApi(Context context) {
        try {
            mAppContext = context;
            mHwan = IHwAn.getService();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private dataPack mkPack(int arg )
    {
        dataPack tmp = new dataPack();
        tmp.iVal = arg;
        return tmp;
    }

    private dataPack mkPack( String arg )
    {
        dataPack tmp = new dataPack();
        if( arg == null )
            tmp.sVal = "";
        else
            tmp.sVal = arg;
        return tmp;
    }

    public int anCommonApi( String inStr, int... inArg )
    {
        int retVal = -1;
        ArrayList<dataPack> req = new ArrayList<>();

        for( int val : inArg )
            req.add( mkPack( val ));

        if( inStr != null )
            req.add( mkPack( inStr ));

        try {
            retVal = mHwan.anHidlApiPack( req );
        } catch (RemoteException e) {
            e.printStackTrace();
//            restartSystemService();
//          throw new RuntimeException("An Api");
        }
        return retVal;
    }

    public int anCommonApi( String str1, String str2, int... inArg )
    {
        int retVal = -1;
        ArrayList<dataPack> req = new ArrayList<>();

        for( int val : inArg )
            req.add( mkPack( val ));

        if( str1 != null )
            req.add( mkPack( str1 ));
        if( str2 != null )
            req.add( mkPack( str2 ));

        try {
            retVal = mHwan.anHidlApiPack( req );
        } catch (RemoteException e) {
            e.printStackTrace();
//			restartSystemService();
        }

        return retVal;
    }

    public int anCommonApi( ArrayList<dataPack> arg )
    {
        int retVal = -1;

        try {
            retVal = mHwan.anHidlApiPack( arg );
        } catch (RemoteException e) {
            e.printStackTrace();
            //           restartSystemService();
        }
        return retVal;
    }

    public void registerCallbackFunc( IHwAnCallback func )
    {
        try {
            mHwan.setAnNotifyCallback( func );
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public final static int DVB_STORAGE_Mount = 0xF0;

    public int appStorageMount( String devPath, String mountPath, String type, String option )
    {
        ArrayList<dataPack> dPack = new ArrayList<>();

        dPack.add(mkPack(DVB_STORAGE_Mount));
        dPack.add(mkPack(devPath));
        dPack.add(mkPack(mountPath));
        dPack.add(mkPack(type));
        dPack.add(mkPack(option));
        return anCommonApi(dPack);
    }

    public final static int DVB_STORAGE_Umount = 0xF1;

    public int appStorageUmount( String mountPath )
    {
        if( mountPath == null ) mountPath = "";
        return anCommonApi( mountPath, DVB_STORAGE_Umount );
    }
}

