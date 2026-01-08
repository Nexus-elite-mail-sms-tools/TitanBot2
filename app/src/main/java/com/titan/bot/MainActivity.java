package com.titan.bot;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
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
import android.net.http.SslError; // إصلاح استيراد SslError
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    // تعريف العناصر
    private WebView web1, web2, web3;
    private Button controlBtn;
    private EditText linkIn;
    private TextView dashView, aiStatusView, serverCountView;
    private LinearLayout webContainer;
    
    // أدوات التحكم في الخلفية
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private ExecutorService scrapExec = Executors.newFixedThreadPool(200); 
    private ExecutorService validExec = Executors.newFixedThreadPool(1000); 
    
    private Random rnd = new Random();
    private int totalJumps = 0;
    private boolean isRunning = false;
    private CopyOnWriteArrayList<String> PROXY_POOL = new CopyOnWriteArrayList<>();
    private PowerManager.WakeLock wakeLock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
            setContentView(R.layout.activity_main);
            
            // 1. إعداد نظام العمل في الخلفية (لمنع توقف البوت عند إغلاق الشاشة)
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TitanBot::Run");

            // 2. ربط العناصر فوراً (إصلاح مشكلة الانهيار)
            dashView = findViewById(R.id.dashboardView);
            aiStatusView = findViewById(R.id.aiStatusView);
            serverCountView = findViewById(R.id.serverCountView);
            linkIn = findViewById(R.id.linkInput);
            controlBtn = findViewById(R.id.controlButton);
            webContainer = findViewById(R.id.webContainer);

            // 3. التحقق من وجود الحاوية قبل البدء
            if (webContainer != null) {
                // إنشاء المتصفحات
                web1 = initWeb(); 
                web2 = initWeb(); 
                web3 = initWeb();
                
                setupTripleLayout();
                startMegaScraping(); // بدء جلب السيرفرات
                
                // زر التشغيل والإيقاف
                controlBtn.setOnClickListener(v -> toggleZenithV5());
                
                aiStatusView.setText("🤖 System Ready: No Crash Mode");
            } else {
                Toast.makeText(this, "خطأ: لم يتم العثور على webContainer في التصميم", Toast.LENGTH_LONG).show();
            }

        } catch (Exception e) {
            // في حال حدوث أي خطأ غير متوقع عند الفتح
            Toast.makeText(this, "Startup Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // دالة ترتيب المتصفحات في الشاشة
    private void setupTripleLayout() {
        if (webContainer == null || web1 == null) return;
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
        web1.setLayoutParams(p); web2.setLayoutParams(p); web3.setLayoutParams(p);
        webContainer.addView(web1); webContainer.addView(web2); webContainer.addView(web3);
    }

    // إعداد المتصفح (WebView) مع إصلاح الصور
    private WebView initWeb() {
        WebView wv = new WebView(this);
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        
        // تفعيل الصور للإعلانات
        s.setLoadsImagesAutomatically(true); 
        s.setBlockNetworkImage(false); 
        
        wv.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView v, String url) {
                // كود محاكاة السلوك البشري (التمرير والنقر)
                v.evaluateJavascript("(function(){" +
                    "try{" +
                    "Object.defineProperty(navigator,'webdriver',{get:()=>false});" +
                    "window.scrollTo(0, "+rnd.nextInt(500)+");" +
                    "setInterval(function(){ window.scrollBy(0, 50); }, 1000);" +
                    "if(document.body) document.body.click();" + 
                    "}catch(e){}" +
                    "})()", null);
                
                mHandler.post(() -> aiStatusView.setText("🟢 Active: " + url));
            }

            // تجاهل أخطاء SSL
            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.proceed();
            }

            // التعامل مع الأخطاء وإعادة المحاولة
            @Override
            public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError err) {
                if (isRunning && req.isForMainFrame()) {
                    // إذا فشل التحميل، انتظر قليلاً ثم حاول ببروكسي آخر
                    mHandler.postDelayed(() -> runSingleBot(v), 2000);
                }
            }
        });
        return wv;
    }

    // زر التشغيل والإيقاف
    private void toggleZenithV5() {
        isRunning = !isRunning;
        controlBtn.setText(isRunning ? "🛑 STOP V5 GHOST" : "🚀 LAUNCH ZENITH V5");
        
        if (isRunning) {
            if (wakeLock != null && !wakeLock.isHeld()) wakeLock.acquire();
            
            // تشغيل تدريجي لمنع التجمد
            runSingleBot(web1);
            mHandler.postDelayed(() -> runSingleBot(web2), 2000);
            mHandler.postDelayed(() -> runSingleBot(web3), 4000);
        } else {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
            aiStatusView.setText("🔴 Stopped by User");
        }
    }

    // المحرك الرئيسي للبوت (تم إصلاح الانهيار هنا)
    private void runSingleBot(WebView wv) {
        // حماية ضد الانهيار: إذا كان المتصفح غير موجود، توقف فوراً
        if (wv == null) return;

        if (!isRunning || PROXY_POOL.isEmpty()) {
            if (isRunning) mHandler.postDelayed(() -> runSingleBot(wv), 3000);
            return;
        }

        try {
            // سحب بروكسي من القائمة
            String proxy = PROXY_POOL.remove(0);
            updateUI();

            // تطبيق البروكسي
            if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
                try {
                    ProxyController.getInstance().setProxyOverride(new ProxyConfig.Builder()
                        .addProxyRule(proxy).build(), r -> {}, () -> {});
                } catch (Exception e) {}
            }

            // قائمة وكلاء المستخدم (User Agents)
            String[] agents = {
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
                "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
            };
            
            // تغيير البصمة (محمي بـ try-catch)
            wv.getSettings().setUserAgentString(agents[rnd.nextInt(agents.length)]);
            
            // التأكد من أن الرابط ليس فارغاً
            String url = linkIn.getText().toString().trim();
            if (url.isEmpty()) url = "https://www.google.com";
            
            wv.loadUrl(url);
            totalJumps++;
            
            // الجدولة للزيارة التالية (وقت عشوائي بين 15 و 30 ثانية)
            mHandler.postDelayed(() -> runSingleBot(wv), (15 + rnd.nextInt(15)) * 1000);

        } catch (Exception e) {
            // في حال حدوث خطأ، أعد المحاولة بعد ثانيتين بدلاً من إغلاق التطبيق
            mHandler.postDelayed(() -> runSingleBot(wv), 2000);
        }
    }

    private void updateUI() {
        mHandler.post(() -> {
            serverCountView.setText("🌐 Live Proxies: " + PROXY_POOL.size());
            dashView.setText("💰 Jumps: " + totalJumps);
        });
    }

    // جلب البروكسيات (السيرفرات)
    private void startMegaScraping() {
        String[] sources = {
            "https://api.proxyscrape.com/v2/?request=getproxies&protocol=http&timeout=500",
            "https://raw.githubusercontent.com/TheSpeedX/SOCKS-List/master/http.txt",
            "https://raw.githubusercontent.com/monosans/proxy-list/main/proxies/http.txt"
        };
        for (String url : sources) {
            scrapExec.execute(() -> {
                while (true) {
                    try {
                        URL u = new URL(url);
                        BufferedReader r = new BufferedReader(new InputStreamReader(u.openStream()));
                        String l;
                        while ((l = r.readLine()) != null) { if (l.contains(":")) validateProxy(l.trim()); }
                        Thread.sleep(60000); // تحديث كل دقيقة
                    } catch (Exception e) {}
                }
            });
        }
    }

    // التحقق من صحة البروكسي
    private void validateProxy(String a) {
        validExec.execute(() -> {
            try {
                String[] p = a.split(":");
                HttpURLConnection c = (HttpURLConnection) new URL("https://www.google.com").openConnection(
                    new Proxy(Proxy.Type.HTTP, new InetSocketAddress(p[0], Integer.parseInt(p[1])))
                );
                c.setConnectTimeout(3000); // 3 ثواني مهلة الاتصال
                c.setReadTimeout(3000);
                if (c.getResponseCode() == 200) {
                    if (!PROXY_POOL.contains(a)) {
                        PROXY_POOL.add(a);
                        updateUI();
                    }
                }
            } catch (Exception e) {}
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
    }
}
