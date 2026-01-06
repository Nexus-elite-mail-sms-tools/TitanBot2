package com.titan.bot;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private WebView myBrowser;
    private Button controlButton;
    private EditText linkInput, manualProxyInput;
    private TextView dashboardView;
    private Switch proxyModeSwitch;
    private Handler handler = new Handler();
    private Random random = new Random();
    private int visitCounter = 0;
    private boolean isBotRunning = false;
    private String currentProxy = "Direct";
    private String currentCountry = "Analyzing...";
    private CopyOnWriteArrayList<String> VERIFIED_PROXIES = new CopyOnWriteArrayList<>();
    
    // إدارة المهام لمنع انهيار التطبيق (Stability Fix)
    private ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private String[] DEVICE_PROFILES = {
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Linux; Android 14; SM-S928B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1"
    };

    private String[] REFERRER_SOURCES = {
        "https://www.youtube.com/", "https://www.instagram.com/",
        "https://www.tiktok.com/", "https://www.facebook.com/", "https://www.google.com/"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        dashboardView = findViewById(R.id.dashboardView);
        linkInput = findViewById(R.id.linkInput);
        manualProxyInput = findViewById(R.id.manualProxyInput);
        proxyModeSwitch = findViewById(R.id.proxyModeSwitch);
        controlButton = findViewById(R.id.controlButton);
        myBrowser = findViewById(R.id.myBrowser);

        setupStealthEngine();
        startOptimizedHarvesting(); // نظام جلب محسن لا يستهلك الرام
    }

    private void setupStealthEngine() {
        WebSettings s = myBrowser.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        myBrowser.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (isBotRunning) {
                    injectOmniStealth();
                    myBrowser.loadUrl("javascript:window.scrollBy({top: 500, behavior: 'smooth'});");
                }
            }
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (isBotRunning && request.isForMainFrame()) startNewSession();
            }
        });
        controlButton.setOnClickListener(v -> toggleBot());
    }

    private void injectOmniStealth() {
        String script = "javascript:(function() { " +
                "Object.defineProperty(navigator, 'webdriver', {get: () => false}); " +
                "Object.defineProperty(navigator, 'deviceMemory', {get: () => 8}); " +
                "Object.defineProperty(navigator, 'hardwareConcurrency', {get: () => 8}); " +
                "})()";
        myBrowser.loadUrl(script);
    }

    private void startNewSession() {
        if (!isBotRunning) return;
        CookieManager.getInstance().removeAllCookies(null);

        if (proxyModeSwitch.isChecked() && !manualProxyInput.getText().toString().isEmpty()) {
            String[] list = manualProxyInput.getText().toString().split("\n");
            currentProxy = list[random.nextInt(list.length)].trim();
        } else if (!VERIFIED_PROXIES.isEmpty()) {
            currentProxy = VERIFIED_PROXIES.remove(0);
        }
        
        applyProxy(currentProxy);
        fetchCountryInfo(currentProxy);

        String deviceUA = DEVICE_PROFILES[random.nextInt(DEVICE_PROFILES.length)];
        myBrowser.getSettings().setUserAgentString(deviceUA);

        String url = linkInput.getText().toString().trim();
        if (url.isEmpty() || url.contains("emulated")) return;
        if (!url.startsWith("http")) url = "https://" + url;

        Map<String, String> headers = new HashMap<>();
        String ref = REFERRER_SOURCES[random.nextInt(REFERRER_SOURCES.length)];
        headers.put("Referer", ref);

        visitCounter++;
        updateUI("🎭 Device: " + (deviceUA.contains("Windows") ? "PC" : "Mobile"));
        myBrowser.loadUrl(url, headers);

        // توقيت متذبذب (30-60 ثانية) محفوظ
        handler.postDelayed(this::startNewSession, 30000 + random.nextInt(30000));
    }

    private void applyProxy(String p) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE) && !p.contains("Direct")) {
            ProxyConfig config = new ProxyConfig.Builder().addProxyRule(p).addDirect().build();
            ProxyController.getInstance().setProxyOverride(config, r -> {}, () -> {});
        }
    }

    private void startOptimizedHarvesting() {
        // فحص هادئ كل 5 دقائق لمنع توقف التطبيق (Stability Fix)
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                URL url = new URL("https://raw.githubusercontent.com/TheSpeedX/SOCKS-List/master/http.txt");
                BufferedReader r = new BufferedReader(new InputStreamReader(url.openStream()));
                String l;
                while ((l = r.readLine()) != null) {
                    if (l.contains(":") && VERIFIED_PROXIES.size() < 100) validate(l.trim());
                }
            } catch (Exception e) {}
        }, 0, 5, TimeUnit.MINUTES);
    }

    private void validate(String a) {
        // استخدام مسارات فحص محدودة (10 بدلاً من 40) لمنع استهلاك المعالج
        Executors.newFixedThreadPool(10).execute(() -> {
            try {
                String[] p = a.split(":");
                HttpURLConnection c = (HttpURLConnection) new URL("https://www.google.com").openConnection(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(p[0], Integer.parseInt(p[1]))));
                c.setConnectTimeout(3000);
                if (c.getResponseCode() == 200) {
                    VERIFIED_PROXIES.add(a);
                    updateUI("");
                }
            } catch (Exception e) {}
        });
    }

    private void fetchCountryInfo(String proxyStr) {
        if (proxyStr.contains("Direct")) return;
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String ip = proxyStr.split(":")[0];
                URL url = new URL("http://ip-api.com/json/" + ip);
                BufferedReader r = new BufferedReader(new InputStreamReader(url.openStream()));
                JSONObject json = new JSONObject(r.readLine());
                currentCountry = json.optString("country", "Unknown") + " 🌍";
                updateUI("");
            } catch (Exception e) { currentCountry = "Syncing..."; }
        });
    }

    private void toggleBot() {
        isBotRunning = !isBotRunning;
        controlButton.setText(isBotRunning ? "STOP OMNI" : "LAUNCH OMNI BOT");
        if (isBotRunning) startNewSession();
        else {
            myBrowser.loadUrl("about:blank");
            handler.removeCallbacksAndMessages(null);
        }
    }

    private void updateUI(String msg) {
        runOnUiThread(() -> {
            String status = msg.isEmpty() ? "⚡ Mode: OMNI-STEALTH" : msg;
            dashboardView.setText(status + 
                "\n📊 Total Visits: " + visitCounter + 
                "\n🌍 Origin: " + currentCountry + 
                "\n🌐 Proxy: " + currentProxy + 
                "\n📦 Global Pool: " + VERIFIED_PROXIES.size());
        });
    }
            }
