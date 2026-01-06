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
import org.json.JSONObject;

public class MainActivity extends Activity {
    private WebView myBrowser;
    private Button controlButton;
    private EditText linkInput, manualProxyInput;
    private TextView dashboardView;
    private Switch proxyModeSwitch;
    private Handler handler = new Handler();
    private Random random = new Random();
    private int visitCounter = 0, clickCounter = 0;
    private boolean isBotRunning = false;
    private String currentProxy = "Direct";
    private String currentCountry = "Analyzing...";
    private CopyOnWriteArrayList<String> VERIFIED_PROXIES = new CopyOnWriteArrayList<>();

    // ميزة 1: تزييف مصدر الزيارة (Referrer Spoofing)
    private String[] REFERRERS = {
        "https://www.youtube.com/", "https://www.instagram.com/",
        "https://www.tiktok.com/", "https://www.facebook.com/",
        "https://t.co/", "https://www.google.com/search?q=titan+bot"
    };

    // ميزة 2: 20 مصدراً عالمياً للبروكسيات (HTTP/SOCKS)
    private String[] PROXY_SOURCES = {
        "https://raw.githubusercontent.com/TheSpeedX/SOCKS-List/master/http.txt",
        "https://raw.githubusercontent.com/TheSpeedX/SOCKS-List/master/socks4.txt",
        "https://raw.githubusercontent.com/TheSpeedX/SOCKS-List/master/socks5.txt",
        "https://raw.githubusercontent.com/ShiftyTR/Proxy-List/master/http.txt",
        "https://raw.githubusercontent.com/monosans/proxy-list/main/proxies/http.txt",
        "https://raw.githubusercontent.com/clarketm/proxy-list/master/proxy-list-raw.txt",
        "https://api.proxyscrape.com/v2/?request=getproxies&protocol=http",
        "https://www.proxy-list.download/api/v1/get?type=http",
        "https://raw.githubusercontent.com/AnisYessou/Proxy-List/main/http.txt",
        "https://raw.githubusercontent.com/roosterkid/openproxylist/main/HTTPS_RAW.txt",
        "https://raw.githubusercontent.com/MuRongPIG/Proxy-Master/main/http.txt",
        "https://raw.githubusercontent.com/Zaeem20/FREE_PROXIES_LIST/master/http.txt",
        "https://raw.githubusercontent.com/sunny9577/proxy-scraper/master/proxies.txt",
        "https://raw.githubusercontent.com/mmpx12/proxy-list/master/https.txt",
        "https://raw.githubusercontent.com/jetkai/proxy-list/main/online-proxies/txt/proxies-http.txt",
        "https://raw.githubusercontent.com/RX403/Proxy-List/main/http.txt",
        "https://raw.githubusercontent.com/andypu/proxylist/master/proxylist.txt",
        "https://www.proxyscan.io/download?type=http",
        "https://raw.githubusercontent.com/officialputuid/Proxy-List/master/http.txt",
        "https://raw.githubusercontent.com/ErcinDedeoglu/proxies/main/proxies/http.txt"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // ميزة 3: الوضع المظلم (Dark Mode)
        
        dashboardView = findViewById(R.id.dashboardView);
        linkInput = findViewById(R.id.linkInput);
        manualProxyInput = findViewById(R.id.manualProxyInput);
        proxyModeSwitch = findViewById(R.id.proxyModeSwitch);
        controlButton = findViewById(R.id.controlButton);
        myBrowser = findViewById(R.id.myBrowser);

        setupTurboStealthEngine();
        startGlobalHarvesting(); // ميزة 4: معالجة متوازية سريعة للفحص
    }

