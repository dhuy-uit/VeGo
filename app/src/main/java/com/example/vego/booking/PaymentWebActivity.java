package com.example.vego.booking;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;

import com.example.vego.R;
import com.example.vego.common.BaseActivity;

import java.net.URISyntaxException;

/**
 * Mở trang thanh toán của cổng (MoMo / VNPay / trang mô phỏng) trong WebView.
 * Khi cổng chuyển về trang kết quả của server (resultUrl), màn này đóng lại;
 * màn Thanh toán sẽ hỏi server để biết kết quả thật (không tin kết quả hiển thị trên trang).
 */
public class PaymentWebActivity extends BaseActivity {

    private static final String EXTRA_URL = "url";
    private static final String EXTRA_RESULT_URL = "result_url";
    private static final String EXTRA_TITLE = "title";

    private WebView web;
    private String resultUrl;

    public static Intent intent(Context context, String payUrl, String resultUrl, String title) {
        return new Intent(context, PaymentWebActivity.class)
                .putExtra(EXTRA_URL, payUrl)
                .putExtra(EXTRA_RESULT_URL, resultUrl)
                .putExtra(EXTRA_TITLE, title);
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setScreenContent(R.layout.activity_payment_web);

        resultUrl = getIntent().getStringExtra(EXTRA_RESULT_URL);
        ((TextView) findViewById(R.id.tv_title)).setText(getIntent().getStringExtra(EXTRA_TITLE));
        ProgressBar progress = findViewById(R.id.progress);

        web = findViewById(R.id.web);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);       // trang của cổng thanh toán cần JavaScript
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progress.setProgress(newProgress);
                progress.setVisibility(newProgress >= 100 ? View.INVISIBLE : View.VISIBLE);
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handle(request.getUrl().toString());
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                if (isResult(url)) finishWithResult();
            }
        });

        setupBackButton(this::finish);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (web.canGoBack()) web.goBack();
                else finish();
            }
        });

        if (savedInstanceState == null) {
            web.loadUrl(getIntent().getStringExtra(EXTRA_URL));
        } else {
            web.restoreState(savedInstanceState);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @Override
    protected void onDestroy() {
        web.destroy();
        super.onDestroy();
    }

    private boolean isResult(String url) {
        return resultUrl != null && url != null && url.startsWith(resultUrl);
    }

    /** true = app tự xử lý, WebView không tải địa chỉ này. */
    private boolean handle(String url) {
        if (isResult(url)) {
            finishWithResult();
            return true;
        }
        if (url.startsWith("http://") || url.startsWith("https://")) return false;
        // Link mở ứng dụng ngoài (momo://, intent:// của app ngân hàng...)
        try {
            Intent intent = url.startsWith("intent:")
                    ? Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                    : new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            intent.setComponent(null);
            try {
                startActivity(intent);
            } catch (ActivityNotFoundException e) {
                String fallback = intent.getStringExtra("browser_fallback_url");
                if (fallback != null) web.loadUrl(fallback);
            }
        } catch (URISyntaxException ignored) {
            // link hỏng: bỏ qua
        }
        return true;
    }

    private void finishWithResult() {
        if (isFinishing()) return;
        setResult(Activity.RESULT_OK);
        finish();
    }
}
