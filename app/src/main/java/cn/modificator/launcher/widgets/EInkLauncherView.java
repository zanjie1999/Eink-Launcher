package cn.modificator.launcher.widgets;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import cn.modificator.launcher.R;

import static android.view.View.MeasureSpec.EXACTLY;
import static android.view.View.MeasureSpec.getSize;
import static android.view.View.MeasureSpec.makeMeasureSpec;

/**
 * E-Ink 桌面网格布局 ViewGroup。
 * <p>
 * 纯布局容器，职责仅限于：
 * <ul>
 *   <li>网格布局（行列排列、尺寸测量）</li>
 *   <li>手势检测（滑动翻页）</li>
 *   <li>向 {@link LauncherAdapter} 请求 ViewHolder 的创建与绑定</li>
 * </ul>
 * 所有数据管理和交互逻辑由 {@link LauncherAdapter} 处理。
 */
public class EInkLauncherView extends ViewGroup {

  // =========================================================================
  // 翻页监听
  // =========================================================================

  /** 翻页手势回调 */
  public interface OnPageChangeListener {
    void onPageNext();
    void onPagePrev();
  }

  // =========================================================================
  // 字段
  // =========================================================================

  // 网格参数
  private int rowNum = 5;
  private int colNum = 5;
  private boolean bottomAlignIcons;
  private boolean hideDivider = false;

  // 外部依赖
  private LauncherAdapter adapter;
  private OnPageChangeListener pageChangeListener;
  private int selectedIndex = -1;
  private boolean selectionVisible = false;

  // 滑动检测
  private float touchDownX;
  private float touchDownY;
  private float swipeThreshold;

  // =========================================================================
  // 构造器
  // =========================================================================

  public EInkLauncherView(Context context) {
    this(context, null);
  }

  public EInkLauncherView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public EInkLauncherView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  // =========================================================================
  // Adapter & Listener
  // =========================================================================

  /** 设置数据适配器，触发网格重建 */
  public void setAdapter(LauncherAdapter adapter) {
    if (this.adapter != null) {
      this.adapter.detachView();
    }
    this.adapter = adapter;
    if (adapter != null) {
      adapter.attachView(this);
    }
    resetGrid();
  }

  public LauncherAdapter getAdapter() {
    return adapter;
  }

  /** 设置翻页手势监听 */
  public void setOnPageChangeListener(OnPageChangeListener listener) {
    this.pageChangeListener = listener;
  }

  // =========================================================================
  // 网格配置
  // =========================================================================

  /**
   * 一次性配置所有网格参数，仅触发一次 {@code resetGrid()}。
   */
  public void configure(int colNum, int rowNum, boolean hideDivider) {
    this.colNum = colNum;
    this.rowNum = rowNum;
    this.hideDivider = hideDivider;
    resetGrid();
  }

  public void setGridSize(int colNum, int rowNum) {
    this.colNum = colNum;
    this.rowNum = rowNum;
    resetGrid();
  }

  public void setColNum(int colNum) {
    this.colNum = colNum;
    resetGrid();
  }

  public void setRowNum(int rowNum) {
    this.rowNum = rowNum;
    resetGrid();
  }

  public void setBottomAlignIcons(boolean align) {
    if (bottomAlignIcons == align) return;
    bottomAlignIcons = align;
    resetGrid();
  }

  public boolean isBottomAlignIcons() {
    return bottomAlignIcons;
  }

  public void setHideDivider(boolean hideDivider) {
    this.hideDivider = hideDivider;
    resetGrid();
  }

  public int getColNum() {
    return colNum;
  }

  public int getRowNum() {
    return rowNum;
  }

  // =========================================================================
  // onLayout / onMeasure
  // =========================================================================

  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    int w = getAdjustedWidth();
    int h = getAdjustedHeight();
    if (w <= 0 || h <= 0) return;

