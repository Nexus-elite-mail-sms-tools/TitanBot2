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
    // رفع عدد المسارات للفحص السريع (Turbo Threads)
    private ExecutorService scraperExecutor = Executors.newFixedThreadPool(8); 
    private ExecutorService validatorExecutor = Executors.newFixedThreadPool(25); 
    
    private Random random = new Random();
    private int visitCounter = 0;
    private int clickCounter = 0;
    private boolean isBotRunning = false;
    private String currentProxy = "Direct";
    private String currentCountry = "Auto-Harvesting...";
    private CopyOnWriteArrayList<String> VERIFIED_PROXIES = new CopyOnWriteArrayList<>();

    private String[] DEVICE_PROFILES = {
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Linux; Android 14; SM-S928B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1"
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
        
        // --- ميزة الجلب التلقائي الفوري عند فتح التطبيق ---
        startUltraScraper(); 
        updateDashboard("⚡ Harvest Engine: ONLINE");
    }

    private void initSettings() {
        WebSettings s = myBrowser.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        
        myBrowser.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (isBotRunning) {
                    // ميزة GoLogin Stealth
                    myBrowser.loadUrl("javascript:(function(){" +
                        "Object.defineProperty(navigator,'webdriver',{get:()=>false});" +
                        "Object.defineProperty(navigator,'deviceMemory',{get:()=>8});" +
                        "})()");
                    
                    // ميزة النقر المتذبذب (3%-5%)
                    if (random.nextInt(100) < (3 + random.nextInt(3))) {
                        mainHandler.postDelayed(() -> {
                            myBrowser.loadUrl("javascript:(function(){" +
                                "var links = document.getElementsByTagName('a');" +
                                "if(links.length > 0) { links[Math.floor(Math.random()*links.length)].click(); }" +
                                "})()");
                            clickCounter++;
                            updateDashboard("");
                        }, 8000 + random.nextInt(5000));
                    }
                    myBrowser.loadUrl("javascript:window.scrollBy({top: 700, behavior: 'smooth'});");
                }
            }
        });
        controlButton.setOnClickListener(v -> toggleBot());
    }

    // --- محرك الجلب الخارق (لا يتوقف ولا يحتاج لضغط زر) ---
    private void startUltraScraper() {
        String[] sources = {
            "https://raw.githubusercontent.com/TheSpeedX/SOCKS-List/master/http.txt",
            "https://api.proxyscrape.com/v2/?request=getproxies&protocol=http",
            "https://www.proxy-list.download/api/v1/get?type=http",
            "https://raw.githubusercontent.com/jetkai/proxy-list/main/online-proxies/txt/proxies-http.txt",
            "https://raw.githubusercontent.com/shiftytr/proxy-list/master/proxy.txt"
        };

        scraperExecutor.execute(() -> {
            while (true) {
                for (String src : sources) {
                    try {
                        URL url = new URL(src);
                        BufferedReader r = new BufferedReader(new InputStreamReader(url.openStream()));
                        String l;
                        while ((l = r.readLine()) != null && VERIFIED_PROXIES.size() < 2000) { // رفع السعة لزيارات ضخمة
                            if (l.contains(":")) validateProxy(l.trim());
                        }
                    } catch (Exception e) {}
                }
                try { Thread.sleep(60000); } catch (Exception e) {} // تحديث كل دقيقة
            }
        });
    }

    private void validateProxy(String proxyAddr) {
        validatorExecutor.execute(() -> {
            try {
                String[] p = proxyAddr.split(":");
                HttpURLConnection c = (HttpURLConnection) new URL("https://www.google.com").openConnection(
                    new Proxy(Proxy.Type.HTTP, new InetSocketAddress(p[0], Integer.parseInt(p[1])))
                );
                c.setConnectTimeout(2500); // سرعة فحص عالية
                if (c.getResponseCode() == 200) {
                    if (!VERIFIED_PROXIES.contains(proxyAddr)) {
                        VERIFIED_PROXIES.add(proxyAddr);
                        updateDashboard("");
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
        fetchGeoInfo(currentProxy); // كاشف الدولة

        String ua = DEVICE_PROFILES[random.nextInt(DEVICE_PROFILES.length)];
        myBrowser.getSettings().setUserAgentString(ua);

        String url = linkInput.getText().toString().trim();
        if (url.isEmpty()) return;

        visitCounter++;
        updateDashboard("");
        
        Map<String, String> headers = new HashMap<>();
        headers.put("Referer", "https://www.google.com/"); // تزييف المصدر
        myBrowser.loadUrl(url, headers);

        // سرعة توربينية متذبذبة (30-60 ثانية)
        mainHandler.postDelayed(this::startNewSession, 30000 + random.nextInt(30000));
    }

    private void updateDashboard(String msg) {
        mainHandler.post(() -> {
            String status = isBotRunning ? "🛡️ Stealth: TITAN-ULTRA PRO" : "⚡ Engine: Harvesting...";
            dashboardView.setText(status + 
                "\n📊 Visits: " + visitCounter + " | Clicks: " + clickCounter + 
                "\n🌍 Geo: " + currentCountry + 
                "\n🌐 Proxy: " + currentProxy + 
                "\n📦 Global Pool: " + VERIFIED_PROXIES.size());
        });
    }

    private void fetchGeoInfo(String p) {
        if (p.equals("Direct")) return;
        scraperExecutor.execute(() -> {
            try {
                String ip = p.split(":")[0];
                JSONObject j = new JSONObject(new BufferedReader(new InputStreamReader(new URL("http://ip-api.com/json/"+ip).openStream())).readLine());
                currentCountry = j.optString("country", "Global") + " 🌍";
                updateDashboard("");
            } catch (Exception e) {}
        });
    }

    private void applyProxySettings(String p) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE) && !p.equals("Direct")) {
            ProxyController.getInstance().setProxyOverride(new ProxyConfig.Builder().addProxyRule(p).build(), r -> {}, () -> {});
        }
    }

    private void toggleBot() {
        isBotRunning = !isBotRunning;
        controlButton.setText(isBotRunning ? "STOP TITAN" : "LAUNCH TITAN BOT");
        if (isBotRunning) startNewSession();
        else {
            mainHandler.removeCallbacksAndMessages(null);
            stopNotification();
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager m = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            m.createNotificationChannel(new NotificationChannel("BOT_CHANNEL", "Titan Bot Service", NotificationManager.IMPORTANCE_LOW));
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
