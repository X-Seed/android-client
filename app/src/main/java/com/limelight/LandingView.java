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
    private RequestQueue httpQueue; 
    private String serverUrl = "http://xseed.tech:2048/";
    private String serverIP = "";

    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        httpQueue = Volley.newRequestQueue(this);

        // UiHelper.setLocale(this);
        setContentView(R.layout.landing);
        UiHelper.notifyNewRootView(this);

   }

    public void handleLogin(View v){
        EditText userNameV = findViewById(R.id.userName);
        EditText passwordV = findViewById(R.id.password);
        String userName = userNameV.getText().toString();
        String password = passwordV.getText().toString();
        
        SpinnerDialog.displayDialog(this, "Logging in...", "", false);
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
                SpinnerDialog.closeDialogs(LandingView.this);
                try{
                    if(response.getString("status").equals("ok")){
                        // startActivity(new Intent(LandingView.this, PcView.class));
                        String token = response.getString("token");
                        
                        Intent i = new Intent(LandingView.this, Dashboard.class);
                        i.putExtra("TOKEN", token);
                        startActivity(i);
                    }
                    else{
                        Dialog.displayDialog(LandingView.this, "Login failed", response.getString("msg"), false);
                    }
                }
                catch(Exception e){
                    e = e;
                    Dialog.displayDialog(LandingView.this, "Exception", "Something bad happened with JSON object parsing", false);
                    // throw e;
                }
            }
        }, new ErrorListener(){
            @Override 
            public void onErrorResponse(VolleyError error){
                SpinnerDialog.closeDialogs(LandingView.this);
                error = error;

                Dialog.displayDialog(LandingView.this, "Network error", "", false);
            }
        });

        this.httpQueue.add(req);
    }

    public void handleRegister(View v){
        SpinnerDialog.displayDialog(this, "Registering", "Checking the details...", false);
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
                SpinnerDialog.closeDialogs(LandingView.this);
                try{
                    if(response.getString("status").equals("ok")){
                        // startActivity(new Intent(LandingView.this, LandingView.class));
                        login(userName, password);
                        return;
                    }
                    else{
                        String msg = response.getString("msg");
                        Dialog.displayDialog(LandingView.this, "Failed", msg, false);
                    }
                }
                catch(Exception e){
                    Dialog.displayDialog(LandingView.this, "Exception", e.getMessage(), false);
                }
            }
        }, new ErrorListener(){
            @Override 
            public void onErrorResponse(VolleyError error){
                SpinnerDialog.closeDialogs(LandingView.this);
                Dialog.displayDialog(LandingView.this, "Network error", "", false);
            }
        });

        this.httpQueue.add(req);
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