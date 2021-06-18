package com.limelight;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.ComponentName;
import android.content.ServiceConnection;

import android.media.MediaCodecInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
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
import android.view.ViewGroup;
import android.widget.EditText;
import android.app.Activity;
import android.app.Service;

import android.view.KeyEvent;
import android.view.View;

import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ToggleButton;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.android.volley.Response.Listener;
import com.android.volley.Response.ErrorListener;
import com.android.volley.DefaultRetryPolicy;

import com.limelight.LimeLog;
import com.limelight.PcView;
// import com.limelight.R;
import com.limelight.binding.video.MediaCodecHelper;
import com.limelight.preferences.AddComputerManually;
import com.limelight.binding.PlatformBinding;
import com.limelight.computers.ComputerManagerService;
import com.limelight.computers.ComputerManagerListener;
import com.limelight.grid.assets.DiskAssetLoader;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.jni.MoonBridge;
import com.limelight.nvstream.http.PairingManager;
import com.limelight.nvstream.http.PairingManager.PairState;
import com.limelight.utils.Dialog;
import com.limelight.utils.ServerHelper;
import com.limelight.utils.SpinnerDialog;
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
import java.util.Arrays;
// import java.util.function.UnaryOperator;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.UnknownHostException;

import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParserException;
import org.riversun.promise.Promise;
import org.riversun.promise.Func;
import org.riversun.promise.Action;

import com.limelight.utils.NetHelper;
import com.limelight.utils.NetHelper.NetQuality;

public class Dashboard extends Activity {
    private RequestQueue xseedApiQueue; 
    private String serverUrl = "http://xseed.tech:2048/";
    private String serverIP = "";
    private Thread addThread, pairThread;
    private String token = "TOKEN";

    private ComputerManagerService.ComputerManagerBinder managerBinder;
    private boolean freezeUpdates, runningPolling, inForeground, completeOnCreateCalled;
    private LinkedBlockingQueue<String> computersToAdd;
    private LinkedBlockingQueue<ComputerDetails> computersToPair;

    private SpinnerDialog resourceAllocateDialog;
    private ToggleButton playButton;
    private boolean playButtonCheckedBySystem = false;
    private ComputerDetails assignedComputer;

    // No retry, 7s timeout
    private DefaultRetryPolicy volleyPolicy = new DefaultRetryPolicy(
        7000, 0, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT);

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
        computersToAdd = new LinkedBlockingQueue<>();
        computersToPair = new LinkedBlockingQueue<>();
        xseedApiQueue = Volley.newRequestQueue(this);
        
        token = getIntent().getStringExtra("TOKEN");

        // UiHelper.setLocale(this);
        setContentView(R.layout.dashboard);
        UiHelper.notifyNewRootView(this);

        // Bind to the ComputerManager service
        bindService(new Intent(Dashboard.this,
        ComputerManagerService.class), serviceConnection, Service.BIND_AUTO_CREATE);

        this.playButton = (ToggleButton) findViewById(R.id.playButton);
        this.playButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(playButtonCheckedBySystem) {
                    playButtonCheckedBySystem = false;
                    return;
                }

