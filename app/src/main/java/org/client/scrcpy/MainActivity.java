package org.client.scrcpy;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListPopupWindow;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.Toast;

import org.client.scrcpy.utils.HttpRequest;
import org.client.scrcpy.utils.PreUtils;
import org.client.scrcpy.utils.Progress;
import org.client.scrcpy.utils.ThreadUtils;
import org.client.scrcpy.utils.Util;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;


public class MainActivity extends Activity implements Scrcpy.ServiceCallbacks, SensorEventListener {

    // Flag that indicates whether to connect directly to a remote device
    public final static String START_REMOTE = "start_remote_headless";

    private boolean headlessMode = false;  // Headless mode hides controls and related UI
    private int screenWidth;
    private int screenHeight;
    private boolean landscape = false;
    private boolean first_time = true;
    private boolean result_of_Rotation = false;
    private boolean serviceBound = false;
    // Auto-reconnect if we paused to the background and were disconnected
    // Do not persist this state across restarts
    private boolean resumeScrcpy = false;
    SensorManager sensorManager;
    private SendCommands sendCommands;
    private int videoBitrate;
    private int delayControl;
    private Context context;
    private String serverAdr = null;
    private SurfaceView surfaceView;
    private Surface surface;
    private Scrcpy scrcpy;
    private long timestamp = 0;

    // private byte[] fileBase64;
    private LinearLayout linearLayout;
    private FrameLayout videoContainer;

    private int errorCount = 0;  // Count connection failures; restart the service after too many errors

    private boolean isQuestPanelEnvironment() {
        // Oculus/Metaブランド端末ではHorizon Homeの自由リサイズを優先する
        String manufacturer = Build.MANUFACTURER;
        if (manufacturer != null) {
            String lower = manufacturer.toLowerCase();
            if (lower.contains("oculus") || lower.contains("meta")) {
                return true;
            }
        }
        String brand = Build.BRAND;
        if (brand != null) {
            String lowerBrand = brand.toLowerCase();
            if (lowerBrand.contains("oculus") || lowerBrand.contains("meta")) {
                return true;
            }
        }
        return false;
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            scrcpy = ((Scrcpy.MyServiceBinder) iBinder).getService();
            scrcpy.setServiceCallbacks(MainActivity.this);
            serviceBound = true;
            if (first_time) {
                if (!Progress.isShowing()) {
                    Progress.showDialog(MainActivity.this, getString(R.string.please_wait));
                }
                scrcpy.start(surface, Scrcpy.LOCAL_IP + ":" + Scrcpy.LOCAL_FORWART_PORT,
                        screenHeight, screenWidth, delayControl);
                ThreadUtils.workPost(() -> {
                    int count = 50;
                    while (count > 0 && !scrcpy.check_socket_connection()) {
                        count--;
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    int finalCount = count;
                    ThreadUtils.post(() -> {
                        Progress.closeDialog();
                        if (finalCount == 0) {
                            if (serviceBound) {
                                showMainView();
                            }
                            Toast.makeText(context, "Connection Timed out 2", Toast.LENGTH_SHORT).show();
                        } else {
                            first_time = false;
                            // Show the controls only after the connection succeeds
                            set_display_nd_touch();
                            connectSuccessExt();
                        }
                    });
                });
            } else {
                scrcpy.setParms(surface, screenWidth, screenHeight);
                set_display_nd_touch();
                connectSuccessExt();
            }
            // set_display_nd_touch();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            serviceBound = false;
        }
    };

    private void showMainView() {
        showMainView(false);
    }

