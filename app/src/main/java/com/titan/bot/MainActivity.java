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
    private int clickCounter = 0;
    private boolean isBotRunning = false;
    private String currentProxy = "Direct";
    private String currentCountry = "Searching...";
    
    private CopyOnWriteArrayList<String> VERIFIED_PROXIES = new CopyOnWriteArrayList<>();

    // ميزة 1: المصادر المتعددة (عشرة مصادر عالمية)
    private String[] PROXY_SOURCES = {
        "https://raw.githubusercontent.com/TheSpeedX/SOCKS-List/master/http.txt",
        "https://raw.githubusercontent.com/TheSpeedX/SOCKS-List/master/socks4.txt",
        "https://raw.githubusercontent.com/TheSpeedX/SOCKS-List/master/socks5.txt",
        "https://raw.githubusercontent.com/ShiftyTR/Proxy-List/master/http.txt",
        "https://raw.githubusercontent.com/monosans/proxy-list/main/proxies/http.txt",
        "https://raw.githubusercontent.com/clarketm/proxy-list/master/proxy-list-raw.txt",
        "https://raw.githubusercontent.com/hookzof/socks5_list/master/proxy.txt",
        "https://alexa.lr2b.com/proxylist.txt",
        "https://api.proxyscrape.com/v2/?request=getproxies&protocol=http&timeout=10000&country=all",
        "https://www.proxy-list.download/api/v1/get?type=http"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // الميزة 2: الحفاظ على الوضع المظلم

        dashboardView = findViewById(R.id.dashboardView);
        linkInput = findViewById(R.id.linkInput);
        manualProxyInput = findViewById(R.id.manualProxyInput);
        proxyModeSwitch = findViewById(R.id.proxyModeSwitch);
        controlButton = findViewById(R.id.controlButton);
        myBrowser = findViewById(R.id.myBrowser);

        setupAdvancedEngine();
        startProxyHarvestingSystem(); // الميزة 3: بوت الفحص التلقائي
    }

    private void setupAdvancedEngine() {
        WebSettings s = myBrowser.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        
        // الميزة 4: بصمة متصفح Gologin Stealth
        s.setUserAgentString("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36");

        myBrowser.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (isBotRunning) {
                    // الميزة 5: سكرول بشري هادئ
                    handler.postDelayed(() -> {
                        myBrowser.loadUrl("javascript:window.scrollBy({top: 650, behavior: 'smooth'});");
                    }, 12000 + random.nextInt(5000));
                }
            }
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (isBotRunning && request.isForMainFrame()) {
                    startNewSession(); // الميزة 6: التعافي التلقائي
                }
            }
        });
        controlButton.setOnClickListener(v -> toggleBot());
    }

    private void startNewSession() {
        if (!isBotRunning) return;
        CookieManager.getInstance().removeAllCookies(null);

        // الميزة 7: التحكم اليدوي المزدوج
        if (proxyModeSwitch.isChecked() && !manualProxyInput.getText().toString().isEmpty()) {
            String[] list = manualProxyInput.getText().toString().split("\n");
            currentProxy = list[random.nextInt(list.length)].trim();
        } else if (!VERIFIED_PROXIES.isEmpty()) {
            currentProxy = VERIFIED_PROXIES.remove(0);
        }
        
        detectCountry(currentProxy); // ميزة 8 الجديدة: كاشف الدولة
        applyProxy(currentProxy);

        String url = linkInput.getText().toString().trim();
        if (url.isEmpty() || url.contains("emulated")) {
             updateUI("⚠️ يرجى إدخال رابط صحيح");
             return;
        }

        if (!url.startsWith("http")) url = "https://" + url;

        visitCounter++;
        updateUI("");
        myBrowser.loadUrl(url);

        // الميزة 9: إبطاء السرعة الآمن (3-6 دقائق)
        int humanDelay = 180000 + random.nextInt(180000); 
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(this::startNewSession, humanDelay);
    }

    private void detectCountry(String proxyStr) {
        if (proxyStr.contains("Direct")) return;
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String ip = proxyStr.split(":")[0];
                URL url = new URL("http://ip-api.com/json/" + ip);
                BufferedReader r = new BufferedReader(new InputStreamReader(url.openStream()));
                JSONObject json = new JSONObject(r.readLine());
                currentCountry = json.optString("country", "Unknown") + " 🌍";
                updateUI("");
            } catch (Exception e) { currentCountry = "Detecting..."; }
        });
    }

    private void applyProxy(String p) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE) && !p.contains("Direct")) {
            ProxyConfig config = new ProxyConfig.Builder().addProxyRule(p).addDirect().build();
            ProxyController.getInstance().setProxyOverride(config, r -> {}, () -> {});
        }
    }

    private void startProxyHarvestingSystem() {
        Executors.newSingleThreadExecutor().execute(() -> {
            while (true) {
                for (String source : PROXY_SOURCES) {
                    try {
                        BufferedReader r = new BufferedReader(new InputStreamReader(new URL(source).openStream()));
                        String l;
                        while ((l = r.readLine()) != null) {
                            if (l.contains(":") && VERIFIED_PROXIES.size() < 250) validateProxy(l.trim());
                        }
                    } catch (Exception e) {}
                }
                try { Thread.sleep(600000); } catch (Exception e) {}
            }
        });
    }

    private void validateProxy(String a) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String[] p = a.split(":");
                HttpURLConnection c = (HttpURLConnection) new URL("https://www.google.com").openConnection(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(p[0], Integer.parseInt(p[1]))));
                c.setConnectTimeout(4500);
                if (c.getResponseCode() == 200) {
                    VERIFIED_PROXIES.add(a);
                    updateUI("");
                }
            } catch (Exception e) {}
        });
    }

    private void toggleBot() {
        isBotRunning = !isBotRunning;
        controlButton.setText(isBotRunning ? "STOP TITAN" : "LAUNCH TITAN BOT");
        if (isBotRunning) startNewSession();
        else {
            myBrowser.loadUrl("about:blank");
            handler.removeCallbacksAndMessages(null);
        }
    }

    private void updateUI(String msg) {
        runOnUiThread(() -> {
            String status = msg.isEmpty() ? "🛡️ Stealth: ACTIVE" : msg;
            dashboardView.setText(status + 
                "\n📊 Visits: " + visitCounter + " | Clicks: " + clickCounter + 
                "\n🌍 Origin: " + currentCountry + 
                "\n🌐 Proxy: " + currentProxy + 
                "\n📦 Global Pool: " + VERIFIED_PROXIES.size());
        });
    }
                                              }
                                    
