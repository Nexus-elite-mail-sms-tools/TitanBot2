package com.titan.bot;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.net.http.SslError;
import androidx.webkit.ProxyConfig;
import androidx.webkit.ProxyController;
import androidx.webkit.WebViewFeature;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class MainActivity extends Activity {
    
    private List<String> ACTIVE_PROXIES = new ArrayList<>();

    // قائمة هواتف حديثة (تمويه كامل)
    private final String[] USER_AGENTS = {
        "Mozilla/5.0 (Linux; Android 14; SM-S928B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Mobile Safari/537.36", 
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1",
        "Mozilla/5.0 (Linux; Android 13; SM-A536B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Mobile Safari/537.36"
    };

    private WebView web1, web2, web3;
    private Button controlBtn;
    private EditText linkIn, proxyInputBox;
    private TextView dashView, aiStatusView;
    
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private Random rnd = new Random();
    private int totalJumps = 0;
    private boolean isRunning = false;
    private PowerManager.WakeLock wakeLock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED, WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

            setContentView(R.layout.activity_main);

            dashView = findViewById(R.id.dashboardView);
            aiStatusView = findViewById(R.id.aiStatusView);
            linkIn = findViewById(R.id.linkInput);
            proxyInputBox = findViewById(R.id.proxyInputBox);
            controlBtn = findViewById(R.id.controlButton);

            web1 = findViewById(R.id.webview_1);
            web2 = findViewById(R.id.webview_2);
            web3 = findViewById(R.id.webview_3);

            controlBtn.setOnClickListener(v -> toggleSystem());

            CookieManager.getInstance().setAcceptCookie(true);
            
            if(web1 != null) setupNuclearWeb(web1);
            if(web2 != null) setupNuclearWeb(web2);
            if(web3 != null) setupNuclearWeb(web3);

            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TitanBot::V26Nuclear");

        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void setupNuclearWeb(WebView wv) {
        if (wv == null) return;
        try {
            WebSettings s = wv.getSettings();
            s.setJavaScriptEnabled(true);
            s.setDomStorageEnabled(true);
            s.setDatabaseEnabled(true);
            s.setMediaPlaybackRequiresUserGesture(false);
            
            wv.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView v, String url) {
                    injectStealth(v); // حقن التخفي
                    
                    if (url.contains("google.com")) {
                        // الانتظار قليلاً للتأكد من التخفي
                        mHandler.postDelayed(() -> navigateToTarget(v), 2500);
                    } else if (!url.equals("about:blank") && !url.contains("captcha")) {
                        mHandler.post(() -> {
                            dashView.setText("💰 Hits: " + (++totalJumps));
                            aiStatusView.setText("🛡️ V26 Secured Hit");
                        });
                        simulateHuman(v);
                    }
                }

                @Override
                public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError err) {
                    if (req.isForMainFrame()) {
                        // إذا فشل، نظف وأعد المحاولة
                        v.loadUrl("about:blank");
                        if (isRunning) mHandler.postDelayed(() -> runSingleBot(v), 1000);
                    }
                }
            });
        } catch (Exception e) {}
    }

    // 🔥 V26: الحقن النووي (إخفاء كامل) 🔥
    private void injectStealth(WebView v) {
        String js = "javascript:(function() {" +
            // 1. قتل WebRTC (أهم خطوة)
            "const rtcBlock = {value: undefined, writable: false};" +
            "Object.defineProperty(window, 'RTCPeerConnection', rtcBlock);" +
            "Object.defineProperty(window, 'webkitRTCPeerConnection', rtcBlock);" +
            "Object.defineProperty(window, 'mozRTCPeerConnection', rtcBlock);" +
            
            // 2. إخفاء البوت
            "Object.defineProperty(navigator, 'webdriver', {get: () => undefined});" +
            
            // 3. تزييف الإضافات واللغات
            "Object.defineProperty(navigator, 'languages', {get: () => ['en-US', 'en']});" +
            "Object.defineProperty(navigator, 'plugins', {get: () => [1, 2, 3, 4, 5]});" +
            
            // 4. تزييف دقة الشاشة العشوائية
            "Object.defineProperty(screen, 'height', {get: () => 800 + Math.floor(Math.random() * 100)});" +
            "Object.defineProperty(screen, 'width', {get: () => 360 + Math.floor(Math.random() * 50)});" +
            "})()";
        v.evaluateJavascript(js, null);
    }

    private void navigateToTarget(WebView v) {
        String targetUrl = "";
        if(linkIn != null) targetUrl = linkIn.getText().toString().trim();
        
        if(!targetUrl.isEmpty() && v != null) {
            // إرسال "قوقل" كمصدر للزيارة
            Map<String, String> headers = new HashMap<>();
            headers.put("Referer", "https://www.google.com/");
            v.loadUrl(targetUrl, headers);
        }
    }

    private void simulateHuman(WebView v) {
        v.evaluateJavascript("(function(){" +
            "   var interval = setInterval(function(){ window.scrollBy(0, 10 + Math.random()*20); }, 400);" +
            "   setTimeout(function(){ clearInterval(interval); document.body.click(); }, 3500);" +
            "})()", null);
    }

    private void toggleSystem() {
        if (!isRunning) {
            String rawText = proxyInputBox.getText().toString();
            ACTIVE_PROXIES.clear();
            String[] lines = rawText.split("\n");
            for (String line : lines) {
                String clean = line.trim();
                if (!clean.isEmpty() && clean.contains(":")) {
                    ACTIVE_PROXIES.add(clean);
                }
            }
            if (ACTIVE_PROXIES.isEmpty()) {
                Toast.makeText(this, "⚠️ Paste Proxies First!", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        isRunning = !isRunning;
        controlBtn.setText(isRunning ? "🛑 STOP" : "☢️ LAUNCH V26");
        
        if (isRunning) {
            if (wakeLock != null && !wakeLock.isHeld()) wakeLock.acquire();
            if (web1 != null) runSingleBot(web1);
            if (web2 != null) mHandler.postDelayed(() -> runSingleBot(web2), 2000);
            if (web3 != null) mHandler.postDelayed(() -> runSingleBot(web3), 4000);
        } else {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        }
    }

    private void runSingleBot(WebView wv) {
        if (wv == null || !isRunning || ACTIVE_PROXIES.isEmpty()) return;

        try {
            // تنظيف عميق جداً
            CookieManager.getInstance().removeAllCookies(null);
            WebStorage.getInstance().deleteAllData();
            wv.clearHistory();
            wv.clearCache(true);
            wv.clearFormData();

            String proxy = ACTIVE_PROXIES.get(rnd.nextInt(ACTIVE_PROXIES.size()));
            
            // تغيير هوية عشوائي
            String randomAgent = USER_AGENTS[rnd.nextInt(USER_AGENTS.length)];
            wv.getSettings().setUserAgentString(randomAgent);

            if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
                ProxyController.getInstance().setProxyOverride(new ProxyConfig.Builder()
                    .addProxyRule(proxy).build(), r -> {}, () -> {});
            }
            
            // البداية دائماً قوقل
            wv.loadUrl("https://www.google.com");
            
            // 35 ثانية لكل دورة (لإعطاء وقت للإعلان)
            mHandler.postDelayed(() -> {
                if(isRunning && wv.getProgress() == 100) runSingleBot(wv);
            }, 35000); 

        } catch (Exception e) {
            mHandler.postDelayed(() -> runSingleBot(wv), 1000);
        }
    }
}
