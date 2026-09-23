package cn.modificator.launcher.model;

import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Environment;
import android.util.LruCache;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 应用图标、标签的内存缓存，以及自定义图标替换映射管理。
 * <ul>
 *   <li>{@link #getIcon} / {@link #getLabel} —— 带缓存的图标 / 标签加载</li>
 *   <li>{@link #refreshCustomIcons} —— 扫描外部存储中的自定义图标文件</li>
 *   <li>{@link #markDirty()} —— 标记需要重新扫描文件系统</li>
 *   <li>{@link #clearAppCache()} —— 应用安装/卸载后清除缓存</li>
 * </ul>
 */
public class IconCache {

  private static final String ICON_DIR = "E-Ink Launcher" + File.separator + "icon";

  // Keep only the icons needed by the current/nearby pages.  An unbounded map
  // retains every Drawable ever seen until the process dies.
  private final LruCache<String, Drawable> drawableCache = new LruCache<String, Drawable>(48) {
    @Override
    protected int sizeOf(String key, Drawable value) {
      return 1;
    }
  };
  private final Map<String, CharSequence> labelCache = new HashMap<>();
  private final Map<String, File> customIconMap = new HashMap<>();
  private boolean dirty = true;

  // =========================================================================
  // 自定义图标
  // =========================================================================

  /** 标记自定义图标映射为脏，下次 {@link #refreshCustomIcons} 时重新扫描 */
  public void markDirty() {
    dirty = true;
  }

  /**
   * 如有必要，重新扫描外部存储中的自定义图标目录。
   *
   * @param hasExternalStorage 外部存储是否可用
   * @param showCustomIcon     用户是否启用"显示自定义图标"
   * @return true 表示执行了实际扫描
   */
  public boolean refreshCustomIcons(boolean hasExternalStorage, boolean showCustomIcon) {
    if (!dirty) return false;
    Map<String, File> previous = new HashMap<>(customIconMap);
    customIconMap.clear();

    if (hasExternalStorage && showCustomIcon) {
      File root = getIconDirectory();
      if (!root.exists()) {
        try {
          root.mkdirs();
        } catch (Exception ignored) {
        }
      }
      File[] files = root.listFiles();
      if (files != null) {
        for (File file : files) {
          String name = file.getName();
          int dot = name.lastIndexOf('.');
          customIconMap.put(dot > 0 ? name.substring(0, dot) : name, file);
        }
      }
    }
    dirty = false;
    return !previous.equals(customIconMap);
  }

  private static File getIconDirectory() {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
      return new File(
          Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), ICON_DIR);
    }
    return new File(Environment.getExternalStorageDirectory(), ICON_DIR);
  }

  /** 获取指定包名的自定义图标文件，不存在时返回 null */
  public File getCustomIcon(String packageName) {
    return customIconMap.get(packageName);
  }

  /** 获取完整的自定义图标映射（包名 → 文件），供 WifiControl 使用 */
  public Map<String, File> getCustomIconMap() {
    return Collections.unmodifiableMap(customIconMap);
  }

  // =========================================================================
  // 应用图标 & 标签缓存
  // =========================================================================

  public Drawable getCachedIcon(String packageName) {
    return drawableCache.get(packageName);
  }

  public void putIcon(String cacheKey, Drawable drawable) {
    if (cacheKey != null && drawable != null) {
      drawableCache.put(cacheKey, drawable);
    }
  }

  /** 带缓存的图标加载 */
  public Drawable getIcon(String packageName, ResolveInfo info, PackageManager pm) {
    Drawable cached = getCachedIcon(packageName);
    if (cached != null) {
      return cached;
    }
    Drawable loaded = info.loadIcon(pm);
    cached = drawableCache.get(packageName);
    if (cached == null) {
      drawableCache.put(packageName, loaded);
      cached = loaded;
    }
    return cached;
  }

  /** 带缓存的标签加载 */
  public CharSequence getLabel(String packageName, ResolveInfo info, PackageManager pm) {
    synchronized (labelCache) {
      CharSequence cached = labelCache.get(packageName);
      if (cached != null) {
        return cached;
      }
    }
    CharSequence loaded = info.nonLocalizedLabel != null ? info.nonLocalizedLabel : info.loadLabel(pm);
    synchronized (labelCache) {
      CharSequence cached = labelCache.get(packageName);
      if (cached == null) {
        labelCache.put(packageName, loaded);
        cached = loaded;
      }
      return cached;
    }
  }

  /** 清除图标和标签缓存（应用安装/卸载时调用） */
  public void clearAppCache() {
    drawableCache.evictAll();
    synchronized (labelCache) {
      labelCache.clear();
    }
  }

  /** 清除图标 Drawable 缓存（显示自定义图标开关切换时调用）。 */
  public void clearIconCache() {
    drawableCache.evictAll();
  }

  /** Release the least recently used icons under memory pressure. */
  public void trimMemory(int level) {
    if (level >= 80) {
      drawableCache.evictAll();
    } else if (level >= 40) {
      // evictAll is available on every supported API and avoids retaining
      // large bitmap-backed Drawables during a moderate memory warning.
      drawableCache.evictAll();
    }
  }
}
