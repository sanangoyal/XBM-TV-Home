package com.sanan.xmb;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.SecureRandom;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Minimal MCP server (JSON-RPC over HTTP POST /mcp, bearer token). Off by default. */
class AgentServer {
  static final int PORT = 8765;
  final MainActivity act;
  final SharedPreferences sp;
  volatile ServerSocket ss;
  volatile boolean running;

  AgentServer(MainActivity a) { act = a; sp = a.getSharedPreferences("agent", 0); }

  boolean enabled() { return sp.getBoolean("on", false); }

  String token() {
    String t = sp.getString("token", null);
    if (t == null) t = newToken();
    return t;
  }

  String newToken() {
    SecureRandom r = new SecureRandom(); StringBuilder b = new StringBuilder();
    String cs = "abcdefghjkmnpqrstuvwxyz23456789";
    for (int i = 0; i < 16; i++) b.append(cs.charAt(r.nextInt(cs.length())));
    sp.edit().putString("token", b.toString()).apply();
    return b.toString();
  }

  String ip() {
    try {
      Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces();
      while (e.hasMoreElements()) {
        NetworkInterface n = e.nextElement();
        if (n.isLoopback() || !n.isUp()) continue;
        Enumeration<InetAddress> a = n.getInetAddresses();
        while (a.hasMoreElements()) {
          InetAddress x = a.nextElement();
          if (!x.isLoopbackAddress() && x.getAddress().length == 4) return x.getHostAddress();
        }
      }
    } catch (Throwable t) {}
    return "";
  }

  void setEnabled(boolean on) {
    sp.edit().putBoolean("on", on).apply();
    if (on) start(); else stop();
  }

  synchronized void start() {
    if (running) return;
    token();
    running = true;
    new Thread(new Runnable() { public void run() {
      try {
        ss = new ServerSocket(PORT);
        while (running) {
          final Socket s = ss.accept();
          new Thread(new Runnable() { public void run() { handle(s); } }).start();
        }
      } catch (Throwable t) { running = false; }
    } }).start();
  }

  synchronized void stop() {
    running = false;
    try { if (ss != null) ss.close(); } catch (Throwable t) {}
    ss = null;
  }

  // ---------- HTTP ----------
  void handle(Socket s) {
    try {
      s.setSoTimeout(15000);
      InputStream in = s.getInputStream();
      ByteArrayOutputStream hb = new ByteArrayOutputStream();
      int m = 0, c;
      while ((c = in.read()) >= 0) {
        hb.write(c);
        m = (c == '\r' || c == '\n') ? m + 1 : 0;
        if (m == 4 || hb.size() > 16384) break;
      }
      String head = hb.toString("UTF-8");
      String[] lines = head.split("\r\n");
      String reqLine = lines.length > 0 ? lines[0] : "";
      int len = 0; String auth = "";
      for (String l : lines) {
        String lo = l.toLowerCase();
        if (lo.startsWith("content-length:")) len = Integer.parseInt(l.substring(15).trim());
        else if (lo.startsWith("authorization:")) auth = l.substring(14).trim();
      }
      byte[] body = new byte[Math.min(len, 1 << 20)];
      int off = 0;
      while (off < body.length) { int n = in.read(body, off, body.length - off); if (n < 0) break; off += n; }
      OutputStream out = s.getOutputStream();
      if (!auth.equals("Bearer " + token())) { reply(out, 401, "{\"error\":\"unauthorized\"}"); s.close(); return; }
      if (!reqLine.startsWith("POST ")) { reply(out, 405, "{\"error\":\"use POST /mcp\"}"); s.close(); return; }
      String resp = rpc(new String(body, "UTF-8"));
      if (resp == null) reply(out, 202, ""); else reply(out, 200, resp);
      s.close();
    } catch (Throwable t) { try { s.close(); } catch (Throwable x) {} }
  }

