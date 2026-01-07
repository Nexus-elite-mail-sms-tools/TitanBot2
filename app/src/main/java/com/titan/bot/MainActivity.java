package com.titan.bot;

import android.app.Activity;
import android.graphics.Bitmap;
import android.net.http.SslError;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.view.View;
import android.webkit.*;
import androidx.webkit.ProxyConfig;
import androidx.webkit.ProxyController;
import androidx.webkit.WebViewFeature;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.ViewGroup;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    
    // --- عناصر الواجهة ---
    private WebView web1, web2, web3;
    private Button controlBtn;
    private EditText linkIn;
    private TextView dashView, aiStatusView, serverCountView;
    private LinearLayout webContainer;
    
    // --- أدوات النظام ---
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private ExecutorService scrapExec = Executors.newFixedThreadPool(20); // خففنا العدد لتوفير موارد الهاتف
    private ExecutorService validExec = Executors.newFixedThreadPool(200); 
    private PowerManager.WakeLock wakeLock;
    private Random rnd = new Random();
    
    // --- المتغيرات التشغيلية ---
    private boolean isRunning = false;
    private int totalJumps = 0;
    
    // --- القوائم ---
    private Set<String> CHECKED_HISTORY = Collections.synchronizedSet(new HashSet<>());
    private CopyOnWriteArrayList<String> PROXY_POOL = new CopyOnWriteArrayList<>();
    
    // --- روابط الحماية ---
    // هذه الخريطة تضمن أن كل ويب فيو لديه مهمة واحدة مجدولة فقط
    private Map<Integer, Runnable> taskMap = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_main);
            
            // إعداد أولي لتجنب الانهيار
            mHandler.postDelayed(() -> {
                try {
                    // إعداد الطاقة (للاستمرار في الخلفية)
                    PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                    wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TitanBot::Heartbeat");

                    // ربط الواجهات
                    dashView = findViewById(R.id.dashboardView);
                    aiStatusView = findViewById(R.id.aiStatusView);
                    serverCountView = findViewById(R.id.serverCountView);
                    linkIn = findViewById(R.id.linkInput);
                    controlBtn = findViewById(R.id.controlButton);
                    webContainer = findViewById(R.id.webContainer);

                    // إعدادات الكوكيز
                    CookieManager.getInstance().setAcceptCookie(true);
                    CookieManager.getInstance().setAcceptThirdPartyCookies(null, true); 

                    if (webContainer != null) {
                        // تهيئة المتصفحات الثلاثة
                        web1 = initWeb(); web2 = initWeb(); web3 = initWeb();
                        setupTripleLayout();
                        
                        // بدء البحث عن الخوادم
                        startSmartScraping(); 
                        
                        // زر التحكم
                        controlBtn.setOnClickListener(v -> toggleEngine());
                        
                        aiStatusView.setText("🟢 System Ready: Waiting for Command");
                    }
                } catch (Exception e) {
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }, 1000); 
        } catch (Exception e) {}
    }

    private void setupTripleLayout() {
        if (webContainer == null || web1 == null) return;
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
        web1.setLayoutParams(p); web2.setLayoutParams(p); web3.setLayoutParams(p);
        webContainer.addView(web1); webContainer.addView(web2); webContainer.addView(web3);
    }

    private WebView initWeb() {
        WebView wv = new WebView(this);
        // تفعيل تسريع الرسوميات
        wv.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setLoadsImagesAutomatically(true); // مهم جداً للإعلانات
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT); 
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        
        wv.setWebViewClient(new WebViewClient() {
            // تجاوز أخطاء SSL (يحل مشكلة الشاشة البيضاء)
            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.proceed();
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                // عند انتهاء التحميل، ابدأ التمرير الناعم
                if(isRunning) startSmoothScroll(v);
            }
        });
        return wv;
    }

    // --- دالة التمرير الناعم جداً ---
    private void startSmoothScroll(WebView v) {
        v.evaluateJavascript(
            "(function() {" +
            "   var fps = 30;" + // 30 إطار في الثانية لسلاسة الحركة
            "   var speed = 1;" + // سرعة بطيئة جداً
            "   var timer = setInterval(function() {" +
            "       window.scrollBy(0, speed);" +
            "       if ((window.innerHeight + window.scrollY) >= document.body.offsetHeight) {" +
            "           window.scrollTo(0, 0);" + // العودة للأعلى عند النهاية
            "       }" +
            "   }, 1000 / fps);" +
            "})()", null);
    }

    private void toggleEngine() {
        isRunning = !isRunning;
        controlBtn.setText(isRunning ? "🛑 STOP SYSTEM" : "🚀 START TURTLE MODE");
        
        if (isRunning) {
            if (wakeLock != null && !wakeLock.isHeld()) wakeLock.acquire();
            
            // تشغيل المحركات بتوقيتات مختلفة لتوزيع الحمل
            startBotCycle(web1, 1, 0);
            startBotCycle(web2, 2, 10000); // تأخير 10 ثواني
            startBotCycle(web3, 3, 20000); // تأخير 20 ثانية
        } else {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
            // إيقاف كل المهام المجدولة
            mHandler.removeCallbacksAndMessages(null);
            aiStatusView.setText("🔴 System Stopped");
        }
    }

    // --- القلب النابض (The Heartbeat Function) ---
    private void startBotCycle(WebView wv, int id, long delayMillis) {
        if (!isRunning) return;

        Runnable task = () -> {
            if (!isRunning) return;

            // 1. التحقق من توفر بروكسيات
            if (PROXY_POOL.isEmpty()) {
                aiStatusView.setText("⚠️ Pool Empty! Waiting...");
                // إعادة المحاولة بعد 3 ثواني
                startBotCycle(wv, id, 3000);
                return;
            }

            // 2. سحب بروكسي وتدويره (Recycle)
            String proxy = "";
            try {
                proxy = PROXY_POOL.remove(0); 
                PROXY_POOL.add(proxy); // إعادته لآخر القائمة
            } catch (Exception e) {
                startBotCycle(wv, id, 1000);
                return;
            }

            // 3. تطبيق البروكسي
            if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
                try {
                    ProxyConfig proxyConfig = new ProxyConfig.Builder().addProxyRule(proxy).build();
                    ProxyController.getInstance().setProxyOverride(proxyConfig, r -> {}, () -> {});
                } catch (Exception e) {}
            }

            // 4. إعداد المتصفح للزيارة
            CookieManager.getInstance().removeAllCookies(null); // مسح الكوكيز
            wv.clearHistory();
            
            // تغيير الـ User-Agent عشوائياً
            String[] agents = {
                "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (Chrome/120.0.0.0) Mobile Safari/537.36",
                "Mozilla/5.0 (Linux; Android 13; SM-S908B) AppleWebKit/537.36 (Chrome/119.0.0.0) Mobile Safari/537.36",
                "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (Chrome/121.0.0.0) Mobile Safari/537.36"
            };
            wv.getSettings().setUserAgentString(agents[rnd.nextInt(agents.length)]);

            // 5. تحميل الرابط
            String url = linkIn.getText().toString().trim();
            if(url.isEmpty()) url = "https://www.google.com";
            
            // إضافة Referer ليبدو كزيارة حقيقية
            Map<String, String> headers = new HashMap<>();
            headers.put("Referer", "https://www.google.com/");
            
            wv.loadUrl(url, headers);
            
            // تحديث الواجهة
            totalJumps++;
            updateUI();

            // 6. جدولة الدورة القادمة بعد 30 ثانية (مقدسة!)
            // هذا هو أهم سطر: مهما حدث، بعد 30 ثانية سننتقل للتالي
            startBotCycle(wv, id, 30000);
        };

        // تنفيذ المهمة بعد التأخير المطلوب
        mHandler.postDelayed(task, delayMillis);
    }

    private void updateUI() {
        mHandler.post(() -> {
            serverCountView.setText("🔋 Live IPs: " + PROXY_POOL.size());
            dashView.setText("💰 Visits: " + totalJumps);
        });
    }

    // --- نظام جلب الخوادم ---
    private void startSmartScraping() {
        String[] sources = {
            "https://api.proxyscrape.com/v2/?request=getproxies&protocol=http&timeout=10000&country=all&ssl=all&anonymity=all",
            "https://raw.githubusercontent.com/TheSpeedX/SOCKS-List/master/http.txt",
            "https://raw.githubusercontent.com/monosans/proxy-list/main/proxies/http.txt",
            "https://raw.githubusercontent.com/prxchk/proxy-list/main/http.txt",
            "https://www.proxy-list.download/api/v1/get?type=http"
        };

        for (String url : sources) {
            scrapExec.execute(() -> {
                while (true) {
                    try {
                        URL u = new URL(url);
                        HttpURLConnection conn = (HttpURLConnection) u.openConnection();
                        conn.setConnectTimeout(15000);
                        BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                        String l;
                        while ((l = r.readLine()) != null) { 
                            String clean = l.trim();
                            if (clean.contains(":") && !CHECKED_HISTORY.contains(clean)) { 
                                CHECKED_HISTORY.add(clean);
                                // فحص سريع
                                validateProxy(clean); 
                            }
                        }
                        r.close();
                        // تنظيف الذاكرة كل فترة لإعادة الفحص
                        if (CHECKED_HISTORY.size() > 5000) CHECKED_HISTORY.clear();
                        Thread.sleep(60000); 
                    } catch (Exception e) {
                        try { Thread.sleep(10000); } catch (Exception ex) {}
                    }
                }
            });
        }
    }

    private void validateProxy(String a) {
        validExec.execute(() -> {
            try {
                String[] p = a.split(":");
                Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(p[0], Integer.parseInt(p[1])));
                // نستخدم gstatic لأنه خفيف جداً ولا يستهلك بيانات
                URL testUrl = new URL("http://www.gstatic.com/generate_204");
                HttpURLConnection c = (HttpURLConnection) testUrl.openConnection(proxy);
                c.setConnectTimeout(5000); // 5 ثواني مهلة
                c.setReadTimeout(5000);
                c.connect();
                
                // قبول أي استجابة تدل على الحياة
                if (c.getResponseCode() > 0) {
                    if (!PROXY_POOL.contains(a)) {
                        PROXY_POOL.add(a);
                        updateUI();
                    }
                }
                c.disconnect();
            } catch (Exception e) {}
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
    }
            }
