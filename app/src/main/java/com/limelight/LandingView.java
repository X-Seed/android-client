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
import com.limelight.utils.Dialog;
import com.limelight.utils.UiHelper;
import com.limelight.preferences.AddComputerManually;
import com.limelight.binding.PlatformBinding;
import com.limelight.computers.ComputerManagerService;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.jni.MoonBridge;
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
import org.json.JSONObject;

public class LandingView extends Activity {
    private RequestQueue queue; 
    private String serverUrl = "http://xseed.tech:2048/";
    private String serverIP = "";

    private ComputerManagerService.ComputerManagerBinder managerBinder;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, final IBinder binder) {
            managerBinder = ((ComputerManagerService.ComputerManagerBinder)binder);
            // startAddThread();
        }

        public void onServiceDisconnected(ComponentName className) {
            // joinAddThread();
            managerBinder = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        this.queue = Volley.newRequestQueue(this);
        // UiHelper.setLocale(this);
        setContentView(R.layout.landing);
        // UiHelper.notifyNewRootView(this);

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

                    doAddPc(response.getString("ip"));
                    startActivity(new Intent(LandingView.this, PcView.class));
                }
                catch(Exception e){
                    // throw e;
                }
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
        boolean success;
        int portTestResult;

        SpinnerDialog dialog = SpinnerDialog.displayDialog(this, getResources().getString(R.string.title_add_pc),
            getResources().getString(R.string.msg_add_pc), false);

        try {
            ComputerDetails details = new ComputerDetails();
            details.manualAddress = host;
            success = managerBinder.addComputerBlocking(details);
        } catch (IllegalArgumentException e) {
            // This can be thrown from OkHttp if the host fails to canonicalize to a valid name.
            // https://github.com/square/okhttp/blob/okhttp_27/okhttp/src/main/java/com/squareup/okhttp/HttpUrl.java#L705
            e.printStackTrace();
            success = false;
        }
        // if (!success){
        //     wrongSiteLocal = isWrongSubnetSiteLocalAddress(host);
        // }
        // if (!success && !wrongSiteLocal) {
        //     // Run the test before dismissing the spinner because it can take a few seconds.
        //     portTestResult = MoonBridge.testClientConnectivity(ServerHelper.CONNECTION_TEST_SERVER, 443,
        //             MoonBridge.ML_PORT_FLAG_TCP_47984 | MoonBridge.ML_PORT_FLAG_TCP_47989);
        // } else {
        //     // Don't bother with the test if we succeeded or the IP address was bogus
        //     portTestResult = MoonBridge.ML_TEST_RESULT_INCONCLUSIVE;
        // }

        dialog.dismiss();

        // if (wrongSiteLocal) {
        //     Dialog.displayDialog(this, getResources().getString(R.string.conn_error_title), getResources().getString(R.string.addpc_wrong_sitelocal), false);
        // }
        // else if (!success) {
        //     String dialogText;
        //     if (portTestResult != MoonBridge.ML_TEST_RESULT_INCONCLUSIVE && portTestResult != 0)  {
        //         dialogText = getResources().getString(R.string.nettest_text_blocked);
        //     }
        //     else {
        //         dialogText = getResources().getString(R.string.addpc_fail);
        //     }
        //     Dialog.displayDialog(this, getResources().getString(R.string.conn_error_title), dialogText, false);
        // }
        // else {
        //     AddComputerManually.this.runOnUiThread(new Runnable() {
        //         @Override
        //         public void run() {
        //         Toast.makeText(AddComputerManually.this, getResources().getString(R.string.addpc_success), Toast.LENGTH_LONG).show();

        //         if (!isFinishing()) {
        //             // Close the activity
        //             AddComputerManually.this.finish();
        //         }
        //         }
        //     });
        // }
        
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
                        // startActivity(new Intent(LandingView.this, PcView.class));
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