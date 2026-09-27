package cn.modificator.launcher.widgets;

import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.util.ArrayList;
import java.util.List;
import java.util.Observer;

import cn.modificator.launcher.R;
import cn.modificator.launcher.model.ObservableFloat;

/**
 * {@link EInkLauncherView} 的数据适配器，管理 ViewHolder 池、数据列表和显示参数。
 * <p>
 * 绑定/交互逻辑委派给 {@link AppItemBinder}，自身只关心：
 * <ul>
 *   <li>ViewHolder 的创建与回收</li>
 *   <li>数据列表（当前页应用）的增删</li>
 *   <li>全局显示参数（字体大小、应用名行数）</li>
 * </ul>
 */
public class LauncherAdapter {

  // =========================================================================
  // ViewHolder
  // =========================================================================

  /** 缓存 launcher_item 布局中的所有子 View 引用。 */
  public static class ItemViewHolder {
    public final View itemView;
    public final ImageView appImage;
    public final ObserverFontTextView appName;
    public final View menuContainer;
    public final View menuDelete;
    public final View menuHide;
    public final View focusFrame;

    ItemViewHolder(View itemView) {
      this.itemView = itemView;
      appImage = itemView.findViewById(R.id.appImage);
      appName = (ObserverFontTextView) itemView.findViewById(R.id.appName);
      menuContainer = ((ViewGroup) itemView).getChildAt(1);
      menuDelete = itemView.findViewById(R.id.menu_delete);
      menuHide = itemView.findViewById(R.id.menu_hide);
      focusFrame = itemView.findViewById(R.id.focusFrame);
    }
  }

  // =========================================================================
  // 字段
  // =========================================================================

  private final ObservableFloat fontSizeObservable = new ObservableFloat();
  private final List<android.content.pm.ResolveInfo> dataList = new ArrayList<>();
  private final List<ItemViewHolder> holders = new ArrayList<>();

  private AppItemBinder binder;
  private EInkLauncherView attachedView;

  private float fontSize = 14;
  private int appNameLines = Integer.MAX_VALUE;

  // =========================================================================
  // View 绑定（包级可见，由 EInkLauncherView 调用）
  // =========================================================================

  void attachView(EInkLauncherView view) {
    this.attachedView = view;
  }

  void detachView() {
    this.attachedView = null;
  }

  // =========================================================================
  // Binder
  // =========================================================================

  /** 设置负责数据绑定和交互的 Binder */
  public void setBinder(AppItemBinder binder) {
    this.binder = binder;
  }

  public AppItemBinder getBinder() {
    return binder;
  }

  // =========================================================================
  // 显示参数
  // =========================================================================

  public void setFontSize(float fontSize) {
    this.fontSize = fontSize;
    fontSizeObservable.set(fontSize);
  }

  public float getFontSize() {
    return fontSize;
  }

  public void setAppNameLines(int lines) {
    this.appNameLines = lines;
    for (ItemViewHolder holder : holders) {
      holder.appName.setMinLines(lines == 2 ? lines : 0);
      holder.appName.setMaxLines(lines);
    }
  }

  // =========================================================================
  // 数据
  // =========================================================================

  /** 设置当前页要显示的应用列表，自动触发重新绑定 */
  public void setAppList(List<android.content.pm.ResolveInfo> appList) {
    dataList.clear();
    dataList.addAll(appList);
    if (attachedView != null) {
      attachedView.rebind();
    }
  }

  /** 仅刷新显示（自定义图标变更后调用） */
  public void refreshDisplay() {
    if (attachedView != null) {
      attachedView.rebind();
    }
  }

  public int getItemCount() {
    return dataList.size();
  }

  List<android.content.pm.ResolveInfo> getData() {
    return dataList;
  }

  // =========================================================================
  // ViewHolder 管理（包级可见，由 EInkLauncherView 调用）
  // =========================================================================

  List<ItemViewHolder> getHolders() {
    return holders;
  }

  int getHolderCount() {
    return holders.size();
  }

  /** 创建一个新的 ViewHolder，注册字体观察者，初始化文字参数 */
  ItemViewHolder createViewHolder(ViewGroup parent) {
    View itemView = LayoutInflater.from(parent.getContext())
        .inflate(R.layout.launcher_item, parent, false);
    ItemViewHolder holder = new ItemViewHolder(itemView);
    holders.add(holder);

    fontSizeObservable.addObserver((Observer) holder.appName);
    holder.appName.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize);
    holder.appName.setMinLines(appNameLines == 2 ? appNameLines : 0);
    holder.appName.setMaxLines(appNameLines);
    holder.appName.setEllipsize(TextUtils.TruncateAt.END);

    return holder;
  }

  /** 清除所有 ViewHolder 并取消字体观察 */
  void clearHolders() {
    fontSizeObservable.deleteObservers();
    holders.clear();
  }

  // =========================================================================
  // 绑定调度（包级可见，由 EInkLauncherView.rebind 调用）
  // =========================================================================

  /** 委派给 Binder 执行所有 ViewHolder 的数据绑定与状态更新 */
  void bindAll() {
    if (binder != null) {
      binder.bindAll(holders, dataList);
      binder.updateDeleteState(holders, dataList);
    }
  }

  /** 更新按键导航选中框。 */
  void updateSelection(int selectedIndex, boolean visible) {
    for (int i = 0; i < holders.size(); i++) {
      ItemViewHolder holder = holders.get(i);
      int leadingEmpty = attachedView != null && attachedView.isBottomAlignIcons()
          ? Math.max(0, holders.size() - dataList.size()) : 0;
      holder.focusFrame.setVisibility(visible && i == selectedIndex + leadingEmpty
          && selectedIndex >= 0 && selectedIndex < dataList.size()
          ? View.VISIBLE : View.GONE);
    }
  }

  View getItemView(int index) {
    if (index < 0 || index >= holders.size() || index >= dataList.size()) {
      return null;
    }
    return holders.get(index).itemView;
  }

  /** 仅更新管理模式 UI 状态 */
  void updateDeleteState() {
    if (binder != null) {
      binder.updateDeleteState(holders, dataList);
    }
  }
}