                if (isChecked) {
                    startPlaying(buttonView);
                } else {
                    stopPlaying(buttonView);
                }
            }
        });

        updateUserInfo();
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        // super.onSaveInstanceState(savedInstanceState);
    }

    public static String networkCheckHostIp = "";
    public void networkCheck(View v){
        SpinnerDialog spinner = SpinnerDialog.displayDialog(Dashboard.this, "Network Check", "Allocating a PC...", false);

        JSONObject postObjToken = new JSONObject();
        try{
            postObjToken.put("token", token);
        }
        catch(Exception e){}

        String releaseUrl = serverUrl + "releaseResource";
        JsonObjectRequest releaseResourceReq = 
        new JsonObjectRequest(Request.Method.POST, releaseUrl, postObjToken, new Listener<JSONObject>(){
            @Override
            public void onResponse(JSONObject response){
                try{
                    if(!response.getString("status").equals("ok")){
                        failedResourceRelease(response.getString("msg"));
                        return;
                    }
                }
                catch(Exception e){
                    e = e;
                    failedResourceRelease(e.getMessage());
                    // throw e;
                }
            }
        }, new ErrorListener(){
            @Override 
            public void onErrorResponse(VolleyError error){
                failedResourceRelease(error.toString());
            }
        });
        releaseResourceReq.setRetryPolicy(volleyPolicy);

        String allocateUrl = serverUrl + "allocateResource";
        JsonObjectRequest allocateResourceReq = new JsonObjectRequest(Request.Method.POST, allocateUrl, postObjToken, new Listener<JSONObject>(){
            @Override
            public void onResponse(JSONObject response){
                try{
                    if(!response.getString("status").equals("ok")){
                        
                        failedResourceAllocation(response.getString("msg"));
                        return;
                    }
                    JSONObject resource = response.getJSONObject("resource");
                    String hostIp = resource.getString("ip");
                    Dashboard.networkCheckHostIp = hostIp;

                    spinner.setMessage("Testing network connection to " + hostIp);

                    // Define runnable 
                    Runnable r = () -> {
                        NetQuality res = NetHelper.networkTest(Dashboard.networkCheckHostIp);

                        SpinnerDialog.closeDialogs(Dashboard.this);

                        Dialog.displayDialog(Dashboard.this, "Network check", "Average ping: " 
                        + res.latency + " ms\nBandwidth: " + res.bandwidth + " Mbps", false);

                        // Dialog.displayDialog(Dashboard.this, "Network check", "Pings: " 
                        // + Arrays.toString(res.pings) + "\nBites: " + Arrays.toString(res.bites), false);
                        
                        Dashboard.this.xseedApiQueue.add(releaseResourceReq);
                    };

                    Thread t = new Thread(r);
                    t.setName("Network Test");
                    t.start();
                }
                catch(Exception e){
                    e = e;
                    // throw e;
                    failedResourceAllocation(e.getMessage());
                    return;
                }
            }
        }, new ErrorListener(){
            @Override 
            public void onErrorResponse(VolleyError error){
                SpinnerDialog.closeDialogs(Dashboard.this);
                failedResourceAllocation(error.toString());
            }
        });
        allocateResourceReq.setRetryPolicy(volleyPolicy);

        this.xseedApiQueue.add(allocateResourceReq);
    }

    private void updateUserInfo(){
        String url = serverUrl + "user/";
        JSONObject obj = new JSONObject();
        try{
            obj.put("operation", "get");
            obj.put("token", this.token);
        }
        catch(Exception e){ /*Catch con me may*/}

        JsonObjectRequest req = 
        new JsonObjectRequest(Request.Method.POST, url, obj, new Listener<JSONObject>(){
            @Override
            public void onResponse(JSONObject response){
                try{
                    if(!response.getString("status").equals("ok")){
                        Dialog.displayDialog(Dashboard.this, "Server error", response.getString("msg"), false);
                        return;
                    }
                    JSONObject user = response.getJSONObject("user");
                    TextView coinTv = (TextView) findViewById(R.id.remainingCoins);
                    coinTv.setText("Remaining coins: " + String.format("%.0f", user.getDouble("remainingCoin")));

                    if(!user.isNull("currentResource")){
                        if(playButton.isChecked()) return;

                        playButtonCheckedBySystem = true;
                        playButton.setChecked(true);
                    }
                }
                catch(Exception e){
                    e = e;
                    Dialog.displayDialog(Dashboard.this, "Exception", e.getMessage(), false);
                    // throw e;
                }
            }
        }, new ErrorListener(){
            @Override 
            public void onErrorResponse(VolleyError error){
                Dialog.displayDialog(Dashboard.this, "Network error", "Cannot get user info", false);
            }
        });

        xseedApiQueue.add(req);
    }

    private void startPlaying(CompoundButton b){
        SpinnerDialog.displayDialog(this, "Allocating PC", "Finding a PC for you...", false);
        allocatePC();
    }

    private void stopPlaying(CompoundButton b){
        SpinnerDialog.displayDialog(this, "Releasing PC", "Stop the server from taking your coins...", false);
        releasePC();
    }

    private void gameStreamAutoPair(String hostIP, String pin){
        Handler handler = new Handler(Looper.getMainLooper());
        Runnable sendRequestToAgent = ()-> {
            String url = "http://[" + hostIP + "]:1704/" + "gameStreamAutoPair/" + pin;

            JsonObjectRequest req = 
            new JsonObjectRequest(Request.Method.GET, url, new JSONObject(), new Listener<JSONObject>(){
                @Override
                public void onResponse(JSONObject response){
                    try{
                        if(!response.getString("status").equals("ok")){
                            failedResourceConnection(response.getString("msg"));
                            return;
                        }
                    }
                    catch(Exception e){
                        e = e;
                        // throw e;
                    }
                }
            }, new ErrorListener(){
                @Override 
                public void onErrorResponse(VolleyError error){
                    error = error;
                }
            });
            req.setRetryPolicy(volleyPolicy);

            xseedApiQueue.add(req);
        };

        handler.postDelayed(sendRequestToAgent, 0);
        handler.postDelayed(sendRequestToAgent, 500);
        handler.postDelayed(sendRequestToAgent, 1000);
        handler.postDelayed(sendRequestToAgent, 1500);
        handler.postDelayed(sendRequestToAgent, 2000);
    }

    private void setTimeoutLol(Runnable r, long delayinMs){
        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(r, delayinMs);
    }

    protected void allocatePC(){
        String url = serverUrl + "allocateResource";
        JSONObject postObj = new JSONObject();
        try{
            postObj.put("token", token);
        }
        catch(Exception e){}

        JsonObjectRequest req = new JsonObjectRequest(Request.Method.POST, url, postObj, new Listener<JSONObject>(){
            @Override
            public void onResponse(JSONObject response){
                SpinnerDialog.closeDialogs(Dashboard.this);
                try{
                    if(!response.getString("status").equals("ok")){
                        
                        failedResourceAllocation(response.getString("msg"));
                        return;
                    }
                    JSONObject resource = response.getJSONObject("resource");
                    String hostAddress = resource.getString("ip");

                    SpinnerDialog.displayDialog(Dashboard.this, "PC assigned", "Pairing with the host PC...", false);
                    computersToAdd.add(hostAddress);
                }
                catch(Exception e){
                    e = e;
                    // throw e;
                    failedResourceAllocation(e.getMessage());
                }
            }
        }, new ErrorListener(){
            @Override 
            public void onErrorResponse(VolleyError error){
                SpinnerDialog.closeDialogs(Dashboard.this);
                failedResourceAllocation(error.toString());
            }
        });

        req.setRetryPolicy(volleyPolicy);
        this.xseedApiQueue.add(req);
    }

    private void releasePC(){
        String url = serverUrl + "releaseResource";
        JSONObject postObj = new JSONObject();
        try{
            postObj.put("token", token);
        }
        catch(Exception e){}

        JsonObjectRequest req = new JsonObjectRequest(Request.Method.POST, url, postObj, new Listener<JSONObject>(){
            @Override
            public void onResponse(JSONObject response){
                try{
                    if(!response.getString("status").equals("ok")){
                        failedResourceRelease(response.getString("msg"));
                        return;
                    }
                    SpinnerDialog.closeDialogs(Dashboard.this);
                    Dialog.displayDialog(Dashboard.this, "PC stopped", "Billing is stopped. Your coins are safe! You can safely close the app now.", false);
                }
                catch(Exception e){
                    e = e;
                    failedResourceRelease(e.getMessage());
                    // throw e;
                }
            }
        }, new ErrorListener(){
            @Override 
            public void onErrorResponse(VolleyError error){
                failedResourceRelease(error.toString());
            }
        });

        req.setRetryPolicy(volleyPolicy);
        this.xseedApiQueue.add(req);
    }

    private void failedResourceConnection(String message){
        playButtonCheckedBySystem = true;
        playButton.setChecked(false);
        SpinnerDialog.closeDialogs(this);
        Dialog.displayDialog(this, "Failed to connect to PC", message, false);

        releasePC();
    }

    protected void failedResourceAllocation(String message){
        playButtonCheckedBySystem = true;
        playButton.setChecked(false);
        SpinnerDialog.closeDialogs(this);
        Dialog.displayDialog(this, "Failed to assign PC", message, false);
    }

    protected void failedResourceRelease(String message){
        playButtonCheckedBySystem = true;
        playButton.setChecked(true);
        SpinnerDialog.closeDialogs(this);
        Dialog.displayDialog(this, "Failed to stop PC", "WARNING: You are still losing you coins if the PC is not stopped. Please send this to the admins for assistance. Error message: " +  message, false);
    }

    private boolean isWrongSubnetSiteLocalAddress(String address) {
        try {
            InetAddress targetAddress = InetAddress.getByName(address);
            if (!(targetAddress instanceof Inet4Address) || !targetAddress.isSiteLocalAddress()) {
                return false;
            }

            // We have a site-local address. Look for a matching local interface.
            for (NetworkInterface iface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (InterfaceAddress addr : iface.getInterfaceAddresses()) {
                    if (!(addr.getAddress() instanceof Inet4Address) || !addr.getAddress().isSiteLocalAddress()) {
                        // Skip non-site-local or non-IPv4 addresses
                        continue;
                    }

                    byte[] targetAddrBytes = targetAddress.getAddress();
                    byte[] ifaceAddrBytes = addr.getAddress().getAddress();

                    // Compare prefix to ensure it's the same
                    boolean addressMatches = true;
                    for (int i = 0; i < addr.getNetworkPrefixLength(); i++) {
                        if ((ifaceAddrBytes[i / 8] & (1 << (i % 8))) != (targetAddrBytes[i / 8] & (1 << (i % 8)))) {
                            addressMatches = false;
                            break;
                        }
                    }

                    if (addressMatches) {
                        return false;
                    }
                }
            }

            // Couldn't find a matching interface
            return true;
        } catch (SocketException e) {
            e.printStackTrace();
            return false;
        } catch (UnknownHostException e) {
            return false;
        }
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

        if (!success){
            wrongSiteLocal = isWrongSubnetSiteLocalAddress(host);
        }
        if (!success && !wrongSiteLocal) {
            // Run the test before dismissing the spinner because it can take a few seconds.
            portTestResult = MoonBridge.testClientConnectivity(ServerHelper.CONNECTION_TEST_SERVER, 443,
                    MoonBridge.ML_PORT_FLAG_TCP_47984 | MoonBridge.ML_PORT_FLAG_TCP_47989);
        } else {
            // Don't bother with the test if we succeeded or the IP address was bogus
            portTestResult = MoonBridge.ML_TEST_RESULT_INCONCLUSIVE;
        }
        
        // dialog.dismiss();

        if (wrongSiteLocal) {
            // Dialog.displayDialog(this, getResources().getString(R.string.conn_error_title), getResources().getString(R.string.addpc_wrong_sitelocal), false);
            failedResourceConnection("Cannot add PC to MoonBridge. Msg: " + getResources().getString(R.string.addpc_wrong_sitelocal));
        }
        else if (!success) {
            String dialogText;
            if (portTestResult != MoonBridge.ML_TEST_RESULT_INCONCLUSIVE && portTestResult != 0)  {
                dialogText = getResources().getString(R.string.nettest_text_blocked);
            }
            else {
                dialogText = getResources().getString(R.string.addpc_fail);
            }
            // Dialog.displayDialog(this, getResources().getString(R.string.conn_error_title), dialogText, false);
            failedResourceConnection("Cannot add PC to MoonBridge. Msg: " + dialogText);
            return;
        }
        
        // Success
        computersToPair.add(details);
        assignedComputer = details;
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

        NvHTTP httpConn;
        String message;
        boolean success = false;
        try {
            // Stop updates and wait while pairing
            stopComputerUpdates(true);

            String computerAddr = ServerHelper.getCurrentAddressFromComputer(computer);
            httpConn = new NvHTTP(computerAddr,
                    managerBinder.getUniqueId(),
                    computer.serverCert,
                    PlatformBinding.getCryptoProvider(Dashboard.this));
            if (httpConn.getPairState() == PairState.PAIRED) {
                // Don't display any toast, but open the app list
                message = null;
                success = true;
            }
            else {
                final String pinStr = PairingManager.generatePinString();

                // TODO: Start auto pairing thread
                gameStreamAutoPair(computerAddr, pinStr);

                // Spin the dialog off in a thread because it blocks
                // Dialog.displayDialog(Dashboard.this, getResources().getString(R.string.pair_pairing_title),
                //         getResources().getString(R.string.pair_pairing_msg)+" "+pinStr, false);

                PairingManager pm = httpConn.getPairingManager();

                PairState pairState = pm.pair(httpConn.getServerInfo(), pinStr, true);

                SpinnerDialog.closeDialogs(Dashboard.this);
                if (pairState == PairState.PAIRED) {
                    // Just navigate to the app view without displaying a toast
                    message = null;
                    success = true;

                    // Pin this certificate for later HTTPS use
                    managerBinder.getComputer(computer.uuid).serverCert = pm.getPairedCert();

                    // Invalidate reachability information after pairing to force
                    // a refresh before reading pair state again
                    managerBinder.invalidateStateForComputer(computer.uuid);

                    // Start cost incurring service
                }
                else if (pairState == PairState.PIN_WRONG) {
                    message = getResources().getString(R.string.pair_incorrect_pin);
                    // failedResourceConnection(message);
                }
                else if (pairState == PairState.FAILED) {
                    message = getResources().getString(R.string.pair_fail);
                    // failedResourceConnection(message);
                }
                else if (pairState == PairState.ALREADY_IN_PROGRESS) {
                    message = getResources().getString(R.string.pair_already_in_progress);
                    // failedResourceConnection(message);
                }
                else {
                    // Should be no other values
                    message = null;
                }
            }
        } catch (UnknownHostException e) {
            message = getResources().getString(R.string.error_unknown_host);
            // failedResourceConnection(message);
        } catch (FileNotFoundException e) {
            message = getResources().getString(R.string.error_404);
            // failedResourceConnection(message);
        } catch (XmlPullParserException | IOException e) {
            e.printStackTrace();
            message = e.getMessage();
            // failedResourceConnection(message);
        }

        Dialog.closeDialogs();
        SpinnerDialog.closeDialogs(Dashboard.this);

        final String toastMessage = message;
        final boolean toastSuccess = success;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (toastMessage != null) {
                    // Toast.makeText(Dashboard.this, toastMessage, Toast.LENGTH_LONG).show();
                }

                if (toastSuccess) {
                    // Open the app list after a successful pairing attempt
                    doAppList(computer, true, false);
                }
                else {
                    failedResourceConnection(toastMessage);
                    // Start polling again if we're still in the foreground
                    startComputerUpdates();
                }
            }
        });
    }

    private void doAppList(ComputerDetails computer, boolean newlyPaired, boolean showHiddenGames) {
        if (computer.state == ComputerDetails.State.OFFLINE) {
            Toast.makeText(Dashboard.this, getResources().getString(R.string.error_pc_offline), Toast.LENGTH_SHORT).show();
            return;
        }
        if (managerBinder == null) {
            Toast.makeText(Dashboard.this, getResources().getString(R.string.error_manager_not_running), Toast.LENGTH_LONG).show();
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
                        Dashboard.this.runOnUiThread(new Runnable() {
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

    //                     // startActivity(new Intent(Dashboard.this, PcView.class));
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
        updateUserInfo();
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