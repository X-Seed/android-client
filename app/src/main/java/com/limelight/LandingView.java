package com.limelight;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.MediaCodecInfo;
import android.os.Build;
import android.os.Bundle;
import android.app.Activity;
import android.os.Handler;
import android.os.Vibrator;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.util.DisplayMetrics;
import android.util.Range;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.android.volley.Response.Listener;
import com.android.volley.Response.ErrorListener;
import android.content.ServiceConnection;

import android.app.Activity;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import android.widget.Toast;

import com.limelight.LimeLog;
import com.limelight.PcView;
// import com.limelight.R;
import com.limelight.binding.video.MediaCodecHelper;
import com.limelight.preferences.AddComputerManually;
import com.limelight.binding.PlatformBinding;
import com.limelight.computers.ComputerManagerService;
import com.limelight.computers.ComputerManagerListener;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.jni.MoonBridge;
import com.limelight.nvstream.http.PairingManager;
import com.limelight.nvstream.http.PairingManager.PairState;
import com.limelight.utils.Dialog;
import com.limelight.utils.ServerHelper;
import com.limelight.utils.SpinnerDialog;
import com.limelight.utils.UiHelper;
import com.limelight.utils.Dialog;
import com.limelight.utils.UiHelper;


import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.HashMap;
import java.util.Map;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.UnknownHostException;

import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParserException;

public class LandingView extends Activity {
    private RequestQueue queue; 
    private String serverUrl = "http://xseed.tech:2048/";
    private String serverIP = "";
    private Thread addThread, pairThread;

