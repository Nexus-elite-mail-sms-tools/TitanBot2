package com.titan.bot;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
// === استيرادات هامة جداً لمنع أخطاء البناء ===
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.net.http.SslError; // إصلاح خطأ Gradle
import androidx.webkit.ProxyConfig;
import androidx.webkit.ProxyController;
import androidx.webkit.WebViewFeature;
// ==========================================
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.ViewGroup;
import android.widget.Toast;
import android.view.WindowManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    // === تعريف العناصر ===
    private WebView web1, web2, web3;
    private Button controlBtn;
    private EditText linkIn;
    private TextView dashView, aiStatusView, serverCountView;
    private LinearLayout webContainer;
    
    // === المحرك الخلفي ===
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private ExecutorService scrapExec = Executors.newFixedThreadPool(40); // زيادة الطاقة
    private ExecutorService validExec = Executors.newFixedThreadPool(1200); 
    
    private Random rnd = new Random();
    private int totalJumps = 0;
    private boolean isRunning = false;
    private CopyOnWriteArrayList<String> PROXY_POOL = new CopyOnWriteArrayList<>();
    private PowerManager.WakeLock wakeLock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
            // تفعيل تسريع الهاردوير
            getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            );

            setContentView(R.layout.activity_main);
            
            // تشغيل الوحش فوراً
            startMegaScraping(); 

            dashView = findViewById(R.id.dashboardView);
            aiStatusView = findViewById(R.id.aiStatusView);
            serverCountView = findViewById(R.id.serverCountView);
            linkIn = findViewById(R.id.linkInput);
            controlBtn = findViewById(R.id.controlButton);
            webContainer = findViewById(R.id.webContainer);

            if (controlBtn != null) {
                controlBtn.setOnClickListener(v -> toggleSystem());
            }

            // تأخير بسيط لضمان استقرار النظام
            mHandler.postDelayed(this::forceInitWebViews, 800);

            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TitanBot::V10Atomic");

        } catch (Exception e) {
            Toast.makeText(this, "Ui Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void forceInitWebViews() {
        try {
            if (webContainer != null) {
                CookieManager.getInstance().setAcceptCookie(true);
                CookieManager.getInstance().setAcceptThirdPartyCookies(null, true);
                
                web1 = createSafeWebView();
                web2 = createSafeWebView();
                web3 = createSafeWebView();
                
                aiStatusView.setText("🛡️ V10 SYSTEM: ONLINE");
            }
        } catch (Exception e) {
            aiStatusView.setText("Init Error: " + e.getMessage());
        }
    }

    private WebView createSafeWebView() {
        try {
            // 🔥 التغيير الجذري: استخدام getApplicationContext() بدلاً من this
            // هذا يحل مشكلة الانهيار في الهواتف العنيدة
            WebView wv = new WebView(getApplicationContext());
            
            if (wv != null) {
                WebSettings s = wv.getSettings();
                s.setJavaScriptEnabled(true);
                s.setDomStorageEnabled(true);
                s.setDatabaseEnabled(true);
                s.setAllowFileAccess(false);
                s.setGeolocationEnabled(false);
                s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
                s.setLoadsImagesAutomatically(true);
                
                wv.setWebViewClient(new WebViewClient() {
                    Runnable timeoutRunnable = () -> {
                        if (wv != null) {
                            mHandler.post(() -> aiStatusView.setText("⏳ Timeout -> Resetting..."));
                            wv.stopLoading();
                            handleFailure(wv, "Timeout");
                        }
                    };

                    @Override
                    public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                        mHandler.removeCallbacks(timeoutRunnable);
                        mHandler.postDelayed(timeoutRunnable, 30000); 
                    }

                    @Override
                    public void onPageFinished(WebView v, String url) {
                        mHandler.removeCallbacks(timeoutRunnable);
                        if (url.equals("about:blank")) return;

                        injectStealthScripts(v);

                        if (url.contains("google.com") || url.contains("bing.com")) {
                            injectFakeHistory(v); 
                            mHandler.postDelayed(() -> {
                                 String targetUrl = "";
                                 if(linkIn != null) targetUrl = linkIn.getText().toString().trim();
                                 if(targetUrl.isEmpty()) targetUrl = "https://www.google.com";
                                 
                                 Map<String, String> headers = new HashMap<>();
                                 headers.put("X-Requested-With", ""); 
                                 headers.put("Referer", "https://www.google.com/");
                                 
                                 if (v != null) v.loadUrl(targetUrl, headers);
                                 mHandler.post(() -> aiStatusView.setText("🚀 Moved to Target"));
                            }, 4000); 
                        } else {
                            checkBanStatus(v, url);
                        }
                    }

                    @Override
                    public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError err) {
                        if (req.isForMainFrame()) {
                            mHandler.removeCallbacks(timeoutRunnable);
                            v.loadUrl("about:blank");
                            handleFailure(v, "Conn Error");
                        }
                    }
                    
                    @Override
                    public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                        handler.proceed(); // تجاوز أخطاء SSL
                    }
                });

                // إضافة المتصفح للشاشة
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
                wv.setLayoutParams(p);
                webContainer.addView(wv);
                return wv;
            }
        } catch (Exception e) {
            // تجاهل الخطأ بصمت
        }
        return null;
    }

    // === دوال الذكاء ===
    private void injectFakeHistory(WebView v) {
        String js = "(function() { try { localStorage.setItem('user_consent', 'true'); document.cookie = 'CONSENT=YES+US.en+202201; path=/; domain=.google.com'; } catch(e) {} })();";
        v.evaluateJavascript(js, null);
    }

    private void injectStealthScripts(WebView v) {
        String js = 
            "(function() {" +
            "   try {" +
            "       Object.defineProperty(navigator, 'webdriver', {get: () => undefined});" +
            "       Object.defineProperty(navigator, 'platform', {get: () => 'Win32'});" +
            "       var getParameter = WebGLRenderingContext.prototype.getParameter;" +
            "       WebGLRenderingContext.prototype.getParameter = function(parameter) {" +
            "           if (parameter === 37445) return 'Intel Inc.';" + 
            "           if (parameter === 37446) return 'Intel(R) Iris(TM) Plus Graphics 640';" + 
            "           return getParameter(parameter);" +
            "       };" +
            "   } catch(e) {}" +
            "})();";
        v.evaluateJavascript(js, null);
    }

    private void checkBanStatus(WebView v, String url) {
        v.evaluateJavascript(
            "(function() { " +
            "   var content = document.body.innerText.toLowerCase(); " +
            "   if (content.includes('anonymous proxy') || content.includes('access denied')) { " +
            "       return 'BLOCKED';" +
            "   } else { " +
            "       return 'OK';" +
            "   } " +
            "})();",
            value -> {
                if (value != null && value.contains("BLOCKED")) {
                    handleFailure(v, "Banned"); 
                } else {
                    if (v != null) v.setTag(0); 
                    simulateHumanBehavior(v);
                    mHandler.post(() -> aiStatusView.setText("🟢 Success: " + url));
                }
            }
        );
    }

    private void handleFailure(WebView v, String reason) {
        if (v == null) return;
        mHandler.post(() -> aiStatusView.setText("⛔ " + reason + " -> Skipping..."));
        v.stopLoading();
        v.loadUrl("about:blank");
        mHandler.postDelayed(() -> runSingleBot(v), 1000);
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
        if (controlBtn != null) controlBtn.setText(isRunning ? "🛑 STOP" : "🚀 LAUNCH ZENITH V10");
        
        if (isRunning) {
            if (wakeLock != null && !wakeLock.isHeld()) wakeLock.acquire();
            
            boolean atLeastOneRunning = false;
            if (web1 != null) { runSingleBot(web1); atLeastOneRunning = true; }
            if (web2 != null) { mHandler.postDelayed(() -> runSingleBot(web2), 2000); atLeastOneRunning = true; }
            if (web3 != null) { mHandler.postDelayed(() -> runSingleBot(web3), 4000); atLeastOneRunning = true; }
            
            if (!atLeastOneRunning) {
                Toast.makeText(this, "Warning: WebViews Failed - Proxy Mode Only", Toast.LENGTH_SHORT).show();
            }
        } else {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        }
    }

    private void runSingleBot(WebView wv) {
        if (wv == null) return;
        
        wv.setTag(0);

        if (!isRunning || PROXY_POOL.isEmpty()) {
            if (isRunning) mHandler.postDelayed(() -> runSingleBot(wv), 3000);
            return;
        }

        try {
            CookieManager.getInstance().removeAllCookies(null);
            WebStorage.getInstance().deleteAllData();
            wv.clearCache(true);
            wv.clearHistory();

            String proxy = PROXY_POOL.remove(0);
            updateUI();

            if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
                try {
                    ProxyController.getInstance().setProxyOverride(new ProxyConfig.Builder()
                        .addProxyRule(proxy).build(), r -> {}, () -> {});
                } catch (Exception e) {}
            }

            String[] agents = {
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Safari/605.1.15"
            };
            
            if (wv != null && wv.getSettings() != null) {
                wv.getSettings().setUserAgentString(agents[rnd.nextInt(agents.length)]);
                wv.loadUrl("https://www.google.com"); 
            }
            
            totalJumps++;
            mHandler.postDelayed(() -> runSingleBot(wv), (30 + rnd.nextInt(20)) * 1000);

        } catch (Exception e) {
            mHandler.postDelayed(() -> runSingleBot(wv), 2000);
        }
    }

    private void updateUI() {
        mHandler.post(() -> {
            serverCountView.setText("🌐 Proxies: " + PROXY_POOL.size());
            dashView.setText("💰 Jumps: " + totalJumps);
        });
    }

    private void startMegaScraping() {
        String[] sources = {
            "https://api.proxyscrape.com/v2/?request=getproxies&protocol=http&timeout=3000&country=all&ssl=all&anonymity=all",
            "https://raw.githubusercontent.com/TheSpeedX/PROXY-List/master/http.txt",
            "https://raw.githubusercontent.com/monosans/proxy-list/main/proxies/http.txt",
            "https://raw.githubusercontent.com/officialputuid/KangProxy/KangProxy/http/http.txt",
            "https://raw.githubusercontent.com/roosterkid/openproxylist/main/HTTPS_RAW.txt",
            "https://raw.githubusercontent.com/clarketm/proxy-list/master/proxy-list-raw.txt",
            "https://raw.githubusercontent.com/ShiftyTR/Proxy-List/master/http.txt",
            "https://raw.githubusercontent.com/sunny9577/proxy-scraper/master/proxies.txt",
            "https://raw.githubusercontent.com/almroot/proxylist/master/list.txt",
            "https://raw.githubusercontent.com/opsxcq/proxy-list/master/list.txt",
            "https://raw.githubusercontent.com/proxy4parsing/proxy-list/main/http.txt",
            "https://raw.githubusercontent.com/mmpx12/proxy-list/master/http.txt",
            "https://raw.githubusercontent.com/vakhov/fresh-proxy-list/master/http.txt",
            "https://raw.githubusercontent.com/mertguvencli/http-proxy-list/main/proxy-list/data.txt",
            "https://raw.githubusercontent.com/hendrikbgr/Free-Proxy-Repo/master/proxy_list.txt",
            "https://raw.githubusercontent.com/jetkai/proxy-list/main/online-proxies/txt/proxies-http.txt",
            "https://raw.githubusercontent.com/asimo17/proxy-list/master/proxies.txt",
            "https://raw.githubusercontent.com/B4RC0DE-TM/proxy-list/main/HTTP.txt",
            "https://raw.githubusercontent.com/saisuiu/Lionkings-Http-Proxys-Proxies/main/free.txt"
        };

        for (String url : sources) {
            scrapExec.execute(() -> {
                while (true) {
                    try {
                        if (PROXY_POOL.size() > 8000) { Thread.sleep(20000); continue; }
                        URL u = new URL(url);
                        HttpURLConnection conn = (HttpURLConnection) u.openConnection();
                        conn.setConnectTimeout(8000); 
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
        validExec.execute(() -> {
            try {
                String[] p = a.split(":");
                HttpURLConnection c = (HttpURLConnection) new URL("https://www.google.com").openConnection(
                    new Proxy(Proxy.Type.HTTP, new InetSocketAddress(p[0], Integer.parseInt(p[1])))
                );
                c.setConnectTimeout(5000); 
                c.setReadTimeout(5000);
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
                if (c.getResponseCode() == 200) {
                    if (!PROXY_POOL.contains(a)) {
                        PROXY_POOL.add(a);
                        updateUI();
                    }
                }
            } catch (Exception e) {}
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isRunning = false;
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        scrapExec.shutdownNow();
        validExec.shutdownNow();
    }
                                                                   }
