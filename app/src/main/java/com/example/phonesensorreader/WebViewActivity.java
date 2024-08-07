package com.example.phonesensorreader;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class WebViewActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web_view);

        SharedPreferences sharedPreferences = getSharedPreferences("APP_PREFS", MODE_PRIVATE);
        String prompt = sharedPreferences.getString("PROMPT", "");

        WebView webView = findViewById(R.id.webview);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.setWebChromeClient(new WebChromeClient());

        String js = "javascript:(function() {" +
                "console.log('Setting chat-input value');" +
                "var inputElement = document.getElementById('chat-input');" +
                "inputElement.value = '" + prompt + "';" +
                "console.log('chat-input value set to: " + prompt + "');" +
                "inputElement.dispatchEvent(new Event('input', { bubbles: true }));" +  // Ensure input event is fired
                "})()";

       //webView.setWebViewClient(new WebViewClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                // Inject the prompt into the page using JavaScript
                webView.evaluateJavascript(js, null);
            }
        });

        // Loading HTML file from assets
        webView.loadUrl("http://127.0.0.1:8080/index-new.html");
    }
}