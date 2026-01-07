package com.titan.bot;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
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
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
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
    private ExecutorService scrapExec = Executors.newFixedThreadPool(200); 
    private ExecutorService validExec = Executors.newFixedThreadPool(300); // قللنا العدد للتركيز على الجودة
    
    private Random rnd = new Random();
    private int totalJumps = 0;
    private boolean isRunning = false;
    
    private CopyOnWriteArrayList<String> BLACKLIST = new CopyOnWriteArrayList<>();
    private CopyOnWriteArrayList<String> PROXY_POOL = new CopyOnWriteArrayList<>();
    
    private PowerManager.WakeLock wakeLock;
    private String currentProxy1 = "", currentProxy2 = "", currentProxy3 = "";

    // مؤقتات لإعادة التحميل إذا علق الموقع
    private Runnable timeoutRunnable1, timeoutRunnable2, timeoutRunnable3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_main);
            
            mHandler.postDelayed(() -> {
                try {
                    PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                    wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TitanBot::Turbo");

                    dashView = findViewById(R.id.dashboardView);
                    aiStatusView = findViewById(R.id.aiStatusView);
                    serverCountView = findViewById(R.id.serverCountView);
                    linkIn = findViewById(R.id.linkInput);
                    controlBtn = findViewById(R.id.controlButton);
                    webContainer = findViewById(R.id.webContainer);

                    CookieManager.getInstance().setAcceptCookie(true);
                    CookieManager.getInstance().removeAllCookies(null);

                    if (webContainer != null) {
                        web1 = initWeb(1); web2 = initWeb(2); web3 = initWeb(3);
                        setupTripleLayout();
                        startMegaScraping(); 
                        controlBtn.setOnClickListener(v -> toggleEngine());
                        aiStatusView.setText("🚀 AI Turbo: Filtering High-Speed Nodes...");
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

    public class WebAppInterface {
        Context mContext;
        int webId;
        WebAppInterface(Context c, int id) { mContext = c; webId = id; }

        @JavascriptInterface
        public void reportBadProxy(String reason) {
            mHandler.post(() -> handleBadProxy(webId, reason));
        }
    }

    private void handleBadProxy(int id, String reason) {
        String badProxy = (id == 1) ? currentProxy1 : (id == 2) ? currentProxy2 : currentProxy3;
        if (!badProxy.isEmpty() && !BLACKLIST.contains(badProxy)) {
            BLACKLIST.add(badProxy);
            PROXY_POOL.remove(badProxy);
            aiStatusView.setText("⚡ Kill Switch: " + badProxy + " [" + reason + "]");
            updateUI();
        }
        // إعادة التشغيل فوراً
        WebView wv = (id == 1) ? web1 : (id == 2) ? web2 : web3;
        if(wv != null) runSingleBot(wv, id);
    }

    private WebView initWeb(int id) {
        WebView wv = new WebView(this);
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true); // ضروري للإعلانات
        s.setDatabaseEnabled(true);
        s.setLoadsImagesAutomatically(true);
        s.setBlockNetworkImage(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        
        wv.addJavascriptInterface(new WebAppInterface(this, id), "TitanGuard");

        wv.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                // ضبط مؤقت: إذا لم تحمل الصفحة خلال 20 ثانية، اعتبر البروكسي ميتاً
                Runnable timeoutTask = () -> handleBadProxy(id, "Timeout");
                if (id == 1) timeoutRunnable1 = timeoutTask;
                else if (id == 2) timeoutRunnable2 = timeoutTask;
                else timeoutRunnable3 = timeoutTask;
                
                mHandler.postDelayed(timeoutTask, 20000);
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                // إلغاء المؤقت لأن الصفحة حملت بنجاح
                if (id == 1) mHandler.removeCallbacks(timeoutRunnable1);
                else if (id == 2) mHandler.removeCallbacks(timeoutRunnable2);
                else mHandler.removeCallbacks(timeoutRunnable3);

                // فحص النصوص المحظورة
                v.evaluateJavascript(
                    "javascript:(function() {" +
                    "  var text = document.body.innerText;" +
                    "  if(text.includes('Anonymous Proxy') || text.includes('Access Denied')) {" +
                    "     window.TitanGuard.reportBadProxy('Blocked Content');" +
                    "  }" +
                    "})()", null);
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError err) {
                if (isRunning && req.isForMainFrame()) {
                    // أي خطأ في الصفحة الرئيسية = حظر فوري للبروكسي
                    handleBadProxy(id, "Net Error: " + err.getErrorCode());
                }
            }
        });
        return wv;
    }

    private void toggleEngine() {
        isRunning = !isRunning;
        controlBtn.setText(isRunning ? "🛑 STOP TURBO" : "🚀 START TURBO");
        if (isRunning) {
            if (wakeLock != null && !wakeLock.isHeld()) wakeLock.acquire();
            runSingleBot(web1, 1);
            mHandler.postDelayed(() -> runSingleBot(web2, 2), 2000);
            mHandler.postDelayed(() -> runSingleBot(web3, 3), 4000);
        } else {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
            mHandler.removeCallbacksAndMessages(null);
        }
    }

    private void runSingleBot(WebView wv, int id) {
        if (!isRunning || wv == null) return;
        
        if (PROXY_POOL.isEmpty()) {
            mHandler.postDelayed(() -> runSingleBot(wv, id), 2000);
            return;
        }

        // اختيار أفضل بروكسي (الأحدث في القائمة)
        String proxy;
        try {
            proxy = PROXY_POOL.get(rnd.nextInt(Math.min(PROXY_POOL.size(), 50))); 
        } catch (Exception e) { proxy = PROXY_POOL.get(0); }

        if (id == 1) currentProxy1 = proxy;
        else if (id == 2) currentProxy2 = proxy;
        else currentProxy3 = proxy;

        updateUI();

        if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            try {
                ProxyConfig proxyConfig = new ProxyConfig.Builder()
                    .addProxyRule(proxy)
                    .build();
                ProxyController.getInstance().setProxyOverride(proxyConfig, r -> {}, () -> {});
            } catch (Exception e) {
                runSingleBot(wv, id); return;
            }
        }

        wv.clearHistory();
        wv.clearCache(true); // تنظيف الكاش مهم جداً لظهور الإعلانات
        CookieManager.getInstance().removeAllCookies(null);

        // User-Agent حديث جداً
        wv.getSettings().setUserAgentString("Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");

        String url = linkIn.getText().toString().trim();
        if(url.isEmpty()) url = "https://www.google.com";

        Map<String, String> headers = new HashMap<>();
        headers.put("Referer", "https://www.google.com/");
        
        wv.loadUrl(url, headers);
        totalJumps++;
        
        // الانتقال للبروكسي التالي بعد 35 ثانية
        mHandler.postDelayed(() -> runSingleBot(wv, id), 35000);
    }

    private void updateUI() {
        mHandler.post(() -> {
            serverCountView.setText("🚀 Fast IPs: " + PROXY_POOL.size() + " | ☠️ Banned: " + BLACKLIST.size());
            dashView.setText("💰 Visits: " + totalJumps);
        });
    }

    private void startMegaScraping() {
        String[] sources = {
            "https://api.proxyscrape.com/v2/?request=getproxies&protocol=http&timeout=2000&country=all&ssl=all&anonymity=elite", 
            "https://raw.githubusercontent.com/TheSpeedX/SOCKS-List/master/http.txt", 
            "https://raw.githubusercontent.com/ShiftyTR/Proxy-List/master/http.txt"
        };
        for (String url : sources) {
            scrapExec.execute(() -> {
                while (true) {
                    try {
                        URL u = new URL(url);
                        BufferedReader r = new BufferedReader(new InputStreamReader(u.openStream()));
                        String l;
                        while ((l = r.readLine()) != null) { 
                            if (l.contains(":") && !BLACKLIST.contains(l.trim())) validateFastProxy(l.trim()); 
                        }
                        Thread.sleep(30000); 
                    } catch (Exception e) {}
                }
            });
        }
    }

    // دالة الفحص المحدثة: تقيس السرعة وترفض البطيء
    private void validateFastProxy(String a) {
        validExec.execute(() -> {
            if (BLACKLIST.contains(a)) return;

            try {
                String[] p = a.split(":");
                Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(p[0], Integer.parseInt(p[1])));
                
                // استخدام رابط Google الخفيف لقياس السرعة
                URL testUrl = new URL("http://www.gstatic.com/generate_204");
                
                long startTime = System.currentTimeMillis(); // بداية العداد
                
                HttpURLConnection c = (HttpURLConnection) testUrl.openConnection(proxy);
                c.setConnectTimeout(3000); // 3 ثواني كحد أقصى للاتصال
                c.setReadTimeout(3000);
                
                c.connect();
                int responseCode = c.getResponseCode();
                
                long endTime = System.currentTimeMillis(); // نهاية العداد
                long duration = endTime - startTime; // الزمن المستغرق

                // الشروط: الاستجابة 204 (ناجح) + السرعة أقل من 2500 ميلي ثانية (2.5 ثانية)
                if (responseCode == 204 && duration < 2500) {
                    if (!PROXY_POOL.contains(a)) {
                        PROXY_POOL.add(a);
                        updateUI();
                    }
                } else {
                    // إذا كان بطيئاً جداً لا نضيفه للقائمة
                }
                c.disconnect();
            } catch (Exception e) {
                // فشل الاتصال = تجاهل
            }
        });
    }
}
