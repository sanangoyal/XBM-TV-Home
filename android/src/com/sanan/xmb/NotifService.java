package com.sanan.xmb;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class NotifService extends NotificationListenerService {
  static volatile int count = -1;
  static volatile NotificationListenerService inst;
  static String list() {
    org.json.JSONArray out = new org.json.JSONArray();
    try {
      NotificationListenerService s = inst;
      StatusBarNotification[] a = s == null ? null : s.getActiveNotifications();
      if (a != null) for (StatusBarNotification x : a) {
        if (!(x.isClearable() || !x.isOngoing())) continue;
        android.os.Bundle e = x.getNotification().extras;
        CharSequence t = e.getCharSequence("android.title"), tx = e.getCharSequence("android.text");
        String lab = x.getPackageName();
        try { android.content.pm.PackageManager pm = s.getPackageManager(); lab = pm.getApplicationLabel(pm.getApplicationInfo(x.getPackageName(), 0)).toString(); } catch (Throwable q) {}
        org.json.JSONObject o = new org.json.JSONObject();
        o.put("p", x.getPackageName()); o.put("a", lab);
        o.put("t", t != null ? t.toString() : (tx != null ? tx.toString() : "(no title)"));
        out.put(o);
      }
    } catch (Throwable t) {}
    return out.toString();
  }
  static void recount(NotificationListenerService s) {
    try {
      int n = 0;
      StatusBarNotification[] a = s.getActiveNotifications();
      if (a != null) for (StatusBarNotification x : a) if (x.isClearable() || !x.isOngoing()) n++;
      count = n;
    } catch (Throwable t) { count = -1; }
  }
  @Override public void onListenerConnected() { inst = this; recount(this); }
  @Override public void onListenerDisconnected() { count = -1; }
  @Override public void onNotificationPosted(StatusBarNotification sbn) { recount(this); }
  @Override public void onNotificationRemoved(StatusBarNotification sbn) { recount(this); }
}
