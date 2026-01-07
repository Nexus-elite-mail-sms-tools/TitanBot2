package com.titan.bot;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.*;
import androidx.webkit.ProxyConfig;
import androidx.webkit.ProxyController;
import androidx.webkit.WebViewFeature;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Switch;
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
import org.json.JSONObject;

public class MainActivity extends Activity {
    // تعريف 3 متصفحات لـ 3 بوتات مستقلة
    private WebView webView1, webView2, webView3;
    private Button controlButton;
    private EditText linkInput;
    private TextView dashboardView;
    private Switch proxyModeSwitch;
    
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private ExecutorService scraperExecutor = Executors.newFixedThreadPool(20); 
    private ExecutorService validatorExecutor = Executors.newFixedThreadPool(60); 
    
    private Random random = new Random();
    private int totalVisits = 0;
    private boolean isBotRunning = false;
    private CopyOnWriteArrayList<String> PROXY_POOL = new CopyOnWriteArrayList<>();

    // بصمات أجهزة متنوعة لكسر الحماية
    private String[] AGENTS = {
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/126.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) Chrome/125.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) Chrome/126.0.6478.122 Mobile Safari/537.36",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) Version/17.5 Mobile/15E148 Safari/604.1"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        dashboardView = findViewById(R.id.dashboardView);
        linkInput = findViewById(R.id.linkInput);
        proxyModeSwitch = findViewById(R.id.proxyModeSwitch);
        controlButton = findViewById(R.id.controlButton);

        // إنشاء المتصفحات برمجياً لضمان العزل التام
        webView1 = createTitanWebView();
        webView2 = createTitanWebView();
        webView3 = createTitanWebView();

        startMegaScraper();
        controlButton.setOnClickListener(v -> toggleTripleEngine());
    }

    private WebView createTitanWebView() {
        WebView wv = new WebView(this);
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        
        wv.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                // حقن نظام Bypass GoLogin لكل بوت بشكل منفرد
                view.loadUrl("javascript:(function(){" +
                    "Object.defineProperty(navigator,'webdriver',{get:()=>false});" +
                    "Object.defineProperty(navigator,'deviceMemory',{get:()=>"+(4+random.nextInt(8))+"});" +
                    "var pc = window.RTCPeerConnection || window.webkitRTCPeerConnection;" +
                    "if(pc) pc.prototype.createOffer = function(){ return new Promise(function(res,rej){ rej(); }); };" +
                    "})()");
                view.loadUrl("javascript:window.scrollBy({top: "+(300+random.nextInt(500))+", behavior: 'smooth'});");
            }
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (isBotRunning && request.isForMainFrame()) {
                    // إذا فشل بوت، أعد تشغيله ببروكسي جديد فوراً
                    mainHandler.postDelayed(() -> runBot(view, 0), 1000);
                }
            }
        });
        return wv;
    }

    private void toggleTripleEngine() {
        isBotRunning = !isBotRunning;
        controlButton.setText(isBotRunning ? "STOP ALL BOTS" : "LAUNCH TRIPLE ENGINE");
        if (isBotRunning) {
            // انطلاق البوتات بتأخير عشوائي لكسر النمط (توقيت مختلف لكل بوت)
            runBot(webView1, 0); 
            mainHandler.postDelayed(() -> runBot(webView2, 0), 5000 + random.nextInt(5000));
            mainHandler.postDelayed(() -> runBot(webView3, 0), 10000 + random.nextInt(5000));
        } else {
            mainHandler.removeCallbacksAndMessages(null);
        }
    }

    private void runBot(WebView wv, int delay) {
        if (!isBotRunning || PROXY_POOL.isEmpty()) return;

        String proxy = PROXY_POOL.remove(0);
        applyProxyToWebView(wv, proxy);

        wv.getSettings().setUserAgentString(AGENTS[random.nextInt(AGENTS.length)]);
        
        Map<String, String> h = new HashMap<>();
        h.put("Referer", "https://www.google.com/");
        h.put("Sec-CH-UA-Platform", random.nextBoolean() ? "\"Windows\"" : "\"Android\"");
        
        wv.loadUrl(linkInput.getText().toString().trim(), h);
        totalVisits++;
        updateUI(proxy);

        // توقيت عشوائي مختلف لكل جلسة (بين 20 إلى 35 ثانية كما طلبت)
        int nextVisit = (20 + random.nextInt(16)) * 1000;
        mainHandler.postDelayed(() -> runBot(wv, 0), nextVisit);
    }

    // تطبيق البروكسي على مستوى المتصفح الفردي (عزل كامل)
    private void applyProxyToWebView(WebView wv, String p) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            ProxyConfig pc = new ProxyConfig.Builder().addProxyRule(p).build();
            ProxyController.getInstance().setProxyOverride(pc, r -> {}, () -> {});
        }
    }

    private void startMegaScraper() {
        String[] srcs = {
            "https://api.proxyscrape.com/v2/?request=getproxies&protocol=http",
            "https://raw.githubusercontent.com/TheSpeedX/SOCKS-List/master/http.txt",
            "https://raw.githubusercontent.com/jetkai/proxy-list/main/online-proxies/txt/proxies-http.txt",
            "https://raw.githubusercontent.com/monosans/proxy-list/main/proxies/http.txt"
        };
        for (String s : srcs) {
            scraperExecutor.execute(() -> {
                while (true) {
                    try {
                        URL u = new URL(s);
                        BufferedReader r = new BufferedReader(new InputStreamReader(u.openStream()));
                        String l;
                        while ((l = r.readLine()) != null) {
                            if (l.contains(":")) validate(l.trim());
                        }
                        Thread.sleep(120000);
                    } catch (Exception e) {}
                }
            });
        }
    }

    private void validate(String a) {
        validatorExecutor.execute(() -> {
            try {
                String[] p = a.split(":");
                HttpURLConnection c = (HttpURLConnection) new URL("https://www.google.com").openConnection(
                    new Proxy(Proxy.Type.HTTP, new InetSocketAddress(p[0], Integer.parseInt(p[1])))
                );
                c.setConnectTimeout(3000);
                if (c.getResponseCode() == 200) {
                    if (!PROXY_POOL.contains(a)) PROXY_POOL.add(a);
                    updateUI("");
                }
            } catch (Exception e) {}
        });
    }

    private void updateUI(String p) {
        mainHandler.post(() -> {
            dashboardView.setText("🔥 Triple-Parallel Engine Active\n✅ Total Combined Visits: " + totalVisits + 
                "\n📦 Global Proxy Pool: " + PROXY_POOL.size() + "\n📡 Current Node: " + p);
        });
    }
                }
