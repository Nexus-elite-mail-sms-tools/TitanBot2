package com.titan.bot;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.MotionEvent;
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
    private ExecutorService scrapExec = Executors.newFixedThreadPool(200); 
    private ExecutorService validExec = Executors.newFixedThreadPool(1000); 
    
    private Random rnd = new Random();
    private int totalJumps = 0;
    private boolean isRunning = false;
    private CopyOnWriteArrayList<String> PROXY_POOL = new CopyOnWriteArrayList<>();
    private PowerManager.WakeLock wakeLock;

    // قائمة المصادر المزيفة لخداع المواقع
    private final String[] REFERERS = {
        "https://www.google.com/",
        "https://www.facebook.com/",
        "https://twitter.com/",
        "https://www.youtube.com/",
        "https://bing.com/"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_main);
            
            mHandler.postDelayed(() -> {
                try {
                    PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                    wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TitanBot::StealthMode");

                    dashView = findViewById(R.id.dashboardView);
                    aiStatusView = findViewById(R.id.aiStatusView);
                    serverCountView = findViewById(R.id.serverCountView);
                    linkIn = findViewById(R.id.linkInput);
                    controlBtn = findViewById(R.id.controlButton);
                    webContainer = findViewById(R.id.webContainer);

                    // مسح الكوكيز لجعل الزيارات فريدة
                    CookieManager.getInstance().removeAllCookies(null);
                    CookieManager.getInstance().flush();

                    if (webContainer != null) {
                        web1 = initWeb(); web2 = initWeb(); web3 = initWeb();
                        setupTripleLayout();
                        startMegaScraping(); 
                        controlBtn.setOnClickListener(v -> toggleZenithV5());
                        aiStatusView.setText("🤖 Titan AI: Stealth Protocol Loaded");
                    }
                } catch (Exception e) {
                    Toast.makeText(this, "Init Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }, 1000); 

        } catch (Exception e) {
            Toast.makeText(this, "Fatal Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void setupTripleLayout() {
        if (webContainer == null || web1 == null) return;
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
        web1.setLayoutParams(p); web2.setLayoutParams(p); web3.setLayoutParams(p);
        webContainer.addView(web1); webContainer.addView(web2); webContainer.addView(web3);
    }

    private WebView initWeb() {
        WebView wv = new WebView(this);
        WebSettings s = wv.getSettings();
        
        // إعدادات متقدمة للمتصفح
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false); // أمان
        s.setGeolocationEnabled(false); // منع كشف الموقع الحقيقي
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        
        // تعطيل الزووم لمنع تشوه الصفحة
        s.setSupportZoom(false);
        
        wv.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView v, String url) {
                // سكريبت التخفي المطور (Stealth Injection V2)
                String stealthScript = 
                    "try {" +
                    "  Object.defineProperty(navigator, 'webdriver', {get: () => undefined});" +
                    "  Object.defineProperty(navigator, 'plugins', {get: () => [1, 2, 3]});" +
                    "  Object.defineProperty(navigator, 'languages', {get: () => ['en-US', 'en']});" +
                    "  const originalQuery = window.navigator.permissions.query;" +
                    "  window.navigator.permissions.query = (parameters) => (" +
                    "    parameters.name === 'notifications' ?" +
                    "      Promise.resolve({ state: Notification.permission }) :" +
                    "      originalQuery(parameters)" +
                    "  );" +
                    "} catch (e) {}";
                
                v.evaluateJavascript(stealthScript, null);

                // محاكاة سلوك بشري عشوائي
                simulateHumanBehavior(v);
                
                mHandler.post(() -> aiStatusView.setText("🤖 Traffic: Masked & Verified"));
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError err) {
                // إعادة المحاولة فقط إذا كان الخطأ في الرابط الرئيسي
                if (isRunning && req.isForMainFrame()) {
                    mHandler.postDelayed(() -> runSingleBot(v), 2000);
                }
            }
        });
        return wv;
    }

    // دالة محاكاة السلوك البشري (تمرير + لمس حقيقي)
    private void simulateHumanBehavior(WebView v) {
        // تمرير عشوائي
        v.evaluateJavascript("window.scrollTo(0, " + rnd.nextInt(500) + ");", null);
        
        // محاكاة لمس حقيقي بعد ثانيتين
        mHandler.postDelayed(() -> {
            simulateRealTouch(v);
        }, 2000 + rnd.nextInt(3000));

        // تمرير آخر بعد اللمس
        mHandler.postDelayed(() -> {
             v.evaluateJavascript("window.scrollBy(0, " + (rnd.nextInt(300) + 50) + ");", null);
        }, 6000);
    }

    // هذه الدالة هي الأهم: تقوم بإنشاء حدث لمس حقيقي في النظام
    private void simulateRealTouch(View view) {
        long downTime = SystemClock.uptimeMillis();
        long eventTime = SystemClock.uptimeMillis() + 100;
        
        // اختيار إحداثيات عشوائية في منتصف الشاشة تقريباً (مكان الإعلانات عادة)
        float x = (float) (view.getWidth() * (0.3 + (rnd.nextDouble() * 0.4))); 
        float y = (float) (view.getHeight() * (0.3 + (rnd.nextDouble() * 0.4)));
        
        int metaState = 0;
        MotionEvent motionEventDown = MotionEvent.obtain(
            downTime, eventTime, MotionEvent.ACTION_DOWN, x, y, metaState
        );
        MotionEvent motionEventUp = MotionEvent.obtain(
            downTime, eventTime + 100, MotionEvent.ACTION_UP, x, y, metaState
        );

        view.dispatchTouchEvent(motionEventDown);
        view.dispatchTouchEvent(motionEventUp);
        
        motionEventDown.recycle();
        motionEventUp.recycle();
    }

    private void toggleZenithV5() {
        isRunning = !isRunning;
        controlBtn.setText(isRunning ? "🛑 STOP TITAN ENGINE" : "🚀 START TITAN ENGINE");
        
        if (isRunning) {
            if (wakeLock != null && !wakeLock.isHeld()) wakeLock.acquire();
            // بدء التشغيل بتسلسل زمني لتجنب الضغط
            runSingleBot(web1);
            mHandler.postDelayed(() -> runSingleBot(web2), 2000);
            mHandler.postDelayed(() -> runSingleBot(web3), 4000);
        } else {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
            // تنظيف الكوكيز عند الإيقاف
            CookieManager.getInstance().removeAllCookies(null);
        }
    }

    private void runSingleBot(WebView wv) {
        if (!isRunning || wv == null) return;
        
        // إذا لم توجد بروكسيات، انتظر قليلاً وحاول مجدداً
        if (PROXY_POOL.isEmpty()) {
            mHandler.postDelayed(() -> runSingleBot(wv), 3000);
            return;
        }

        String proxy = PROXY_POOL.remove(0); // سحب بروكسي
        updateUI();

        // تطبيق البروكسي
        if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            try {
                ProxyConfig proxyConfig = new ProxyConfig.Builder()
                    .addProxyRule(proxy)
                    .build();
                ProxyController.getInstance().setProxyOverride(proxyConfig, r -> {}, () -> {});
            } catch (Exception e) {
                // في حال فشل البروكسي، انتقل للتالي
                runSingleBot(wv);
                return;
            }
        }

        // تزوير الـ User Agent ليكون متغيراً جداً
        String[] agents = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
        };
        wv.getSettings().setUserAgentString(agents[rnd.nextInt(agents.length)]);
        
        // مسح الذاكرة المؤقتة للـ WebView الحالي لضمان نظافة الجلسة
        wv.clearHistory();
        wv.clearCache(true);

        // إضافة الهيدرز المخادعة (Referer)
        Map<String, String> extraHeaders = new HashMap<>();
        extraHeaders.put("Referer", REFERERS[rnd.nextInt(REFERERS.length)]);
        // محاولة لإلغاء الهيدر الافتراضي (قد لا تعمل في كل النسخ لكنها ضرورية للمحاولة)
        extraHeaders.put("X-Requested-With", ""); 

        String url = linkIn.getText().toString().trim();
        if(url.isEmpty()) url = "https://www.google.com"; // رابط افتراضي

        wv.loadUrl(url, extraHeaders);
        
        totalJumps++;
        
        // تحديد وقت بقاء عشوائي (بين 25 و 45 ثانية)
        long stayTime = (25 + rnd.nextInt(20)) * 1000;
        mHandler.postDelayed(() -> runSingleBot(wv), stayTime);
    }

    private void updateUI() {
        mHandler.post(() -> {
            serverCountView.setText("🌐 Active IPs: " + PROXY_POOL.size());
            dashView.setText("💰 Visits: " + totalJumps);
        });
    }

    private void startMegaScraping() {
        // نفس المصادر القديمة مع إضافة مصادر جديدة
        String[] sources = {
            "https://api.proxyscrape.com/v2/?request=getproxies&protocol=http&timeout=2000&country=all&ssl=all", // تم تعديل المهلة
            "https://raw.githubusercontent.com/TheSpeedX/SOCKS-List/master/http.txt",
            "https://raw.githubusercontent.com/monosans/proxy-list/main/proxies/http.txt",
            "https://proxyspace.pro/http.txt",
            "https://raw.githubusercontent.com/ShiftyTR/Proxy-List/master/http.txt",
            "https://raw.githubusercontent.com/clarketm/proxy-list/master/proxy-list-raw.txt"
        };
        for (String url : sources) {
            scrapExec.execute(() -> {
                while (true) {
                    try {
                        URL u = new URL(url);
                        HttpURLConnection conn = (HttpURLConnection) u.openConnection();
                        conn.setConnectTimeout(5000); // زيادة وقت الاتصال لتجنب الفشل السريع
                        BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                        String l;
                        while ((l = r.readLine()) != null) { 
                            if (l.contains(":")) validateProxy(l.trim()); 
                        }
                        r.close();
                        Thread.sleep(60000); // الفحص كل دقيقة لتحديث القائمة
                    } catch (Exception e) {}
                }
            });
        }
    }

    private void validateProxy(String a) {
        validExec.execute(() -> {
            try {
                String[] p = a.split(":");
                // التحقق باستخدام موقع خفيف وسريع بدلاً من جوجل الثقيل
                URL testUrl = new URL("http://www.gstatic.com/generate_204"); 
                Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(p[0], Integer.parseInt(p[1])));
                HttpURLConnection c = (HttpURLConnection) testUrl.openConnection(proxy);
                c.setConnectTimeout(3000); // 3 ثواني مهلة
                c.setReadTimeout(3000);
                
                if (c.getResponseCode() == 204) { // 204 يعني اتصال ناجح بدون محتوى (أسرع)
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
            