  void reply(OutputStream out, int code, String body) throws Exception {
    byte[] b = body.getBytes("UTF-8");
    String h = "HTTP/1.1 " + code + " " + (code == 200 ? "OK" : code == 202 ? "Accepted" : code == 401 ? "Unauthorized" : "Error")
      + "\r\nContent-Type: application/json\r\nContent-Length: " + b.length + "\r\nConnection: close\r\n\r\n";
    out.write(h.getBytes("UTF-8")); out.write(b); out.flush();
  }

  // ---------- JSON-RPC ----------
  String rpc(String txt) {
    Object id = null;
    try {
      JSONObject q = new JSONObject(txt);
      id = q.opt("id");
      String method = q.optString("method");
      if (id == null || method.startsWith("notifications/")) return null;
      JSONObject res = new JSONObject();
      if (method.equals("initialize")) {
        res.put("protocolVersion", "2025-03-26");
        res.put("capabilities", new JSONObject().put("tools", new JSONObject()));
        res.put("serverInfo", new JSONObject().put("name", "xmb-launcher").put("version", "1.0"));
      } else if (method.equals("tools/list")) {
        res.put("tools", tools());
      } else if (method.equals("tools/call")) {
        JSONObject p = q.getJSONObject("params");
        res = call(p.getString("name"), p.optJSONObject("arguments") == null ? new JSONObject() : p.optJSONObject("arguments"));
      } else if (method.equals("ping")) {
        // empty result
      } else {
        return new JSONObject().put("jsonrpc", "2.0").put("id", id).put("error", new JSONObject().put("code", -32601).put("message", "method not found")).toString();
      }
      return new JSONObject().put("jsonrpc", "2.0").put("id", id).put("result", res).toString();
    } catch (Throwable t) {
      try { return new JSONObject().put("jsonrpc", "2.0").put("id", id).put("error", new JSONObject().put("code", -32603).put("message", String.valueOf(t))).toString(); } catch (Throwable x) { return null; }
    }
  }

  JSONObject tool(String name, String desc, String props, String req) throws Exception {
    JSONObject schema = new JSONObject("{\"type\":\"object\",\"properties\":" + props + ",\"required\":" + req + "}");
    return new JSONObject().put("name", name).put("description", desc).put("inputSchema", schema);
  }

  JSONArray tools() throws Exception {
    JSONArray a = new JSONArray();
    a.put(tool("status", "Launcher version, TV IP, current settings and screen state.", "{}", "[]"));
    a.put(tool("get_settings", "Read launcher settings: background, color, music.", "{}", "[]"));
    a.put(tool("set_setting", "Change a launcher setting. background: static|dynamic. color: auto|rand|gold|blue|black|sc_black|sc_red|sc_orange|sc_green|sc_teal|sc_indigo|sc_purple (sc_* = solid, static only). music: 0|15|30|50 (volume %, 0=off).",
      "{\"key\":{\"type\":\"string\",\"enum\":[\"background\",\"color\",\"music\"]},\"value\":{\"type\":\"string\"}}", "[\"key\",\"value\"]"));
    a.put(tool("press_key", "Press a remote key inside the launcher UI: up, down, left, right, enter, back, home.", "{\"key\":{\"type\":\"string\",\"enum\":[\"up\",\"down\",\"left\",\"right\",\"enter\",\"back\",\"home\"]}}", "[\"key\"]"));
    a.put(tool("list_apps", "List launchable apps (label + package).", "{}", "[]"));
    a.put(tool("open_app", "Launch an app by package name.", "{\"package\":{\"type\":\"string\"}}", "[\"package\"]"));
    a.put(tool("screenshot", "JPEG screenshot of the launcher (only while the launcher is in the foreground).", "{}", "[]"));
    return a;
  }

  JSONObject text(String s) throws Exception {
    return new JSONObject().put("content", new JSONArray().put(new JSONObject().put("type", "text").put("text", s)));
  }

  JSONObject err(String s) throws Exception { return text(s).put("isError", true); }

