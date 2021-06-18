package com.limelight.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;

import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.Call;

import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParserException;
import org.riversun.promise.Promise;
import org.riversun.promise.Func;
import org.riversun.promise.Action;

import com.google.common.math.Stats;

import java.util.concurrent.TimeUnit;
import java.lang.Process;
import java.lang.Runtime;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.StringBuffer;

public class NetHelper {
    public static boolean isActiveNetworkVpn(Context context) {
        ConnectivityManager connMgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network activeNetwork = connMgr.getActiveNetwork();
            if (activeNetwork != null) {
                NetworkCapabilities netCaps = connMgr.getNetworkCapabilities(activeNetwork);
                if (netCaps != null) {
                    return netCaps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ||
                            !netCaps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN);
                }
            }
        }
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            NetworkInfo activeNetworkInfo = connMgr.getActiveNetworkInfo();
            if (activeNetworkInfo != null) {
                return activeNetworkInfo.getType() == ConnectivityManager.TYPE_VPN;
            }
        }

        return false;
    }

    public static class NetQuality{
        public NetQuality(){
            
        }
        //In ms
        public long latency;
        //In Mbps
        public long bandwidth;

        public long[] pings = new long[testN];
        public long[] bites = new long[testN];
        public long pingDeviation;
        public long biteDeviation;
    }

    public static OkHttpClient httpClient;

    public static int testN = 10;
    public static NetQuality networkTest(String serverIp){
        long startT[]  = new long[testN];
        long pings[] = new long[testN]; 
        long bites[] = new long[testN]; //bandwidth - bite of data 
        String url;
        Request req;

        if(httpClient == null){
            httpClient = new OkHttpClient.Builder()
            // .connectionPool(new ConnectionPool(1, 5, TimeUnit.MILLISECONDS))
            // .readTimeout(0, TimeUnit.MILLISECONDS)
            // .connectTimeout(5000, TimeUnit.MILLISECONDS)
            .build();
        }

        //////////////////
        // HTTP Ping test
        // String url = "http://[" + serverIp + "]:1704/networkCheck/ping";
        // Request req = new Request.Builder().url(url).build();

        // long startT[]  = new long[testN];
        // long pings[] = new long[testN];
        // Response res; 
        // for (int i = 0; i < testN; i++) {
        //     startT[i] = System.currentTimeMillis();
        //     try{
        //         httpClient.newCall(req).execute();
        //         pings[i] = System.currentTimeMillis() - startT[i];
        //     }
        //     catch(Exception e){
        //         pings[i] = -1;
        //     }
        // }

        //////////////////
        // ICMP Ping test
        try{
            Process p = Runtime.getRuntime().exec("ping6 -c " + testN + " " + serverIp);
            // p.waitFor();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            int i;
            char[] buffer = new char[4096];
            StringBuffer output = new StringBuffer();
            while((i = reader.read(buffer)) > 0){
                output.append(buffer, 0, i);
            }
            reader.close();

            String[] lines = output.toString().split("\n", 0);
            i = 0;
            for(String l : lines){
                System.out.println(l);
                if(l.contains("icmp_seq")){
                    pings[i] =(long) Double.parseDouble(l.split("time=", 0)[1].split(" ms", 0)[0]);
                    i++;
                }
            }
        }
        catch(IOException e){
            e.printStackTrace();
        }
        
        // TODO: use a standard protocol to test bandwidth
        // HTTP can only be used to estimate average bandwidth
        // Avg bandwidth test
        url = "http://[" + serverIp + "]:1704/networkCheck/bandwidth";
        int biteSize = 500; //500kb
        req = new Request.Builder().url(url).build();
        startT  = new long[testN];

        long[] bwInMbps = new long[testN];
        for (int i = 0; i < testN; i++) {
            try{
                Call c = httpClient.newCall(req);
                startT[i] = System.currentTimeMillis();
                c.execute();
                bites[i] = System.currentTimeMillis() - startT[i];
                bwInMbps[i] = (long) (biteSize * 8 / bites[i] );
            }
            catch(Exception e){
                bites[i] = -1;
            }
        }
        

        Stats pStat = Stats.of(pings);
        Stats bStat = Stats.of(bwInMbps);
        NetQuality nq = new NetQuality();
        System.arraycopy(pings, 0, nq.pings, 0, testN);
        System.arraycopy(bites, 0, nq.bites, 0, testN);
        nq.latency = (long) pStat.mean();
        nq.pingDeviation = (long) pStat.sampleStandardDeviation();
        nq.bandwidth = (long) bStat.mean();
        nq.biteDeviation = (long) bStat.sampleStandardDeviation();
        
        return nq;
    }

    private void pingCheck(){ 
        // Func job = (action, data) -> {
        //     // Logic here
        //     action.resolve();
        // };
    }
}
