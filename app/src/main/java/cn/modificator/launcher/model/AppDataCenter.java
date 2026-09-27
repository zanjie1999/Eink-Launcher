package cn.modificator.launcher.model;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import cn.modificator.launcher.R;
import cn.modificator.launcher.widgets.AppItemBinder;
import cn.modificator.launcher.widgets.LauncherAdapter;

/**
 * 应用数据管理中心，负责加载应用列表和分页逻辑。
 */
public class AppDataCenter {

  /** 虚拟包名：Wifi 控制入口 */
  public static final String WIFI_PACKAGE_NAME = "E-ink_Launcher.WiFi";
  /** 虚拟包名：一键锁屏入口 */
  public static final String LOCK_PACKAGE_NAME = "E-ink_Launcher.Lock";
  /** 虚拟包名：后台清理入口 */
  public static final String CLEAR_PACKAGE_NAME = "E-ink_Launcher.Clear";

  private final Context mContext;
  private final List<ResolveInfo> mApps = new ArrayList<>();
  private final Handler mainHandler = new Handler(Looper.getMainLooper());
  private final ExecutorService appLoadExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {
    @Override
    public Thread newThread(Runnable r) {
      Thread thread = new Thread(r, "app-list-loader");
      thread.setPriority(Thread.NORM_PRIORITY - 1);
      return thread;
    }
  });
  private final AtomicInteger loadGeneration = new AtomicInteger();
  private int pageIndex = 0;
  private int pageCount = 0;
  private int colNum = 5;
  private int rowNum = 5;
  private boolean largeClockEnabled;
  private LauncherAdapter adapter;
  private AppItemBinder binder;
  private TextView pageStatus;
  private final Set<String> hideApps = new HashSet<>();
  private int sortMode = AppSortComparator.SORT_NAME_ASC;
  private volatile boolean shutdown;

  public AppDataCenter(Context context) {
    this.mContext = context.getApplicationContext();
  }

  // =========================================================================
  // Adapter / Binder 绑定
  // =========================================================================

  public void setAdapter(LauncherAdapter adapter) {
    this.adapter = adapter;
    this.binder = adapter.getBinder();
    setPageShow();
  }

  public void setPageStatus(TextView pageStatus) {
    this.pageStatus = pageStatus;
    pageStatus.setText((pageIndex + 1) + "/" + (pageCount + 1));
  }

  // =========================================================================
  // 隐藏应用管理
  // =========================================================================

  public void setHideApps(Set<String> hideApps) {
    this.hideApps.clear();
    this.hideApps.addAll(hideApps);
    if (binder != null) {
      binder.setHideAppPkg(this.hideApps);
    }
    refreshAppList(false);
  }

  public Set<String> getHideApps() {
    return hideApps;
  }

  // =========================================================================
  // 列数/行数
  // =========================================================================

  public void setColRowNum(int colNum, int rowNum) {
    this.colNum = colNum;
    this.rowNum = rowNum;
    updatePageCount();
    setPageShow();
  }
  public void setColNum(int colNum) {
    this.colNum = colNum;
    updatePageCount();
    setPageShow();
  }

  public void setRowNum(int rowNum) {
    this.rowNum = rowNum;
    updatePageCount();
    setPageShow();
  }

  /** 批量设置行列数，只触发一次分页更新 */
  public void setLargeClockEnabled(boolean enabled) {
    this.largeClockEnabled = enabled;
    updatePageCount();
    setPageShow();
  }

  public int getPageIndex() { return pageIndex; }

  public void setGridSize(int colNum, int rowNum) {
    this.colNum = colNum;
    this.rowNum = rowNum;
    updatePageCount();
    setPageShow();
  }

  // =========================================================================
  // 排序
  // =========================================================================

  public void setSortMode(int sortMode) {
    this.sortMode = sortMode;
  }

  public int getSortMode() {
    return sortMode;
  }

  // =========================================================================
  // 翻页
  // =========================================================================

  public boolean showNextPage() {
    if (pageIndex >= pageCount) return false;
    pageIndex++;
    setPageShow();
    return true;
  }

  public boolean showLastPage() {
    if (pageIndex <= 0) return false;
    pageIndex--;
    setPageShow();
    return true;
  }

  public boolean showFirstPage() {
    if (pageIndex == 0) return false;
    pageIndex = 0;
    setPageShow();
    return true;
  }

  // =========================================================================
  // 刷新
  // =========================================================================

  public void refreshAppList() {
    refreshAppList(false);
  }

  public void refreshAppList(boolean showAll) {
    if (shutdown) return;
    final int requestGeneration = loadGeneration.incrementAndGet();
    final int requestSortMode = sortMode;
    final Set<String> requestHideApps = new HashSet<>();

    if (binder != null) {
      if (!showAll) {
        hideApps.clear();
        hideApps.addAll(binder.getHideAppPkg());
      } else {
        binder.setHideAppPkg(hideApps);
      }
    }
    requestHideApps.addAll(hideApps);

    appLoadExecutor.execute(new Runnable() {
      @Override
      public void run() {
        final List<ResolveInfo> loadedApps = loadAppsSnapshot(showAll, requestHideApps, requestSortMode);
        mainHandler.post(new Runnable() {
          @Override
          public void run() {
            if (shutdown || requestGeneration != loadGeneration.get()) return;
            mApps.clear();
            mApps.addAll(loadedApps);
            updatePageCount();
            setPageShow();
          }
        });
      }
    });
  }

  // =========================================================================
  // 内部加载
  // =========================================================================

  private List<ResolveInfo> loadAppsSnapshot(boolean showAll, Set<String> hiddenPackages,
                                            int requestSortMode) {
    Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
    mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);

    PackageManager packageManager = mContext.getPackageManager();
    List<ResolveInfo> apps = new ArrayList<>();
    for (ResolveInfo resolveInfo : packageManager.queryIntentActivities(mainIntent, 0)) {
      if (resolveInfo.activityInfo == null) continue;
      String packageName = resolveInfo.activityInfo.packageName;
      if (!showAll && "cn.modificator.launcher.Launcher".equals(resolveInfo.activityInfo.name)) {
        continue;
      }
      if (showAll || !hiddenPackages.contains(packageName)) {
        apps.add(resolveInfo);
      }
    }

    if (showAll || !hiddenPackages.contains(LOCK_PACKAGE_NAME)) {
      apps.add(createPowerIcon());
    }
    if (showAll || !hiddenPackages.contains(WIFI_PACKAGE_NAME)) {
      apps.add(createWifiIcon());
    }
    if (showAll || !hiddenPackages.contains(CLEAR_PACKAGE_NAME)) {
      apps.add(createClearIcon());
    }
    sortApps(apps, packageManager, requestSortMode);
    return apps;
  }

  private void setPageShow() {
    if (adapter == null || pageStatus == null) return;
    int itemCount = colNum * rowNum;
    if (itemCount <= 0) return;
    int firstPageCount = getFirstPageItemCount();
    int pageStart = pageIndex == 0 ? 0 : firstPageCount + (pageIndex - 1) * itemCount;
    int pageCapacity = pageIndex == 0 ? firstPageCount : itemCount;
    int pageEnd = Math.min(pageStart + pageCapacity, mApps.size());
    adapter.setAppList(mApps.subList(pageStart, pageEnd));
    pageStatus.setText((pageIndex + 1) + "/" + (pageCount + 1));
  }

  private void updatePageCount() {
    int itemCount = colNum * rowNum;
    if (itemCount <= 0) {
      pageCount = 0;
      pageIndex = 0;
      return;
    }
    int firstPageCount = getFirstPageItemCount();
    int remaining = Math.max(0, mApps.size() - firstPageCount);
    pageCount = remaining == 0 ? 0 : 1 + (remaining - 1) / itemCount;
    pageIndex = Math.min(pageIndex, pageCount);
  }

  private int getFirstPageItemCount() {
    if (!largeClockEnabled) return colNum * rowNum;
    int firstPageRows = Math.max(1, rowNum - 2);
    return Math.min(colNum * firstPageRows, colNum * rowNum);
  }

  private void sortApps(List<ResolveInfo> apps, PackageManager packageManager, int requestSortMode) {
    Collections.sort(apps, new AppSortComparator(mContext, packageManager, requestSortMode, apps));
  }

  public void shutdown() {
    shutdown = true;
    loadGeneration.incrementAndGet();
    mainHandler.removeCallbacksAndMessages(null);
    appLoadExecutor.shutdownNow();
  }

  // =========================================================================
  // 虚拟图标创建
  // =========================================================================

  private ResolveInfo createWifiIcon() {
    ResolveInfo resolveInfo = new ResolveInfo();
    resolveInfo.icon = R.drawable.wifi_on;
    resolveInfo.activityInfo = new ActivityInfo();
    resolveInfo.activityInfo.packageName = WIFI_PACKAGE_NAME;
    return resolveInfo;
  }

  private ResolveInfo createPowerIcon() {
    ResolveInfo resolveInfo = new ResolveInfo();
    resolveInfo.icon = R.drawable.ic_onekeylock;
    resolveInfo.activityInfo = new ActivityInfo();
    resolveInfo.activityInfo.packageName = LOCK_PACKAGE_NAME;
    return resolveInfo;
  }

  private ResolveInfo createClearIcon(){
    ResolveInfo resolveInfo = new ResolveInfo();
    resolveInfo.icon = R.drawable.ic_onekeyclear;
    resolveInfo.activityInfo = new ActivityInfo();
    resolveInfo.activityInfo.packageName = CLEAR_PACKAGE_NAME;
    return resolveInfo;
  }
}
