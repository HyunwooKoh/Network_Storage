package tv.formuler.service.gtv.networkstorage.search;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;

import jcifs.CIFSContext;
import jcifs.context.SingletonContext;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;


public class FindSamba {

    private static final String TAG = "NetworkStorage_FindSamba";

    NetworkScanner mNetworkScanner;
    ArrayList<String> smb;
    ArrayList<NetworkScanner.serverBean> servers;

    public static FindSamba newInstance() {
        return new FindSamba();
    }

    public ArrayList<String> start(Context mContext) {
        mNetworkScanner = new NetworkScanner(mContext);
        smb = new ArrayList<String>();
        servers = mNetworkScanner.start();

        if(servers != null) {

            try {
                CIFSContext base = SingletonContext.getInstance();
                CIFSContext authed1 = base.withGuestCrendentials();

                for(final NetworkScanner.serverBean Info : servers ) {
                    final String Url = "smb://" + Info.serverIp + "/";

                    try {
                        //왜 윈도우 host면 아래의 list()함수가 오류가 뜨는지 모르겠음.
                        // jcifs.smb.SmbTreeImpl.treeConnect(SmbTreeImpl.java:569) 여기서 오류 발생
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
        else {
            Log.d(TAG,"servers is null");
        }
        return smb;
    }
}