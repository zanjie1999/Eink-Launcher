package cn.modificator.launcher;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.WallpaperManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import cn.modificator.launcher.ftpservice.FTPService;
import cn.modificator.launcher.model.AppSortComparator;
import cn.modificator.launcher.model.WifiControl;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * 设置页面 Fragment。
 */
public class SettingFragment extends Fragment implements View.OnClickListener {

  private static final String TAG = "SettingFragment";
  private static final int REQUEST_PICK_WALLPAPER = 10003;
  private static final int REQUEST_FTP_STORAGE_PERMISSION = 10004;
  private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
  private static final ExecutorService WALLPAPER_EXECUTOR =
      Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable runnable) {
          return new Thread(runnable, "wallpaper-setter");
        }
      });

  /** 设置变更回调接口：宿主 Activity 应实现此接口以响应设置变更。 */
  public interface OnSettingChangeListener {
    void onRowNumChanged(int rowNum);
    void onColNumChanged(int colNum);
    void onFontSizeChanged(float size);
    void onAppNameLinesChanged(int lines);
    void onHideDividerChanged(boolean hide);
    void onShowStatusBarChanged(boolean show);
    void onShowCustomIconChanged(boolean show);
    void onClockShowSecondsChanged(boolean show);
    void onSortModeChanged(int mode);
    void onThemeModeChanged(int mode);
    void onScreenOrientationChanged(int mode);
    void onEnterManageMode();
  }

  private OnSettingChangeListener listener;

  private Spinner colNumSpinner;
  private Spinner rowNumSpinner;
  private Spinner appNameLinesSpinner;
  private Spinner sortModeSpinner;
  private Spinner themeModeSpinner;
  private Spinner screenOrientationSpinner;
  private SeekBar fontControl;
  private View rootView;
  private TextView hideDivider;
  private TextView ftpAddr;
  private TextView ftpStatus;
  private TextView showStatusBar;
  private TextView showCustomIcon;
  private TextView clockShowSeconds;
  private TextView startAtBoot;
  private Config config;
  private View changeFontSize;
  private View deleteApp;
  private View helpAbout;
  private View menuFtp;
  private boolean waitingForFtpStorageAccess;
  private View openDeviceManager;
  private View setWallpaper;
  private View showWifiName;

  @SuppressWarnings("deprecation")
  @Override
  public void onAttach(Activity activity) {
    super.onAttach(activity);
    if (activity instanceof OnSettingChangeListener) {
      listener = (OnSettingChangeListener) activity;
    } else {
      throw new ClassCastException(activity.toString() + " must implement OnSettingChangeListener");
    }
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.activity_setting, null);
  }

  @Override
  public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
    rootView = getView();
    config = new Config(getActivity());
    initViews();
    initSpinners();
    initFontControl();
    updateFtpStatus();
  }

  // =========================================================================
  // 初始化
  // =========================================================================

  private void initViews() {
    View toBack = rootView.findViewById(R.id.toBack);
    View btnHideFontControl = rootView.findViewById(R.id.btnHideFontControl);

    toBack.setOnClickListener(this);
    rootView.findViewById(R.id.rootView).setOnClickListener(this);
    btnHideFontControl.setOnClickListener(this);

    showStatusBar = rootView.findViewById(R.id.showStatusBar);
    showCustomIcon = rootView.findViewById(R.id.showCustomIcon);
    clockShowSeconds = rootView.findViewById(R.id.clockShowSeconds);
    startAtBoot = rootView.findViewById(R.id.startAtBoot);
    showWifiName = rootView.findViewById(R.id.showWifiName);
    ftpStatus = rootView.findViewById(R.id.ftp_status);
    ftpAddr = rootView.findViewById(R.id.ftp_addr);
    hideDivider = rootView.findViewById(R.id.hideDivider);
    fontControl = rootView.findViewById(R.id.font_control);
    colNumSpinner = rootView.findViewById(R.id.col_num_spinner);
    rowNumSpinner = rootView.findViewById(R.id.row_num_spinner);
    appNameLinesSpinner = rootView.findViewById(R.id.appNameLine);
    sortModeSpinner = rootView.findViewById(R.id.sortModeSpinner);
    themeModeSpinner = rootView.findViewById(R.id.theme_mode_spinner);
    screenOrientationSpinner = rootView.findViewById(R.id.screen_orientation_spinner);
    changeFontSize = rootView.findViewById(R.id.changeFontSize);
    deleteApp = rootView.findViewById(R.id.deleteApp);
    helpAbout = rootView.findViewById(R.id.helpAbout);
    menuFtp = rootView.findViewById(R.id.menu_ftp);
    openDeviceManager = rootView.findViewById(R.id.openDeviceManager);
    setWallpaper = rootView.findViewById(R.id.setWallpaper);

    showStatusBar.setOnClickListener(this);
    hideDivider.setOnClickListener(this);
    showCustomIcon.setOnClickListener(this);
    clockShowSeconds.setOnClickListener(this);
    startAtBoot.setOnClickListener(this);
    showWifiName.setOnClickListener(this);
    changeFontSize.setOnClickListener(this);
    deleteApp.setOnClickListener(this);
    helpAbout.setOnClickListener(this);
    menuFtp.setOnClickListener(this);
    openDeviceManager.setOnClickListener(this);
    setWallpaper.setOnClickListener(this);

    initDpadFocus(toBack, btnHideFontControl);

    // 初始化 UI 状态
    showStatusBar.getPaint().setStrikeThruText(!config.isShowStatusBar());
    hideDivider.getPaint().setStrikeThruText(config.isHideDivider());
    hideDivider.setText(config.isHideDivider() ? "显示分隔线" : "隐藏分隔线");
    showCustomIcon.getPaint().setStrikeThruText(!config.isShowCustomIcon());
    updateClockShowSecondsState();
    updateStartAtBootState();
    fontControl.setProgress((int) ((config.getFontSize() - 10) * 10));
  }

  private void initDpadFocus(View toBack, View btnHideFontControl) {
    View[] menuItems = new View[] {
        colNumSpinner,
        rowNumSpinner,
        appNameLinesSpinner,
        sortModeSpinner,
        screenOrientationSpinner,
        hideDivider,
        showStatusBar,
        showWifiName,
        showCustomIcon,
        clockShowSeconds,
        startAtBoot,
        changeFontSize,
        deleteApp,
        themeModeSpinner,
        setWallpaper,
        openDeviceManager,
        helpAbout,
        menuFtp
    };

    for (View item : menuItems) {
      makeFocusable(item);
    }
    makeFocusable(toBack);
    makeFocusable(btnHideFontControl);
    makeFocusable(fontControl);

    colNumSpinner.requestFocus();
  }

  private void makeFocusable(View view) {
    view.setFocusable(true);
    view.setFocusableInTouchMode(false);
    if (!(view instanceof Spinner) && !(view instanceof SeekBar)) {
      view.setBackgroundResource(R.drawable.setting_item_focus);
    }
  }

  private void initSpinners() {
    rowNumSpinner.setSelection(config.getRowNum() - 1, false);
    rowNumSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        int rowNum = position + 1;
        config.setRowNum(rowNum);
        listener.onRowNumChanged(rowNum);
      }

      @Override
      public void onNothingSelected(AdapterView<?> parent) {
      }
    });

    colNumSpinner.setSelection(config.getColNum() - 1, false);
    colNumSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        int colNum = position + 1;
        config.setColNum(colNum);
        listener.onColNumChanged(colNum);
      }

      @Override
      public void onNothingSelected(AdapterView<?> parent) {
      }
    });

    appNameLinesSpinner.setSelection(getAppLineSpinnerSelectPosition(), false);
    appNameLinesSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        int lines = (position == 3) ? Integer.MAX_VALUE : position;
        config.setAppNameLines(lines);
        listener.onAppNameLinesChanged(lines);
      }

      @Override
      public void onNothingSelected(AdapterView<?> parent) {
      }
    });

    sortModeSpinner.setSelection(config.getSortMode(), false);
    sortModeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (AppSortComparator.modeNeedsUsageStats(position)
            && !AppSortComparator.hasUsageStatsPermission(getActivity())) {
          Toast.makeText(getActivity(), R.string.sort_need_usage_permission, Toast.LENGTH_LONG).show();
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
          }
          sortModeSpinner.setSelection(config.getSortMode(), false);
          return;
        }
        config.setSortMode(position);
        listener.onSortModeChanged(position);
      }

      @Override
      public void onNothingSelected(AdapterView<?> parent) {
      }
    });

    screenOrientationSpinner.setSelection(config.getScreenOrientation(), false);
    screenOrientationSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (position == config.getScreenOrientation()) {
          return;
        }
        config.setScreenOrientation(position);
        listener.onScreenOrientationChanged(position);
      }

      @Override
      public void onNothingSelected(AdapterView<?> parent) {
      }
    });

    themeModeSpinner.setSelection(config.getThemeMode(), false);
    themeModeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (position == config.getThemeMode()) {
          return;
        }
        config.setThemeMode(position);
        listener.onThemeModeChanged(position);
      }

      @Override
      public void onNothingSelected(AdapterView<?> parent) {
      }
    });
  }

  private void initFontControl() {
    fontControl.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      @Override
      public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (fromUser) {
          float newSize = 10 + progress / 10f;
          config.setFontSize(newSize);
          listener.onFontSizeChanged(newSize);
        }
      }

      @Override
      public void onStartTrackingTouch(SeekBar seekBar) {
      }

      @Override
      public void onStopTrackingTouch(SeekBar seekBar) {
      }
    });
  }

  private int getAppLineSpinnerSelectPosition() {
    int lines = config.getAppNameLines();
    return (lines <= 2) ? lines : 3;
  }

  // =========================================================================
  // 点击处理
  // =========================================================================

  @Override
  public void onClick(View v) {
    int id = v.getId();
    if (id == R.id.toBack || id == R.id.rootView) {
      getActivity().onBackPressed();
    } else if (id == R.id.deleteApp) {
      handleDeleteApp();
    } else if (id == R.id.showStatusBar) {
      handleToggleStatusBar();
    } else if (id == R.id.helpAbout) {
      AboutDialog.getInstance(getActivity()).show();
    } else if (id == R.id.btnHideFontControl) {
      rootView.findViewById(R.id.menuList).setVisibility(View.VISIBLE);
      rootView.findViewById(R.id.font_control_p).setVisibility(View.GONE);
      changeFontSize.requestFocus();
    } else if (id == R.id.changeFontSize) {
      rootView.findViewById(R.id.menuList).setVisibility(View.GONE);
      rootView.findViewById(R.id.font_control_p).setVisibility(View.VISIBLE);
      fontControl.requestFocus();
    } else if (id == R.id.hideDivider) {
      handleToggleDivider();
    } else if (id == R.id.menu_ftp) {
      handleFtp();
    } else if (id == R.id.showWifiName) {
      handleShowWifiName();
    } else if (id == R.id.showCustomIcon) {
      handleToggleCustomIcon();
    } else if (id == R.id.clockShowSeconds) {
      handleToggleClockShowSeconds();
    } else if (id == R.id.startAtBoot) {
      handleToggleStartAtBoot();
    } else if (id == R.id.setWallpaper) {
      handleSetWallpaper();
    } else if (id == R.id.openDeviceManager) {
      startActivity(new Intent().setComponent(
          new ComponentName("com.android.settings", "com.android.settings.DeviceAdminSettings")));
    }
  }

  private void handleDeleteApp() {
    listener.onEnterManageMode();
    getActivity().onBackPressed();
  }

  private void handleToggleStatusBar() {
    boolean newValue = !config.isShowStatusBar();
    config.setShowStatusBar(newValue);
    listener.onShowStatusBarChanged(newValue);
    getActivity().onBackPressed();
  }

  private void handleToggleDivider() {
    boolean newValue = !config.isHideDivider();
    config.setHideDivider(newValue);
    hideDivider.setText(newValue ? "显示分隔线" : "隐藏分隔线");
    listener.onHideDividerChanged(newValue);
    getActivity().onBackPressed();
  }

  private void handleFtp() {
    if (FTPService.isRunning()) {
      stopFtpServer();
      return;
    }
    if (!FTPService.isConnectedToWifi(getActivity())) {
      Toast.makeText(getActivity(), "大哥诶，麻烦先把WIFI连上吧", Toast.LENGTH_SHORT).show();
      return;
    }
    if (hasFtpStorageAccess()) {
      startFtpServer();
    } else {
      requestFtpStorageAccess();
    }
  }

  private boolean hasFtpStorageAccess() {
    Activity activity = getActivity();
    if (activity == null) return false;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      return Environment.isExternalStorageManager();
    }
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
        || activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        == PackageManager.PERMISSION_GRANTED;
  }

  private void requestFtpStorageAccess() {
    Activity activity = getActivity();
    if (activity == null) return;

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
          Uri.parse("package:" + activity.getPackageName()));
      waitingForFtpStorageAccess = true;
      try {
        Log.i(TAG, "Opening app all files access settings");
        startActivity(intent);
      } catch (ActivityNotFoundException | SecurityException e) {
        try {
          Log.i(TAG, "Opening all files access settings list");
          startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
        } catch (ActivityNotFoundException | SecurityException unavailable) {
          waitingForFtpStorageAccess = false;
          Log.e(TAG, "All files access settings are unavailable", unavailable);
          Toast.makeText(activity, "无法打开所有文件访问权限设置", Toast.LENGTH_SHORT).show();
        }
      }
      return;
    }

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
          requestPermissions(new String[] {
              Manifest.permission.READ_EXTERNAL_STORAGE,
              Manifest.permission.WRITE_EXTERNAL_STORAGE
          }, REQUEST_FTP_STORAGE_PERMISSION);
      }
  }

  private void handleShowWifiName() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 10002);
    }
  }

  private void handleToggleCustomIcon() {
    Utils.checkStoragePermission(getActivity(), new Runnable() {
      @Override
      public void run() {
        boolean newValue = !config.isShowCustomIcon();
        config.setShowCustomIcon(newValue);
        listener.onShowCustomIconChanged(newValue);
        getActivity().onBackPressed();
      }
    });
  }

  private void handleToggleClockShowSeconds() {
    boolean newValue = !config.isClockShowSeconds();
    config.setClockShowSeconds(newValue);
    updateClockShowSecondsState();
    listener.onClockShowSecondsChanged(newValue);
  }

  private void updateClockShowSecondsState() {
    clockShowSeconds.getPaint().setStrikeThruText(!config.isClockShowSeconds());
  }

  private void handleToggleStartAtBoot() {
    config.setStartAtBoot(!config.isStartAtBoot());
    updateStartAtBootState();
  }

  private void updateStartAtBootState() {
    startAtBoot.getPaint().setStrikeThruText(!config.isStartAtBoot());
  }

  private void handleSetWallpaper() {
    Activity activity = getActivity();
    if (activity == null) return;

    WallpaperManager wallpaperManager = WallpaperManager.getInstance(activity.getApplicationContext());
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !wallpaperManager.isWallpaperSupported()) {
      Toast.makeText(activity, R.string.wallpaper_not_supported, Toast.LENGTH_SHORT).show();
      return;
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !wallpaperManager.isSetWallpaperAllowed()) {
      Toast.makeText(activity, R.string.wallpaper_not_allowed, Toast.LENGTH_SHORT).show();
      return;
    }

    Intent pickerIntent = new Intent(
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
            ? Intent.ACTION_OPEN_DOCUMENT
            : Intent.ACTION_GET_CONTENT);
    pickerIntent.addCategory(Intent.CATEGORY_OPENABLE);
    pickerIntent.setType("image/*");
    pickerIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

    try {
      startActivityForResult(pickerIntent, REQUEST_PICK_WALLPAPER);
    } catch (ActivityNotFoundException e) {
      Toast.makeText(activity, R.string.wallpaper_picker_unavailable, Toast.LENGTH_SHORT).show();
    }
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode != REQUEST_PICK_WALLPAPER || resultCode != Activity.RESULT_OK) return;

    Uri uri = data != null ? data.getData() : null;
    if (uri == null) {
      Activity activity = getActivity();
      if (activity != null) {
        Toast.makeText(activity, R.string.wallpaper_read_failed, Toast.LENGTH_SHORT).show();
      }
      return;
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      showWallpaperTargetDialog(uri);
    } else {
      applyWallpaper(uri, WallpaperManager.FLAG_SYSTEM);
    }
  }

  private void showWallpaperTargetDialog(final Uri uri) {
    Activity activity = getActivity();
    if (activity == null) return;

    CharSequence[] targets = new CharSequence[] {
        getString(R.string.wallpaper_target_home),
        getString(R.string.wallpaper_target_home_and_lock)
    };
    new AlertDialog.Builder(activity)
        .setTitle(R.string.wallpaper_target_title)
        .setItems(targets, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            int flags = which == 0
                ? WallpaperManager.FLAG_SYSTEM
                : WallpaperManager.FLAG_SYSTEM | WallpaperManager.FLAG_LOCK;
            applyWallpaper(uri, flags);
          }
        })
        .setNegativeButton(R.string.dialog_cancel, null)
        .show();
  }

  private void applyWallpaper(final Uri uri, final int flags) {
    Activity activity = getActivity();
    if (activity == null) return;
    final Context appContext = activity.getApplicationContext();

    WALLPAPER_EXECUTOR.execute(new Runnable() {
      @Override
      public void run() {
        InputStream inputStream;
        try {
          inputStream = appContext.getContentResolver().openInputStream(uri);
        } catch (IOException | SecurityException e) {
          Log.e(TAG, "Unable to open selected wallpaper", e);
          postWallpaperToast(appContext, R.string.wallpaper_read_failed);
          return;
        }

        if (inputStream == null) {
          postWallpaperToast(appContext, R.string.wallpaper_read_failed);
          return;
        }

        try (InputStream stream = inputStream) {
          WallpaperManager wallpaperManager = WallpaperManager.getInstance(appContext);
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            wallpaperManager.setStream(stream, null, true, flags);
          } else {
            wallpaperManager.setStream(stream);
          }
          postWallpaperToast(appContext, R.string.wallpaper_setting_success);
        } catch (IOException | SecurityException | IllegalArgumentException e) {
          Log.e(TAG, "Unable to set wallpaper", e);
          postWallpaperToast(appContext, R.string.wallpaper_setting_failed);
        }
      }
    });
  }

  private static void postWallpaperToast(final Context context, final int messageResId) {
    MAIN_HANDLER.post(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(context, messageResId, Toast.LENGTH_SHORT).show();
      }
    });
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode == REQUEST_FTP_STORAGE_PERMISSION) {
      if (hasFtpStorageAccess()) {
        startFtpServer();
      } else if (getActivity() != null) {
        Toast.makeText(getActivity(), "需要存储权限才能启动网络传书", Toast.LENGTH_SHORT).show();
      }
    } else if (requestCode == 10002) {
      WifiControl.reloadWifiName();
      getActivity().onBackPressed();
    }
  }

  // =========================================================================
  // 生命周期
  // =========================================================================

  @Override
  public void onResume() {
    super.onResume();
    if (waitingForFtpStorageAccess) {
      waitingForFtpStorageAccess = false;
      if (hasFtpStorageAccess()) {
        startFtpServer();
      } else if (getActivity() != null) {
        Toast.makeText(getActivity(), "需要所有文件访问权限才能启动网络传书", Toast.LENGTH_SHORT).show();
      }
    }
    updateFtpStatus();

    IntentFilter wifiFilter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
    Utils.registerReceiverCompat(getActivity(), wifiReceiver, wifiFilter);

    IntentFilter ftpFilter = new IntentFilter();
    ftpFilter.addAction(FTPService.ACTION_STARTED);
    ftpFilter.addAction(FTPService.ACTION_STOPPED);
    ftpFilter.addAction(FTPService.ACTION_FAILEDTOSTART);
    Utils.registerReceiverCompat(getActivity(), ftpReceiver, ftpFilter);
  }

  @Override
  public void onPause() {
    super.onPause();
    getActivity().unregisterReceiver(wifiReceiver);
    getActivity().unregisterReceiver(ftpReceiver);
  }

  // =========================================================================
  // FTP 控制
  // =========================================================================

  private void startFtpServer() {
    Activity activity = getActivity();
    if (activity == null) return;
    Log.i(TAG, "FTP service start requested");
    Intent service = new Intent(activity, FTPService.class);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      activity.startForegroundService(service);
    } else {
      activity.startService(service);
    }
    updateFtpStatus();
  }

  private void stopFtpServer() {
    Activity activity = getActivity();
    if (activity == null) return;
    Log.i(TAG, "FTP service stop requested");
    activity.stopService(new Intent(activity, FTPService.class));
    updateFtpStatus();
  }

  private void updateFtpStatus() {
    if (FTPService.isConnectedToWifi(getActivity())) {
      if (FTPService.isRunning()) {
        ftpStatus.setText(R.string.setting_cloud_manager_on);
        ftpAddr.setVisibility(View.VISIBLE);
        String address = getFTPAddressString();
        if (address != null) {
          ftpAddr.setText(address);
        } else {
          ftpAddr.setVisibility(View.GONE);
        }
      } else {
        ftpStatus.setText(R.string.setting_cloud_manager_off);
        ftpAddr.setVisibility(View.GONE);
      }
    } else {
      ftpStatus.setText(R.string.setting_cloud_manager_wifi_off);
      ftpAddr.setVisibility(View.GONE);
    }
  }

  private String getFTPAddressString() {
    java.net.InetAddress address = FTPService.getLocalInetAddress(getActivity());
    if (address == null) {
      return null;
    }
    return "ftp://" + address.getHostAddress() + ":" + FTPService.getPort();
  }

  // =========================================================================
  // 广播接收器
  // =========================================================================

  private final BroadcastReceiver wifiReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      ConnectivityManager conMan = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
      NetworkInfo netInfo = conMan.getActiveNetworkInfo();
      if (netInfo == null || netInfo.getType() != ConnectivityManager.TYPE_WIFI) {
        stopFtpServer();
      }
      updateFtpStatus();
    }
  };

  private final BroadcastReceiver ftpReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      if (FTPService.ACTION_FAILEDTOSTART.equals(intent.getAction())) {
        Toast.makeText(getActivity(), "网络传书启动失败", Toast.LENGTH_SHORT).show();
      }
      updateFtpStatus();
    }
  };
}
