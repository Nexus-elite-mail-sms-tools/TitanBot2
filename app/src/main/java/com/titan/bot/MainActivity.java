package com.titan.bot;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.http.SslError;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.view.View;
import android.webkit.*;
import androidx.webkit.ProxyConfig;
import androidx.webkit.ProxyController;
import androidx.webkit.WebViewFeature;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.ViewGroup;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private WebView web1, web2, web3;
    private Button controlBtn;
    private EditText linkIn;
    private TextView dashView, aiStatusView, serverCountView;
    private LinearLayout webContainer;
    
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private ExecutorService scrapExec = Executors.newFixedThreadPool(50); 
    private ExecutorService validExec = Executors.newFixedThreadPool(500); 
    
    private Random rnd = new Random();
    private int totalJumps = 0;
    private boolean isRunning = false;
    
    private Set<String> CHECKED_HISTORY = Collections.synchronizedSet(new HashSet<>());
    private CopyOnWriteArrayList<String> BLACKLIST = new CopyOnWriteArrayList<>();
    private CopyOnWriteArrayList<String> PROXY_POOL = new CopyOnWriteArrayList<>();
    
    private PowerManager.WakeLock wakeLock;
    
    // متغيرات للتحكم الدقيق في التوقيت لكل WebView
    private boolean isWeb1Busy = false;
    private boolean isWeb2Busy = false;
    private boolean isWeb3Busy = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_main);
            
            mHandler.postDelayed(() -> {
                try {
                    PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                    wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TitanBot::SlowMotion");

                    dashView = findViewById(R.id.dashboardView);
                    aiStatusView = findViewById(R.id.aiStatusView);
                    serverCountView = findViewById(R.id.serverCountView);
                    linkIn = findViewById(R.id.linkInput);
                    controlBtn = findViewById(R.id.controlButton);
                    webContainer = findViewById(R.id.webContainer);

                    CookieManager.getInstance().setAcceptCookie(true);
                    CookieManager.getInstance().setAcceptThirdPartyCookies(null, true); 

                    if (webContainer != null) {
                        web1 = initWeb(1); web2 = initWeb(2); web3 = initWeb(3);
                        setupTripleLayout();
                        startNuclearScraping(); 
                        controlBtn.setOnClickListener(v -> toggleEngine());
                        aiStatusView.setText("🐢 Turtle Mode: Slow Scrolling Active");
                        
                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                CHECKED_HISTORY.clear();
                                mHandler.postDelayed(this, 300000); 
                            }
                        }, 300000);
                    }
                } catch (Exception e) {}
            }, 1000); 
        } catch (Exception e) {}
    }

    private void setupTripleLayout() {
        if (webContainer == null || web1 == null) return;
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
        web1.setLayoutParams(p); web2.setLayoutParams(p); web3.setLayoutParams(p);
        webContainer.addView(web1); webContainer.addView(web2); webContainer.addView(web3);
    }

    private WebView initWeb(int id) {
        WebView wv = new WebView(this);
        wv.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        WebSettings s = wv.getSettings();
        
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadsImagesAutomatically(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT); 
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        
        wv.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.proceed();
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                // بدء التمرير البطيء جداً فور اكتمال التحميل
                startSlowScrolling(v);
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError err) {
                // حتى لو فشل، انتظر ولا تعيد التحميل فوراً
            }
        });
        return wv;
    }

    // --- دالة التمرير البطيء (محاكاة القراءة) ---
    private void startSlowScrolling(WebView v) {
        final int[] scrollStep = {0};
        final int totalHeight = 5000; // افتراض طول الصفحة
        
        // خيط مستقل للتمرير
        Runnable scroller = new Runnable() {
            @Override
            public void run() {
                if (!isRunning) return;
                // تمرير بمقدار صغير جداً (2 بكسل) كل 100 ملي ثانية
                if (scrollStep[0] < totalHeight) {
                    v.scrollBy(0, 2);
                    scrollStep[0] += 2;
                    mHandler.postDelayed(this, 100); 
                }
            }
        };
        mHandler.postDelayed(scroller, 2000); // ابدأ بعد ثانيتين من التحميل
    }

    private void toggleEngine() {
        isRunning = !isRunning;
        controlBtn.setText(isRunning ? "🛑 STOP" : "🚀 START SLOW-MO");
        if (isRunning) {
            if (wakeLock != null && !wakeLock.isHeld()) wakeLock.acquire();
            // بدء التشغيل بتتابع زمني
            runSingleBot(web1, 1);
            mHandler.postDelayed(() -> runSingleBot(web2, 2), 10000); // 10 ثواني فرق
            mHandler.postDelayed(() -> runSingleBot(web3, 3), 20000); // 20 ثانية فرق
        } else {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
            mHandler.removeCallbacksAndMessages(null);
            isWeb1Busy = false; isWeb2Busy = false; isWeb3Busy = false;
        }
    }

    private void runSingleBot(WebView wv, int id) {
        if (!isRunning || wv == null) return;
        
        // منع التداخل: إذا كان البوت يعمل حالياً، لا تقاطعه
        if (id == 1 && isWeb1Busy) return;
        if (id == 2 && isWeb2Busy) return;
        if (id == 3 && isWeb3Busy) return;

        // وضع علامة "مشغول"
        setBusyState(id, true);

        if (PROXY_POOL.isEmpty()) {
            aiStatusView.setText("⏳ Waiting IPs...");
            setBusyState(id, false);
            mHandler.postDelayed(() -> runSingleBot(wv, id), 5000);
            return;
        }
        
        String proxy;
        try { 
            proxy = PROXY_POOL.get(0); 
            PROXY_POOL.remove(0);
            PROXY_POOL.add(proxy); // تدوير
        } catch (Exception e) { 
            setBusyState(id, false); 
            return; 
        }

        if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            try {
                ProxyConfig proxyConfig = new ProxyConfig.Builder().addProxyRule(proxy).build();
                ProxyController.getInstance().setProxyOverride(proxyConfig, r -> {}, () -> {});
            } catch (Exception e) {}
        }

        CookieManager.getInstance().removeAllCookies(null);
        wv.getSettings().setUserAgentString("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");

        String url = linkIn.getText().toString().trim();
        if(url.isEmpty()) url = "https://www.google.com";
        Map<String, String> headers = new HashMap<>();
        headers.put("Referer", "https://www.google.com/");
        
        wv.loadUrl(url, headers);
        totalJumps++;
        updateUI();
        
        // --- هنا السر: المؤقت الصارم ---
        // لن يتم استدعاء البوت مرة أخرى إلا بعد 35 ثانية، مهما حدث
        mHandler.postDelayed(() -> {
            setBusyState(id, false); // تحرير البوت
            runSingleBot(wv, id);    // الانتقال للتالي
        }, 35000); // 35000 ملي ثانية = 35 ثانية وقت بقاء
    }

    private void setBusyState(int id, boolean state) {
        if (id == 1) isWeb1Busy = state;
        else if (id == 2) isWeb2Busy = state;
        else isWeb3Busy = state;
    }

    private void updateUI() {
        mHandler.post(() -> {
            serverCountView.setText("🔋 Pool: " + PROXY_POOL.size());
            dashView.setText("💰 Visits: " + totalJumps);
        });
    }

    private void startNuclearScraping() {
        String[] sources = {
            "https://api.proxyscrape.com/v2/?request=getproxies&protocol=http&timeout=10000&country=all&ssl=all&anonymity=all",
            "https://raw.githubusercontent.com/TheSpeedX/SOCKS-List/master/http.txt",
            "https://raw.githubusercontent.com/monosans/proxy-list/main/proxies/http.txt",
            "https://raw.githubusercontent.com/hookzof/socks5_list/master/proxy.txt",
            "https://raw.githubusercontent.com/prxchk/proxy-list/main/http.txt",
            "https://www.proxy-list.download/api/v1/get?type=http",
            "https://api.openproxylist.xyz/http.txt"
        };

        for (String url : sources) {
            scrapExec.execute(() -> {
                while (true) {
                    try {
                        URL u = new URL(url);
                        HttpURLConnection conn = (HttpURLConnection) u.openConnection();
                        conn.setConnectTimeout(15000);
                        BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                        String l;
                        while ((l = r.readLine()) != null) { 
                            String clean = l.trim();
                            if (clean.contains(":") && !CHECKED_HISTORY.contains(clean)) { 
                                CHECKED_HISTORY.add(clean);
                                validateProxy(clean); 
                            }
                        }
                        r.close();
                        Thread.sleep(60000); 
                    } catch (Exception e) {}
                }
            });
        }
    }

    private void validateProxy(String a) {
        validExec.execute(() -> {
            if (BLACKLIST.contains(a)) return;
            try {
                String[] p = a.split(":");
                Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(p[0], Integer.parseInt(p[1])));
                URL testUrl = new URL("http://www.gstatic.com/generate_204");
                HttpURLConnection c = (HttpURLConnection) testUrl.openConnection(proxy);
                c.setConnectTimeout(10000); 
                c.setReadTimeout(10000);
                c.connect();
                if (c.getResponseCode() > 0) {
                    if (!PROXY_POOL.contains(a)) {
                        PROXY_POOL.add(a);
                        updateUI();
                    }
                }
                c.disconnect();
            } catch (Exception e) {}
        });
    }
                            }