    // userDisconnect indicates a manual disconnect initiated by the user
    private void showMainView(boolean userDisconnect) {
        if (scrcpy != null) {
            scrcpy.StopService();
        }
        try {
            // Unbinding might happen twice, so catch the exception
            unbindService(serviceConnection);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (surface != null) {
            surface = null;
        }
        if (surfaceView != null) {
            surfaceView = null;
        }
        if (videoContainer != null) {
            videoContainer = null;
        }
        serviceBound = false;
        scrcpy_main();

        if (scrcpy != null) {
            scrcpy = null;
        }
        // Handle additional cleanup when leaving the connection
        connectExitExt(userDisconnect);
    }

    @SuppressLint("SourceLockedOrientationActivity")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.context = this;
        if (savedInstanceState != null) {
            first_time = savedInstanceState.getBoolean("first_time");
            landscape = savedInstanceState.getBoolean("landscape");
            headlessMode = savedInstanceState.getBoolean("headlessMode");
            resumeScrcpy = savedInstanceState.getBoolean("resumeScrcpy");
            screenHeight = savedInstanceState.getInt("screenHeight");
            screenWidth = savedInstanceState.getInt("screenWidth");
        }
        // Determine whether the screen is currently landscape or portrait
        landscape = getApplication().getResources().getConfiguration().orientation
                != Configuration.ORIENTATION_PORTRAIT;
        if (first_time) {
            scrcpy_main();
        } else {
            Log.e("Scrcpy: ", "from onCreate");
            start_screen_copy_magic();
        }
        sensorManager = (SensorManager) this.getSystemService(SENSOR_SERVICE);
        Sensor proximity;
        proximity = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        sensorManager.registerListener(this, proximity, SensorManager.SENSOR_DELAY_NORMAL);

        if (savedInstanceState != null) {
            Log.i("Scrcpy", "outState: " + savedInstanceState.getBoolean("from_save_instance"));
        }
        // Restore from a destroyed state
        if (savedInstanceState == null || !savedInstanceState.getBoolean("from_save_instance", false)) {
            // First time entering the app
            if (getIntent() != null && getIntent().getExtras() != null) {
                headlessMode = getIntent().getExtras().getBoolean(START_REMOTE, headlessMode);
            }
        }
        if (headlessMode && first_time) {
            getAttributes();
            connectScrcpyServer(PreUtils.get(this, Constant.CONTROL_REMOTE_ADDR, ""));
        }
        if (headlessMode) {
            View scrollView = findViewById(R.id.main_scroll_view);
            if (scrollView != null) {
                scrollView.setVisibility(View.INVISIBLE);
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Log.i("Scrcpy", "enter onSaveInstanceState");
        outState.putBoolean("from_save_instance", true);
        outState.putBoolean("first_time", first_time);
        outState.putBoolean("landscape", landscape);
        outState.putBoolean("headlessMode", headlessMode);  // Intent resets on the second entry, so persist the state
        // Avoid restoring orientation when toggling between floating/half-screen to prevent black screens (resume only allows a single reconnection)
        // outState.putBoolean("resumeScrcpy", resumeScrcpy);
        outState.putInt("screenHeight", screenHeight);
        outState.putInt("screenWidth", screenWidth);
    }

    @SuppressLint("SourceLockedOrientationActivity")
    public void scrcpy_main() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            this.getWindow().setStatusBarColor(getColor(R.color.status_bar));
        } else {
            this.getWindow().setStatusBarColor(getResources().getColor(R.color.status_bar));
        }
        final View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(View.VISIBLE);
        if (!isQuestPanelEnvironment()) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            landscape = false;  // Reset to portrait; incorrect mode can lead to a black screen
        }
        setContentView(R.layout.activity_main);
        final Button startButton = findViewById(R.id.button_start);
        // final Button floatButton = findViewById(R.id.button_start_float);

        sendCommands = new SendCommands();

        startButton.setOnClickListener(v -> {
            // local_ip = wifiIpAddress();
            getAttributes();
            connectScrcpyServer(serverAdr);
        });

//        floatButton.setOnClickListener(v -> {
//            getAttributes();
//            showDisplayWindow();
//        });
        get_saved_preferences();

        EditText editText = findViewById(R.id.editText_server_host);

        findViewById(R.id.history_list).setOnClickListener(v -> {
            Log.i("Scrcpy", "focus true");
            editText.clearFocus();
            showListPopulWindow(editText);
        });

        // In headless mode, hide all controls to avoid revealing the IP address
        if (headlessMode) {
            View scrollView = findViewById(R.id.main_scroll_view);
            if (scrollView != null) {
                scrollView.setVisibility(View.INVISIBLE);
            }
        }
    }

    private void showListPopulWindow(EditText mEditText) {
        String[] list = getHistoryList(); // Data to populate the popup
        if (list.length == 0) {  // Default to the local host if the list is empty
            list = new String[]{"127.0.0.1"};
        }
        final ListPopupWindow listPopupWindow;
        listPopupWindow = new ListPopupWindow(this);
        listPopupWindow.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, list)); // Use the built-in layout or customize if needed
        listPopupWindow.setAnchorView(mEditText); // Anchor the popup to the EditText
        listPopupWindow.setModal(true);
        listPopupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        String[] finalList = list;
        listPopupWindow.setOnItemClickListener(new AdapterView.OnItemClickListener() { // Handle item selection
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                mEditText.setText(finalList[i]);
                listPopupWindow.dismiss();
            }
        });
        listPopupWindow.show();
    }


