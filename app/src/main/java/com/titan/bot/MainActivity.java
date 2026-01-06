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
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private WebView myBrowser;
    private Button controlButton;
    private EditText linkInput, manualProxyInput;
    private TextView dashboardView;
    private Switch proxyModeSwitch;
    
    private Handler handler = new Handler();
    private Random random = new Random();
    
    private int visitCounter = 0;
    private int clickCounter = 0;
    private boolean isBotRunning = false;
    private String currentProxy = "Direct";
    
    // مخزن البروكسيات المفحوصة (AI Verified Vault)
    private CopyOnWriteArrayList<String> VERIFIED_PROXIES = new CopyOnWriteArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // ربط جميع العناصر بالواجهة
        dashboardView = findViewById(R.id.dashboardView);
        linkInput = findViewById(R.id.linkInput);
        manualProxyInput = findViewById(R.id.manualProxyInput);
        proxyModeSwitch = findViewById(R.id.proxyModeSwitch);
        controlButton = findViewById(R.id.controlButton);
        myBrowser = findViewById(R.id.myBrowser);

        setupTitanEngine();
        startProxyHunterAndChecker(); // البوت الثاني المدمج للفحص
    }

    private void setupTitanEngine() {
        WebSettings s = myBrowser.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW); // حل الشاشة البيضاء
        
        // محاكاة بصمة متصفح Gologin Stealth
        s.setUserAgentString("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");

        myBrowser.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (isBotRunning) {
                    // سكرول بشري بطيء جداً (AI Scroll)
                    handler.postDelayed(() -> {
                        myBrowser.loadUrl("javascript:window.scrollBy({top: 450, behavior: 'smooth'});");
                    }, 10000 + random.nextInt(5000));

                    // تقليل النقرات: احتمال 1% فقط وتوقيت متأخر جداً (أمان فائق)
                    if (random.nextInt(100) < 1) { 
                        handler.postDelayed(() -> {
                            myBrowser.loadUrl("javascript:(function(){ " +
                                "var ads = document.querySelectorAll('iframe, a[href*=\"ad\"]'); " +
                                "if(ads.length > 0) ads[0].click(); " +
                                "})()");
                            clickCounter++;
                            updateUI();
                        }, 50000 + random.nextInt(40000)); // النقر بعد دقيقة تقريباً
                    }
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (isBotRunning && request.isForMainFrame()) {
                    // تغيير البروكسي فوراً عند فشل الاتصال (Self-Healing)
                    startNewSession();
                }
            }
        });

        controlButton.setOnClickListener(v -> toggleBotStatus());
    }

    private void startNewSession() {
        if (!isBotRunning) return;
        
        // تنظيف البيانات لضمان عدم التعقب
        CookieManager.getInstance().removeAllCookies(null);
        WebStorage.getInstance().deleteAllData();

        // منطق اختيار البروكسي (يدوي أولاً أو تلقائي مفحوص)
        if (proxyModeSwitch.isChecked() && !manualProxyInput.getText().toString().isEmpty()) {
            String[] manualList = manualProxyInput.getText().toString().split("\n");
            currentProxy = manualList[random.nextInt(manualList.length)].trim();
        } 
        else if (!VERIFIED_PROXIES.isEmpty()) {
            currentProxy = VERIFIED_PROXIES.remove(0);
        } else {
            currentProxy = "Direct (No Proxy)";
        }

        applyProxy(currentProxy);

        String targetUrl = linkInput.getText().toString();
        if (!targetUrl.startsWith("http")) targetUrl = "https://" + targetUrl;

        visitCounter++;
        updateUI();
        myBrowser.loadUrl(targetUrl);

        // --- المؤقت المتذبذب البطيء (بين 2 إلى 4 دقائق لكل زيارة) ---
        int slowDelay = 120000 + random.nextInt(120000); 
        handler.postDelayed(this::startNewSession, slowDelay);
    }

    private void applyProxy(String proxyStr) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE) && !proxyStr.contains("Direct")) {
            ProxyConfig config = new ProxyConfig.Builder().addProxyRule(proxyStr).addDirect().build();
            ProxyController.getInstance().setProxyOverride(config, r -> {}, () -> {});
        }
    }

    private void startProxyHunterAndChecker() {
        Executors.newSingleThreadExecutor().execute(() -> {
            String[] sources = {
                "https://raw.githubusercontent.com/TheSpeedX/SOCKS-List/master/http.txt",
                "https://raw.githubusercontent.com/ShiftyTR/Proxy-List/master/http.txt"
            };
            while (true) {
                for (String src : sources) {
                    try {
                        BufferedReader r = new BufferedReader(new InputStreamReader(new URL(src).openStream()));
                        String l;
                        while ((l = r.readLine()) != null) {
                            if (l.contains(":") && VERIFIED_PROXIES.size() < 100) {
                                validateProxy(l.trim());
                            }
                        }
                    } catch (Exception e) {}
                }
                try { Thread.sleep(300000); } catch (Exception e) {}
            }
        });
    }

    private void validateProxy(String addr) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String[] p = addr.split(":");
                Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(p[0], Integer.parseInt(p[1])));
                HttpURLConnection c = (HttpURLConnection) new URL("https://www.google.com").openConnection(proxy);
                c.setConnectTimeout(3000);
                c.connect();
                if (c.getResponseCode() == 200) {
                    VERIFIED_PROXIES.add(addr);
                    updateUI();
                }
            } catch (Exception e) {}
        });
    }

    private void toggleBotStatus() {
        isBotRunning = !isBotRunning;
        controlButton.setText(isBotRunning ? "STOP TITAN" : "LAUNCH TITAN BOT");
        if (isBotRunning) startNewSession();
        else {
            myBrowser.loadUrl("about:blank");
            handler.removeCallbacksAndMessages(null);
        }
    }

    private void updateUI() {
        runOnUiThread(() -> {
            dashboardView.setText("🛡️ Mode: Gologin Stealth\n📊 Visits: " + visitCounter + " | Clicks: " + clickCounter + 
                                 "\n🌐 Current: " + currentProxy + " | Verified Vault: " + VERIFIED_PROXIES.size());
        });
    }
}
