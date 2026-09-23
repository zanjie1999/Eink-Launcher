package cn.modificator.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/**
 * 应用配置管理类。
 * 统一管理 SharedPreferences 的读写，缓存常用配置值。
 * 偏好键（KEY_*）集中定义于此类。
 */
public class Config {

  // ---- 偏好键常量 ----
  public static final String KEY_COL_NUM = "colNumKey";
  public static final String KEY_ROW_NUM = "rowNumKey";
  public static final String KEY_APP_NAME_LINES = "appNameShowLines";
  public static final String KEY_HIDE_APPS = "hideAppsKey";
  public static final String KEY_FONT_SIZE = "launcherFontSize";
  public static final String KEY_HIDE_DIVIDER = "launcherHideDivider";
  public static final String KEY_SHOW_STATUS_BAR = "launcherShowStatusBar";
  public static final String KEY_SHOW_CUSTOM_ICON = "launcherShowCustomIcon";
  public static final String KEY_SORT_MODE = "launcherSortMode";
  public static final String KEY_THEME_MODE = "themeMode";
  public static final String KEY_CLOCK_SHOW_SECONDS = "launcherClockShowSeconds";
  public static final String KEY_SCREEN_ORIENTATION = "launcherScreenOrientation";
  public static final String KEY_START_AT_BOOT = "launcherStartAtBoot";

  // ---- 默认值 ----
  private static final int DEFAULT_COL_NUM = 5;
  private static final int DEFAULT_ROW_NUM = 5;
  private static final float DEFAULT_FONT_SIZE = 14f;
  private static final int DEFAULT_APP_NAME_LINES = Integer.MAX_VALUE;
  private static final boolean DEFAULT_HIDE_DIVIDER = true;
  private static final boolean DEFAULT_SHOW_STATUS_BAR = false;
  private static final boolean DEFAULT_SHOW_CUSTOM_ICON = false;
  private static final int DEFAULT_SORT_MODE = 0;
  private static final int DEFAULT_THEME_MODE = 3;
  private static final boolean DEFAULT_CLOCK_SHOW_SECONDS = false;
  private static final int DEFAULT_SCREEN_ORIENTATION = 0;
  private static final boolean DEFAULT_START_AT_BOOT = false;

  private static final String PREFS_FILE = "launcherPropertyFile";

  private final SharedPreferences prefs;

  // ---- 缓存字段 ----
  private int colNum = -1;
  private int rowNum = -1;
  private float fontSize = -1;
  private int appNameLines = -1;
  private boolean hideDivider;
  private boolean showStatusBar;
  private boolean showCustomIcon;
  private boolean clockShowSeconds;
  private boolean startAtBoot;
  private int sortMode = -1;
  private int themeMode = -1;
  private int screenOrientation = -1;
  private final Set<String> hideApps = new HashSet<>();
  private boolean hideAppsLoaded = false;

  public Config(Context context) {
    this.prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
    // 预加载布尔配置
    this.hideDivider = prefs.getBoolean(KEY_HIDE_DIVIDER, DEFAULT_HIDE_DIVIDER);
    this.showStatusBar = prefs.getBoolean(KEY_SHOW_STATUS_BAR, DEFAULT_SHOW_STATUS_BAR);
    this.showCustomIcon = prefs.getBoolean(KEY_SHOW_CUSTOM_ICON, DEFAULT_SHOW_CUSTOM_ICON);
    this.clockShowSeconds = prefs.getBoolean(KEY_CLOCK_SHOW_SECONDS, DEFAULT_CLOCK_SHOW_SECONDS);
    this.startAtBoot = prefs.getBoolean(KEY_START_AT_BOOT, DEFAULT_START_AT_BOOT);
    this.appNameLines = prefs.getInt(KEY_APP_NAME_LINES, DEFAULT_APP_NAME_LINES);
  }

  // ---- 列数 ----

  public int getColNum() {
    if (colNum == -1) {
      colNum = prefs.getInt(KEY_COL_NUM, DEFAULT_COL_NUM);
    }
    return colNum;
  }

  public void setColNum(int colNum) {
    if (this.colNum == colNum) return;
    this.colNum = colNum;
    prefs.edit().putInt(KEY_COL_NUM, colNum).apply();
  }

  // ---- 行数 ----

  public int getRowNum() {
    if (rowNum == -1) {
      rowNum = prefs.getInt(KEY_ROW_NUM, DEFAULT_ROW_NUM);
    }
    return rowNum;
  }

  public void setRowNum(int rowNum) {
    if (this.rowNum == rowNum) return;
    this.rowNum = rowNum;
    prefs.edit().putInt(KEY_ROW_NUM, rowNum).apply();
  }

  // ---- 主题模式 ----