//    private void showDisplayWindow() {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//            if (!Settings.canDrawOverlays(this)) {
//                // Launch an activity so the user can grant the permission
//                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
//                startActivity(intent);
//                return;
//            }
//        }
//        Intent it = new Intent(this, FloatService.class);
//        it.putExtra("ip", serverAdr);
//        it.putExtra("w", screenWidth);
//        it.putExtra("h", screenHeight);
//        it.putExtra("b", videoBitrate);
//        startService(it);
//        finish();
//    }


    public void get_saved_preferences() {
        final EditText editTextServerHost = findViewById(R.id.editText_server_host);
        final Switch aSwitch0 = findViewById(R.id.switch0);
        final Switch aSwitch1 = findViewById(R.id.switch1);
        String historySpServerAdr = PreUtils.get(context, Constant.CONTROL_REMOTE_ADDR, "");
        if (TextUtils.isEmpty(historySpServerAdr)) {
            String[] historyList = getHistoryList();
            if (historyList.length > 0) {
                editTextServerHost.setText(historyList[0]);
            }
        } else {
            editTextServerHost.setText(historySpServerAdr);
        }
        aSwitch0.setChecked(PreUtils.get(context, Constant.CONTROL_NO, false));
        aSwitch1.setChecked(PreUtils.get(context, Constant.CONTROL_NAV, false));
        setSpinner(R.array.options_resolution_values, R.id.spinner_video_resolution, Constant.PREFERENCE_SPINNER_RESOLUTION);
        setSpinner(R.array.options_bitrate_keys, R.id.spinner_video_bitrate, Constant.PREFERENCE_SPINNER_BITRATE);
        setSpinner(R.array.options_delay_keys, R.id.delay_control_spinner, Constant.PREFERENCE_SPINNER_DELAY);
        if (aSwitch0.isChecked()) {
            aSwitch1.setClickable(false);
            aSwitch1.setTextColor(Color.GRAY);
            // aSwitch1.setVisibility(View.GONE);
        }

        aSwitch0.setOnClickListener(v -> {
            if (aSwitch0.isChecked()) {
                aSwitch1.setClickable(false);
                aSwitch1.setTextColor(Color.GRAY);
                // aSwitch1.setVisibility(View.GONE);
            } else {
                aSwitch1.setClickable(true);
                aSwitch1.setTextColor(Color.WHITE);
                // aSwitch1.setVisibility(View.VISIBLE);
            }
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    public void set_display_nd_touch() {
        if (linearLayout != null) {
            linearLayout.setPadding(0, 0, 0, 0);
        }
        int[] rem_res = scrcpy.get_remote_device_resolution();
        int remoteLong = rem_res[1];
        int remoteShort = rem_res[0];
        if (remoteLong <= 0 || remoteShort <= 0) {
            return;
        }
        int remoteWidth = landscape ? remoteLong : remoteShort;
        int remoteHeight = landscape ? remoteShort : remoteLong;

        updateSurfaceLayout(remoteWidth, remoteHeight);
        if (!PreUtils.get(context, Constant.CONTROL_NO, false)) {
            // Log.i("Screen", "setOnTouchListener: " + surfaceView.getWidth() + "x" + surfaceView.getHeight());
            surfaceView.setOnTouchListener((view, event) -> scrcpy.touchevent(event, landscape, surfaceView.getWidth(), surfaceView.getHeight()));
        }

        if (PreUtils.get(context, Constant.CONTROL_NAV, false) &&
                !PreUtils.get(context, Constant.CONTROL_NO, false)) {
            final View backButton = findViewById(R.id.back_button);
            final View homeButton = findViewById(R.id.home_button);
            final View appswitchButton = findViewById(R.id.appswitch_button);

            if (backButton != null) {
                backButton.setOnClickListener(v -> scrcpy.sendKeyevent(KeyEvent.KEYCODE_BACK));
            }
            if (homeButton != null) {
                homeButton.setOnClickListener(v -> scrcpy.sendKeyevent(KeyEvent.KEYCODE_HOME));
            }
            if (appswitchButton != null) {
                appswitchButton.setOnClickListener(v -> scrcpy.sendKeyevent(KeyEvent.KEYCODE_APP_SWITCH));
            }
        }
    }

    private void updateSurfaceLayout(int remoteWidth, int remoteHeight) {
        if (videoContainer == null || surfaceView == null) {
            return;
        }
        int containerWidth = videoContainer.getWidth();
        int containerHeight = videoContainer.getHeight();
        if (containerWidth == 0 || containerHeight == 0) {
            videoContainer.post(() -> updateSurfaceLayout(remoteWidth, remoteHeight));
            return;
        }

        float remoteAspect = remoteWidth / (float) remoteHeight;
        float containerAspect = containerWidth / (float) containerHeight;

        int targetWidth;
        int targetHeight;
        if (remoteAspect > containerAspect) {
            targetWidth = containerWidth;
            targetHeight = Math.round(targetWidth / remoteAspect);
        } else {
            targetHeight = containerHeight;
            targetWidth = Math.round(targetHeight * remoteAspect);
        }

        ViewGroup.LayoutParams layoutParams = surfaceView.getLayoutParams();
        if (layoutParams.width != targetWidth || layoutParams.height != targetHeight) {
            layoutParams.width = targetWidth;
            layoutParams.height = targetHeight;
            surfaceView.setLayoutParams(layoutParams);
        }
    }

    private void setSpinner(final int textArrayOptionResId, final int textViewResId, final String preferenceId) {

        final Spinner spinner = findViewById(textViewResId);
        ArrayAdapter<CharSequence> arrayAdapter = ArrayAdapter.createFromResource(this, textArrayOptionResId, android.R.layout.simple_spinner_item);
        arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(arrayAdapter);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                PreUtils.put(context, preferenceId, position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                PreUtils.put(context, preferenceId, 0);
            }
        });
        int selection = PreUtils.get(context, preferenceId, 0);
        if (selection < arrayAdapter.getCount()) {
            spinner.setSelection(selection);
        } else {
            spinner.setSelection(0);
        }
    }

    private void getAttributes() {

        final EditText editTextServerHost = findViewById(R.id.editText_server_host);
        serverAdr = editTextServerHost.getText().toString();
        if (!TextUtils.isEmpty(serverAdr)) {
            serverAdr = serverAdr.trim();
        }
        if (!TextUtils.isEmpty(serverAdr)) {
            PreUtils.put(context, Constant.CONTROL_REMOTE_ADDR, serverAdr);
        }
        final Spinner videoResolutionSpinner = findViewById(R.id.spinner_video_resolution);
        final Spinner videoBitrateSpinner = findViewById(R.id.spinner_video_bitrate);
        final Spinner delayControlSpinner = findViewById(R.id.delay_control_spinner);
        final Switch a_Switch0 = findViewById(R.id.switch0);
        boolean no_control = a_Switch0.isChecked();
        final Switch a_Switch1 = findViewById(R.id.switch1);
        boolean nav = a_Switch1.isChecked();
        PreUtils.put(context, Constant.CONTROL_NO, no_control);
        PreUtils.put(context, Constant.CONTROL_NAV, nav);

        final String[] videoResolutions = getResources().getStringArray(R.array.options_resolution_values)[videoResolutionSpinner.getSelectedItemPosition()].split("x");
        screenHeight = Integer.parseInt(videoResolutions[0]);
        screenWidth = Integer.parseInt(videoResolutions[1]);
        videoBitrate = getResources().getIntArray(R.array.options_bitrate_values)[videoBitrateSpinner.getSelectedItemPosition()];
        delayControl = getResources().getIntArray(R.array.options_delay_values)[delayControlSpinner.getSelectedItemPosition()];
    }

    private String[] getHistoryList() {
        String historyList = PreUtils.get(context, Constant.HISTORY_LIST_KEY, "");
        if (TextUtils.isEmpty(historyList)) {
            return new String[]{};
        }
        try {
            JSONArray historyJson = new JSONArray(historyList);
            String[] retList = new String[historyJson.length()];
            for (int i = 0; i < historyJson.length(); i++) {
                retList[i] = historyJson.get(i).toString();
            }
            return retList;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new String[]{};
    }

    /**
     * Save the device's connection history.
     */
    private boolean saveHistory(String device) {
        if (headlessMode) {
            // Skip saving history when running headless
            return false;
        }
        JSONArray historyJson = new JSONArray();
        String[] historyList = getHistoryList();
        if (historyList.length == 0) {
            historyJson.put(device);
        } else {
            try {
                historyJson.put(0, device);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            // Store at most 30 entries
            int count = Math.min(historyList.length, 30);
            for (int i = 0; i < count; i++) {
                if (!historyList[i].equals(device)) {
                    historyJson.put(historyList[i]);
                }
            }
        }
        try {
            return PreUtils.put(context, Constant.HISTORY_LIST_KEY, historyJson.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private void swapDimensions() {
        int temp = screenHeight;
        screenHeight = screenWidth;
        screenWidth = temp;
    }

    @SuppressLint("ClickableViewAccessibility")
    private void start_screen_copy_magic() {
        setContentView(R.layout.surface);
        final View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        surfaceView = findViewById(R.id.decoder_surface);
        surface = surfaceView.getHolder().getSurface();
        final LinearLayout nav_bar = findViewById(R.id.nav_button_bar);
        if (PreUtils.get(context, Constant.CONTROL_NAV, false) &&
                !PreUtils.get(context, Constant.CONTROL_NO, false)) {
            nav_bar.setVisibility(LinearLayout.VISIBLE);
        } else {
            nav_bar.setVisibility(LinearLayout.GONE);
        }
        linearLayout = findViewById(R.id.container1);
        videoContainer = findViewById(R.id.video_container);
        start_Scrcpy_service();
    }


//    protected String wifiIpAddress() {
////https://stackoverflow.com/questions/6064510/how-to-get-ip-address-of-the-device-from-code
//        try {
//            InetAddress ipv4 = null;
//            InetAddress ipv6 = null;
//            Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
//            if (en != null) {
//                while (en.hasMoreElements()) {
//                    NetworkInterface int_f = en.nextElement();
//                    for (Enumeration<InetAddress> enumIpAddr = int_f
//                            .getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
//                        InetAddress inetAddress = enumIpAddr.nextElement();
//                        if (inetAddress instanceof Inet6Address) {
//                            ipv6 = inetAddress;
//                            continue;
//                        }
//                        if (inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
//                            ipv4 = inetAddress;
//                            continue;
//                        }
//                        return inetAddress.getHostAddress();
//                    }
//                }
//            }
//            if (ipv6 != null) {
//                return ipv6.getHostAddress();
//            }
//            if (ipv4 != null) {
//                return ipv4.getHostAddress();
//            }
//        } catch (Exception ex) {
//            ex.printStackTrace();
//        }
//        return "127.0.0.1";
//    }


    private void start_Scrcpy_service() {
        Intent intent = new Intent(this, Scrcpy.class);
        startService(intent);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @SuppressLint("SourceLockedOrientationActivity")
    @Override
    public void loadNewRotation() {
        if (first_time) {
            first_time = false;
        }
        try {
            // Unbinding might happen twice, so catch the exception
            unbindService(serviceConnection);
        } catch (Exception e) {
            e.printStackTrace();
        }
        serviceBound = false;
        result_of_Rotation = true;
        landscape = !landscape;
        swapDimensions();
        if (!isQuestPanelEnvironment()) {
            if (landscape) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            } else {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
            }
        }
    }

    @Override
    public void errorDisconnect() {
        // Force an exit and offer to reconnect
        Dialog.displayDialog(this, getString(R.string.disconnect),
                getString(R.string.disconnect_ask), () -> {
                    if (serviceBound) {
                        showMainView();
                        first_time = true;
                    } else {
                        MainActivity.this.finish();
                    }
                }, false);
    }


    @Override
    protected void onPause() {
        super.onPause();
        if (serviceBound) {
            scrcpy.pause();
            resumeScrcpy = true;
            // Returning to the main screen counts as a manual disconnect
            showMainView(true);
            first_time = true;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!first_time && !result_of_Rotation) {
            final View decorView = getWindow().getDecorView();
            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            if (serviceBound) {
                // A black screen here is due to configuration and does not require fixing
                linearLayout = findViewById(R.id.container1);
                scrcpy.resume();
            }
        }
        if (resumeScrcpy && !result_of_Rotation) {
            resumeScrcpy = false;
            connectScrcpyServer(PreUtils.get(context, Constant.CONTROL_REMOTE_ADDR, ""));
        }
        resumeScrcpy = false;  // Reset resumeScrcpy to false in both code paths
        result_of_Rotation = false;
    }

    @Override
    public void onBackPressed() {
        if (timestamp == 0) {
            if (serviceBound) {
                timestamp = SystemClock.uptimeMillis();
                Toast.makeText(context, "Press again to exit", Toast.LENGTH_SHORT).show();
            } else {
                finish();
            }
        } else {
            long now = SystemClock.uptimeMillis();
            if (now < timestamp + 1000) {
                timestamp = 0;
                if (serviceBound) {
                    showMainView(true);
                    first_time = true;
                    errorCount = 0;  // Manual disconnects reset the error counter
                } else {
                    finish();
                }
            }
            timestamp = 0;
        }
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if (sensorEvent.sensor.getType() == Sensor.TYPE_PROXIMITY) {
            if (sensorEvent.values[0] == 0) {
                if (serviceBound) {
                    // This event presses the remote power button; cover the proximity sensor and tap the screen to lock
                    // Sending orientation changes would make the release event invalid
                    // scrcpy.sendKeyevent(28);
                }
            } else {
                if (serviceBound) {
                    // Sending orientation changes would make the release event invalid
                    // scrcpy.sendKeyevent(29);
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    private void connectScrcpyServer(String serverAdr) {
        if (!TextUtils.isEmpty(serverAdr)) {
            saveHistory(serverAdr);  // Store in the connection history
            String[] serverInfo = Util.getServerHostAndPort(serverAdr);
            String serverHost = serverInfo[0];
            int serverPort = Integer.parseInt(serverInfo[1]);
            int localForwardPort = Scrcpy.LOCAL_FORWART_PORT;

            Progress.showDialog(MainActivity.this, getString(R.string.please_wait));
            ThreadUtils.workPost(() -> {
                AssetManager assetManager = getAssets();
                Log.d("Scrcpy", "File scrcpy-server.jar try write");
                try {
                    InputStream input_Stream = assetManager.open("scrcpy-server.jar");
                    byte[] buffer = new byte[input_Stream.available()];
                    input_Stream.read(buffer);
                    File scrcpyDir = context.getExternalFilesDir("scrcpy");
                    if (!scrcpyDir.exists()) {
                        scrcpyDir.mkdirs();
                    }
                    FileOutputStream outputStream = new FileOutputStream(new File(
                            context.getExternalFilesDir("scrcpy"), "scrcpy-server.jar"
                    ));
                    outputStream.write(buffer);
                    outputStream.flush();
                    outputStream.close();

                    // fileBase64 = Base64.encode(buffer, 2);
                } catch (IOException e) {
                    Log.d("Scrcpy", "File scrcpy-server.jar write faild");
                }
                if (sendCommands.SendAdbCommands(context, serverHost,
                        serverPort,
                        localForwardPort,
                        Scrcpy.LOCAL_IP,
                        videoBitrate, Math.max(screenHeight, screenWidth)) == 0) {
                    ThreadUtils.post(() -> {
                        if (!MainActivity.this.isFinishing()) {
                            // Execute on the main thread
                            Log.e("Scrcpy: ", "from startButton");
                            start_screen_copy_magic();
                        }
                    });
                } else {
                    ThreadUtils.post(Progress::closeDialog);
                    Toast.makeText(context, "Network OR ADB connection failed", Toast.LENGTH_SHORT).show();
                    connectExitExt();
                }
            });
        } else {
            Toast.makeText(context, "Server Address Empty", Toast.LENGTH_SHORT).show();
            connectExitExt();
        }
    }

    /**
     * Called once the connection succeeds and video is visible.
     */
    protected void connectSuccessExt() {
        errorCount = 0;
    }

    protected void connectExitExt() {
        this.connectExitExt(false);
    }

    /**
     * Additional processing when the connection fails.
     */
    protected void connectExitExt(boolean userDisconnect) {
        if (!userDisconnect) {  // userDisconnect represents a manual disconnect
            errorCount += 1;
            // errorCount = 0;
            Log.i("Scrcpy", "Connection error count: " + errorCount);
            // Restart the adb service after three errors
            App.startAdbServer();
        }
        // In headless mode, show a reconnect prompt automatically
        if (headlessMode && !resumeScrcpy && !result_of_Rotation) {
            // Only show the disconnect dialog if it was not manual, not a page switch, and not an orientation change
            if (!userDisconnect) {
                Dialog.displayDialog(this, getString(R.string.connect_faild),
                        getString(R.string.connect_faild_ask), () -> {
                            // Retry the connection
                            connectScrcpyServer(PreUtils.get(context, Constant.CONTROL_REMOTE_ADDR, ""));
                        }, () -> {

                            // Cancel the retry
                            finishAndRemoveTask();
                        });
            } else {
                finishAndRemoveTask();
            }
        }
//        Log.i("Scrcpy", "headlessMode： " + headlessMode +
//                " ,resumeScrcpy: " + resumeScrcpy + " ,result_of_Rotation: " + result_of_Rotation);
    }

}
