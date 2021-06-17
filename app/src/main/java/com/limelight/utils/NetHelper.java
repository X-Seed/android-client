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

import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParserException;
import org.riversun.promise.Promise;
import org.riversun.promise.Func;
import org.riversun.promise.Action;

import com.google.common.math.Stats;

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
        //In kbps
        public long bandwidth;

        public long[] pings = new long[testN];
        public long[] bites = new long[testN];
        public long pingVariance;
        public long bitesVariance;
    }

    public static OkHttpClient httpClient = new OkHttpClient();

    public static int testN = 10;
    public static NetQuality networkTest(){
        // Ping test
        String url = serverUrl + "networkCheck/ping";
        Request req = new Request.Builder().url(url).build();

        long startT[]  = new long[testN];
        long pings[] = new long[testN];
        for (int i = 0; i < testN; i++) {
            startT[i] = System.currentTimeMillis();
            try (Response res = httpClient.newCall(req).execute()){
                pings[i] = System.currentTimeMillis() - startT[i];
            }
        }

        // Avg bandwidth test
        url = serverUrl + "networkCheck/bandwidth";
        req = new Request.Builder().url(url).build();
        startT  = new long[testN];
        long bites[] = new long[testN];
        for (int i = 0; i < testN; i++) {
            startT[i] = System.currentTimeMillis();
            try (Response res = httpClient.newCall(req).execute()){
                bites[i] = System.currentTimeMillis() - startT[i];
            }
        }

        Stats pStat = new Stats(pings);
        Stats bStat = new Stats(bites);
        NetQuality nq = new NetQuality();
        System.arraycopy(pings, 0, nq.pings, 0, testN);
        System.arraycopy(bites, 0, nq.bites, 0, testN);
        nq.latency = pStat.mean();
        nq.pingVariance = pStat.sampleVariance();
        nq.bandwidth = bStat.mean();
        nq.bitesVariance = bStat.sampleVariance();

        return nq;
    }

    private void pingCheck(){ 
        // Func job = (action, data) -> {
        //     // Logic here
        //     action.resolve();
        // };
    }
}
