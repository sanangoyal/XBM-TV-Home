package com.sanan.xmb;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;

public class MainActivity extends Activity {
  static final String TAG = "XMB";
  WebView wv; AgentServer agent;
  static volatile long lastKey = 0; static volatile boolean fg = false;

  // ---- ambient music (idle) ----
  android.media.MediaPlayer amb; int ambVol = 0; long ambForce = 0; float ambCur = 0; boolean ambWant = false; android.os.Handler ambH = new android.os.Handler();
  final Runnable ambTick = new Runnable() { public void run() {
    try {
      long now = System.currentTimeMillis();
      boolean want = ambVol > 0 && fg && (now - lastKey > 300000 || now < ambForce);
      if (want && amb == null) {
        amb = new android.media.MediaPlayer();
        android.content.res.AssetFileDescriptor f = getAssets().openFd("ambient.ogg");
        amb.setDataSource(f.getFileDescriptor(), f.getStartOffset(), f.getLength()); f.close();
        amb.setLooping(true); amb.setVolume(0f, 0f); amb.prepare();
      }
      if (amb != null) {
        float tgt = want ? ambVol / 100f : 0f;
        float step = want ? 0.004f * (ambVol / 100f) + 0.0005f : 0.03f;
        if (ambCur < tgt) ambCur = Math.min(tgt, ambCur + step); else if (ambCur > tgt) ambCur = Math.max(tgt, ambCur - step * 3);
        float v = ambCur * ambCur; amb.setVolume(v, v);
        if (ambCur > 0 && !amb.isPlaying()) amb.start();
        if (ambCur == 0 && !want && amb.isPlaying()) amb.pause();
      }
    } catch (Throwable e) {}
    ambH.postDelayed(this, 100);
  } };
  @Override public boolean dispatchKeyEvent(KeyEvent e) { lastKey = System.currentTimeMillis(); return super.dispatchKeyEvent(e); }
  android.view.SurfaceView sv; android.view.Surface surf; android.media.MediaPlayer mp; String bgKey; boolean bgOn = true; boolean bgStatic = false; int bgErr = 0; int bgGen = 0; volatile boolean mpReady = false;
  void bgRelease() { mpReady = false; try { if (mp != null) { mp.release(); } } catch (Throwable e) {} mp = null; }
  // A surface that has been drawn on with lockCanvas (static mode) cannot be given to the video decoder again. Hiding and showing the SurfaceView makes Android build a fresh one.
  void resetSurface() { try { surf = null; sv.setVisibility(View.GONE); ambH.postDelayed(new Runnable() { public void run() { sv.setVisibility(bgOn ? View.VISIBLE : View.INVISIBLE); } }, 150); } catch (Throwable e) {} }
  volatile int bgBlur = 0; // 0 none, 1 submenu (soft), 2 settings panel (softer + dim)
  // Settings panels: draw the still soft (shrunk then stretched) and slightly dimmed. Video cannot be blurred on Android 11, so dynamic mode only gets the web-side dim.
  void paintStill(android.graphics.Canvas c, android.graphics.Bitmap gb, android.graphics.Bitmap rb) {
    int w = c.getWidth(), h = c.getHeight();
    android.graphics.Paint p1 = new android.graphics.Paint(); p1.setDither(true); p1.setFilterBitmap(true);
    android.graphics.Paint p2 = null;
    if (rb != null) { p2 = new android.graphics.Paint(); p2.setFilterBitmap(true); p2.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.ADD)); }
    final int lv = bgBlur;
    if (lv == 0) { c.drawColor(android.graphics.Color.BLACK); c.drawBitmap(gb, null, new android.graphics.Rect(0, 0, w, h), p1); if (rb != null) c.drawBitmap(rb, null, new android.graphics.Rect(0, 0, w, h), p2); return; }
    int dv = lv == 1 ? 6 : 12; int sw = Math.max(16, w / dv), sh = Math.max(9, h / dv);
    android.graphics.Bitmap off = android.graphics.Bitmap.createBitmap(sw, sh, android.graphics.Bitmap.Config.ARGB_8888);
    android.graphics.Canvas oc = new android.graphics.Canvas(off); oc.drawColor(android.graphics.Color.BLACK);
    android.graphics.Bitmap a = gb; for (int k = 0; k < 3 && a.getWidth() > sw * 2; k++) { android.graphics.Bitmap n = android.graphics.Bitmap.createScaledBitmap(a, Math.max(sw, a.getWidth() / 2), Math.max(sh, a.getHeight() / 2), true); if (a != gb) a.recycle(); a = n; }
    oc.drawBitmap(a, null, new android.graphics.Rect(0, 0, sw, sh), p1); if (a != gb) a.recycle();
    if (rb != null) { android.graphics.Bitmap r = rb; for (int k = 0; k < 3 && r.getWidth() > sw * 2; k++) { android.graphics.Bitmap n = android.graphics.Bitmap.createScaledBitmap(r, Math.max(sw, r.getWidth() / 2), Math.max(sh, r.getHeight() / 2), true); if (r != rb) r.recycle(); r = n; } oc.drawBitmap(r, null, new android.graphics.Rect(0, 0, sw, sh), p2); if (r != rb) r.recycle(); }
    android.graphics.Bitmap mid = android.graphics.Bitmap.createScaledBitmap(off, sw * 3, sh * 3, true);
    c.drawColor(android.graphics.Color.BLACK); c.drawBitmap(mid, null, new android.graphics.Rect(0, 0, w, h), p1); c.drawColor(lv == 1 ? 0x1A000000 : 0x33000000);
    mid.recycle(); off.recycle();
  }
  void bgPlay(final String key, boolean force) {
    if (!force && key.equals(bgKey) && mp != null) return;
    bgKey = key; if (surf == null) return;
    bgRelease(); final int gen = ++bgGen; // any video start still waiting is now stale
    if (bgStatic) {
      try {
        android.graphics.Bitmap bm = null;
        if (key.startsWith("sc_")) {
          String n = key.substring(3); int c1 = 0xFF3A404A, c2 = 0xFF0A0C10;
          if (n.equals("black")) { c1 = 0xFF34363B; c2 = 0xFF040405; } else if (n.equals("red")) { c1 = 0xFF7A1C24; c2 = 0xFF14060A; } else if (n.equals("orange")) { c1 = 0xFF8A4A12; c2 = 0xFF150C05; }
          else if (n.equals("green")) { c1 = 0xFF1F6B45; c2 = 0xFF06140E; } else if (n.equals("teal")) { c1 = 0xFF16666B; c2 = 0xFF051315; }
          else if (n.equals("indigo")) { c1 = 0xFF1F3F8A; c2 = 0xFF060B1A; } else if (n.equals("purple")) { c1 = 0xFF5A2A86; c2 = 0xFF0E0618; }
          else if (n.equals("pink")) { c1 = 0xFF8A2A5E; c2 = 0xFF16060F; }
          android.view.SurfaceHolder sh2 = sv.getHolder(); android.graphics.Canvas c = sh2.lockCanvas();
          if (c != null) { int w = c.getWidth(), h = c.getHeight(); android.graphics.Paint pt = new android.graphics.Paint(); pt.setAntiAlias(true); pt.setDither(true);
            pt.setShader(new android.graphics.LinearGradient(0, 0, w, h, c1, c2, android.graphics.Shader.TileMode.CLAMP)); c.drawRect(0, 0, w, h, pt);
            pt.setShader(new android.graphics.RadialGradient(w * 0.28f, h * 0.2f, w * 0.7f, (c1 & 0x00FFFFFF) | 0x55000000, 0x00000000, android.graphics.Shader.TileMode.CLAMP)); c.drawRect(0, 0, w, h, pt);
            android.graphics.Bitmap nz = android.graphics.Bitmap.createBitmap(256, 256, android.graphics.Bitmap.Config.ARGB_8888); java.util.Random rnd = new java.util.Random(7); int[] px = new int[256 * 256];
            for (int i = 0; i < px.length; i++) { int v = rnd.nextInt(2) == 0 ? 0xFF : 0x00; px[i] = (v << 24 >>> 24 == 0xFF ? 0x04FFFFFF : 0x04000000); }
            nz.setPixels(px, 0, 256, 0, 0, 256, 256); android.graphics.Paint np = new android.graphics.Paint(); np.setShader(new android.graphics.BitmapShader(nz, android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT)); c.drawRect(0, 0, w, h, np); nz.recycle();
            sh2.unlockCanvasAndPost(c); }
          return;
        }
        if (key.equals("custom")) bm = android.graphics.BitmapFactory.decodeFile(new java.io.File(getFilesDir(), "custom_bg.jpg").getPath());
        else {
          java.io.InputStream is = getAssets().open("bg_" + key + "_g.png"); android.graphics.Bitmap gb = android.graphics.BitmapFactory.decodeStream(is); is.close();
          java.io.InputStream is2 = getAssets().open("bg_" + key + "_r.png"); android.graphics.Bitmap rb = android.graphics.BitmapFactory.decodeStream(is2); is2.close();
          android.view.SurfaceHolder sh3 = sv.getHolder(); android.graphics.Canvas c3 = sh3.lockCanvas();
          if (c3 != null) { paintStill(c3, gb, rb); sh3.unlockCanvasAndPost(c3); }
          gb.recycle(); rb.recycle(); return;
        }
        android.view.SurfaceHolder sh = sv.getHolder();
        android.graphics.Canvas c = sh.lockCanvas();
        if (c != null) { if (bm != null) paintStill(c, bm, null); else c.drawColor(android.graphics.Color.BLACK); sh.unlockCanvasAndPost(c); }
        if (bm != null) bm.recycle();
      } catch (Throwable e) { Log.e(TAG, "bgStatic", e); }
      return;
    }
    // Wait briefly so rapid colour/mode changes only start the last video: this TV's hardware decoder fails if it is torn down and rebuilt in quick succession.
    final String vk = key;
    ambH.postDelayed(new Runnable() { public void run() { if (gen == bgGen && !bgStatic && surf != null) bgStart(vk); } }, 250);
  }
  void bgStart(String key) {
    try {
      android.content.res.AssetFileDescriptor f = getAssets().openFd("bg_" + key + ".mp4");
      android.media.MediaPlayer n = new android.media.MediaPlayer();
      n.setDataSource(f.getFileDescriptor(), f.getStartOffset(), f.getLength()); f.close();
      n.setSurface(surf); n.setLooping(true); n.setVolume(0f, 0f);
      n.setVideoScalingMode(android.media.MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
      n.setOnPreparedListener(new android.media.MediaPlayer.OnPreparedListener() { public void onPrepared(android.media.MediaPlayer p) { if (mp == p) { bgErr = 0; mpReady = true; } if (bgOn && mp == p) p.start(); } });
      n.setOnErrorListener(new android.media.MediaPlayer.OnErrorListener() { public boolean onError(android.media.MediaPlayer p, int w, int x) {
        Log.i(TAG, "bgv error " + w + "/" + x + (p == mp ? "" : " (stale, ignored)"));
        if (p != mp) return true; // a player we already replaced: not a real failure
        if (++bgErr <= 3) { ambH.postDelayed(new Runnable() { public void run() { if (bgKey != null && bgStatic == false) bgPlay(bgKey, true); } }, 600); }
        else js("window.nvFail&&nvFail()");
        return true; } });
      mpReady = false; mp = n; n.prepareAsync();
    } catch (Throwable e) { Log.e(TAG, "bgStart", e); mpReady = false; resetSurface();
      if (++bgErr <= 3) ambH.postDelayed(new Runnable() { public void run() { if (bgKey != null && !bgStatic) bgPlay(bgKey, true); } }, 800);
      else js("window.nvFail&&nvFail()"); }
  }
  void bgShow(boolean on) {
    bgOn = on; sv.setVisibility(on ? View.VISIBLE : View.INVISIBLE);
    try { if (mp != null && mpReady) { if (on && !mp.isPlaying()) mp.start(); else if (!on && mp.isPlaying()) mp.pause(); } } catch (Throwable e) {}
  }
  float brightness = -1f;

  @Override protected void onCreate(Bundle b) {
    super.onCreate(b);
    fetchAvatar();
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    wv = new WebView(this);
    wv.setBackgroundColor(Color.TRANSPARENT);
    WebSettings s = wv.getSettings();
    s.setJavaScriptEnabled(true);
    s.setDomStorageEnabled(true);
    s.setAllowFileAccess(true);
    s.setAllowFileAccessFromFileURLs(true);
    s.setAllowUniversalAccessFromFileURLs(true);
    s.setMediaPlaybackRequiresUserGesture(false);
    s.setCacheMode(WebSettings.LOAD_DEFAULT);
    wv.setWebViewClient(new WebViewClient());
    wv.setWebChromeClient(new WebChromeClient() {
      @Override public boolean onConsoleMessage(android.webkit.ConsoleMessage m) {
        Log.i(TAG, "js: " + m.message() + " @" + m.lineNumber());
        return true;
      }
    });
    wv.addJavascriptInterface(new Bridge(), "Android");
    android.widget.FrameLayout root = new android.widget.FrameLayout(this);
    sv = new android.view.SurfaceView(this);
    sv.getHolder().setFormat(android.graphics.PixelFormat.RGBA_8888);
    sv.getHolder().addCallback(new android.view.SurfaceHolder.Callback() {
      public void surfaceCreated(android.view.SurfaceHolder h) { surf = h.getSurface(); if (bgKey != null) bgPlay(bgKey, true); }
      public void surfaceChanged(android.view.SurfaceHolder h, int f, int w, int hh) {}
      public void surfaceDestroyed(android.view.SurfaceHolder h) { bgRelease(); surf = null; }
    });
    root.addView(sv, new android.widget.FrameLayout.LayoutParams(-1, -1));
    root.addView(wv, new android.widget.FrameLayout.LayoutParams(-1, -1));
    setContentView(root);
    wv.requestFocus();
    String q = getIntent() != null && getIntent().getStringExtra("q") != null ? getIntent().getStringExtra("q") : "";
    wv.loadUrl("file:///android_asset/index.html" + q);
    hideBars();
    WebView.setWebContentsDebuggingEnabled(false); // release build: no remote DevTools
    ambH.postDelayed(ambTick, 1000);
    agent = new AgentServer(this); if (agent.enabled()) agent.start();
    final android.os.Handler h = new android.os.Handler();
    for (int k = 1; k <= 3; k++) h.postDelayed(new Runnable() { public void run() {
      wv.evaluateJavascript("JSON.stringify({ph:typeof phase!=='undefined'?phase:null,w:innerWidth,h:innerHeight,cw:document.documentElement.clientWidth,sw:screen.width,dpr:devicePixelRatio,vp:(document.querySelector('meta[name=viewport]')||{}).content,cv:document.querySelectorAll('canvas').length,boot:(document.getElementById('boot')||{}).className,bodyH:document.body.innerHTML.length,ua:navigator.userAgent})", new android.webkit.ValueCallback<String>() { public void onReceiveValue(String v) { Log.i(TAG, "state " + v + " wv=" + wv.getWidth() + "x" + wv.getHeight() + " shown=" + wv.isShown()); } });
    }}, 4000 * k);
  }

  void hideBars() {
    getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
  }

  void js(final String code) { runOnUiThread(new Runnable() { public void run() { wv.evaluateJavascript(code, null); } }); }

  @Override protected void onNewIntent(Intent i) {
    super.onNewIntent(i);
    if (i != null && i.getStringExtra("js") != null) { js(i.getStringExtra("js")); return; } // debug hook
    if (i != null && i.hasCategory(Intent.CATEGORY_HOME)) js("window.hostHome&&hostHome()");
  }
  @Override protected void onPause() { super.onPause(); fg = false; try { if (mp != null && mpReady && mp.isPlaying()) mp.pause(); } catch (Throwable e) {} }
  @Override protected void onResume() { super.onResume(); fg = true; lastKey = System.currentTimeMillis(); try { if (mp != null && mpReady && bgOn && !mp.isPlaying()) mp.start(); } catch (Throwable e) {} MemGuard.start(this); MemGuard.nudge(); hideBars(); js("window.hostResume&&hostResume()"); }

  @Override public boolean onKeyDown(int code, KeyEvent e) {
    if (code == KeyEvent.KEYCODE_BACK) { js("window.hostKey&&hostKey('Escape')"); return true; }
    if (code == KeyEvent.KEYCODE_MENU || code == KeyEvent.KEYCODE_INFO || code == KeyEvent.KEYCODE_BOOKMARK) { js("window.hostKey&&hostKey('o')"); return true; }
    return super.onKeyDown(code, e);
  }
  @Override public boolean onKeyUp(int code, KeyEvent e) {
    if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_MENU) return true;
    return super.onKeyUp(code, e);
  }
  @Override public void onBackPressed() { /* handled in onKeyDown */ }


  // Android 11 hides Google accounts from third-party apps. Optional: put your address in /sdcard/xmb-account.txt to show your initial.
  String acctFallback() { try { java.io.File f = new java.io.File("/sdcard/xmb-account.txt"); if (f.exists() && f.length() < 200) { java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(f)); String l = r.readLine(); r.close(); if (l != null && l.trim().length() > 0) return l.trim(); } } catch (Throwable t) {} return "Google"; }
  android.accounts.Account gacct() {
    try {
      android.accounts.Account[] a = android.accounts.AccountManager.get(this).getAccountsByType("com.google");
      if (a != null && a.length > 0) return a[0];
    } catch (Throwable t) {}
    return new android.accounts.Account(acctFallback(), "com.google");
  }
  static volatile String avatarUri = "";
  void fetchAvatar() {
    try {
      final java.io.File f = new java.io.File(getFilesDir(), "avatar.jpg");
      if (f.exists() && f.length() > 100) { loadAvatar(f); return; }
      final android.accounts.AccountManager am = android.accounts.AccountManager.get(this);
      final android.accounts.Account[] a = new android.accounts.Account[] { gacct() };
      android.content.SharedPreferences sp = getSharedPreferences("av", 0);
      long last = sp.getLong("try", 0);
      if (System.currentTimeMillis() - last < 120000) return;
      sp.edit().putLong("try", System.currentTimeMillis()).apply();
      am.getAuthToken(a[0], "oauth2:https://www.googleapis.com/auth/userinfo.profile", null, this,
        new android.accounts.AccountManagerCallback<Bundle>() {
          public void run(android.accounts.AccountManagerFuture<Bundle> fu) {
            try {
              Bundle r = fu.getResult();
              final String tok = r.getString(android.accounts.AccountManager.KEY_AUTHTOKEN);
              Log.i(TAG, "avatar token " + (tok != null));
              if (tok == null) return;
              new Thread(new Runnable() { public void run() { try {
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL("https://www.googleapis.com/oauth2/v2/userinfo").openConnection();
                c.setRequestProperty("Authorization", "Bearer " + tok);
                java.io.InputStream in = c.getInputStream();
                java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream(); byte[] buf = new byte[4096]; int n;
                while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                String pic = new JSONObject(bo.toString("UTF-8")).optString("picture", "");
                Log.i(TAG, "avatar pic " + pic);
                if (pic.length() == 0) return;
                pic = pic.replaceAll("=s\\d+(-c)?$", "") + (pic.contains("googleusercontent") && !pic.contains("=") ? "=s160-c" : "");
                in = new java.net.URL(pic).openStream();
                java.io.FileOutputStream fo = new java.io.FileOutputStream(f);
                while ((n = in.read(buf)) > 0) fo.write(buf, 0, n);
                fo.close();
                loadAvatar(f);
              } catch (Throwable t) { Log.i(TAG, "avatar fail " + t); } } }).start();
            } catch (Throwable t) { Log.i(TAG, "avatar auth fail " + t); }
          }
        }, null);
    } catch (Throwable t) { Log.i(TAG, "avatar err " + t); }
  }
  void loadAvatar(java.io.File f) {
    try {
      java.io.FileInputStream in = new java.io.FileInputStream(f);
      java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream(); byte[] buf = new byte[4096]; int n;
      while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
      in.close();
      avatarUri = "data:image/jpeg;base64," + android.util.Base64.encodeToString(bo.toByteArray(), android.util.Base64.NO_WRAP);
    } catch (Throwable t) {}
  }
  class Bridge {
    @JavascriptInterface public boolean coldStart() {
      try {
        long boot = System.currentTimeMillis() - android.os.SystemClock.elapsedRealtime();
        android.content.SharedPreferences sp = getSharedPreferences("av", 0);
        long prev = sp.getLong("boot", 0);
        boolean first = Math.abs(boot - prev) > 60000;
        if (first) sp.edit().putLong("boot", boot).commit();
        return first;
      } catch (Throwable t) { return true; }
    }
    @JavascriptInterface public String notifs() { return NotifService.list(); }
    @JavascriptInterface public String avatar() { return avatarUri; }
    final java.util.HashMap<String,String> ICC = new java.util.HashMap<String,String>();
    @JavascriptInterface public String icon(String pkg) {
      try {
        String c = ICC.get(pkg); if (c != null) return c;
        android.graphics.drawable.Drawable d = getPackageManager().getApplicationIcon(pkg);
        int z = 144;
        android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(z, z, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas cv = new android.graphics.Canvas(b);
        d.setBounds(0, 0, z, z); d.draw(cv);
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, bo);
        b.recycle();
        String u = "data:image/png;base64," + android.util.Base64.encodeToString(bo.toByteArray(), android.util.Base64.NO_WRAP);
        ICC.put(pkg, u); return u;
      } catch (Throwable t) { Log.e(TAG, "icon " + pkg, t); return ""; }
    }
    @JavascriptInterface public String apps() {
      JSONArray out = new JSONArray();
      try {
        PackageManager pm = getPackageManager();
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> l = pm.queryIntentActivities(q, 0);
        java.util.HashSet<String> seen = new java.util.HashSet<String>();
        for (ResolveInfo r : l) {
          String p = r.activityInfo.packageName;
          if (p.equals(getPackageName()) || p.equals("com.oneplus.tv.launcher") || !seen.add(p)) continue;
          JSONObject o = new JSONObject();
          o.put("p", p); o.put("l", r.loadLabel(pm).toString());
          out.put(o);
        }
      } catch (Exception e) { Log.e(TAG, "apps", e); }
      return out.toString();
    }
    @JavascriptInterface public void bgv(final String key) { runOnUiThread(new Runnable() { public void run() { bgPlay(key, false); } }); }
    @JavascriptInterface public void bgvPause(final boolean p) { runOnUiThread(new Runnable() { public void run() { try { if (mp != null && mpReady) { if (p && mp.isPlaying()) mp.pause(); else if (!p && bgOn && !mp.isPlaying()) mp.start(); } } catch (Throwable e) {} } }); }
    @JavascriptInterface public boolean hasCustom() { return new java.io.File(getFilesDir(), "custom_bg.jpg").exists(); }
    @JavascriptInterface public void ambCfg(final int vol, final boolean preview) { runOnUiThread(new Runnable() { public void run() { ambVol = vol; if (preview) ambForce = System.currentTimeMillis() + 8000; else if (vol == 0) ambForce = 0; } }); }
    // Optional user key: one line in /sdcard/xmb-tmdb.txt. Never shipped in the APK.
    @JavascriptInterface public String tmdbKey() { try { java.io.File f = new java.io.File("/sdcard/xmb-tmdb.txt"); if (f.exists() && f.length() < 400) { java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(f)); String l = r.readLine(); r.close(); if (l != null) return l.trim(); } } catch (Throwable t) {} return ""; }
    @JavascriptInterface public String agentInfo() { try { return new JSONObject().put("on", agent.enabled()).put("ip", agent.ip()).put("port", AgentServer.PORT).put("token", agent.token()).toString(); } catch (Throwable t) { return "{}"; } }
    @JavascriptInterface public void agentSet(final boolean on) { agent.setEnabled(on); }
    @JavascriptInterface public void agentNewToken() { agent.newToken(); }
    @JavascriptInterface public void bgBlur(final int lv) { runOnUiThread(new Runnable() { public void run() {
      float s = lv > 0 ? 1.05f : 1f; sv.animate().scaleX(s).scaleY(s).setDuration(320).start(); // slight zoom works for still and video
      if (bgBlur == lv) return; bgBlur = lv; if (bgStatic && bgKey != null && surf != null) bgPlay(bgKey, true); } }); }
    @JavascriptInterface public void bgvStatic(final boolean on) { runOnUiThread(new Runnable() { public void run() {
      boolean was = bgStatic; bgStatic = on;
      if (on) { if (bgKey != null) bgPlay(bgKey, true); }          // draw the still now
      else if (was) { bgRelease(); ++bgGen; resetSurface(); }       // leaving static: fresh surface, then surfaceCreated / bgv(key) starts the video
      // dynamic -> dynamic (colour change): do nothing here, bgv(newKey) switches the video once
    } }); }
    @JavascriptInterface public String ls(String path) {
      try {
        org.json.JSONArray out = new org.json.JSONArray();
        if (path == null || path.length() == 0) {
          org.json.JSONObject i0 = new org.json.JSONObject(); i0.put("n", "Internal storage"); i0.put("d", true); i0.put("p", "/storage/emulated/0"); out.put(i0);
          java.io.File[] ds = getExternalFilesDirs(null);
          if (ds != null) for (java.io.File d : ds) { if (d == null) continue; String q = d.getPath(); int ix = q.indexOf("/Android/"); if (ix <= 0) continue; String root = q.substring(0, ix); if (root.equals("/storage/emulated/0") || root.startsWith("/storage/emulated")) continue; org.json.JSONObject o = new org.json.JSONObject(); o.put("n", "USB " + new java.io.File(root).getName()); o.put("d", true); o.put("p", root); out.put(o); }
          return out.toString();
        }
        java.io.File[] fs = new java.io.File(path).listFiles();
        if (fs == null) return "[]";
        java.util.Arrays.sort(fs, new java.util.Comparator<java.io.File>() { public int compare(java.io.File a, java.io.File b) { if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1; return a.getName().compareToIgnoreCase(b.getName()); } });
        for (java.io.File f : fs) {
          String n = f.getName(); if (n.startsWith(".")) continue;
          String l = n.toLowerCase(); boolean im = l.endsWith(".jpg") || l.endsWith(".jpeg") || l.endsWith(".png") || l.endsWith(".webp");
          if (!f.isDirectory() && !im) continue;
          org.json.JSONObject o = new org.json.JSONObject(); o.put("n", n); o.put("d", f.isDirectory()); o.put("p", f.getPath());
          if (!f.isDirectory()) o.put("s", (f.length() / 1024) + " KB");
          out.put(o);
        }
        return out.toString();
      } catch (Throwable e) { return "[]"; }
    }
    @JavascriptInterface public String setCustom(String path) {
      try {
        android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options(); o.inJustDecodeBounds = true;
        android.graphics.BitmapFactory.decodeFile(path, o);
        int ss = 1; while (o.outWidth / (ss * 2) >= 1920) ss *= 2;
        android.graphics.BitmapFactory.Options o2 = new android.graphics.BitmapFactory.Options(); o2.inSampleSize = ss;
        android.graphics.Bitmap bm = android.graphics.BitmapFactory.decodeFile(path, o2);
        if (bm == null) return "err";
        java.io.FileOutputStream fo = new java.io.FileOutputStream(new java.io.File(getFilesDir(), "custom_bg.jpg"));
        bm.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, fo); fo.close(); bm.recycle();
        return "ok";
      } catch (Throwable e) { return "err"; }
    }
    @JavascriptInterface public void bgvShow(final boolean on) { runOnUiThread(new Runnable() { public void run() { bgShow(on); } }); }
    @JavascriptInterface public boolean account() {
      try {
        android.accounts.Account a = gacct(); if (a == null) return false;
        Intent i = new Intent().setComponent(new android.content.ComponentName("com.android.tv.settings", "com.android.tv.settings.accounts.AccountSyncActivity"));
        i.putExtra("account_name", a.name); i.putExtra("account_type", a.type);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); return true;
      } catch (Throwable e) { Log.i(TAG, "account fail " + e); return false; }
    }
    @JavascriptInterface public boolean launch(String pkg) {
      try {
        PackageManager pm = getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i == null) return false;
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        startActivity(i); return true;
      } catch (Exception e) { Log.e(TAG, "launch " + pkg, e); return false; }
    }
    @JavascriptInterface public boolean open(String spec) {
      try {
        Intent i;
        if (spec.startsWith("c:")) { String[] p = spec.substring(2).split("/", 2); String cls = p[1].startsWith(".") ? p[0] + p[1] : p[1]; i = new Intent().setComponent(new android.content.ComponentName(p[0], cls)); }
        else if (spec.startsWith("i:")) { i = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + spec.substring(2))); }
        else if (spec.startsWith("d:")) { i = new Intent(Intent.ACTION_DELETE, android.net.Uri.parse("package:" + spec.substring(2))); }
        else if (spec.startsWith("m:")) { i = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=" + spec.substring(2))); }
        else i = new Intent(spec.substring(2));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i); return true;
      } catch (Throwable e) { Log.i(TAG, "open fail " + spec + " " + e); return false; }
    }
    @JavascriptInterface public boolean settings(String action) {
      try {
        Intent i = new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i); return true;
      } catch (Exception e) {
        try { startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return true; } catch (Exception e2) { return false; }
      }
    }
    @JavascriptInterface public String inputs() {
      JSONArray out = new JSONArray();
      try {
        TvInputManager tm = (TvInputManager) getSystemService(Context_TV());
        for (TvInputInfo info : tm.getTvInputList()) {
          int t = info.getType();
          if (t == TvInputInfo.TYPE_TUNER || t == TvInputInfo.TYPE_OTHER) continue;
          JSONObject o = new JSONObject();
          o.put("id", info.getId()); o.put("type", t);
          CharSequence lab = info.loadCustomLabel(MainActivity.this);
          if (lab == null) lab = info.loadLabel(MainActivity.this);
          o.put("label", lab == null ? "" : lab.toString());
          out.put(o);
        }
      } catch (Exception e) { Log.e(TAG, "inputs", e); }
      return out.toString();
    }
    @JavascriptInterface public boolean input(String id) {
      try {
        Intent i = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(id)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i); return true;
      } catch (Exception e) { Log.e(TAG, "input " + id, e); return false; }
    }
    @JavascriptInterface public void dim(final boolean on) {
      runOnUiThread(new Runnable() { public void run() {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = on ? 0f : WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        getWindow().setAttributes(lp);
      }});
    }
    @JavascriptInterface public String status() {
      String net = "none", acct = "";
      try {
        android.net.ConnectivityManager cm = (android.net.ConnectivityManager) getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
        android.net.Network n = cm.getActiveNetwork();
        android.net.NetworkCapabilities c = n == null ? null : cm.getNetworkCapabilities(n);
        if (c != null) {
          if (c.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)) net = "eth";
          else if (c.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)) net = "wifi";
          else net = "other";
        }
      } catch (Throwable t) {}
      try {
        acct = gacct().name;
      } catch (Throwable t) {}
      return "{\"net\":\"" + net + "\",\"n\":" + NotifService.count + ",\"acct\":\"" + acct.replace("\"", "") + "\"}";
    }
    @JavascriptInterface public void log(String m) { Log.i(TAG, "page: " + m); }
  }
  static String Context_TV() { return android.content.Context.TV_INPUT_SERVICE; }
}
