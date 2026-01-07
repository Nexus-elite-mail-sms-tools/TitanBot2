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
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.ViewGroup;
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
    private WebView web1, web2, web3;
    private Button controlBtn;
    private EditText linkIn;
    private TextView dashView, aiStatusView, serverCountView;
    private LinearLayout webContainer;
    
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private ExecutorService scrapExec = Executors.newFixedThreadPool(60); 
    private ExecutorService validExec = Executors.newFixedThreadPool(250); // رفع كفاءة الفحص
    private ExecutorService aiExec = Executors.newSingleThreadExecutor();
    
    private Random rnd = new Random();
    private int totalJumps = 0;
    private boolean isRunning = false;
    private CopyOnWriteArrayList<String> PROXY_POOL = new CopyOnWriteArrayList<>();

    // متغيرات ميزة "تبديل بصمة الجهاز"
    private int deviceSwitchCounter = 0;
    private String currentFakeHardware = "8"; // الرام الافتراضي

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().setFlags(16777216, 16777216); 

        dashView = findViewById(R.id.dashboardView);
        aiStatusView = findViewById(R.id.aiStatusView);
        serverCountView = findViewById(R.id.serverCountView);
        linkIn = findViewById(R.id.linkInput);
        controlBtn = findViewById(R.id.controlButton);
        webContainer = findViewById(R.id.webContainer);

        web1 = initWeb(); web2 = initWeb(); web3 = initWeb();
        setupTripleLayout();
        
        startTurboScraping(); 
        controlBtn.setOnClickListener(v -> toggleZenithEngine());
    }

    private void setupTripleLayout() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
        p.setMargins(2, 2, 2, 2);
        web1.setLayoutParams(p); web2.setLayoutParams(p); web3.setLayoutParams(p);
        webContainer.addView(web1); webContainer.addView(web2); webContainer.addView(web3);
    }

    private WebView initWeb() {
        WebView wv = new WebView(this);
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadsImagesAutomatically(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        
        wv.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView v, String url) {
                // حقن البصمة المتغيرة للأجهزة واللمس البشري
                v.loadUrl("javascript:(function(){" +
                    "Object.defineProperty(navigator,'webdriver',{get:()=>false});" +
                    "Object.defineProperty(navigator,'deviceMemory',{get:()=>"+currentFakeHardware+"});" +
                    "Object.defineProperty(navigator,'hardwareConcurrency',{get:()=>"+(rnd.nextInt(8)+4)+"});" +
                    "window.scrollTo(0, "+rnd.nextInt(500)+");" +
                    "setInterval(function(){ " +
                    "   window.scrollBy(0, "+(rnd.nextBoolean() ? 70 : -20)+");" +
                    "}, 5000);" +
                    "})()");

                v.evaluateJavascript("document.body.innerText.includes('Anonymous Proxy')", value -> {
                    if (Boolean.parseBoolean(value)) mHandler.post(() -> runSingleBot(v));
                });
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError err) {
                if (isRunning && req.isForMainFrame()) mHandler.post(() -> runSingleBot(v));
            }
        });
        return wv;
    }

    private void toggleZenithEngine() {
        isRunning = !isRunning;
        controlBtn.setText(isRunning ? "🛑 STOP ZENITH" : "🚀 LAUNCH ELITE V4");
        if (isRunning) {
            runAISensor(linkIn.getText().toString());
            runSingleBot(web1);
            mHandler.postDelayed(() -> runSingleBot(web2), 6000);
            mHandler.postDelayed(() -> runSingleBot(web3), 12000);
        }
    }

    private void runSingleBot(WebView wv) {
        if (!isRunning || PROXY_POOL.isEmpty()) return;

        // ميزة تبديل البصمة كل 10 زيارات
        deviceSwitchCounter++;
        if (deviceSwitchCounter >= 10) {
            currentFakeHardware = String.valueOf(rnd.nextBoolean() ? 4 : 12); // تبديل الرام عشوائياً
            deviceSwitchCounter = 0;
            mHandler.post(() -> aiStatusView.setText("🤖 AI: Hardware Identity Rotated"));
        }

        String proxy = PROXY_POOL.remove(0);
        updateUI();

        if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            ProxyController.getInstance().setProxyOverride(new ProxyConfig.Builder().addProxyRule(proxy).build(), r -> {}, () -> {});
        }

        // بصمات أجهزة متغيرة باستمرار
        String[] mobileModels = {
            "Mozilla/5.0 (Linux; Android 14; Pixel 8 Build/UD1A.230805.019) Chrome/126.0.0.0 Mobile Safari/537.36",
            "Mozilla/5.0 (Linux; Android 13; SM-G998B Build/TP1A.220624.014) Chrome/125.0.0.0 Mobile Safari/537.36",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) Version/17.5 Mobile/15E148 Safari/604.1"
        };
        wv.getSettings().setUserAgentString(mobileModels[rnd.nextInt(mobileModels.length)]);
        
        Map<String, String> h = new HashMap<>();
        h.put("X-Requested-With", "com.android.chrome");
        h.put("Sec-Fetch-Site", "cross-site");
        
        wv.loadUrl(linkIn.getText().toString().trim(), h);
        totalJumps++;
        
        mHandler.postDelayed(() -> runSingleBot(wv), (35 + rnd.nextInt(30)) * 1000);
    }

    private void updateUI() {
        mHandler.post(() -> {
            serverCountView.setText("🌐 INFINITY POOL: " + PROXY_POOL.size() + " [LIVE]");
            dashView.setText("💰 Zenith Master | Total Jumps: " + totalJumps);
        });
    }

    private void startTurboScraping() {
        String[] sources = {
            "https://api.proxyscrape.com/v2/?request=getproxies&protocol=http&timeout=3500&country=all",
            "https://raw.githubusercontent.com/TheSpeedX/SOCKS-List/master/http.txt",
            "https://raw.githubusercontent.com/monosans/proxy-list/main/proxies/http.txt",
            "https://proxyspace.pro/http.txt",
            "https://raw.githubusercontent.com/ShiftyTR/Proxy-List/master/http.txt"
        };
        for (String url : sources) {
            scrapExec.execute(() -> {
                while (true) {
                    try {
                        URL u = new URL(url);
                        BufferedReader r = new BufferedReader(new InputStreamReader(u.openStream()));
                        String l;
                        while ((l = r.readLine()) != null) { if (l.contains(":")) validateProxy(l.trim()); }
                        Thread.sleep(40000); 
                    } catch (Exception e) {}
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
                c.setConnectTimeout(1000); // فلترة الخوادم الصاروخية فقط
                if (c.getResponseCode() == 200) {
                    if (!PROXY_POOL.contains(a)) {
                        PROXY_POOL.add(a);
                        updateUI();
                    }
                }
            } catch (Exception e) {}
        });
    }

    private void runAISensor(String targetUrl) {
        aiExec.execute(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(targetUrl).openConnection();
                conn.setConnectTimeout(5000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 Chrome/126.0.0.0");
                conn.connect();
                mHandler.post(() -> aiStatusView.setText("🤖 AI Zenith: Website Security Synced"));
            } catch (Exception e) {
                mHandler.post(() -> aiStatusView.setText("🤖 AI Zenith: Dynamic Mode On"));
            }
        });
    }
            }
