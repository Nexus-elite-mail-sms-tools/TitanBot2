package com.titan.bot;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.net.http.SslError;
import androidx.webkit.ProxyConfig;
import androidx.webkit.ProxyController;
import androidx.webkit.WebViewFeature;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.view.WindowManager;
import android.content.Intent; // مهم
import android.net.Uri; // مهم

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
    
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private ExecutorService scrapExec = Executors.newFixedThreadPool(50); 
    private ExecutorService validExec = Executors.newFixedThreadPool(1000); 
    
    private Random rnd = new Random();
    private int totalJumps = 0;
    private boolean isRunning = false;
    
    private CopyOnWriteArrayList<String> PROXY_POOL = new CopyOnWriteArrayList<>();
    private Set<String> BLACKLIST = Collections.synchronizedSet(new HashSet<>());
    private PowerManager.WakeLock wakeLock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
            getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            );

            setContentView(R.layout.activity_main);
            startMassiveScraping(); 

            dashView = findViewById(R.id.dashboardView);
            aiStatusView = findViewById(R.id.aiStatusView);
            serverCountView = findViewById(R.id.serverCountView);
            linkIn = findViewById(R.id.linkInput);
            controlBtn = findViewById(R.id.controlButton);

            // ربط المتصفحات من XML
            web1 = findViewById(R.id.webview_1);
            web2 = findViewById(R.id.webview_2);
            web3 = findViewById(R.id.webview_3);

            if (controlBtn != null) {
                controlBtn.setOnClickListener(v -> toggleSystem());
            }

            CookieManager.getInstance().setAcceptCookie(true);
            CookieManager.getInstance().setAcceptThirdPartyCookies(null, true);
            
            // تفعيل المتصفحات مع النظام الجديد
            if(web1 != null) setupSmartWebView(web1);
            if(web2 != null) setupSmartWebView(web2);
            if(web3 != null) setupSmartWebView(web3);

            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TitanBot::V15Direct");
            
            aiStatusView.setText("🛡️ V15: DIRECT LINK MODE");

        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void setupSmartWebView(WebView wv) {
        if (wv == null) return;
        try {
            WebSettings s = wv.getSettings();
            s.setJavaScriptEnabled(true);
            s.setDomStorageEnabled(true);
            s.setDatabaseEnabled(true);
            s.setAllowFileAccess(false);
            s.setGeolocationEnabled(false);
            // منع فتح نوافذ جديدة (لحل مشكلة كروم)
            s.setSupportMultipleWindows(false); 
            s.setJavaScriptCanOpenWindowsAutomatically(false);
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            s.setLoadsImagesAutomatically(true);
            
            wv.setWebViewClient(new WebViewClient() {
                // 🔥 هذا الكود يمنع فتح كروم ويجبر الروابط داخل التطبيق 🔥
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    view.loadUrl(url);
                    return true;
                }

                @Override
                public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError err) {
                    if (req.isForMainFrame()) {
                        String currentProxy = (String) v.getTag();
                        if (currentProxy != null) BLACKLIST.add(currentProxy);
                        
                        v.loadUrl("about:blank");
                        if (isRunning) mHandler.postDelayed(() -> runSingleBot(v), 500); 
                    }
                }
                
                @Override
                public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                    handler.proceed(); 
                }

                @Override
                public void onPageFinished(WebView v, String url) {
                    if (url.equals("about:blank")) return;

                    injectStealthScripts(v);

                    if (url.contains("google.com")) {
                        // وصلنا جوجل -> نحقن الكوكيز -> ننتقل للرابط فوراً
                        injectFakeHistory(v); 
                        mHandler.postDelayed(() -> navigateToTarget(v), 1000); // تأخير ثانية واحدة فقط
                    } else {
                        // وصلنا لرابطك المباشر
                        checkBanStatus(v);
                    }
                }
            });

        } catch (Exception e) {}
    }

    private void navigateToTarget(WebView v) {
        String targetUrl = "";
        if(linkIn != null) targetUrl = linkIn.getText().toString().trim();
        // إذا لم يضع المستخدم رابط، نبقى في جوجل، وإلا نذهب للرابط
        if(!targetUrl.isEmpty()) {
            Map<String, String> headers = new HashMap<>();
            headers.put("X-Requested-With", ""); 
            headers.put("Referer", "https://www.google.com/");
            if (v != null) {
                v.loadUrl(targetUrl, headers);
                mHandler.post(() -> aiStatusView.setText("🚀 OPENING AD LINK..."));
            }
        }
    }

    private void checkBanStatus(WebView v) {
        // هنا يمكنك إضافة منطق للنقر على الإعلان إذا ظهر
        mHandler.post(() -> aiStatusView.setText("🟢 AD Loaded"));
        simulateHumanBehavior(v);
    }

    // === دوال الذكاء ===
    private void injectFakeHistory(WebView v) {
        String js = "(function() { try { localStorage.setItem('user_consent', 'true'); document.cookie = 'CONSENT=YES+US.en+202201; path=/; domain=.google.com'; } catch(e) {} })();";
        v.evaluateJavascript(js, null);
    }

    private void injectStealthScripts(WebView v) {
        String js = "(function() { try { Object.defineProperty(navigator, 'webdriver', {get: () => undefined}); Object.defineProperty(navigator, 'platform', {get: () => 'Win32'}); } catch(e) {} })();";
        v.evaluateJavascript(js, null);
    }

    private void simulateHumanBehavior(WebView v) {
        v.evaluateJavascript("(function(){" +
            "   var sc=0; var intr = setInterval(function(){ " +
            "       window.scrollBy(0, 30 + Math.random()*30); " +
            "       sc++; if(sc>50) clearInterval(intr);" +
            "   }, 400);" +
            "   setTimeout(function(){ if(document.body) document.body.click(); }, 3000);" +
            "})()", null);
    }

    private void toggleSystem() {
        isRunning = !isRunning;
        if (controlBtn != null) controlBtn.setText(isRunning ? "🛑 STOP" : "🚀 LAUNCH V15");
        
        if (isRunning) {
            if (wakeLock != null && !wakeLock.isHeld()) wakeLock.acquire();
            if (web1 != null) runSingleBot(web1);
            if (web2 != null) mHandler.postDelayed(() -> runSingleBot(web2), 2000);
            if (web3 != null) mHandler.postDelayed(() -> runSingleBot(web3), 4000);
        } else {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        }
    }

    private void runSingleBot(WebView wv) {
        if (wv == null || !isRunning) return;
        
        if (PROXY_POOL.isEmpty()) {
            mHandler.postDelayed(() -> runSingleBot(wv), 3000);
            return;
        }

        try {
            CookieManager.getInstance().removeAllCookies(null);
            WebStorage.getInstance().deleteAllData();
            wv.clearHistory();

            int index = rnd.nextInt(PROXY_POOL.size());
            String proxy = PROXY_POOL.get(index);

            if (BLACKLIST.contains(proxy)) {
                PROXY_POOL.remove(index); 
                runSingleBot(wv); 
                return;
            }

            wv.setTag(proxy);
            updateUI();

            if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
                try {
                    ProxyController.getInstance().setProxyOverride(new ProxyConfig.Builder()
                        .addProxyRule(proxy).build(), r -> {}, () -> {});
                } catch (Exception e) {}
            }
            
            if (wv.getSettings() != null) {
                String[] agents = {
                    "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36",
                    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1"
                };
                wv.getSettings().setUserAgentString(agents[rnd.nextInt(agents.length)]);
                wv.loadUrl("https://www.google.com"); 
            }
            
            totalJumps++;
            mHandler.postDelayed(() -> runSingleBot(wv), (30 + rnd.nextInt(20)) * 1000);

        } catch (Exception e) {
            mHandler.postDelayed(() -> runSingleBot(wv), 1000);
        }
    }

    private void updateUI() {
        mHandler.post(() -> {
            serverCountView.setText("🌐 Good IPs: " + PROXY_POOL.size());
            dashView.setText("💰 Hits: " + totalJumps);
        });
    }

    private void startMassiveScraping() {
        String[] sources = {
            "https://raw.githubusercontent.com/TheSpeedX/PROXY-List/master/http.txt",
            "https://raw.githubusercontent.com/monosans/proxy-list/main/proxies/http.txt",
            "https://raw.githubusercontent.com/officialputuid/KangProxy/KangProxy/http/http.txt",
            "https://raw.githubusercontent.com/roosterkid/openproxylist/main/HTTPS_RAW.txt",
            "https://raw.githubusercontent.com/clarketm/proxy-list/master/proxy-list-raw.txt",
            "https://raw.githubusercontent.com/ShiftyTR/Proxy-List/master/http.txt"
        };

        for (String url : sources) {
            scrapExec.execute(() -> {
                while (true) {
                    try {
                        if (PROXY_POOL.size() > 5000) { Thread.sleep(60000); continue; }
                        URL u = new URL(url);
                        HttpURLConnection conn = (HttpURLConnection) u.openConnection();
                        conn.setConnectTimeout(10000); 
                        BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                        String l;
                        while ((l = r.readLine()) != null) { 
                            if (l.contains(":")) validateProxy(l.trim()); 
                        }
                        r.close();
                        Thread.sleep(600000); 
                    } catch (Exception e) {
                        try { Thread.sleep(30000); } catch (Exception ex) {}
                    }
                }
            });
        }
    }

    private void validateProxy(String a) {
        if (BLACKLIST.contains(a)) return;
        validExec.execute(() -> {
            try {
                String[] p = a.split(":");
                HttpURLConnection c = (HttpURLConnection) new URL("https://www.google.com").openConnection(
                    new Proxy(Proxy.Type.HTTP, new InetSocketAddress(p[0], Integer.parseInt(p[1])))
                );
                c.setConnectTimeout(15000); 
                c.setReadTimeout(15000);
                if (c.getResponseCode() == 200) {
                    if (!PROXY_POOL.contains(a) && !BLACKLIST.contains(a)) {
                        PROXY_POOL.add(a);
                        updateUI();
                    }
                }
            } catch (Exception e) {}
        });
    }
        }