    private void setupTurboStealthEngine() {
        WebSettings s = myBrowser.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        
        // ميزة 5: تزييف بصمة الجهاز (Device Fingerprinting)
        s.setUserAgentString("Mozilla/5.0 (Linux; Android 14; SM-S928B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36");

        myBrowser.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (isBotRunning) {
                    injectStealthScripts(); // ميزة 6: إخفاء سمات البوت
                    myBrowser.loadUrl("javascript:window.scrollBy({top: 600, behavior: 'smooth'});");
                }
            }
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (isBotRunning && request.isForMainFrame()) startNewSession();
            }
        });
        controlButton.setOnClickListener(v -> toggleBot());
    }

    private void injectStealthScripts() {
        String script = "javascript:(function() { " +
                "Object.defineProperty(navigator, 'webdriver', {get: () => false}); " +
                "Object.defineProperty(navigator, 'hardwareConcurrency', {get: () => 8}); " +
                "Object.defineProperty(navigator, 'deviceMemory', {get: () => 12}); " +
                "})()";
        myBrowser.loadUrl(script);
    }

    private void startNewSession() {
        if (!isBotRunning) return;
        CookieManager.getInstance().removeAllCookies(null);

        // ميزة 7: التحكم اليدوي (Manual Mode)
        if (proxyModeSwitch.isChecked() && !manualProxyInput.getText().toString().isEmpty()) {
            String[] list = manualProxyInput.getText().toString().split("\n");
            currentProxy = list[random.nextInt(list.length)].trim();
        } else if (!VERIFIED_PROXIES.isEmpty()) {
            currentProxy = VERIFIED_PROXIES.remove(0);
        }
        
        fetchCountryData(currentProxy); // ميزة 8: كاشف الدولة (Geo-IP)
        applyProxy(currentProxy);

        String url = linkInput.getText().toString().trim();
        if (url.isEmpty() || url.contains("emulated")) return;
        if (!url.startsWith("http")) url = "https://" + url;

        // ميزة 9: إرسال الزيارة مع المصدر الوهمي (YouTube/Instagram/TikTok)
        Map<String, String> headers = new HashMap<>();
        String randomRef = REFERRERS[random.nextInt(REFERRERS.length)];
        headers.put("Referer", randomRef);

        visitCounter++;
        updateUI("🚀 Turbo Active | Source: " + randomRef.replace("https://www.", ""));
        myBrowser.loadUrl(url, headers);

        // ميزة 10: السرعة المتذبذبة (30 إلى 60 ثانية)
        int turboDelay = 30000 + random.nextInt(30000); 
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(this::startNewSession, turboDelay);
    }

    private void applyProxy(String p) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE) && !p.contains("Direct")) {
            ProxyConfig config = new ProxyConfig.Builder().addProxyRule(p).addDirect().build();
            ProxyController.getInstance().setProxyOverride(config, r -> {}, () -> {});
        }
    }

    private void startGlobalHarvesting() {
        Executors.newFixedThreadPool(8).execute(() -> { // تسريع السحب (Multi-threaded)
            while (true) {
                for (String src : PROXY_SOURCES) {
                    try {
                        BufferedReader r = new BufferedReader(new InputStreamReader(new URL(src).openStream()));
                        String l;
                        while ((l = r.readLine()) != null) {
                            if (l.contains(":") && VERIFIED_PROXIES.size() < 600) validate(l.trim());
                        }
                    } catch (Exception e) {}
                }
                try { Thread.sleep(300000); } catch (Exception e) {}
            }
        });
    }

    private void validate(String a) {
        Executors.newFixedThreadPool(30).execute(() -> { // فحص 30 بروكسي متوازي
            try {
                String[] p = a.split(":");
                HttpURLConnection c = (HttpURLConnection) new URL("https://www.google.com").openConnection(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(p[0], Integer.parseInt(p[1]))));
                c.setConnectTimeout(3500);
                if (c.getResponseCode() == 200) {
                    VERIFIED_PROXIES.add(a);
                    updateUI("");
                }
            } catch (Exception e) {}
        });
    }

    private void fetchCountryData(String proxyStr) {
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
        controlButton.setText(isBotRunning ? "STOP TURBO" : "LAUNCH TURBO BOT");
        if (isBotRunning) startNewSession();
        else {
            myBrowser.loadUrl("about:blank");
            handler.removeCallbacksAndMessages(null);
        }
    }

    private void updateUI(String msg) {
        runOnUiThread(() -> {
            String status = msg.isEmpty() ? "⚡ Status: STEALTH TURBO" : msg;
            dashboardView.setText(status + 
                "\n📊 Total Visits: " + visitCounter + 
                "\n🌍 Origin: " + currentCountry + 
                "\n🌐 Proxy: " + currentProxy + 
                "\n📦 Global Pool: " + VERIFIED_PROXIES.size() + " (Auto-Filling)");
        });
    }
    }
                    
