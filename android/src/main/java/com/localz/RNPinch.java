package com.localz;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.AsyncTask;
import android.text.TextUtils;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.UnexpectedNativeTypeException;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;
import com.localz.pinch.models.HttpRequest;
import com.localz.pinch.models.HttpResponse;
import com.localz.pinch.utils.HttpUtil;
import com.localz.pinch.utils.JsonUtil;

import org.json.JSONException;

import java.io.IOException;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.List;

public class RNPinch extends ReactContextBaseJavaModule {

    static final String COOKIES_HEADER = "Set-Cookie";
    private static final String OPT_METHOD_KEY = "method";
    private static final String OPT_HEADER_KEY = "headers";
    private static final String OPT_BODY_KEY = "body";
    private static final String OPT_SSL_PINNING_KEY = "sslPinning";
    private static final String OPT_TIMEOUT_KEY = "timeoutInterval";
    private CookieManager cookieManager = null;
    private HttpUtil httpUtil;
    private String packageName = null;
    private String displayName = null;
    private String version = null;
    private String versionCode = null;

    public RNPinch(ReactApplicationContext reactContext) {
        super(reactContext);
        cookieManager = new CookieManager();
        httpUtil = new HttpUtil();
        try {
            PackageManager pManager = reactContext.getPackageManager();
            packageName = reactContext.getPackageName();
            PackageInfo pInfo = pManager.getPackageInfo(packageName, 0);
            ApplicationInfo aInfo = pManager.getApplicationInfo(packageName, 0);
            displayName = pManager.getApplicationLabel(aInfo).toString();
            version = pInfo.versionName;
            versionCode = String.valueOf(pInfo.versionCode);
        } catch (NameNotFoundException nnfe) {
            System.out.println("RNAppInfo: package name not found");
        }
    }

    @Override
    public String getName() {
        return "RNPinch";
    }

    @ReactMethod
    public void getCookies(final Promise promise) {
        try {
            WritableMap map = new WritableNativeMap();
            List<HttpCookie> cookies = cookieManager.getCookieStore().getCookies();
            for (HttpCookie cookie : cookies) {
                map.putString(cookie.getName(), cookie.getValue());
            }

            promise.resolve(map);
        }
        catch (Exception e) {
            promise.reject(e);
        }
    }

    @ReactMethod
    public void fetch(String endpoint, ReadableMap opts, Callback callback) {
        new FetchTask(opts, callback).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, endpoint);
    }

    private class FetchTask extends AsyncTask<String, Void, WritableMap> {
        private ReadableMap opts;
        private Callback callback;

        public FetchTask(ReadableMap opts, Callback callback) {
            this.opts = opts;
            this.callback = callback;
        }

        @Override
        protected WritableMap doInBackground(String... endpoint) {

            try {
                WritableMap response = Arguments.createMap();
                HttpRequest request = new HttpRequest(endpoint[0]);

                if (opts.hasKey(OPT_BODY_KEY)) {
                    request.body = opts.getString(OPT_BODY_KEY);
                }
                if (opts.hasKey(OPT_METHOD_KEY)) {
                    request.method = opts.getString(OPT_METHOD_KEY);
                }
                if (opts.hasKey(OPT_HEADER_KEY)) {
                    request.headers = JsonUtil.convertReadableMapToJson(opts.getMap(OPT_HEADER_KEY));
                }
                if (opts.hasKey(OPT_SSL_PINNING_KEY)) {
                    String fileName = opts.getMap(OPT_SSL_PINNING_KEY).getString("cert");
                    if (fileName != null) {
                        request.certFilenames = new String[]{fileName};
                    } else {
                        ReadableArray certsStrings = opts.getMap(OPT_SSL_PINNING_KEY).getArray("certs");
                        String[] certs = new String[certsStrings.size()];
                        for (int i = 0; i < certsStrings.size(); i++) {
                            certs[i] = certsStrings.getString(i);
                        }
                        request.certFilenames = certs;
                    }
                }
                if (opts.hasKey(OPT_TIMEOUT_KEY)) {
                    request.timeout = opts.getInt(OPT_TIMEOUT_KEY);
                }

                HttpResponse httpResponse = httpUtil.sendHttpRequest(request, cookieManager);

                WritableMap headers = httpResponse.headers;
                if (headers.hasKey(COOKIES_HEADER)) {
                    String cookiesHeader = httpResponse.headers.getString(COOKIES_HEADER);
                    if (cookiesHeader != null) {
                        cookieManager.getCookieStore().add(null, HttpCookie.parse(cookiesHeader).get(0));
                    }
                }
                response.putInt("status", httpResponse.statusCode);
                response.putString("statusText", httpResponse.statusText);
                response.putString("bodyString", httpResponse.bodyString);
                response.putMap("headers", headers);


                return response;
            } catch (JSONException | IOException | UnexpectedNativeTypeException | KeyStoreException | CertificateException | KeyManagementException | NoSuchAlgorithmException e) {
                WritableMap error = Arguments.createMap();
                error.putString("errorMessage", e.toString());
                return error;
            }
        }

        @Override
        protected void onPostExecute(WritableMap response) {

            if (response.hasKey("errorMessage")) {
                callback.invoke(response.getString("errorMessage"), null);
            } else {
                callback.invoke(null, response);
            }
        }
    }
}