    swipeThreshold = Math.min(w, h) / 6f;
    int cellW = w / colNum;
    int cellH = h / rowNum;
    int startLeft = getPaddingLeft();
    int startTop = getPaddingTop();
    int itemCount = adapter != null ? adapter.getItemCount() : 0;
    int leadingEmpty = bottomAlignIcons ? Math.max(0, rowNum * colNum - itemCount) : 0;

    for (int index = 0; index < getChildCount(); index++) {
      int visualIndex = index + leadingEmpty;
      int row = visualIndex / colNum;
      int col = visualIndex % colNum;
      getChildAt(index).layout(
          startLeft + col * cellW, startTop + row * cellH,
          startLeft + (col + 1) * cellW, startTop + (row + 1) * cellH);
    }
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    int measuredWidth = getSize(widthMeasureSpec);
    int measuredHeight = getSize(heightMeasureSpec);
    setMeasuredDimension(measuredWidth, measuredHeight);

    int w = measuredWidth - getPaddingLeft() - getPaddingRight();
    int h = measuredHeight - getPaddingTop() - getPaddingBottom();
    if (w <= 0 || h <= 0) return;

    int cellWSpec = makeMeasureSpec(w / colNum, EXACTLY);
    int cellHSpec = makeMeasureSpec(h / rowNum, EXACTLY);
    for (int i = 0; i < getChildCount(); i++) {
      getChildAt(i).measure(cellWSpec, cellHSpec);
    }
  }

  private int getAdjustedWidth() {
    return getWidth() - getPaddingLeft() - getPaddingRight();
  }

  private int getAdjustedHeight() {
    return getHeight() - getPaddingTop() - getPaddingBottom();
  }

  // =========================================================================
  // 网格构建
  // =========================================================================

  private void resetGrid() {
    if (adapter == null) return;
    int targetCount = rowNum * colNum;

    if (adapter.getHolderCount() == targetCount && getChildCount() == targetCount) {
      // 数量不变，仅刷新背景
      int leadingEmpty = bottomAlignIcons
          ? Math.max(0, targetCount - adapter.getItemCount()) : 0;
      for (int i = 0; i < targetCount; i++) {
        getChildAt(i).setBackgroundResource(getItemBackground(i + leadingEmpty));
      }
      rebind();
      return;
    }

    // 数量变化，完整重建
    removeAllViews();
    adapter.clearHolders();

    for (int i = 0; i < targetCount; i++) {
      LauncherAdapter.ItemViewHolder holder = adapter.createViewHolder(this);
      int leadingEmpty = bottomAlignIcons
          ? Math.max(0, targetCount - adapter.getItemCount()) : 0;
      holder.itemView.setBackgroundResource(getItemBackground(i + leadingEmpty));
      addView(holder.itemView);
    }
    rebind();
  }

  /** 请求 adapter 重新绑定所有数据 */
  void rebind() {
    if (adapter != null) {
      adapter.bindAll();
      normalizeSelection();
      adapter.updateSelection(selectedIndex, selectionVisible);
      requestLayout();
      invalidate();
    }
  }

  public int getSelectedIndex() {
    return selectedIndex;
  }

  public boolean isSelectionVisible() {
    return selectionVisible;
  }

  public void setSelectedIndex(int index) {
    selectedIndex = index;
    normalizeSelection();
    updateSelection();
  }

  public void selectFirstAvailable() {
    setSelectedIndex(0);
  }

  public void showSelection() {
    selectionVisible = true;
    normalizeSelection();
    updateSelection();
  }

  public void hideSelection() {
    selectionVisible = false;
    updateSelection();
  }

  public int getDisplayedItemCount() {
    return adapter != null ? adapter.getItemCount() : 0;
  }

  public boolean performSelectedItemClick() {
    if (adapter == null) return false;
    normalizeSelection();
    View selectedView = adapter.getItemView(selectedIndex);
    return selectedView != null && selectedView.performClick();
  }

  public boolean performSelectedItemLongClick() {
    if (adapter == null) return false;
    normalizeSelection();
    View selectedView = adapter.getItemView(selectedIndex);
    return selectedView != null && selectedView.performLongClick();
  }

  public boolean moveSelection(int keyCode) {
    if (adapter == null || adapter.getItemCount() == 0) {
      setSelectedIndex(-1);
      return false;
    }

    normalizeSelection();
    if (selectedIndex < 0) {
      setSelectedIndex(0);
      return true;
    }

    int target = selectedIndex;
    switch (keyCode) {
      case KeyEvent.KEYCODE_DPAD_LEFT:
        if (selectedIndex % colNum == 0) return false;
        target = selectedIndex - 1;
        break;
      case KeyEvent.KEYCODE_DPAD_RIGHT:
        if (selectedIndex % colNum == colNum - 1 || selectedIndex + 1 >= adapter.getItemCount()) {
          return false;
        }
        target = selectedIndex + 1;
        break;
      case KeyEvent.KEYCODE_DPAD_UP:
        target = selectedIndex - colNum;
        break;
      case KeyEvent.KEYCODE_DPAD_DOWN:
        target = selectedIndex + colNum;
        break;
      default:
        return false;
    }

    if (target < 0 || target >= adapter.getItemCount()) {
      return false;
    }
    setSelectedIndex(target);
    return true;
  }

  public int getCrossPageTargetIndex(int sourceIndex, boolean nextPage) {
    int itemCount = getDisplayedItemCount();
    if (itemCount <= 0) return -1;

    int row = Math.max(sourceIndex, 0) / colNum;
    int target = row * colNum + (nextPage ? 0 : colNum - 1);
    return Math.min(target, itemCount - 1);
  }

  private void normalizeSelection() {
    if (adapter == null || adapter.getItemCount() == 0) {
      selectedIndex = -1;
      return;
    }
    if (selectedIndex < 0) {
      selectedIndex = 0;
    } else if (selectedIndex >= adapter.getItemCount()) {
      selectedIndex = adapter.getItemCount() - 1;
    }
  }

  private void updateSelection() {
    if (adapter != null) {
      adapter.updateSelection(selectedIndex, selectionVisible);
    }
  }

  private int getItemBackground(int index) {
    int total = rowNum * colNum;
    if (hideDivider || index == total - 1) {
      return R.drawable.app_item_final;
    } else if (index % colNum == colNum - 1) {
      return R.drawable.app_item_right;
    } else if (index >= (rowNum - 1) * colNum) {
      return R.drawable.app_item_bottom;
    } else {
      return R.drawable.app_item_normal;
    }
  }

  // =========================================================================
  // 手势检测
  // =========================================================================

  @Override
  public boolean dispatchTouchEvent(MotionEvent event) {
    switch (event.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        hideSelection();
        touchDownX = event.getX();
        touchDownY = event.getY();
        break;
      case MotionEvent.ACTION_UP:
        int dir = detectSwipe(event.getX(), event.getY());
        if (dir != 0 && pageChangeListener != null) {
          if (dir > 0) pageChangeListener.onPagePrev();
          else pageChangeListener.onPageNext();
          return true;
        }
        break;
    }
    return super.dispatchTouchEvent(event);
  }

  /**
   * 检测滑动方向。
   *
   * @return 1 = 上一页, -1 = 下一页, 0 = 无有效滑动
   */
  private int detectSwipe(float upX, float upY) {
    float dx = upX - touchDownX;
    float dy = upY - touchDownY;
    return detectSwipeDelta(dx, dy, swipeThreshold);
  }

  private static int detectSwipeDelta(float dx, float dy, float threshold) {
    if (threshold <= 0) return 0;

    float primaryDelta = Math.abs(dx) >= Math.abs(dy) ? dx : dy;
    if (primaryDelta > threshold) return 1;
    if (primaryDelta < -threshold) return -1;
    return 0;
  }
}
