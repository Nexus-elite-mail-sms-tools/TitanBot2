package com.titan.bot;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
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
    private ExecutorService masterExecutor = Executors.newFixedThreadPool(3);
    private ExecutorService helperExecutor = Executors.newSingleThreadExecutor();
    private Random random = new Random();
    private int visitCounter = 0;
    private boolean isBotRunning = false;
    private String currentProxy = "Direct";
    private String currentCountry = "Waiting...";
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
        startMasterScraper(); 
        startHelperBot();
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
                    // GoLogin Stealth + Hardware Spoofing
                    myBrowser.loadUrl("javascript:(function(){" +
                        "Object.defineProperty(navigator,'webdriver',{get:()=>false});" +
                        "Object.defineProperty(navigator,'deviceMemory',{get:()=>8});" +
                        "})()");
                }
            }
        });
        controlButton.setOnClickListener(v -> toggleBot());
    }

    private void toggleBot() {
        isBotRunning = !isBotRunning;
        controlButton.setText(isBotRunning ? "STOP TITAN (Background Active)" : "START TITAN");
        if (isBotRunning) {
            startNewSession();
            // تفعيل وضع الخلفية عبر إشعار
            showNotification("TitanBot يعمل الآن في الخلفية...");
        } else {
            mainHandler.removeCallbacksAndMessages(null);
            stopNotification();
        }
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
        
        String ua = DEVICE_PROFILES[random.nextInt(DEVICE_PROFILES.length)];
        myBrowser.getSettings().setUserAgentString(ua);

        String url = linkInput.getText().toString().trim();
        if (url.isEmpty()) return;

        visitCounter++;
        updateDashboard("🚀 يعمل في الخلفية بكامل الإضافات");
        myBrowser.loadUrl(url);

        // التوقيت المتذبذب 30-60 ثانية
        mainHandler.postDelayed(this::startNewSession, 30000 + random.nextInt(30000));
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("BOT_CHANNEL", "Titan Bot Service", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private void showNotification(String text) {
        Notification.Builder builder = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, "BOT_CHANNEL")
                    .setContentTitle("TitanBot Ultra")
                    .setContentText(text)
                    .setSmallIcon(android.R.drawable.ic_media_play);
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.notify(1, builder.build());
        }
    }

    private void stopNotification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.cancel(1);
    }

    // [دوال جلب البروكسيات والبوت المساعد تبقى كما هي لضمان الاستقرار]
    private void startMasterScraper() { /* نفس الكود السابق */ }
    private void startHelperBot() { /* نفس الكود السابق */ }
    private void applyProxySettings(String p) { /* نفس الكود السابق */ }

    private void updateDashboard(String msg) {
        mainHandler.post(() -> {
            dashboardView.setText(msg + "\n📊 الزيارات: " + visitCounter + 
                "\n🌐 البروكسي: " + currentProxy + "\n📦 الخزان: " + VERIFIED_PROXIES.size());
        });
    }
                }