  String evalJs(final String code) {
    final String[] r = new String[1];
    final CountDownLatch l = new CountDownLatch(1);
    new Handler(Looper.getMainLooper()).post(new Runnable() { public void run() {
      act.wv.evaluateJavascript(code, new android.webkit.ValueCallback<String>() { public void onReceiveValue(String v) { r[0] = v; l.countDown(); } });
    } });
    try { l.await(5, TimeUnit.SECONDS); } catch (Throwable t) {}
    try { Object o = new JSONTokener(r[0] == null ? "null" : r[0]).nextValue(); return String.valueOf(o); } catch (Throwable t) { return String.valueOf(r[0]); }
  }

  JSONObject call(String name, JSONObject a) throws Exception {
    if (name.equals("status")) {
      JSONObject o = new JSONObject();
      o.put("ip", ip()).put("port", PORT).put("foreground", MainActivity.fg);
      o.put("settings", new JSONObject(evalJs("window.__agent?__agent.get():'{}'")));
      return text(o.toString());
    }
    if (name.equals("get_settings")) return text(evalJs("window.__agent?__agent.get():'{}'"));
    if (name.equals("set_setting")) {
      String k = a.optString("key"), v = a.optString("value");
      return text(evalJs("window.__agent?__agent.set(" + JSONObject.quote(k) + "," + JSONObject.quote(v) + "):'launcher not ready'"));
    }
    if (name.equals("press_key")) {
      String k = a.optString("key"), js;
      if (k.equals("home")) js = "(window.hostHome&&hostHome(),'ok')";
      else {
        String key = k.equals("up") ? "ArrowUp" : k.equals("down") ? "ArrowDown" : k.equals("left") ? "ArrowLeft" : k.equals("right") ? "ArrowRight" : k.equals("enter") ? "Enter" : k.equals("back") ? "Backspace" : "";
        if (key.length() == 0) return err("unknown key");
        js = "(window.hostKey&&hostKey('" + key + "'),'ok')";
      }
      MainActivity.lastKey = System.currentTimeMillis();
      return text(evalJs(js));
    }
    if (name.equals("list_apps")) {
      PackageManager pm = act.getPackageManager();
      JSONArray out = new JSONArray(); java.util.HashSet<String> seen = new java.util.HashSet<String>();
      String[] cats = { Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER };
      for (String cat : cats) {
        List<ResolveInfo> l = pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(cat), 0);
        for (ResolveInfo r : l) {
          String p = r.activityInfo.packageName;
          if (seen.add(p)) out.put(new JSONObject().put("label", String.valueOf(r.loadLabel(pm))).put("package", p));
        }
      }
      return text(out.toString());
    }
    if (name.equals("open_app")) {
      PackageManager pm = act.getPackageManager(); String p = a.optString("package");
      Intent i = pm.getLeanbackLaunchIntentForPackage(p);
      if (i == null) i = pm.getLaunchIntentForPackage(p);
      if (i == null) return err("no launch intent for " + p);
      i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      act.startActivity(i);
      return text("ok");
    }
    if (name.equals("screenshot")) {
      if (!MainActivity.fg) return err("launcher not in foreground");
      final Bitmap bmp = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888);
      final CountDownLatch l = new CountDownLatch(1); final int[] rc = { -1 };
      new Handler(Looper.getMainLooper()).post(new Runnable() { public void run() {
        try {
          android.view.PixelCopy.request(act.getWindow(), bmp, new android.view.PixelCopy.OnPixelCopyFinishedListener() {
            public void onPixelCopyFinished(int r) { rc[0] = r; l.countDown(); } }, new Handler(Looper.getMainLooper()));
        } catch (Throwable t) { l.countDown(); }
      } });
      l.await(5, TimeUnit.SECONDS);
      if (rc[0] != 0) return err("screenshot failed " + rc[0]);
      ByteArrayOutputStream bo = new ByteArrayOutputStream();
      bmp.compress(Bitmap.CompressFormat.JPEG, 70, bo);
      return new JSONObject().put("content", new JSONArray().put(new JSONObject().put("type", "image").put("data", Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP)).put("mimeType", "image/jpeg")));
    }
    return err("unknown tool " + name);
  }
}
