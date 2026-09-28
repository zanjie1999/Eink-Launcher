package cn.modificator.launcher.model;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import java.io.File;
import java.util.Map;

import cn.modificator.launcher.R;
import cn.modificator.launcher.Utils;
import cn.modificator.launcher.widgets.ObserverFontTextView;
import cn.modificator.launcher.widgets.RatioImageView;

/**
 * WiFi 状态管理及 UI 绑定。
 * 通过 {@link #init(Context)} 初始化单例，{@link #bind(View, Map)} 绑定视图。
 */
public class WifiControl {

  private static final String TAG = "WifiControl";
  private static final String WIFI_ON_RES_NAME = "E-ink_Launcher.WifiOn";
  private static final String WIFI_OFF_RES_NAME = "E-ink_Launcher.WifiOff";

  private ObserverFontTextView appName;
  private RatioImageView appImage;
  private final WifiStateReceiver wifiStateReceiver;
  private final WifiManager wifiManager;
  private final Context appContext;
  private boolean receiverRegistered;

  private int showNameRes;
  private int showIconRes;
  private String connectWifiName;
  private Map<String, File> iconReplaceMap;

  private static WifiControl instance;

  public static void init(Context context) {
    release();
    instance = new WifiControl(context.getApplicationContext());
    reloadWifiName();
  }

  private WifiControl(Context context) {
    appContext = context;
    wifiManager = (WifiManager) appContext.getSystemService(Context.WIFI_SERVICE);

    applyWifiState(wifiManager.getWifiState());

    wifiStateReceiver = new WifiStateReceiver();
    IntentFilter filter = new IntentFilter();
    filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
    filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
    Utils.registerReceiverCompat(appContext, wifiStateReceiver, filter);
    receiverRegistered = true;
  }

  public static void release() {
    if (instance == null) return;
    instance.appImage = null;
    instance.appName = null;
    if (instance.receiverRegistered) {
      try {
        instance.appContext.unregisterReceiver(instance.wifiStateReceiver);
      } catch (IllegalArgumentException ignored) {
      }
      instance.receiverRegistered = false;
    }
    instance = null;
  }

  public static void bind(View view, Map<String, File> iconReplaceMap) {
    if (view == null) {
      instance.appImage = null;
      instance.appName = null;
      return;
    }
    instance.iconReplaceMap = iconReplaceMap;
    instance.appName = view.findViewById(R.id.appName);
    instance.appImage = view.findViewById(R.id.appImage);
    instance.updateStatus();
  }

  public static void reloadWifiName() {
    if (instance == null || instance.wifiManager == null) return;
    try {
      String ssid = instance.wifiManager.getConnectionInfo().getSSID();
      if (ssid == null) return;
      ssid = ssid.replace("\"", "");
      if (TextUtils.isEmpty(ssid) || "<unknown ssid>".equalsIgnoreCase(ssid)) return;
      instance.connectWifiName = "\n" + ssid;
      instance.showNameRes = R.string.wifi_status_connected;
      instance.updateStatus();
    } catch (SecurityException ignored) {
      // Android 8+ requires location permission for SSID access.
    }
  }

  private void updateStatus() {
    if (appName == null) return;

    appName.setText(appContext.getString(showNameRes, connectWifiName));

    String fileName = showIconRes == R.drawable.wifi_on ? WIFI_ON_RES_NAME : WIFI_OFF_RES_NAME;
    File replaceFile = iconReplaceMap != null ? iconReplaceMap.get(fileName) : null;
    if (replaceFile != null) {
      appImage.setImageURI(Uri.fromFile(replaceFile));
    } else {
      appImage.setImageResource(showIconRes);
    }
  }

  public static void onClickWifiItem() {
    try {
      int state = instance.wifiManager.getWifiState();
      boolean isEnabled = (state == WifiManager.WIFI_STATE_ENABLING || state == WifiManager.WIFI_STATE_ENABLED);
      instance.wifiManager.setWifiEnabled(!isEnabled);
    } catch (Exception e) {
      instance.openWifiSettingsFallback();
    }
  }

  public static void onLongClickWifiItem() {
    Intent intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    instance.appContext.startActivity(intent);
  }

  private void openWifiSettingsFallback() {
    if (tryStartSettings(Settings.ACTION_WIFI_SETTINGS)) return;
    if (tryStartSettings(Settings.ACTION_SETTINGS)) return;
    Toast.makeText(appContext, "打开Wi-Fi设置失败", Toast.LENGTH_SHORT).show();
  }

  private boolean tryStartSettings(String action) {
    Intent intent = new Intent(action);
    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    try {
      appContext.startActivity(intent);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  private void applyWifiState(int wifiState) {
    switch (wifiState) {
      case WifiManager.WIFI_STATE_DISABLED:
        showNameRes = R.string.wifi_status_off;
        showIconRes = R.drawable.wifi_off;
        break;
      case WifiManager.WIFI_STATE_DISABLING:
        showNameRes = R.string.wifi_status_closing;
        showIconRes = R.drawable.wifi_on;
        break;
      case WifiManager.WIFI_STATE_ENABLING:
        showNameRes = R.string.wifi_status_opening;
        showIconRes = R.drawable.wifi_off;
        break;
      case WifiManager.WIFI_STATE_ENABLED:
        showNameRes = R.string.wifi_status_on;
        showIconRes = R.drawable.wifi_on;
        break;
    }
  }

  private class WifiStateReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
      String action = intent.getAction();

      if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action)) {
        int wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, 0);
        applyWifiState(wifiState);
      }

      if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(action)) {
        NetworkInfo networkInfo = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
        if (networkInfo != null) {
          handleNetworkStateChanged(networkInfo);
        }
      }

      updateStatus();
    }
  }

  private void handleNetworkStateChanged(NetworkInfo networkInfo) {
    switch (networkInfo.getState()) {
      case CONNECTED:
        Log.d(TAG, "CONNECTED");
        String wifiName = "";
        if (networkInfo.getExtraInfo() != null) {
          wifiName = networkInfo.getExtraInfo().replace("\"", "");
        }
        if (wifiName.isEmpty()) {
          wifiName = wifiManager.getConnectionInfo().getSSID().replace("\"", "");
        }
        if (!TextUtils.isEmpty(wifiName)) {
          wifiName = "\n" + wifiName;
        }
        showNameRes = R.string.wifi_status_connected;
        connectWifiName = wifiName;
        break;
      case CONNECTING:
        Log.d(TAG, "CONNECTING");
        showNameRes = R.string.wifi_status_connecting;
        break;
      case DISCONNECTED:
        Log.d(TAG, "DISCONNECTED");
        showNameRes = R.string.wifi_status_disconnected;
        break;
      case DISCONNECTING:
        Log.d(TAG, "DISCONNECTING");
        showNameRes = R.string.wifi_status_disconnecting;
        break;
      default:
        break;
    }
  }
}
