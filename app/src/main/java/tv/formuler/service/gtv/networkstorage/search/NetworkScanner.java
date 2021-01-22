package tv.formuler.service.gtv.networkstorage.search;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkInfo;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NetworkScanner {
    private final String TAG = "NetworkScanner";
    private long network_ip = 0;
    private long network_start = 0;
    private long network_end = 0;
    private boolean IsScanFinished;

    private int size;
    private int pt_move = 2; // 1=backward 2=forward

    private String NOMAC = "00:00:00:00:00:00";
    public Context mContext;

    public int cidr = 24;
    private static final String CMD_IP = " -f inet addr show %s";
    private static final String PTN_IP1 = "\\s*inet [0-9\\.]+\\/([0-9]+) brd [0-9\\.]+ scope global %s$";
    private static final String PTN_IP2 = "\\s*inet [0-9\\.]+ peer [0-9\\.]+\\/([0-9]+) scope global %s$"; // FIXME: Merge with PTN_IP1
    private static final String PTN_IF = "^%s: ip [0-9\\.]+ mask ([0-9\\.]+) flags.*";
    public String intf = "eth0";
    private static final int BUF = 8 * 1024;


    public class serverBean {
        public String serverName;
        public String serverIp;
    }

    public interface ScanListener {
        void onFinish(ArrayList<serverBean> servers);
    }

    private ArrayList<serverBean> servers;

    public NetworkScanner(Context context) {
        servers = new ArrayList<NetworkScanner.serverBean>();
        IsScanFinished = false;
        mContext = context;
    }

    private ScanListener scanListener;
    public void setListener(ScanListener scanListener){
        this.scanListener = scanListener;
    }

    public ArrayList<serverBean> start(){
        if(mContext == null)
            return null;

        String ip = null;
        final LinkProperties linkProperties;

        ConnectivityManager mConnectivityManager = (ConnectivityManager) mContext.getSystemService(
                Context.CONNECTIVITY_SERVICE);

        for (Network network : mConnectivityManager.getAllNetworks()) {
            NetworkInfo networkInfo = mConnectivityManager.getNetworkInfo(network);
            if (networkInfo != null && (networkInfo.getType() == ConnectivityManager.TYPE_ETHERNET || networkInfo.getType() == ConnectivityManager.TYPE_WIFI)) {

                linkProperties = mConnectivityManager.getLinkProperties(network);

                for (LinkAddress linkAddress : linkProperties.getLinkAddresses()) {
                    ip = linkAddress.getAddress().getHostAddress(); // ip address is located at last(second)
                }
                break;
            }
        }

        if(ip == null)
        {
            IsScanFinished = true;
            return null;
        }

        getCidr();
        network_ip = getUnsignedLongFromIp(ip);
        int shift = (32 - cidr);
        if (cidr < 31) {
            network_start = (network_ip >> shift << shift) + 1;
            network_end = (network_start | ((1 << shift) - 1)) - 1;
        } else {
            network_start = (network_ip >> shift << shift);
            network_end = (network_start | ((1 << shift) - 1));
        }

        size = (int) (network_end - network_start + 1);
        Log.v(TAG, "start=" + getIpFromLongUnsigned(network_start) + " (" + network_start
                + "), end=" + getIpFromLongUnsigned(network_end) + " (" + network_end
                + "), length=" + size);

        return scan();
    }

    private ArrayList<serverBean> scan(){
        if (network_ip <= network_end && network_ip >= network_start) {
            launch(network_start);

            // hosts
            long pt_backward = network_ip;
            long pt_forward = network_ip + 1;
            long size_hosts = size - 1;

            for (int i = 0; i < size_hosts; i++) {
                // Set pointer if of limits
                if (pt_backward <= network_start) {
                    pt_move = 2;
                } else if (pt_forward > network_end) {
                    pt_move = 1;
                }
                // Move back and forth
                if (pt_move == 1) {
                    launch(pt_backward);
                    pt_backward--;
                    pt_move = 2;
                } else if (pt_move == 2) {
                    launch(pt_forward);
                    pt_forward++;
                    pt_move = 1;
                }
            }
            if(scanListener != null)
                scanListener.onFinish(servers);

        } else {
            for (long i = network_start; i <= network_end; i++) {
                launch(i);
            }
            scanListener.onFinish(servers);
        }
        IsScanFinished = true;

        return servers;
    }

    private String getHardwareAddress(String ip) {
        String hw = NOMAC;
        try {
            if (ip != null) {
                String ptrn = String.format("^%s\\s+0x1\\s+0x2\\s+([:0-9a-fA-F]+)\\s+\\*\\s+\\w+$", ip.replace(".", "\\."));
                Pattern pattern = Pattern.compile(ptrn);
                BufferedReader bufferedReader = new BufferedReader(new FileReader("/proc/net/arp"), BUF);
                String line;
                Matcher matcher;
                while ((line = bufferedReader.readLine()) != null) {
                    matcher = pattern.matcher(line);
                    if (matcher.matches()) {
                        hw = matcher.group(1);
                        break;
                    }
                }
                bufferedReader.close();
            } else {
                Log.e(TAG, "ip is null");
            }
        } catch (IOException e) {
            Log.e(TAG, "Can't open/read file ARP: " + e.getMessage());
            return hw;
        }
        return hw;
    }

    private void launch(final long i){
        new Thread(new Runnable() {

            @Override
            public void run() {
                // TODO Auto-generated method stub
                String addr = getIpFromLongUnsigned(i);

                // Create host object
                String hardwareAddress = NOMAC;
                try {
                    InetAddress h = InetAddress.getByName(addr);
                    // Arp Check #1
                    hardwareAddress = getHardwareAddress(addr);
                    if(!NOMAC.equals(hardwareAddress)){
                        publish(addr);
                        return;
                    }
                    // Native InetAddress check
                    if (h.isReachable(getRate())) {
                        publish(addr);
                        return;
                    }
                    // Arp Check #2
                    hardwareAddress = getHardwareAddress(addr);
                    if(!NOMAC.equals(hardwareAddress)){

                        publish(addr);
                        return;
                    }
                    // Arp Check #3
                    hardwareAddress = getHardwareAddress(addr);
                    if(!NOMAC.equals(hardwareAddress)){

                        publish(addr);
                        return;
                    }

                } catch (IOException e) {
                }
            }
        }).start();
    }

    private void publish(String ipAddress){
        try {
            //String hostname = (InetAddress.getByName(ipAddress)).getCanonicalHostName();
            String hostname = (InetAddress.getByName(ipAddress)).getHostName();

            serverBean server = new serverBean();
            server.serverIp = ipAddress;
            server.serverName = hostname;
            checkSambe(server);
        } catch (UnknownHostException e) {
            Log.e(TAG, e.getMessage());
        }
    }

    private void checkSambe(final serverBean server){
        new Thread(new Runnable() {

            @Override
            public void run() {
                // TODO Auto-generated method stub
                try
                {
                    Socket ServerSok = new Socket(server.serverIp,445);
                    ServerSok.close();
                    servers.add(server);
                }
                catch (Exception e)
                {
                    //e.printStackTrace();
                }
            }
        }).start();
    }

    private int getRate() {
        return 800;
    }

    private String getIpFromLongUnsigned(long ip_long) {
        String ip = "";
        for (int k = 3; k > -1; k--) {
            ip = ip + ((ip_long >> k * 8) & 0xFF) + ".";
        }
        return ip.substring(0, ip.length() - 1);
    }

    private long getUnsignedLongFromIp(String ip_addr) {
        String[] a = ip_addr.split("\\.");
        return (Integer.parseInt(a[0]) * 16777216 + Integer.parseInt(a[1]) * 65536
                + Integer.parseInt(a[2]) * 256 + Integer.parseInt(a[3]));
    }

    private String getInterfaceFirstIp(NetworkInterface ni) {
        if (ni != null) {
            for (Enumeration<InetAddress> nis = ni.getInetAddresses(); nis.hasMoreElements();) {
                InetAddress ia = nis.nextElement();
                if (!ia.isLoopbackAddress()) {
                    if (ia instanceof Inet6Address) {
                        continue;
                    }
                    return ia.getHostAddress();
                }
            }
        }
        return null;
    }

    private void getCidr() {
        String match;
        // Running ip tools
        try {
            if ((match = runCommand("/system/xbin/ip", String.format(CMD_IP, intf), String.format(PTN_IP1, intf))) != null) {
                cidr = Integer.parseInt(match);
                return;
            } else if ((match = runCommand("/system/xbin/ip", String.format(CMD_IP, intf), String.format(PTN_IP2, intf))) != null) {
                cidr = Integer.parseInt(match);
                return;
            } else if ((match = runCommand("/system/bin/ifconfig", " " + intf, String.format(PTN_IF, intf))) != null) {
                cidr = IpToCidr(match);
                return;
            } else {
            }
        } catch (NumberFormatException e) {
        }
    }

    private int IpToCidr(String ip) {
        double sum = -2;
        String[] part = ip.split("\\.");
        for (String p : part) {
            sum += 256D - Double.parseDouble(p);
        }
        return 32 - (int) (Math.log(sum) / Math.log(2d));
    }

    private String runCommand(String path, String cmd, String ptn) {
        try {
            if (new File(path).exists()) {
                String line;
                Matcher matcher;
                Pattern ptrn = Pattern.compile(ptn);
                Process p = Runtime.getRuntime().exec(path + cmd);
                BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()), BUF);
                while ((line = r.readLine()) != null) {
                    matcher = ptrn.matcher(line);
                    if (matcher.matches()) {
                        return matcher.group(1);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Can't use native command: " + e.getMessage());
            return null;
        }
        return null;
    }

    public ArrayList<NetworkScanner.serverBean> getServers() {
        return servers;
    }

    public boolean IsScanFinished() {
        return IsScanFinished;
    }

}