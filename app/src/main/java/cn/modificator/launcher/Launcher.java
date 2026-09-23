package cn.modificator.launcher;

import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import cn.modificator.launcher.ftpservice.FTPReceiver;
import cn.modificator.launcher.ftpservice.FTPService;
import cn.modificator.launcher.model.AdminReceiver;
import cn.modificator.launcher.model.AppDataCenter;
import cn.modificator.launcher.model.HomeEntranceService;
import cn.modificator.launcher.model.IconCache;
import cn.modificator.launcher.model.WifiControl;
import cn.modificator.launcher.widgets.AppItemBinder;
import cn.modificator.launcher.widgets.BatteryView;
import cn.modificator.launcher.widgets.EInkLauncherView;
import cn.modificator.launcher.widgets.LauncherAdapter;

/**
 * 主界面 Activity - E-Ink 墨水屏桌面启动器。
 */
public class Launcher extends AppCompatActivity
    implements AppItemBinder.Callback, EInkLauncherView.OnPageChangeListener,
    SettingFragment.OnSettingChangeListener {

  private static final int REQUEST_DEVICE_ADMIN = 10001;

  // ---- Views ----
  private EInkLauncherView launcherView;
  private View footerContainer;
  private View footerContent;
  private View batteryContainer;
  private TextView pageStatus;
  private BatteryView batteryProgress;
  private TextView batteryStatus;
  private TextView textClock;
  private ImageView settingIcon;

  // ---- Data ----
  private AppDataCenter dataCenter;
  private Config config;
  private Calendar calendar;
  private boolean isChina = true;
  private IconCache iconCache;
  private LauncherAdapter adapter;
  private AppItemBinder binder;
  private boolean isSystemApp = false;
  private int focusArea = FOCUS_NONE;
  private int lastGridIndex = 0;
  private boolean confirmLongPressed = false;
  private int handledLauncherKeyCode = KeyEvent.KEYCODE_UNKNOWN;
  private long handledLauncherKeyDownTime = 0L;
  private final Handler clockHandler = new Handler(Looper.getMainLooper());
  private boolean clockTickerRunning = false;
  private boolean batteryStatusRequestedVisible = false;
  private int settingIconNormalWidth;
  private int settingIconNormalHeight;
  private int settingIconNormalHorizontalPadding;
  private int settingIconNormalVerticalPadding;
  private int batteryIconNormalWidth;
  private int batteryIconNormalHeight;
  private int batteryContainerNormalLeftMargin;
  private int batteryContainerNormalRightMargin;
  private int pageStatusNormalWidth;
  private float pageStatusNormalTextSize;
  private int footerNormalHeight;

  // ---- Device Admin ----
  private DevicePolicyManager policyManager;

  private static final int FOCUS_NONE = 0;
  private static final int FOCUS_GRID = 1;
  private static final int FOCUS_CLOCK = 2;
  private static final int FOCUS_BATTERY = 3;
  private static final int FOCUS_SETTING = 4;

  // ---- Receivers ----
  private FTPReceiver ftpReceiver = new FTPReceiver();
  private boolean batteryRegistered;
  private boolean timeRegistered;
  private boolean usbRegistered;
  private boolean ftpRegistered;

  private final BroadcastReceiver timeReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      updateTimeShow();
    }
  };

  private final Runnable clockTicker = new Runnable() {
    @Override
    public void run() {
      updateTimeShow();
      clockHandler.postDelayed(this, getNextClockTickDelay());
    }
  };

  private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      handleBatteryChanged(intent);
    }
  };

  private final BroadcastReceiver appChangeReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      iconCache.clearAppCache();
      dataCenter.refreshAppList(binder.isDelete());
    }
  };

  private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      String action = intent.getAction();
      if (Intent.ACTION_MEDIA_MOUNTED.equals(action)) {
        iconCache.markDirty();
        refreshIcons();
      }
    }
  };

  // =========================================================================
  // Lifecycle
  // =========================================================================

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.launcher_activity);

    config = new Config(this);
    applyScreenOrientation(config.getScreenOrientation());

    // 主题切换
    int themeMode = config.getThemeMode();
    setThemeMode(themeMode);

    WifiControl.init(this);
    applyStatusBarVisibility();

    isChina = getResources().getConfiguration().locale.getCountry().equals("CN");

    initViews();
    registerStaticReceivers();
    checkLaunchHomeNotification();
  }

  private void setThemeMode(int themeMode) {
    Log.d("zyyme设置themeMode", String.valueOf(themeMode));
    if (themeMode == 0) {
      AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
      applyLauncherBackground(getResources().getColor(R.color.mainBgColor));
    } else if (themeMode == 1) {
      AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
      applyLauncherBackground(getResources().getColor(R.color.mainBgColor));
    } else if (themeMode == 2) {
      AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
      applyLauncherBackground(getResources().getColor(R.color.mainBgColor));
    } else if (themeMode == 3) {
      AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
      applyLauncherBackground(Color.WHITE);
    } else if (themeMode == 4) {
      AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
      applyLauncherBackground(Color.BLACK);
    } else if (themeMode == 5) {
      // 亮色配色，但不绘制白色半透明背景，直接显示壁纸。
      AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
      applySystemBarIconAppearance(true);
      applyLauncherBackground(Color.TRANSPARENT);
    } else if (themeMode == 6) {
      // 暗色配色，但不绘制黑色半透明背景，直接显示壁纸。
      AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
      applySystemBarIconAppearance(false);
      applyLauncherBackground(Color.TRANSPARENT);
    }
  }

  private void applySystemBarIconAppearance(boolean darkIcons) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      int appearance = darkIcons
          ? (android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
              | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
          : 0;
      getWindow().getInsetsController().setSystemBarsAppearance(appearance,
          android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
              | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
      return;
    }
    int flags = getWindow().getDecorView().getSystemUiVisibility();
    if (darkIcons) {
      flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
      }
    } else {
      flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
      }
    }
    getWindow().getDecorView().setSystemUiVisibility(flags);
  }

  private void applyLauncherBackground(final int backgroundColor) {
    findViewById(R.id.launcherBg).post(new Runnable() {
      @Override
      public void run() {
        findViewById(R.id.launcherBg).setBackgroundColor(backgroundColor);
        int themeMode = config != null ? config.getThemeMode() : -1;
        if (themeMode == 5) {
          applySystemBarIconAppearance(true);
        } else if (themeMode == 6) {
          applySystemBarIconAppearance(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
          getWindow().setStatusBarColor(backgroundColor);
          getWindow().setNavigationBarColor(backgroundColor);
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setStatusBarContrastEnforced(false);
            getWindow().setNavigationBarContrastEnforced(false);
            getWindow().setNavigationBarDividerColor(Color.TRANSPARENT);
          }
        }
      }
    });
  }

  @Override
  protected void onResume() {
    super.onResume();
    registerDynamicReceivers();
    int themeMode = config != null ? config.getThemeMode() : -1;
    if (themeMode == 5) {
      applySystemBarIconAppearance(true);
    } else if (themeMode == 6) {
      applySystemBarIconAppearance(false);
    }
    refreshIcons();
  }

  @Override
  protected void onPause() {
    super.onPause();
    unregisterDynamicReceivers();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    if (dataCenter != null) {
      dataCenter.shutdown();
    }
    if (binder != null) {
      binder.shutdown();
    }
    unregisterDynamicReceivers();
    unregisterReceiver(appChangeReceiver);
    WifiControl.release();
  }

  @Override
  public void onTrimMemory(int level) {
    super.onTrimMemory(level);
    if (iconCache != null) {
      iconCache.trimMemory(level);
    }
  }

  @Override
  public boolean dispatchTouchEvent(MotionEvent ev) {
    if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
      clearKeyboardFocus();
    }
    return super.dispatchTouchEvent(ev);
  }

  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    int keyCode = event.getKeyCode();
    Log.d("zyyme dispatchKeyEvent", String.valueOf(keyCode));

    if (event.getAction() == KeyEvent.ACTION_DOWN) {
      if (handleLauncherKeyDown(keyCode, event)) {
        handledLauncherKeyCode = keyCode;
        handledLauncherKeyDownTime = event.getDownTime();
        return true;
      }
    } else if (event.getAction() == KeyEvent.ACTION_UP) {
      boolean wasHandledOnDown =
          handledLauncherKeyCode == keyCode && handledLauncherKeyDownTime == event.getDownTime();
      if (handleLauncherKeyUp(keyCode, event) || wasHandledOnDown) {
        clearHandledLauncherKey();
        return true;
      }
    }

    return super.dispatchKeyEvent(event);
  }

  // =========================================================================
  // View 初始化
  // =========================================================================

  private void initViews() {
    policyManager = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);

    launcherView = findViewById(R.id.mList);
    launcherView.setFocusable(true);
    launcherView.setFocusableInTouchMode(true);
    launcherView.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
    footerContainer = findViewById(R.id.footerContainer);
    footerContent = findViewById(R.id.footerContent);
    batteryContainer = findViewById(R.id.batteryContainer);
    pageStatus = findViewById(R.id.pageStatus);
    batteryProgress = findViewById(R.id.batteryProgress);
    batteryStatus = findViewById(R.id.batteryStatus);
    textClock = findViewById(R.id.textClock);
    textClock.setBackgroundResource(R.drawable.footer_item_focus);
    textClock.setClickable(true);

    settingIcon = findViewById(R.id.toSetting);
    settingIconNormalWidth = settingIcon.getLayoutParams().width;
    settingIconNormalHeight = settingIcon.getLayoutParams().height;
    settingIconNormalHorizontalPadding = settingIcon.getPaddingLeft();
    settingIconNormalVerticalPadding = settingIcon.getPaddingTop();
    batteryIconNormalWidth = batteryProgress.getLayoutParams().width;
    batteryIconNormalHeight = batteryProgress.getLayoutParams().height;
    ViewGroup.MarginLayoutParams batteryParams =
        (ViewGroup.MarginLayoutParams) batteryContainer.getLayoutParams();
    batteryContainerNormalLeftMargin = batteryParams.leftMargin;
    batteryContainerNormalRightMargin = batteryParams.rightMargin;
    pageStatusNormalWidth = pageStatus.getLayoutParams().width;
    pageStatusNormalTextSize = pageStatus.getTextSize();
    footerNormalHeight = footerContent.getLayoutParams().height;
    settingIcon.setImageDrawable(
        Utils.tintDrawable(getResources().getDrawable(R.drawable.navibar_icon_settings_highlight),
            ColorStateList.valueOf(getResources().getColor(R.color.textColor))));
    footerContent.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
      @Override
      public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                 int oldLeft, int oldTop, int oldRight, int oldBottom) {
        if (right - left != oldRight - oldLeft) {
          updateTimeShow();
        }
      }
    });

    // 配置 Binder、Adapter、View
    iconCache = new IconCache();
    binder = new AppItemBinder(getPackageManager());
    binder.setCallback(this);
    binder.setIconCache(iconCache);
    binder.setHideAppPkg(config.getHideApps());
    adapter = new LauncherAdapter();
    adapter.setBinder(binder);
    adapter.setFontSize(config.getFontSize());
    adapter.setAppNameLines(config.getAppNameLines());
    launcherView.setAdapter(adapter);
    launcherView.setOnPageChangeListener(this);

    // 初始化数据中心
    dataCenter = new AppDataCenter(this);
    dataCenter.setSortMode(config.getSortMode());
    dataCenter.setPageStatus(pageStatus);

    // 一次性配置网格参数，避免多次重建
    applyGridSize(config.getColNum(), config.getRowNum());
    launcherView.post(new Runnable() {
      @Override
      public void run() {
        launcherView.requestFocus();
      }
    });
    dataCenter.setAdapter(adapter);
    dataCenter.setHideApps(config.getHideApps());

    // 翻页按钮
    findViewById(R.id.lastPage).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        showLastPageAndHideSelection();
      }
    });
    findViewById(R.id.nextPage).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        showNextPageAndHideSelection();
      }
    });

    // 设置按钮
    findViewById(R.id.toSetting).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        clearKeyboardFocus();
        openSettingsFragment();
      }
    });

    // 管理完成按钮
    findViewById(R.id.deleteFinish).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        finishManageMode();
      }
    });
    // 电池点击打开设置
    batteryProgress.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        clearKeyboardFocus();
        openBatterySettings();
      }
    });
    textClock.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        clearKeyboardFocus();
        openClockActivity();
      }
    });

    // 时间显示
    calendar = Calendar.getInstance();
    updateTimeShow();

    // 检测系统应用
    try {
      isSystemApp = !isUserApp(getPackageManager().getPackageInfo(getPackageName(), 0));
    } catch (PackageManager.NameNotFoundException e) {
      e.printStackTrace();
    }
  }

  // =========================================================================
  // SettingFragment.OnSettingChangeListener 实现
  // =========================================================================

  @Override
  public void onRowNumChanged(int rowNum) {
    config.setRowNum(rowNum);
    applyGridSize(config.getColNum(), config.getRowNum());
  }

  @Override
  public void onColNumChanged(int colNum) {
    config.setColNum(colNum);
    applyGridSize(config.getColNum(), config.getRowNum());
  }

  @Override
  public void onFontSizeChanged(float size) {
    adapter.setFontSize(size);
  }

  @Override
  public void onAppNameLinesChanged(int lines) {
    adapter.setAppNameLines(lines);
  }

  @Override
  public void onHideDividerChanged(boolean hide) {
    launcherView.setHideDivider(hide);
  }

  @Override
  public void onShowStatusBarChanged(boolean show) {
    applyStatusBarVisibility();
    recreate();
  }

  @Override
  public void onShowCustomIconChanged(boolean show) {
    iconCache.markDirty();
    iconCache.clearIconCache();
    refreshIcons();
  }

  @Override
  public void onClockShowSecondsChanged(boolean show) {
    config.setClockShowSeconds(show);
    updateClockRefreshMode();
  }

  @Override
  public void onEnterManageMode() {
    binder.setDelete(true);
    dataCenter.refreshAppList(true);
    findViewById(R.id.deleteFinish).setVisibility(View.VISIBLE);
  }

  @Override
  public void onSortModeChanged(int mode) {
    dataCenter.setSortMode(mode);
    dataCenter.refreshAppList(binder.isDelete());
  }

  @Override
  public void onThemeModeChanged(int mode) {
    if (mode == config.getThemeMode()) {
      return;
    }
    config.setThemeMode(mode);
    setThemeMode(mode);
    recreate();
  }

  @Override
  public void onScreenOrientationChanged(int mode) {
    config.setScreenOrientation(mode);
    applyScreenOrientation(mode);
  }

  // =========================================================================
  // 布局更新
  // =========================================================================

  private void refreshIcons() {
    if (adapter == null || iconCache == null) return;
    if (iconCache.refreshCustomIcons(
        getExternalCacheDir() != null, config.isShowCustomIcon())) {
      adapter.refreshDisplay();
    }
  }

  private void applyGridSize(int colNum, int rowNum) {
    if (launcherView != null) {
      int displayColNum = colNum;
      int displayRowNum = rowNum;
      if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
        displayColNum = rowNum;
        displayRowNum = colNum;
      }
      launcherView.configure(displayColNum, displayRowNum, config.isHideDivider());
    }
    if (dataCenter != null) {
      dataCenter.setGridSize(colNum, rowNum);
    }
    if (calendar != null) {
      updateTimeShow();
    }
  }

  private void applyScreenOrientation(int mode) {
    setRequestedOrientation(getRequestedOrientationForMode(mode));
  }

  private int getRequestedOrientationForMode(int mode) {
    switch (mode) {
      case 1:
        return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
      case 2:
        return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
      case 3:
        return ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
      case 4:
        return ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
      case 0:
      default:
        return ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
    }
  }

  // =========================================================================
  // AppItemBinder.Callback 实现
  // =========================================================================

  @Override
  public void onItemClick(ResolveInfo info) {
    String pkgName = info.activityInfo.packageName;

    if (AppDataCenter.LOCK_PACKAGE_NAME.equals(pkgName)) {
      lockScreen();
    } else if (AppDataCenter.WIFI_PACKAGE_NAME.equals(pkgName)) {
      WifiControl.onClickWifiItem();
    } else if (AppDataCenter.CLEAR_PACKAGE_NAME.equals(pkgName)) {
      cleanMemory();
    } else {
      ComponentName comp = new ComponentName(info.activityInfo.packageName, info.activityInfo.name);
      Intent intent = new Intent(Intent.ACTION_MAIN);
      intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
      intent.addCategory(Intent.CATEGORY_LAUNCHER);
      intent.setComponent(comp);
      startActivity(intent);
    }
  }

  @Override
  public void onItemLongClick(View anchor, ResolveInfo info) {
    String packageName = info.activityInfo.packageName;

    if (AppDataCenter.LOCK_PACKAGE_NAME.equals(packageName)) {
      showPowerMenu();
    } else if (AppDataCenter.WIFI_PACKAGE_NAME.equals(packageName)) {
      WifiControl.onLongClickWifiItem();
    } else if (AppDataCenter.CLEAR_PACKAGE_NAME.equals(packageName)) {
      // 内存清理图标，不做任何操作
    } else {
      showAppInfoDialog(info, packageName);
    }
  }

  @Override
  public void onItemDeleteClick(ResolveInfo info) {
    Intent deleteIntent = new Intent(Intent.ACTION_DELETE,
        Uri.parse("package:" + info.activityInfo.packageName));
    startActivity(deleteIntent);
  }

  @Override
  public void onItemHideToggle(String packageName, boolean hidden) {
    // 管理模式下的隐藏切换仅更新 UI 状态，"完成" 按钮处理持久化
  }

  // =========================================================================
  // EInkLauncherView.OnPageChangeListener 实现
  // =========================================================================

  @Override
  public void onPageNext() {
    showNextPageAndHideSelection();
  }

  @Override
  public void onPagePrev() {
    showLastPageAndHideSelection();
  }

  private void showPowerMenu() {
    if (!isSystemApp) return;
    new AlertDialog.Builder(this)
        .setTitle(R.string.power_title)
        .setItems(R.array.power_menu, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            if (which == 0) {
              Intent intent = new Intent("android.intent.action.ACTION_REQUEST_SHUTDOWN");
              intent.putExtra("android.intent.extra.KEY_CONFIRM", false);
              intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
              startActivity(intent);
            } else {
              PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
              pm.reboot("重启");
            }
          }
        })
        .setPositiveButton(R.string.dialog_cancel, null)
        .show();
  }

  private void showAppInfoDialog(ResolveInfo info, final String packageName) {
    CharSequence[] actions = {
        getString(R.string.dialog_app_info),
        getString(R.string.dialog_copy_package_name, packageName),
        getString(R.string.dialog_hide),
        getString(R.string.dialog_uninstall)
    };
    new AlertDialog.Builder(this)
        .setIcon(iconCache.getIcon(packageName, info, getPackageManager()))
        .setTitle(iconCache.getLabel(packageName, info, getPackageManager()))
        .setItems(actions, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            if (which == 0) {
              Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                  Uri.parse("package:" + packageName));
              startActivity(intent);
            } else if (which == 1) {
              ClipboardManager clipboard = (ClipboardManager)
                  getSystemService(Context.CLIPBOARD_SERVICE);
              if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("packageName", packageName));
                Toast.makeText(Launcher.this, R.string.package_name_copied,
                    Toast.LENGTH_SHORT).show();
              }
            } else if (which == 2) {
              Set<String> hideApps = binder.getHideAppPkg();
              if (!hideApps.add(packageName)) {
                hideApps.remove(packageName);
              }
              config.setHideApps(new HashSet<>(hideApps));
              dataCenter.setHideApps(config.getHideApps());
            } else if (which == 3) {
              Intent deleteIntent = new Intent(Intent.ACTION_DELETE,
                  Uri.parse("package:" + packageName));
              startActivity(deleteIntent);
            }
          }
        })
        .setNegativeButton(R.string.dialog_cancel, null)
        .show();
  }

  // =========================================================================
  // 时间显示
  // =========================================================================

  private void updateTimeShow() {
    if (textClock == null || calendar == null) return;

    boolean is24Hour = DateFormat.is24HourFormat(this);
    boolean showSeconds = config != null && config.isClockShowSeconds();
    calendar.setTimeInMillis(System.currentTimeMillis());
    Locale locale = Locale.getDefault();

    String dateText = new SimpleDateFormat("yyyy-MM-dd", locale).format(calendar.getTime());
    String shortDateText = new SimpleDateFormat("MM-dd", locale).format(calendar.getTime());
    StringBuilder timeFormat = new StringBuilder(is24Hour ? "H:mm" : "h:mm");
    if (showSeconds) {
      timeFormat.append(":ss");
    }
    String plainTimeText = new SimpleDateFormat(
        timeFormat.toString(), locale).format(calendar.getTime());
    String timeText = plainTimeText;
    if (!is24Hour && isChina) {
      timeText = Utils.getAMPMCNString(
          calendar.get(Calendar.HOUR), calendar.get(Calendar.AM_PM)) + timeText;
    }
    if (!is24Hour && !isChina) {
      timeText += new SimpleDateFormat(" a", locale).format(calendar.getTime());
    }
    String weekdayText = new SimpleDateFormat("EEEE", locale).format(calendar.getTime());

    updateFooterLayout(dateText, shortDateText, plainTimeText, timeText, weekdayText);
  }

  private void updateFooterLayout(String dateText, String shortDateText,
                                  String plainTimeText, String timeText,
                                  String weekdayText) {
    textClock.setText(plainTimeText);
    textClock.setVisibility(View.VISIBLE);
    int availableWidth = footerContent.getWidth()
        - footerContent.getPaddingLeft() - footerContent.getPaddingRight();
    if (availableWidth <= 0) return;

    int plainTimeWidth = measureClockWidth(plainTimeText);

    int normalControlsWidth = settingIconNormalWidth + batteryIconNormalWidth
        + batteryContainerNormalLeftMargin + batteryContainerNormalRightMargin
        + pageStatusNormalWidth;
    float controlsScale = normalControlsWidth == 0 ? 1f
        : Math.min(1f, Math.max(0f,
            (availableWidth - plainTimeWidth) / (float) normalControlsWidth));
    int controlsWidth = updateFooterControls(controlsScale);

    String dateAndTime = dateText + " " + timeText;
    String shortDateAndTime = shortDateText + " " + plainTimeText;
    String fullClock = dateAndTime + " " + weekdayText;
    int dateAndTimeWidth = measureClockWidth(dateAndTime);
    int shortDateAndTimeWidth = measureClockWidth(shortDateAndTime);
    int fullClockWidth = measureClockWidth(fullClock);
    boolean showDate = controlsWidth + dateAndTimeWidth <= availableWidth;
    boolean showShortDate = !showDate
        && controlsWidth + shortDateAndTimeWidth <= availableWidth;
    boolean showWeekday = showDate
        && controlsWidth + fullClockWidth <= availableWidth;

    String visibleClockText = plainTimeText;
    if (showWeekday) {
      visibleClockText = fullClock;
    } else if (showDate) {
      visibleClockText = dateAndTime;
    } else if (showShortDate) {
      visibleClockText = shortDateAndTime;
    }

    int batteryIconWidth = batteryProgress.getLayoutParams().width;
    int batteryStatusWidth = (int) Math.ceil(
        batteryStatus.getPaint().measureText(batteryStatus.getText().toString()));
    int batteryStatusExtraWidth = Math.max(0, batteryStatusWidth - batteryIconWidth);
    boolean showBatteryStatus = batteryStatusRequestedVisible
        && controlsScale >= 1f
        && !useCompactFooterHeight()
        && showWeekday
        && controlsWidth + fullClockWidth + batteryStatusExtraWidth <= availableWidth;

    textClock.setText(visibleClockText);
    batteryContainer.setVisibility(View.VISIBLE);
    pageStatus.setVisibility(View.VISIBLE);
    batteryStatus.setVisibility(showBatteryStatus ? View.VISIBLE : View.GONE);
  }

  private int measureClockWidth(String text) {
    return (int) Math.ceil(textClock.getPaint().measureText(text))
        + textClock.getCompoundPaddingLeft() + textClock.getCompoundPaddingRight();
  }

  private int updateFooterControls(float scale) {
    boolean compactHeight = useCompactFooterHeight();
    int footerHeight = compactHeight
        ? footerNormalHeight * 2 / 3
        : footerNormalHeight;
    int settingWidth = scaledSize(settingIconNormalWidth, scale);
    int batteryWidth = scaledSize(batteryIconNormalWidth, scale);
    int batteryHeight = scaledSize(batteryIconNormalHeight, scale);
    int batteryLeftMargin = scaledSize(batteryContainerNormalLeftMargin, scale);
    int batteryRightMargin = scaledSize(batteryContainerNormalRightMargin, scale);
    int pageWidth = scaledSize(pageStatusNormalWidth, scale);

    ViewGroup.LayoutParams params = settingIcon.getLayoutParams();
    int settingHeight = compactHeight ? footerHeight : settingIconNormalHeight;
    if (params.width != settingWidth || params.height != settingHeight) {
      params.width = settingWidth;
      params.height = settingHeight;
      settingIcon.setLayoutParams(params);
    }
    int horizontalPadding = scaledSize(settingIconNormalHorizontalPadding, scale);
    int verticalPadding = compactHeight
        ? settingIconNormalVerticalPadding / 3
        : settingIconNormalVerticalPadding;
    if (settingIcon.getPaddingLeft() != horizontalPadding
        || settingIcon.getPaddingRight() != horizontalPadding
        || settingIcon.getPaddingTop() != verticalPadding
        || settingIcon.getPaddingBottom() != verticalPadding) {
      settingIcon.setPadding(horizontalPadding, verticalPadding,
          horizontalPadding, verticalPadding);
    }

    params = batteryProgress.getLayoutParams();
    if (params.width != batteryWidth || params.height != batteryHeight) {
      params.width = batteryWidth;
      params.height = batteryHeight;
      batteryProgress.setLayoutParams(params);
    }
    ViewGroup.MarginLayoutParams batteryParams =
        (ViewGroup.MarginLayoutParams) batteryContainer.getLayoutParams();
    if (batteryParams.leftMargin != batteryLeftMargin
        || batteryParams.rightMargin != batteryRightMargin) {
      batteryParams.leftMargin = batteryLeftMargin;
      batteryParams.rightMargin = batteryRightMargin;
      batteryContainer.setLayoutParams(batteryParams);
    }

    params = pageStatus.getLayoutParams();
    if (params.width != pageWidth) {
      params.width = pageWidth;
      pageStatus.setLayoutParams(params);
    }
    float pageTextSize = Math.max(1f, pageStatusNormalTextSize * scale);
    if (pageStatus.getTextSize() != pageTextSize) {
      pageStatus.setTextSize(TypedValue.COMPLEX_UNIT_PX, pageTextSize);
    }

    setViewHeight(footerContainer, footerHeight);
    setViewHeight(footerContent, footerHeight);
    return settingWidth + batteryWidth + batteryLeftMargin + batteryRightMargin + pageWidth;
  }

  private int scaledSize(int normalSize, float scale) {
    if (normalSize <= 0) return 0;
    return Math.max(1, (int) Math.floor(normalSize * scale));
  }

  private boolean useCompactFooterHeight() {
    return launcherView != null && launcherView.getRowNum() < 3;
  }

  private void setViewHeight(View view, int height) {
    ViewGroup.LayoutParams params = view.getLayoutParams();
    if (params.height != height) {
      params.height = height;
      view.setLayoutParams(params);
    }
  }

  // =========================================================================
  // 电池信息
  // =========================================================================

  private void handleBatteryChanged(Intent intent) {
    int rawLevel = intent.getIntExtra("level", -1);
    int scale = intent.getIntExtra("scale", -1);
    int status = intent.getIntExtra("status", -1);
    int health = intent.getIntExtra("health", -1);

    int level = (rawLevel >= 0 && scale > 0) ? (rawLevel * 100) / scale : -1;
    batteryProgress.setProgress(level);
    batteryStatusRequestedVisible = true;

    if (BatteryManager.BATTERY_HEALTH_OVERHEAT == health) {
      batteryStatus.setText(R.string.battery_heat);
      updateTimeShow();
      return;
    }

    switch (status) {
      case BatteryManager.BATTERY_STATUS_UNKNOWN:
        batteryStatus.setText(R.string.battery_unknown);
        break;
      case BatteryManager.BATTERY_STATUS_CHARGING:
        batteryStatus.setText(R.string.battery_charging);
        break;
      case BatteryManager.BATTERY_STATUS_DISCHARGING:
      case BatteryManager.BATTERY_STATUS_NOT_CHARGING:
        if (level < 15) {
          batteryStatus.setText(R.string.battery_low);
        } else {
          batteryStatusRequestedVisible = false;
        }
        break;
      case BatteryManager.BATTERY_STATUS_FULL:
        batteryStatus.setText(R.string.battery_full);
        break;
      default:
        batteryStatus.setText(R.string.battery_wtf);
        break;
    }
    updateTimeShow();
  }

  // =========================================================================
  // 广播注册/注销
  // =========================================================================

  /** 注册生命周期不变的静态广播 */
  private void registerStaticReceivers() {
    // 应用安装/卸载广播
    IntentFilter appChangeFilter = new IntentFilter();
    appChangeFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
    appChangeFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
    appChangeFilter.addAction(Intent.ACTION_PACKAGE_REPLACED);
    appChangeFilter.addDataScheme("package");
    registerCompatReceiver(appChangeReceiver, appChangeFilter);
  }

  /** 注册跟随 onResume/onPause 的动态广播 */
  private void registerDynamicReceivers() {
    if (!batteryRegistered) {
      registerCompatReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
      batteryRegistered = true;
    }
    updateClockRefreshMode();
    if (!usbRegistered) {
      registerUsbReceiver();
    }
    if (!ftpRegistered) {
      IntentFilter ftpFilter = new IntentFilter(FTPService.ACTION_START_FTPSERVER);
      ftpFilter.addAction(FTPService.ACTION_STOP_FTPSERVER);
      registerCompatReceiver(ftpReceiver, ftpFilter);
      ftpRegistered = true;
    }
  }

  private void unregisterDynamicReceivers() {
    if (batteryRegistered) {
      unregisterReceiver(batteryReceiver);
      batteryRegistered = false;
    }
    if (timeRegistered) {
      unregisterReceiver(timeReceiver);
      timeRegistered = false;
    }
    stopClockTicker();
    if (usbRegistered) {
      unregisterReceiver(usbReceiver);
      usbRegistered = false;
    }
    if (ftpRegistered) {
      unregisterReceiver(ftpReceiver);
      ftpRegistered = false;
    }
  }

  private void registerUsbReceiver() {
    IntentFilter usbFilter = new IntentFilter();
    usbFilter.addAction(Intent.ACTION_UMS_DISCONNECTED);
    usbFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
    usbFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
    usbFilter.addAction(Intent.ACTION_MEDIA_REMOVED);
    usbFilter.addDataScheme("file");
    registerCompatReceiver(usbReceiver, usbFilter);
    usbRegistered = true;
  }

  private void registerCompatReceiver(BroadcastReceiver receiver, IntentFilter filter) {
    Utils.registerReceiverCompat(this, receiver, filter);
  }

  private void updateClockRefreshMode() {
    updateTimeShow();
    if (config != null && config.isClockShowSeconds()) {
      if (timeRegistered) {
        unregisterReceiver(timeReceiver);
        timeRegistered = false;
      }
      startClockTicker();
    } else {
      stopClockTicker();
      if (!timeRegistered) {
        registerCompatReceiver(timeReceiver, new IntentFilter(Intent.ACTION_TIME_TICK));
        timeRegistered = true;
      }
    }
  }

  private void startClockTicker() {
    if (clockTickerRunning) return;
    clockTickerRunning = true;
    clockHandler.removeCallbacks(clockTicker);
    clockHandler.postDelayed(clockTicker, getNextClockTickDelay());
  }

  private void stopClockTicker() {
    clockTickerRunning = false;
    clockHandler.removeCallbacks(clockTicker);
  }

  private long getNextClockTickDelay() {
    return 1000 - System.currentTimeMillis() % 1000;
  }

  // =========================================================================
  // 按键处理
  // =========================================================================

  private boolean handleLauncherKeyDown(int keyCode, KeyEvent event) {
    boolean hasFragment = getFragmentManager().getBackStackEntryCount() > 0;
    if (hasFragment) {
      if (keyCode == KeyEvent.KEYCODE_BACK) {
        onBackPressed();
        return true;
      }
      return false;
    }

    if (keyCode == KeyEvent.KEYCODE_BACK) {
      if (finishManageModeIfNeeded()) {
        return true;
      }
      showFirstPageAndKeepFocusState();
      return true;
    } else if (keyCode == KeyEvent.KEYCODE_PAGE_UP) {
      showLastPageAndSelectFirst();
      return true;
    } else if (keyCode == KeyEvent.KEYCODE_PAGE_DOWN) {
      showNextPageAndSelectFirst();
      return true;
    } else if (isConfirmKey(keyCode)) {
      if (focusArea == FOCUS_NONE) {
        confirmLongPressed = false;
        return true;
      }
      if (event.getRepeatCount() > 0) {
        if (!confirmLongPressed) {
          confirmLongPressed = true;
          performFocusedLongClick();
        }
        return true;
      }
      event.startTracking();
      return true;
    } else if (isDirectionKey(keyCode)) {
      moveSelectionByKey(keyCode);
      return true;
    }
    return false;
  }

  private boolean handleLauncherKeyUp(int keyCode, KeyEvent event) {
    if (getFragmentManager().getBackStackEntryCount() != 0 || !isLauncherNavigationKey(keyCode)) {
      return false;
    }
    if (isConfirmKey(keyCode)) {
      if (focusArea != FOCUS_NONE && !confirmLongPressed && !event.isCanceled()) {
        performFocusedClick();
      }
      confirmLongPressed = false;
      return true;
    }
    return true;
  }

  private boolean finishManageModeIfNeeded() {
    if (!binder.isDelete()) {
      return false;
    }
    finishManageMode();
    return true;
  }

  private void finishManageMode() {
    Set<String> hideApps = new HashSet<>(binder.getHideAppPkg());
    config.setHideApps(hideApps);
    dataCenter.setHideApps(config.getHideApps());
    binder.setDelete(false);
    findViewById(R.id.deleteFinish).setVisibility(View.GONE);
  }

  private void clearHandledLauncherKey() {
    handledLauncherKeyCode = KeyEvent.KEYCODE_UNKNOWN;
    handledLauncherKeyDownTime = 0L;
  }

  private boolean isLauncherNavigationKey(int keyCode) {
    return keyCode == KeyEvent.KEYCODE_BACK
        || keyCode == KeyEvent.KEYCODE_PAGE_UP
        || keyCode == KeyEvent.KEYCODE_PAGE_DOWN
        || isConfirmKey(keyCode)
        || isDirectionKey(keyCode);
  }

  private boolean isConfirmKey(int keyCode) {
    return keyCode == KeyEvent.KEYCODE_DPAD_CENTER
        || keyCode == KeyEvent.KEYCODE_ENTER
        || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER;
  }

  private boolean isDirectionKey(int keyCode) {
    return keyCode == KeyEvent.KEYCODE_DPAD_LEFT
        || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
        || keyCode == KeyEvent.KEYCODE_DPAD_UP
        || keyCode == KeyEvent.KEYCODE_DPAD_DOWN;
  }

  private void moveSelectionByKey(int keyCode) {
    if (focusArea == FOCUS_NONE) {
      focusGrid(0);
      return;
    }

    if (focusArea == FOCUS_CLOCK || focusArea == FOCUS_BATTERY || focusArea == FOCUS_SETTING) {
      moveFooterFocus(keyCode);
      return;
    }

    if (keyCode == KeyEvent.KEYCODE_DPAD_UP && isOnTopRow()) {
      expandNotifications();
      return;
    }

    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && isOnBottomRow()) {
      lastGridIndex = launcherView.getSelectedIndex();
      focusFooter(FOCUS_CLOCK);
      return;
    }

    if (launcherView.moveSelection(keyCode)) return;

    int selectedIndex = launcherView.getSelectedIndex();
    if (selectedIndex < 0) return;

    int colNum = launcherView.getColNum();
    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && selectedIndex % colNum == 0) {
      if (dataCenter.showLastPage()) {
        launcherView.setSelectedIndex(launcherView.getCrossPageTargetIndex(selectedIndex, false));
        focusGrid(launcherView.getSelectedIndex());
      }
    } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && selectedIndex % colNum == colNum - 1) {
      if (dataCenter.showNextPage()) {
        launcherView.setSelectedIndex(launcherView.getCrossPageTargetIndex(selectedIndex, true));
        focusGrid(launcherView.getSelectedIndex());
      }
    }
  }

  private void moveFooterFocus(int keyCode) {
    if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
      focusGrid(lastGridIndex);
    } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && focusArea == FOCUS_CLOCK) {
      focusFooter(FOCUS_BATTERY);
    } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && focusArea == FOCUS_BATTERY) {
      focusFooter(FOCUS_CLOCK);
    } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && focusArea == FOCUS_BATTERY) {
      focusFooter(FOCUS_SETTING);
    } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && focusArea == FOCUS_SETTING) {
      focusFooter(FOCUS_BATTERY);
    }
  }

  private boolean isOnTopRow() {
    int selectedIndex = launcherView.getSelectedIndex();
    return selectedIndex >= 0 && selectedIndex < launcherView.getColNum();
  }

  private boolean isOnBottomRow() {
    int selectedIndex = launcherView.getSelectedIndex();
    if (selectedIndex < 0) return false;
    int colNum = launcherView.getColNum();
    return selectedIndex + colNum >= launcherView.getDisplayedItemCount();
  }

  private void performFocusedClick() {
    if (focusArea == FOCUS_NONE) {
      return;
    }
    if (focusArea == FOCUS_GRID) {
      launcherView.performSelectedItemClick();
    } else if (focusArea == FOCUS_CLOCK) {
      textClock.performClick();
    } else if (focusArea == FOCUS_BATTERY) {
      batteryProgress.performClick();
    } else if (focusArea == FOCUS_SETTING) {
      settingIcon.performClick();
    }
  }

  private boolean performFocusedLongClick() {
    if (focusArea == FOCUS_NONE) {
      return false;
    }
    if (focusArea == FOCUS_GRID) {
      return launcherView.performSelectedItemLongClick();
    }
    return false;
  }

  private void focusGrid(int index) {
    focusArea = FOCUS_GRID;
    launcherView.setSelectedIndex(index);
    launcherView.showSelection();
    updateFooterFocus();
  }

  private void focusFooter(int area) {
    focusArea = area;
    launcherView.hideSelection();
    updateFooterFocus();
  }

  private void clearKeyboardFocus() {
    focusArea = FOCUS_NONE;
    confirmLongPressed = false;
    launcherView.hideSelection();
    updateFooterFocus();
  }

  private void updateFooterFocus() {
    if (textClock != null) {
      textClock.setSelected(focusArea == FOCUS_CLOCK);
    }
    if (batteryProgress != null) {
      batteryProgress.setSelected(focusArea == FOCUS_BATTERY);
    }
    if (settingIcon != null) {
      settingIcon.setSelected(focusArea == FOCUS_SETTING);
    }
  }

  private void openSettingsFragment() {
    getFragmentManager().beginTransaction()
        .replace(android.R.id.content, new SettingFragment())
        .addToBackStack(null)
        .commit();
  }

  private void openClockActivity() {
    startActivity(new Intent(this, ClockActivity.class));
  }

  private void openBatterySettings() {
    Intent intent = new Intent();
    intent.setComponent(new ComponentName("com.android.settings", "com.android.settings.Settings$PowerUsageSummaryActivity"));
    try {
      startActivity(intent);
    } catch (Exception e) {
      Toast.makeText(Launcher.this, "无法打开电池设置", Toast.LENGTH_SHORT).show();
      e.printStackTrace();
    }
  }

  private void expandNotifications() {
    try {
      Object service = getSystemService("statusbar");
      if (service == null) return;
      String methodName = Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1
          ? "expandNotificationsPanel"
          : "expand";
      Method method = service.getClass().getMethod(methodName);
      method.invoke(service);
    } catch (Exception ignored) {
    }
  }

  private void showNextPageAndSelectFirst() {
    if (dataCenter.showNextPage()) {
      focusGrid(0);
    }
  }

  private void showLastPageAndSelectFirst() {
    if (dataCenter.showLastPage()) {
      focusGrid(0);
    }
  }

  private void showFirstPageAndKeepFocusState() {
    boolean hadKeyboardFocus = focusArea != FOCUS_NONE;
    dataCenter.showFirstPage();
    if (hadKeyboardFocus) {
      focusGrid(0);
    } else {
      launcherView.selectFirstAvailable();
      clearKeyboardFocus();
    }
  }

  private void showNextPageAndHideSelection() {
    if (dataCenter.showNextPage()) {
      launcherView.selectFirstAvailable();
      clearKeyboardFocus();
    }
  }

  private void showLastPageAndHideSelection() {
    if (dataCenter.showLastPage()) {
      launcherView.selectFirstAvailable();
      clearKeyboardFocus();
    }
  }

  @Override
  public void onBackPressed() {
    if (getFragmentManager().getBackStackEntryCount() > 0) {
      super.onBackPressed();
      config.setFontSize(config.getFontSize());
    }
  }

  // =========================================================================
  // 锁屏
  // =========================================================================

  public void lockScreen() {
    try {
      if (policyManager.isAdminActive(new ComponentName(this, AdminReceiver.class))) {
        policyManager.lockNow();
      } else {
        requestDeviceAdmin();
      }
    } catch (Exception e) {
      showDeviceAdminDialog();
    }
  }

  private void requestDeviceAdmin() {
    Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
    intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, new ComponentName(this, AdminReceiver.class));
    intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "E-Ink Launcher 获取锁屏权限");
    startActivity(intent);
  }

  private void showDeviceAdminDialog() {
    new AlertDialog.Builder(this)
        .setTitle(R.string.launch_failed)
        .setMessage(R.string.launch_devicemanager_failed)
        .setPositiveButton(R.string.launch_devicemanager, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            try {
              Intent intent = Intent.parseUri(
                  "intent:#Intent;component=com.android.settings/.DeviceAdminSettings;end",
                  Intent.URI_INTENT_SCHEME);
              intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
              startActivity(intent);
            } catch (Exception e) {
              e.printStackTrace();
            }
          }
        })
        .setNegativeButton(R.string.dialog_cancel, null)
        .show();
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (resultCode == RESULT_OK && requestCode == REQUEST_DEVICE_ADMIN) {
      policyManager.lockNow();
    }
  }

  // =========================================================================
  // 内存清理
  // =========================================================================

  private void cleanMemory() {
    String size = null;
    try {
      Toast.makeText(this, R.string.clean_start, Toast.LENGTH_SHORT).show();
      Process shellProcess = new ProcessBuilder(
          getApplicationInfo().nativeLibraryDir + "/libfillRam.so").start();
      java.io.BufferedReader reader = new java.io.BufferedReader(
          new java.io.InputStreamReader(shellProcess.getInputStream()));
      String line = "";
      while (line != null) {
        size = line;
        line = reader.readLine();
      }
    } catch (Exception e) {
      Toast.makeText(this, getString(R.string.clean_error, e.getMessage()), Toast.LENGTH_LONG).show();
      Log.e("MemoryClean", "Error: " + e.getMessage());
    }
    Toast.makeText(this, getString(R.string.clean_done, size), Toast.LENGTH_SHORT).show();
  }

  // =========================================================================
  // 状态栏/系统应用判断/通知栏
  // =========================================================================

  public void applyStatusBarVisibility() {
    int flags = WindowManager.LayoutParams.FLAG_FULLSCREEN;
    if (config.isShowStatusBar()) {
      getWindow().clearFlags(flags);
    } else {
      getWindow().setFlags(flags, flags);
    }
  }

  public boolean isUserApp(PackageInfo pInfo) {
    return (pInfo.applicationInfo.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) == 0;
  }

  private void checkLaunchHomeNotification() {
    if (!TextUtils.equals(Build.DEVICE, "virgo-perf1")) return;
    Intent service = new Intent(this, HomeEntranceService.class);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      startForegroundService(service);
    } else {
      startService(service);
    }
  }
}
