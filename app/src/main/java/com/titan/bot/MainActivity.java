package com.titan.bot;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
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
    private WebView myBrowser;
    private Button controlButton;
    private EditText linkInput, manualProxyInput;
    private TextView dashboardView;
    private Switch proxyModeSwitch;
    
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private ExecutorService scraperExecutor = Executors.newFixedThreadPool(4); // تقليل المسارات لتناسب 19Mbps
    private ExecutorService validatorExecutor = Executors.newFixedThreadPool(15); 
    
    private Random random = new Random();
    private int visitCounter = 0, clickCounter = 0;
    private boolean isBotRunning = false;
    private String currentProxy = "Direct", currentCountry = "Safe Loading...";
    private CopyOnWriteArrayList<String> VERIFIED_PROXIES = new CopyOnWriteArrayList<>();

    private String[] DEVICE_PROFILES = {
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/126.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) Chrome/125.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) Version/17.5 Mobile/15E148 Safari/604.1"
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

        createNotificationChannel(); 
        initSettings();
        startUltraScraper(); 
    }

    private void initSettings() {
        WebSettings s = myBrowser.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        s.setDatabaseEnabled(true);
        
        myBrowser.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (isBotRunning) {
                    // ميزة 1: قتل ثغرة WebRTC لمنع كشف الـ IP الحقيقي
                    myBrowser.loadUrl("javascript:(function(){" +
                        "var pc = window.RTCPeerConnection || window.mozRTCPeerConnection || window.webkitRTCPeerConnection;" +
                        "if(pc) pc.prototype.createOffer = function(){ return new Promise(function(res,rej){ rej(); }); };" +
                        "Object.defineProperty(navigator,'webdriver',{get:()=>false});" +
                        "Object.defineProperty(navigator,'deviceMemory',{get:()=>8});" +
                        "})()");
                    
                    // ميزة 2: النقر المتذبذب (3%-5%) مع محاكاة قراءة بشرية
                    if (random.nextInt(100) < (3 + random.nextInt(3))) {
                        mainHandler.postDelayed(() -> {
                            myBrowser.loadUrl("javascript:(function(){" +
                                "var el = document.querySelectorAll('a, button, iframe');" +
                                "if(el.length > 0) el[Math.floor(Math.random()*el.length)].click();" +
                                "})()");
                            clickCounter++;
                            updateDashboard("🎯 Pro Click Applied");
                        }, 12000 + random.nextInt(6000));
                    }
                    myBrowser.loadUrl("javascript:window.scrollBy({top: 400, behavior: 'smooth'});");
                }
            }
        });
        controlButton.setOnClickListener(v -> toggleBot());
    }

    private void startUltraScraper() {
        String[] sources = {
            "https://raw.githubusercontent.com/TheSpeedX/SOCKS-List/master/http.txt",
            "https://api.proxyscrape.com/v2/?request=getproxies&protocol=http",
            "https://raw.githubusercontent.com/jetkai/proxy-list/main/online-proxies/txt/proxies-http.txt"
        };
        scraperExecutor.execute(() -> {
            while (true) {
                for (String src : sources) {
                    try {
                        URL url = new URL(src);
                        BufferedReader r = new BufferedReader(new InputStreamReader(url.openStream()));
                        String l;
                        while ((l = r.readLine()) != null && VERIFIED_PROXIES.size() < 2000) {
                            if (l.contains(":")) validateAndFilterProxy(l.trim());
                        }
                    } catch (Exception e) {}
                }
                try { Thread.sleep(90000); } catch (Exception e) {}
            }
        });
    }

    private void validateAndFilterProxy(String addr) {
        validatorExecutor.execute(() -> {
            try {
                String[] p = addr.split(":");
                HttpURLConnection c = (HttpURLConnection) new URL("http://ip-api.com/json/" + p[0]).openConnection(
                    new Proxy(Proxy.Type.HTTP, new InetSocketAddress(p[0], Integer.parseInt(p[1])))
                );
                c.setConnectTimeout(6000); // زيادة المهلة لتجنب Timed Out
                if (c.getResponseCode() == 200) {
                    JSONObject j = new JSONObject(new BufferedReader(new InputStreamReader(c.getInputStream())).readLine());
                    String country = j.optString("countryCode", "");
                    boolean isProxy = j.optBoolean("proxy", false); // ميزة الفلترة ضد الكشف
                    
                    if (!isProxy && (country.matches("US|CA|GB|DE|FR") || random.nextInt(10) < 3)) {
                        if (!VERIFIED_PROXIES.contains(addr)) {
                            VERIFIED_PROXIES.add(addr);
                            updateDashboard("");
                        }
                    }
                }
            } catch (Exception e) {}
        });
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

        applyProxySettings(currentProxy);
        fetchGeoInfo(currentProxy);

        myBrowser.getSettings().setUserAgentString(DEVICE_PROFILES[random.nextInt(DEVICE_PROFILES.length)]);
        String url = linkInput.getText().toString().trim();
        if (url.isEmpty()) return;

        visitCounter++;
        updateDashboard("");
        
        Map<String, String> headers = new HashMap<>();
        headers.put("Referer", "https://www.google.com/");
        myBrowser.loadUrl(url, headers);

        // توقيت متذبذب طويل (45-80 ثانية) لضمان تحميل الإعلانات بالكامل
        mainHandler.postDelayed(this::startNewSession, 45000 + random.nextInt(35000));
    }

    private void fetchGeoInfo(String p) {
        if (p.equals("Direct")) return;
        scraperExecutor.execute(() -> {
            try {
                JSONObject j = new JSONObject(new BufferedReader(new InputStreamReader(new URL("http://ip-api.com/json/"+p.split(":")[0]).openStream())).readLine());
                currentCountry = j.optString("country", "Analyzing") + " 🌍";
                updateDashboard("");
            } catch (Exception e) {}
        });
    }

    private void applyProxySettings(String p) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE) && !p.equals("Direct")) {
            ProxyController.getInstance().setProxyOverride(new ProxyConfig.Builder().addProxyRule(p).build(), r -> {}, () -> {});
        }
    }

    private void updateDashboard(String msg) {
        mainHandler.post(() -> {
            String status = isBotRunning ? "🛡️ Stealth: High-Security Mode" : "⚡ Engine: Ready";
            dashboardView.setText(status + "\n📊 Visits: " + visitCounter + " | Clicks: " + clickCounter + 
                "\n🌍 Geo: " + currentCountry + "\n🌐 Proxy: " + currentProxy + "\n📦 Quality Pool: " + VERIFIED_PROXIES.size());
        });
    }

    private void toggleBot() {
        isBotRunning = !isBotRunning;
        controlButton.setText(isBotRunning ? "STOP TITAN" : "LAUNCH TITAN PRO");
        if (isBotRunning) { startNewSession(); showNotification("TitanBot Stealth Active..."); }
        else { mainHandler.removeCallbacksAndMessages(null); stopNotification(); }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(new NotificationChannel("BOT_CHANNEL", "Titan Bot Service", NotificationManager.IMPORTANCE_LOW));
        }
    }

    private void showNotification(String t) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder b = new Notification.Builder(this, "BOT_CHANNEL").setContentTitle("TitanBot Ultra PRO").setContentText(t).setSmallIcon(android.R.drawable.ic_dialog_info).setOngoing(true);
            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).notify(1, b.build());
        }
    }

    private void stopNotification() { ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).cancel(1); }
                                }
            
