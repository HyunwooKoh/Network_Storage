package tv.formuler.service.gtv.networkstorage.search;

import android.content.Context;
import android.util.Log;
import java.util.ArrayList;

import jcifs.CIFSContext;
import jcifs.context.SingletonContext;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;


public class FindSamba {

    private static final String TAG = "FindSamba";
    public static final int ID_NO_AVAILABLE_SERVER = 0x1007; //4013

    NetworkScanner mNetworkScanner;
    ArrayList<String> smb;
    ArrayList<NetworkScanner.serverBean> servers;

    public static FindSamba newInstance() {
        return new FindSamba();
    }

    public ArrayList<String> start(Context mContext) {
        mNetworkScanner = new NetworkScanner(mContext);
        smb = new ArrayList<String>();
        servers = new ArrayList<NetworkScanner.serverBean>();

        mNetworkScanner.start();

        while(true) {
            if(mNetworkScanner.IsScanFinished()) {
                servers = mNetworkScanner.getServers();
                break;
            }
        }

        if(servers != null) {
            Thread Find = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        CIFSContext base = SingletonContext.getInstance();
                        CIFSContext authed1 = base.withAnonymousCredentials();

                        for(final NetworkScanner.serverBean Info : servers ) {
                            final String Url = "smb://" + Info.serverIp + "/";

                            try {
                                SmbFile f = new SmbFile(Url, authed1);
                                for (String sambafile : f.list()) {
                                    if( !(sambafile.equals("print$/") || sambafile.equals("IPC$/")) ) { // except IPC
                                        String file = (sambafile.subSequence(0, sambafile.length() - 1)).toString();
                                        smb.add( Info.serverIp + "/" + file);
                                    }
                                }
                            } catch (SmbException sambaex) {
                                //sambaex.printStackTrace();
                            }

                        }

                    } catch (Exception e) {
                        //e.printStackTrace();
                    }
                }
            });

            Find.start();
            try {
                Find.join();
            } catch (InterruptedException e) {
                //e.printStackTrace();
            }
        }
        else {
            Log.d(TAG,"servers is null");
        }
        return smb;
    }
}

