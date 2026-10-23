package com.sanan.xmb;

import android.app.ActivityManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import java.util.*;

/** Frees RAM: keeps the KEEP most recently used apps (used within IDLE_MS), kills the other launchable apps' background processes. */
public class MemGuard {
  static final String TAG = "XMB";
  static final int KEEP = 3;
  static final long IDLE_MS = 15L * 60 * 1000, PERIOD_MS = 3L * 60 * 1000;
  // never touched (services / system pieces)
  static final String[] PROTECT = {"com.sanan.", "android", "com.google.android.gms", "com.google.android.gsf", "com.android.systemui",
    "com.google.android.inputmethod", "com.android.inputmethod", "com.google.android.webview", "com.android.webview", "com.oneplus.", "com.mediatek.",
    "com.heytap.", "com.android.providers", "com.google.android.tvlauncher", "com.google.android.apps.mediashell", "com.android.bluetooth",
    "com.google.android.permissioncontroller", "com.android.permissioncontroller", "com.android.keychain", "com.android.phone", "com.google.android.tv.remote"};
  // user-facing apps under protected prefixes that are safe to reclaim
  static final String[] ALLOW = {"com.oneplus.tv.btspeaker", "com.heytap.tv.camera", "com.heytap.tv.filebrowser"};
  final Context ctx; final Handler h;
  MemGuard(Context c) { ctx = c.getApplicationContext(); HandlerThread t = new HandlerThread("memguard"); t.start(); h = new Handler(t.getLooper()); }
  static MemGuard inst;
  final Runnable tick = new Runnable() { public void run() {
    // never while the launcher is on screen and the user is navigating (kill bursts cause jank)
    if (MainActivity.fg && System.currentTimeMillis() - MainActivity.lastKey < 30000) { h.postDelayed(this, 15000); return; }
    clean(); h.postDelayed(this, PERIOD_MS);
  } };
  public static synchronized void start(Context c) {
    if (inst != null) return; inst = new MemGuard(c);
    inst.h.postDelayed(inst.tick, 120000);
  }
  public static void nudge() { if (inst != null) { inst.h.removeCallbacks(inst.tick); inst.h.postDelayed(inst.tick, 35000); } }
  static boolean protectedPkg(String p) {
    for (String a : ALLOW) if (p.equals(a)) return false;
    for (String s : PROTECT) if (p.equals(s) || p.startsWith(s.endsWith(".") ? s : s + ".")) return true;
    return false;
  }
  synchronized void clean() {
    try {
      PackageManager pm = ctx.getPackageManager();
      HashSet<String> cand = new HashSet<String>();
      for (String cat : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
        List<ResolveInfo> l = pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(cat), 0);
        for (ResolveInfo r : l) cand.add(r.activityInfo.packageName);
      }
      Iterator<String> it = cand.iterator(); while (it.hasNext()) if (protectedPkg(it.next())) it.remove();
      long now = System.currentTimeMillis();
      UsageStatsManager u = (UsageStatsManager) ctx.getSystemService(Context.USAGE_STATS_SERVICE);
      List<UsageStats> us = u.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 7L * 86400000, now);
      HashMap<String, Long> last = new HashMap<String, Long>();
      if (us == null || us.isEmpty()) { Log.i(TAG, "memguard: no usage access, skipping"); return; }
      for (UsageStats s : us) { Long o = last.get(s.getPackageName()); if (o == null || s.getLastTimeUsed() > o) last.put(s.getPackageName(), s.getLastTimeUsed()); }
      HashSet<String> playing = new HashSet<String>();
      try {
        MediaSessionManager m = (MediaSessionManager) ctx.getSystemService(Context.MEDIA_SESSION_SERVICE);
        for (MediaController c : m.getActiveSessions(new ComponentName(ctx, NotifService.class))) {
          PlaybackState ps = c.getPlaybackState();
          if (ps != null && (ps.getState() == PlaybackState.STATE_PLAYING || ps.getState() == PlaybackState.STATE_BUFFERING)) playing.add(c.getPackageName());
        }
      } catch (Throwable e) { Log.i(TAG, "memguard media: " + e); }
      ArrayList<String> order = new ArrayList<String>(cand);
      final HashMap<String, Long> L = last;
      Collections.sort(order, new Comparator<String>() { public int compare(String a, String b) { long x = L.containsKey(a) ? L.get(a) : 0, y = L.containsKey(b) ? L.get(b) : 0; return x < y ? 1 : x > y ? -1 : 0; } });
      HashSet<String> keep = new HashSet<String>(); keep.addAll(playing);
      for (String p : order) { if (keep.size() >= KEEP + playing.size()) break; Long t = last.get(p); if (t != null && now - t <= IDLE_MS) keep.add(p); }
      ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
      StringBuilder k = new StringBuilder(), kp = new StringBuilder();
      for (String p : order) { if (keep.contains(p)) { kp.append(p).append(' '); continue; } am.killBackgroundProcesses(p); k.append(p).append(' '); }
      Log.i(TAG, "memguard keep=[" + kp + "] killed=[" + k + "]");
    } catch (Throwable e) { Log.e(TAG, "memguard", e); }
  }
}