  public int getThemeMode() {
    if (themeMode == -1) {
      themeMode = prefs.getInt(KEY_THEME_MODE, DEFAULT_THEME_MODE);
    }
    return themeMode;
  }

  public void setThemeMode(int themeMode) {
    if (this.themeMode == themeMode) return;
    this.themeMode = themeMode;
    prefs.edit().putInt(KEY_THEME_MODE, themeMode).apply();
  }

  // ---- 屏幕方向 ----

  public int getScreenOrientation() {
    if (screenOrientation == -1) {
      screenOrientation = prefs.getInt(KEY_SCREEN_ORIENTATION, DEFAULT_SCREEN_ORIENTATION);
    }
    return screenOrientation;
  }

  public void setScreenOrientation(int screenOrientation) {
    if (this.screenOrientation == screenOrientation) return;
    this.screenOrientation = screenOrientation;
    prefs.edit().putInt(KEY_SCREEN_ORIENTATION, screenOrientation).apply();
  }

  // ---- 隐藏应用 ----

  public void addHideApp(String packageName) {
    ensureHideAppsLoaded();
    hideApps.add(packageName);
    prefs.edit().putStringSet(KEY_HIDE_APPS, new HashSet<>(hideApps)).commit();
  }

  public void removeHideApp(String packageName) {
    ensureHideAppsLoaded();
    hideApps.remove(packageName);
    prefs.edit().putStringSet(KEY_HIDE_APPS, new HashSet<>(hideApps)).commit();
  }

  public void setHideApps(Set<String> hideApps) {
    this.hideApps.clear();
    this.hideApps.addAll(hideApps);
    this.hideAppsLoaded = true;
    prefs.edit().putStringSet(KEY_HIDE_APPS, new HashSet<>(this.hideApps)).commit();
  }

  public Set<String> getHideApps() {
    ensureHideAppsLoaded();
    return new HashSet<>(hideApps);
  }

  private void ensureHideAppsLoaded() {
    if (!hideAppsLoaded) {
      hideApps.addAll(prefs.getStringSet(KEY_HIDE_APPS, new HashSet<String>()));
      hideAppsLoaded = true;
    }
  }

  // ---- 字体大小 ----

  public float getFontSize() {
    if (fontSize < 0) {
      fontSize = prefs.getFloat(KEY_FONT_SIZE, DEFAULT_FONT_SIZE);
    }
    return fontSize;
  }

  public void setFontSize(float fontSize) {
    this.fontSize = fontSize;
    prefs.edit().putFloat(KEY_FONT_SIZE, fontSize).apply();
  }

  // ---- 分隔线 ----

  public boolean isHideDivider() {
    return hideDivider;
  }

  public void setHideDivider(boolean hide) {
    this.hideDivider = hide;
    prefs.edit().putBoolean(KEY_HIDE_DIVIDER, hide).apply();
  }

  // ---- 状态栏 ----

  public boolean isShowStatusBar() {
    return showStatusBar;
  }

  public void setShowStatusBar(boolean show) {
    this.showStatusBar = show;
    prefs.edit().putBoolean(KEY_SHOW_STATUS_BAR, show).apply();
  }

  // ---- 自定义图标 ----

  public boolean isShowCustomIcon() {
    return showCustomIcon;
  }

  public void setShowCustomIcon(boolean show) {
    this.showCustomIcon = show;
    prefs.edit().putBoolean(KEY_SHOW_CUSTOM_ICON, show).apply();
  }

  // ---- 时钟显示秒 ----

  public boolean isClockShowSeconds() {
    return clockShowSeconds;
  }

  public void setClockShowSeconds(boolean show) {
    this.clockShowSeconds = show;
    prefs.edit().putBoolean(KEY_CLOCK_SHOW_SECONDS, show).apply();
  }

  // ---- 开机启动 ----

  public boolean isStartAtBoot() {
    return startAtBoot;
  }

  public void setStartAtBoot(boolean startAtBoot) {
    this.startAtBoot = startAtBoot;
    prefs.edit().putBoolean(KEY_START_AT_BOOT, startAtBoot).apply();
  }

  // ---- 应用名行数 ----

  public int getAppNameLines() {
    return appNameLines;
  }

  public void setAppNameLines(int lines) {
    this.appNameLines = lines;
    prefs.edit().putInt(KEY_APP_NAME_LINES, lines).apply();
  }

  // ---- 排序方式 ----

  public int getSortMode() {
    if (sortMode == -1) {
      sortMode = prefs.getInt(KEY_SORT_MODE, DEFAULT_SORT_MODE);
    }
    return sortMode;
  }

  public void setSortMode(int mode) {
    if (this.sortMode == mode) return;
    this.sortMode = mode;
    prefs.edit().putInt(KEY_SORT_MODE, mode).apply();
  }
}