    private ComputerManagerService.ComputerManagerBinder managerBinder;
    private boolean freezeUpdates, runningPolling, inForeground, completeOnCreateCalled;
    private final LinkedBlockingQueue<String> computersToAdd = new LinkedBlockingQueue<>();
    private final LinkedBlockingQueue<ComputerDetails> computersToPair = new LinkedBlockingQueue<>();

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, final IBinder binder) {
            managerBinder = ((ComputerManagerService.ComputerManagerBinder)binder);
            startAddThread();
            startPairThread();
        }

        public void onServiceDisconnected(ComponentName className) {
            joinAddThread();
            joinPairThread();
            managerBinder = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        this.queue = Volley.newRequestQueue(this);
        // UiHelper.setLocale(this);
        setContentView(R.layout.landing);
        UiHelper.notifyNewRootView(this);

        // Bind to the ComputerManager service
        bindService(new Intent(LandingView.this,
        ComputerManagerService.class), serviceConnection, Service.BIND_AUTO_CREATE);
    }

    public void handleLogin(View v){
        EditText userNameV = findViewById(R.id.userName);
        EditText passwordV = findViewById(R.id.password);
        String userName = userNameV.getText().toString();
        String password = passwordV.getText().toString();

        login(userName, password);
    }

    protected void login(String userName, String password){
        String url = serverUrl + "login";
        JSONObject postObj = new JSONObject();
        try{
            postObj.put("userName", userName);
            postObj.put("password", password);
        }
        catch(Exception e){}

        JsonObjectRequest req = new JsonObjectRequest(Request.Method.POST, url, postObj, new Listener<JSONObject>(){
            @Override
            public void onResponse(JSONObject response){
                try{
                    if(response.getString("status").equals("ok")){
                        // startActivity(new Intent(LandingView.this, PcView.class));
                        allocatePC();
                    }
                }
                catch(Exception e){
                    // throw e;
                }
            }
        }, new ErrorListener(){
            @Override 
            public void onErrorResponse(VolleyError error){
                error = error;
            }
        });

        this.queue.add(req);
    }

    protected void allocatePC(){
        String url = serverUrl + "allocateResource";

        JsonObjectRequest req = new JsonObjectRequest(Request.Method.GET, url, new JSONObject(), new Listener<JSONObject>(){
            @Override
            public void onResponse(JSONObject response){
                try{
                    if(!response.getString("status").equals("ok")){
                        
                        failedResourceAllocation();
                        return;
                    }

                    String hostAddress = response.getString("ip");

                    computersToAdd.add(hostAddress);
                    // doAddPc(hostAddress);
                }
                catch(Exception e){
                    e = e;
                    // throw e;
                }
                // startActivity(new Intent(LandingView.this, PcView.class));
            }
        }, new ErrorListener(){
            @Override 
            public void onErrorResponse(VolleyError error){
                failedResourceAllocation();
            }
        });

        this.queue.add(req);
    }

    protected void failedResourceAllocation(){

    }

    public void doAddPc(String host) {
        boolean wrongSiteLocal = false;
        boolean success = true;
        int portTestResult;

        // SpinnerDialog dialog = SpinnerDialog.displayDialog(this, getResources().getString(R.string.title_add_pc),
        //     getResources().getString(R.string.msg_add_pc), false);

        ComputerDetails details = new ComputerDetails();
        details.manualAddress = host;
        try {
            success = managerBinder.addComputerBlocking(details);
        } catch (Exception e) {
            // This can be thrown from OkHttp if the host fails to canonicalize to a valid name.
            // https://github.com/square/okhttp/blob/okhttp_27/okhttp/src/main/java/com/squareup/okhttp/HttpUrl.java#L705
            e.printStackTrace();
            success = false;
        }
        
        // dialog.dismiss();
        // doPair(details);
        computersToPair.add(details);
    }

    private void stopComputerUpdates(boolean wait) {
        if (managerBinder != null) {
            if (!runningPolling) {
                return;
            }

            freezeUpdates = true;

            managerBinder.stopPolling();

            if (wait) {
                managerBinder.waitForPollingStopped();
            }

            runningPolling = false;
        }
    }

    private void doPair(final ComputerDetails computer) {
        // if (computer.state == ComputerDetails.State.OFFLINE ||
        //         ServerHelper.getCurrentAddressFromComputer(computer) == null) {
        //     Toast.makeText(LandingView.this, getResources().getString(R.string.pair_pc_offline), Toast.LENGTH_SHORT).show();
        //     return;
        // }
        // if (computer.runningGameId != 0) {
        //     Toast.makeText(LandingView.this, getResources().getString(R.string.pair_pc_ingame), Toast.LENGTH_LONG).show();
        //     return;
        // }
        // if (managerBinder == null) {
        //     Toast.makeText(LandingView.this, getResources().getString(R.string.error_manager_not_running), Toast.LENGTH_LONG).show();
        //     return;
        // }

        // Toast.makeText(LandingView.this, getResources().getString(R.string.pairing), Toast.LENGTH_SHORT).show();

        // Thread pairThread = new Thread(new Runnable() {
        //     @Override
        //     public void run() {
                NvHTTP httpConn;
                String message;
                boolean success = false;
                try {
                    // Stop updates and wait while pairing
                    stopComputerUpdates(true);

                    httpConn = new NvHTTP(ServerHelper.getCurrentAddressFromComputer(computer),
                            managerBinder.getUniqueId(),
                            computer.serverCert,
                            PlatformBinding.getCryptoProvider(LandingView.this));
                    if (httpConn.getPairState() == PairState.PAIRED) {
                        // Don't display any toast, but open the app list
                        message = null;
                        success = true;
                    }
                    else {
                        final String pinStr = PairingManager.generatePinString();

                        // Spin the dialog off in a thread because it blocks
                        Dialog.displayDialog(LandingView.this, getResources().getString(R.string.pair_pairing_title),
                                getResources().getString(R.string.pair_pairing_msg)+" "+pinStr, false);

                        PairingManager pm = httpConn.getPairingManager();

                        PairState pairState = pm.pair(httpConn.getServerInfo(), pinStr);
                        if (pairState == PairState.PIN_WRONG) {
                            message = getResources().getString(R.string.pair_incorrect_pin);
                        }
                        else if (pairState == PairState.FAILED) {
                            message = getResources().getString(R.string.pair_fail);
                        }
                        else if (pairState == PairState.ALREADY_IN_PROGRESS) {
                            message = getResources().getString(R.string.pair_already_in_progress);
                        }
                        else if (pairState == PairState.PAIRED) {
                            // Just navigate to the app view without displaying a toast
                            message = null;
                            success = true;

                            // Pin this certificate for later HTTPS use
                            // managerBinder.getComputer(computer.uuid).serverCert = pm.getPairedCert();

                            // Invalidate reachability information after pairing to force
                            // a refresh before reading pair state again
                            // managerBinder.invalidateStateForComputer(computer.uuid);
                        }
                        else {
                            // Should be no other values
                            message = null;
                        }
                    }
                } catch (UnknownHostException e) {
                    message = getResources().getString(R.string.error_unknown_host);
                } catch (FileNotFoundException e) {
                    message = getResources().getString(R.string.error_404);
                } catch (XmlPullParserException | IOException e) {
                    e.printStackTrace();
                    message = e.getMessage();
                }

                Dialog.closeDialogs();

                final String toastMessage = message;
                final boolean toastSuccess = success;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (toastMessage != null) {
                            Toast.makeText(LandingView.this, toastMessage, Toast.LENGTH_LONG).show();
                        }

                        if (toastSuccess) {
                            // Open the app list after a successful pairing attempt
                            doAppList(computer, true, false);
                        }
                        else {
                            // Start polling again if we're still in the foreground
                            startComputerUpdates();
                        }
                    }
                });
            // }
        // });
        // Handler mainHandler = new Handler(getMainLooper());
        // mainHandler.post(pairThread);
    }


    private void doAppList(ComputerDetails computer, boolean newlyPaired, boolean showHiddenGames) {
        if (computer.state == ComputerDetails.State.OFFLINE) {
            Toast.makeText(LandingView.this, getResources().getString(R.string.error_pc_offline), Toast.LENGTH_SHORT).show();
            return;
        }
        if (managerBinder == null) {
            Toast.makeText(LandingView.this, getResources().getString(R.string.error_manager_not_running), Toast.LENGTH_LONG).show();
            return;
        }

        Intent i = new Intent(this, AppView.class);
        i.putExtra(AppView.NAME_EXTRA, computer.name);
        i.putExtra(AppView.UUID_EXTRA, computer.uuid);
        i.putExtra(AppView.NEW_PAIR_EXTRA, newlyPaired);
        i.putExtra(AppView.SHOW_HIDDEN_APPS_EXTRA, showHiddenGames);
        startActivity(i);
    }



    private void startComputerUpdates() {
        // Only allow polling to start if we're bound to CMS, polling is not already running,
        // and our activity is in the foreground.
        if (managerBinder != null && !runningPolling && inForeground) {
            freezeUpdates = false;
            managerBinder.startPolling(new ComputerManagerListener() {
                @Override
                public void notifyComputerUpdated(final ComputerDetails details) {
                    if (!freezeUpdates) {
                        LandingView.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                // updateComputer(details);
                            }
                        });
                    }
                }
            });
            runningPolling = true;
        }
    }


    public void handleRegister(View v){
        String url = serverUrl + "register";

        EditText userNameV = findViewById(R.id.userName);
        EditText passwordV = findViewById(R.id.password);
        String userName = userNameV.getText().toString();
        String password = passwordV.getText().toString();

        JSONObject postObj = new JSONObject();
        try {
            postObj.put("userName", userName);
            postObj.put("password", password);
        }
        catch(Exception e){}

        JsonObjectRequest req = new JsonObjectRequest(Request.Method.POST, url, postObj, new Listener<JSONObject>(){
            @Override
            public void onResponse(JSONObject response){
                try{
                    if(response.getString("status") == "ok"){
                        // startActivity(new Intent(LandingView.this, LandingView.class));
                        login(userName, password);
                    }
                }
                catch(Exception e){
                    // throw e;
                }
            }
        }, new ErrorListener(){
            @Override 
            public void onErrorResponse(VolleyError error){

            }
        });

        this.queue.add(req);
    }


    private void startAddThread() {
        addThread = new Thread() {
            @Override
            public void run() {
                while (!isInterrupted()) {
                    String computer;

                    try {
                        computer = computersToAdd.take();
                    } catch (InterruptedException e) {
                        return;
                    }

                    doAddPc(computer);
                }
            }
        };
        addThread.setName("UI - AddComputerManually");
        addThread.start();
    }

    private void startPairThread(){
        pairThread = new Thread() {
            @Override
            public void run() {
                while (!isInterrupted()) {
                    ComputerDetails computer;

                    try {
                        computer = computersToPair.take();
                    } catch (InterruptedException e) {
                        return;
                    }

                    doPair(computer);
                }
            }
        };
        pairThread.setName("UI - PairComputer");
        pairThread.start();
    }

    private void joinAddThread() {
        if (addThread != null) {
            addThread.interrupt();

            try {
                addThread.join();
            } catch (InterruptedException ignored) {}

            addThread = null;
        }
    }

    private void joinPairThread() {
        if (pairThread != null) {
            pairThread.interrupt();

            try {
                pairThread.join();
            } catch (InterruptedException ignored) {}

            pairThread = null;
        }
    }

    // protected void api(String method, String url, ){
    //     JsonObjectRequest req = new JsonObjectRequest(Request.Method.POST, url, postObj, new Listener<JSONObject>(){
    //         @Override
    //         public void onResponse(JSONObject response){
    //             try{
    //                 if(response.getString("status") == "ok"){

    //                     // startActivity(new Intent(LandingView.this, PcView.class));
    //                 }
    //             }
    //             catch(Exception e){
    //                 // throw e;
    //             }
    //         }
    //     }, new ErrorListener(){
    //         @Override 
    //         public void onErrorResponse(VolleyError error){

    //         }
    //     });
    // }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (managerBinder != null) {
            joinAddThread();
            unbindService(serviceConnection);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

    }

    @Override
    protected void onStop() {
        super.onStop();

    }
}