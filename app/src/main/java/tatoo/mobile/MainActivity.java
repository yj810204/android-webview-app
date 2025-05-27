package tatoo.mobile;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextThemeWrapper;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;

import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.GeolocationPermissions;
import android.webkit.JsResult;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

// 아임포트 sdk관련
import com.iamport.sdk.domain.core.Iamport;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity {

    public Context mContext;
    private static String target_url = "howtattoo.co.kr";
    private static String file_type = "*/*";
    private static String pop_browser_title = "";
    private static String pop_browser_url = "";

    WebView webView;
    ProgressBar progressBar;
    SwipeRefreshLayout swipeRefreshLayout;

    private GeolocationPermissions.Callback mCallBack;
    private String mOrigin;

    private static final String TAG = MainActivity.class.getSimpleName();

    private static final int FILECHOOSER_RESULTCODE = 1;
    private String cam_file_data = null;
    private ValueCallback<Uri> file_data;
    private ValueCallback<Uri[]> file_path;

    private final static int file_req_code = 1;
    private long backBtnTime = 0;

    public AlertDialog alertDialog;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent){
        super.onActivityResult(requestCode, resultCode, intent);

        if(Build.VERSION.SDK_INT >= 21){
            Uri[] results = null;

            if (resultCode == Activity.RESULT_CANCELED) {
                file_path.onReceiveValue(null);
                return;
            }

            if(resultCode == Activity.RESULT_OK){
                if(null == file_path){
                    return;
                }
                ClipData clipData;
                String stringData;

                try {
                    clipData = intent.getClipData();
                    stringData = intent.getDataString();
                } catch (Exception e) {
                    clipData = null;
                    stringData = null;
                }

                if (clipData == null && stringData == null && cam_file_data != null) {
                    results = new Uri[]{Uri.parse(cam_file_data)};
                } else {
                    if (clipData != null) { // 다중파일 업로드 체크
                        final int numSelectedFiles = clipData.getItemCount();
                        results = new Uri[numSelectedFiles];
                        for (int i = 0; i < clipData.getItemCount(); i++) {
                            results[i] = clipData.getItemAt(i).getUri();
                        }
                    } else {
                        try {
                            Bitmap cam_photo = (Bitmap) intent.getExtras().get("data");
                            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                            cam_photo.compress(Bitmap.CompressFormat.JPEG, 100, bytes);
                            stringData = MediaStore.Images.Media.insertImage(this.getContentResolver(), cam_photo, null, null);
                        } catch (Exception ignored){}
                            /* extra data 체크
                            Bundle bundle = intent.getExtras();
                            if (bundle != null) {
                                for (String key : bundle.keySet()) {
                                    Log.w("ExtraData", key + " : " + (bundle.get(key) != null ? bundle.get(key) : "NULL"));
                                }
                            }*/
                        results = new Uri[]{Uri.parse(stringData)};
                    }
                }
            }

            file_path.onReceiveValue(results);
            file_path = null;
        } else {
            if(requestCode == file_req_code){
                if(null == file_data) return;
                Uri result = intent == null || resultCode != RESULT_OK ? null : intent.getData();
                file_data.onReceiveValue(result);
                file_data = null;
            }
        }
   }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mContext = this.getApplicationContext();

//        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
//            getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
//        }

        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        webView = (WebView) findViewById(R.id.webView);
        swipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.refreshLayout);

        // version check
        if ( Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && PackageManager.PERMISSION_DENIED == ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)) {
            // 푸쉬 권한 없음
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 0x0000001);
        }

        // 아임포트 초기화
        Iamport.INSTANCE.init(this);
        Iamport.INSTANCE.pluginMobileWebSupporter(webView);

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setGeolocationEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccessFromFileURLs(true);
        webSettings.setAllowUniversalAccessFromFileURLs(true);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        webSettings.setSupportMultipleWindows(true);

        Intent intent = this.getIntent();
        Bundle bundle = intent.getExtras();

        //User Agent 관련
        String userAgent = webSettings.getUserAgentString();
        webSettings.setUserAgentString(userAgent + " WpApp WpApp_android WpVer_" + BuildConfig.VERSION_NAME);

        AndroidBridge androidBridge = new AndroidBridge(webView, MainActivity.this);
        webView.addJavascriptInterface(androidBridge, "Android");

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        //cookieManager.setCookie("/", "device_token=" + intent.getStringExtra("token"));
        cookieManager.setCookie("https://" + target_url + "/", "device_token=" + intent.getStringExtra("token"));

        //푸시 알림시 타고들어온 url로 열기
        if(bundle != null) {
            if(bundle.getString("url") != null && !bundle.getString("url").equalsIgnoreCase("")) {
                webView.loadUrl(bundle.getString("url"));
            } else {
                webView.loadUrl("https://" + target_url + "/");
            }
        }

        //꾹눌러 이미지 정의
        registerForContextMenu(webView);

        //당겨서 새로고침 정의
        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                webView.reload();
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                Log.d("WebViewLog", "URL 호출됨: " + url);
                Uri uri = Uri.parse(url);
                Uri org_uri = Uri.parse("https://" + target_url + "/");

                if(url.startsWith("app://")) {
                    String uhost = uri.getHost();
                    String upackage = uri.getQueryParameter("package");

                    try {
                        switch (uhost) {
                            case "callApp":
                                try {
                                    Intent appIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(upackage));
                                    startActivity(appIntent);
                                } catch (Exception e) {
                                    Intent storeIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + upackage));
                                    startActivity(storeIntent);
                                }
                            break;
                            case "callMapApp": //지도 호출이 필요한 앱패킹의 경우 통신
//                                String ulat = uri.getQueryParameter("lat");
//                                String ulng = uri.getQueryParameter("lng");
//                                String uz = uri.getQueryParameter("z");
//
//                                Uri location = Uri.parse("geo:" + ulat + "," + ulng + "?z=" + uz);
//                                Intent mapIntent = new Intent(Intent.ACTION_VIEW, location);
//                                startActivity(mapIntent);

//                                Intent tmapIntent = new Intent(Intent.ACTION_MAIN);
//                                tmapIntent.putExtra("rGoName", "T타워");
//                                startActivity(tmapIntent);
                            break;
                        }
                    } catch (Exception e) {
                        Intent storeIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + upackage));
                        startActivity(storeIntent);
                    }
                    return true;
                } else if(url.startsWith("tel:")) {
                    //전화TAG시 전화앱 실행
                    startActivity(new Intent("android.intent.action.DIAL", Uri.parse(url)));
                    return true;
                } else if(url.startsWith("intent:kakaolink:")) {
                    //게시판 카카오 공유관련 패치
                    String kakaoPackage = "com.kakao.talk";
                    try {
                        String kakao_link = url.replace("intent:", "");
                        Intent kakao_intent = new Intent(Intent.ACTION_VIEW, Uri.parse(kakao_link));
                        startActivity(kakao_intent);
                    } catch (Exception e) {
                        Intent storeIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + kakaoPackage));
                        startActivity(storeIntent);
                    }
                    return true;
                } else if(url.startsWith("https://checkout-pretest.tosspayments.com/")) {
                    return true;
                }

                // 카카오 계정 로그인 페이지는 내부 웹뷰에서 열기
                if (url.startsWith("https://kauth.kakao.com/") || url.startsWith("https://accounts.kakao.com/") || url.startsWith("https://logins.daum.net/")) {
                    Log.d("WebViewLog", "카카오 계정 로그인 페이지 → 내부에서 열기");
                    view.loadUrl(url);
                    return true;
                }

                // 호스트가 같으면 내부 WebView에서 열기
                if (org_uri.getHost().equals(uri.getHost())) {
                    view.loadUrl(url);
                } else {
                    // 나머지는 외부 브라우저
                    Log.d("WebViewLog", "외부 링크 → 브라우저로 전송: " + url);
                    Intent browser_intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(browser_intent);
                }

                return true;
            }

            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl){
                Toast.makeText(getApplicationContext(), "인터넷 연결이 없습니다.", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);

                Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        //Do something after 2s
                    }
                }, 2000);
                progressBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                view.loadUrl("javascript:window.Android.getHtml(document.getElementsByTagName('html')[0].innerHTML);");

                progressBar.setVisibility(View.GONE);
                swipeRefreshLayout.setRefreshing(false);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            //위치권한 필요한 경우
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                super.onGeolocationPermissionsShowPrompt(origin, callback);

                if(Build.VERSION.SDK_INT >= 23) {
                    if(location_permission()) {
                        callback.invoke(origin, true, false);
                    } else {
                        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 362);
                        mCallBack = callback;
                        mOrigin = origin;
                    }
                } else {
                    callback.invoke(origin, true, false);
                }
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                //첨부파일 이미지 수정이 필요한 경우(개발중)
//                ImagePicker.with(MainActivity.this)
//                        .crop()	    			//Crop image(Optional), Check Customization for more option
//                        .compress(1024)			//Final image size will be less than 1 MB(Optional)
//                        .maxResultSize(1080, 1080)	//Final image resolution will be less than 1080 x 1080(Optional)
//                        .start();
//
//                return true;

                if(file_permission() && Build.VERSION.SDK_INT >= 21) {
                    file_path = filePathCallback;
                    Intent takePictureIntent = null;
                    takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

                    if (takePictureIntent.resolveActivity(MainActivity.this.getPackageManager()) != null) {
                        File photoFile = null;
                        try {
                            photoFile = create_image();
                            takePictureIntent.putExtra("PhotoPath", cam_file_data);
                        } catch (IOException ex) {
                            Log.e(TAG, "이미지 파일 생성이 실패하였습니다.", ex);
                        }
                        if (photoFile != null) {
                            cam_file_data = "file:" + photoFile.getAbsolutePath();
                            Uri imgUrl;
                            if (getApplicationInfo().targetSdkVersion > Build.VERSION_CODES.M) {
                                String authority = BuildConfig.APPLICATION_ID;
                                imgUrl = FileProvider.getUriForFile(MainActivity.this, authority, photoFile);
                            } else {
                                imgUrl = Uri.fromFile(photoFile);
                            }
                            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, imgUrl);
                        } else {
                            cam_file_data = null;
                            takePictureIntent = null;
                        }
                    }

                    Intent contentSelectionIntent = new Intent(Intent.ACTION_GET_CONTENT);
                    contentSelectionIntent.addCategory(Intent.CATEGORY_OPENABLE);
                    contentSelectionIntent.setType(file_type);
                    contentSelectionIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                    Intent[] intentArray;
                    intentArray = new Intent[]{takePictureIntent};
                    Intent chooserIntent = new Intent(Intent.ACTION_CHOOSER);
                    chooserIntent.putExtra(Intent.EXTRA_INTENT, contentSelectionIntent);
                    chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, intentArray);
                    startActivityForResult(chooserIntent, file_req_code);
                    return true;
                } else {
                    return false;
                }
            }

            @Override
            public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                new AlertDialog.Builder(new ContextThemeWrapper(view.getContext(), android.R.style.Theme_DeviceDefault_Light)).setTitle("").setMessage(message).setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        result.confirm();
                    }
                }).setCancelable(false).create().show();
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
                new AlertDialog.Builder(new ContextThemeWrapper(view.getContext(), android.R.style.Theme_DeviceDefault_Light)).setTitle("").setMessage(message).setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        result.confirm();
                    }
                }).setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        result.cancel();
                    }
                }).setCancelable(false).create().show();
                return true;
            }

            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {

                //WebView webViewPop = new WebView(view.getContext());
                final Dialog dialog = new Dialog(view.getContext(), android.R.style.Theme_Material_Light_NoActionBar_Fullscreen);
                dialog.setContentView(R.layout.dialog_default);

                WebView webViewDialog = dialog.findViewById(R.id.webViewDialog);
                Button dialogCloseBtn = dialog.findViewById(R.id.dialog_close_btn);

                dialogCloseBtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        dialog.dismiss();
                        progressBar.setVisibility(View.GONE);
                        webViewDialog.removeView(view);
                        webViewDialog.destroy();
                    }
                });

                WebSettings webSettings = webViewDialog.getSettings();
                webSettings.setJavaScriptEnabled(true);
                webSettings.setAllowFileAccess(true);
                webSettings.setDomStorageEnabled(true);
                webSettings.setAllowFileAccessFromFileURLs(true);
                webSettings.setAllowUniversalAccessFromFileURLs(true);
                webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
                webSettings.setSupportMultipleWindows(true);

                //User Agent 관련
                String userAgent = webSettings.getUserAgentString();
                webSettings.setUserAgentString(userAgent + " WpApp WpPop");

                dialog.setOnKeyListener(new DialogInterface.OnKeyListener() {
                    @Override
                    public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {

                        if(keyCode == KeyEvent.KEYCODE_BACK) {
                            //MyLog.toastMakeTextShow(view.getContext(), "TAG", "KEYCODE_BACK");
                            if(webViewDialog.canGoBack()){
                                webViewDialog.goBack();
                            }else{
                                dialog.dismiss();
                                webViewDialog.removeView(view);
                                webViewDialog.destroy();
                            }
                            return true;
                        }else{
                            return false;
                        }
                    }
                });
                webViewDialog.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                        return super.shouldOverrideUrlLoading(view, request);
                    }

                    @Override
                    public void onPageStarted(WebView view, String url, Bitmap favicon) {
                        progressBar.setVisibility(View.VISIBLE);
                        super.onPageStarted(view, url, favicon);
                    }

                    @Override
                    public void onPageFinished(WebView view, String url) {

                        pop_browser_title = String.valueOf(view.getTitle());

                        Uri uri = Uri.parse(url);
                        pop_browser_url = String.valueOf(uri.getHost());

                        TextView browserTitle = dialog.findViewById(R.id.browser_title);
                        TextView browserUrl = dialog.findViewById(R.id.browser_url);

                        if(pop_browser_title.length() == 0 || pop_browser_title.equals("about:blank")) {
                            pop_browser_title = "";
                        }

                        if(pop_browser_url.length() == 0 || pop_browser_url.equals("null")) {
                            pop_browser_url = "";
                        }

                        browserTitle.setText(pop_browser_title);
                        browserUrl.setText(pop_browser_url);

//                        dialog.show();
                        progressBar.setVisibility(View.GONE);
                        super.onPageFinished(view, url);
                    }
                });

                webViewDialog.setWebChromeClient(new WebChromeClient() {
                    @Override
                    public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                        new AlertDialog.Builder(new ContextThemeWrapper(view.getContext(), android.R.style.Theme_DeviceDefault_Light)).setTitle("").setMessage(message).setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                //dialog.dismiss();
                                result.confirm();
                            }
                        }).setCancelable(false).create().show();
                        return true;
                    }

                    @Override
                    public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
                        new AlertDialog.Builder(new ContextThemeWrapper(view.getContext(), android.R.style.Theme_DeviceDefault_Light)).setTitle("").setMessage(message).setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                //dialog.dismiss();
                                result.confirm();
                            }
                        }).setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                result.cancel();
                            }
                        }).setCancelable(false).create().show();
                        return true;
                    }

                    @Override
                    public void onCloseWindow(WebView window) {
                        dialog.dismiss();
                        progressBar.setVisibility(View.GONE);
                    }

                });

                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(webViewDialog);
                resultMsg.sendToTarget();

                dialog.show();

                return true;
            }
        });

        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
                String filename = null;
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));

                String contentSplit[] = contentDisposition.split("filename=");
                filename = contentSplit[1].replace("filename=", "").replace("\"", "").trim();

                Log.d("filename", filename);

                request.allowScanningByMediaScanner();
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED); //Notify client once download is completed!
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);
                DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                dm.enqueue(request);
                Toast.makeText(getApplicationContext(), "[내장 메모리/Download] 폴더에 저장되었습니다.", Toast.LENGTH_LONG).show();
            }
        });

        // 아임포트 결제 구분하기 위한 오버라이딩
//        webView.setWebViewClient(new IamPortMobileModeWebViewClient() {
//            @Override
//            public boolean shouldOverrideUrlLoading(@Nullable WebView view, @Nullable WebResourceRequest request) {
//                Log.i("---", "---");
//                Log.w("//===========//", "================================================");
//                Log.i("", "\n" + "[A_Main >> setWebViewClient() :: 웹 브라우저 [window open] [a 태그 _blank 새창 열기] [열기] 주소 감지 실시]");
//                Log.i("", "\n" + "[수행 메소드 :: shouldOverrideUrlLoading()]");
//                Log.i("", "\n" + "[url :: " + String.valueOf(request) + "]");
//                Log.w("//===========//", "================================================");
//                Log.i("---", "---");
//
//                return super.shouldOverrideUrlLoading(view, request);
//            }
//
//            @Override
//            public void onPageStarted(WebView view, String url, Bitmap favicon) {
//                super.onPageStarted(view, url, favicon);
//
//                Handler handler = new Handler();
//                handler.postDelayed(new Runnable() {
//                    @Override
//                    public void run() {
//                        //Do something after 2s
//                    }
//                }, 2000);
//                progressBar.setVisibility(View.VISIBLE);
//            }
//
//            @Override
//            public void onPageFinished(WebView view, String url) {
//                super.onPageFinished(view, url);
//
//                view.loadUrl("javascript:window.Android.getHtml(document.getElementsByTagName('html')[0].innerHTML);");
//
//                progressBar.setVisibility(View.GONE);
//                swipeRefreshLayout.setRefreshing(false);
//            }
//        });

    }

    @Override
    public void onCreateContextMenu(ContextMenu contextMenu, View view, ContextMenu.ContextMenuInfo contextMenuInfo){
        super.onCreateContextMenu(contextMenu, view, contextMenuInfo);

        final WebView.HitTestResult webViewHitTestResult = webView.getHitTestResult();

        if (webViewHitTestResult.getType() == WebView.HitTestResult.IMAGE_TYPE || webViewHitTestResult.getType() == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {

            contextMenu.setHeaderTitle("이미지 다운로드");
            contextMenu.setHeaderIcon(R.mipmap.ic_launcher);

            String path = webViewHitTestResult.getExtra();
            String fileName = path.substring(path.lastIndexOf("/") + 1);

            Log.d("download", fileName);

            contextMenu.add(0, 1, 0, "내 휴대폰: 이미지 저장").setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem menuItem) {

                    String DownloadImageURL = webViewHitTestResult.getExtra();

                    if(URLUtil.isValidUrl(DownloadImageURL)){
                        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(DownloadImageURL));
                        request.allowScanningByMediaScanner();
                        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED); //Notify client once download is completed!
                        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
                        DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                        dm.enqueue(request);
                        Toast.makeText(getApplicationContext(), "[내장 메모리/Download] 폴더에 저장되었습니다.", Toast.LENGTH_LONG).show();
                    }
                    else {
                        Toast.makeText(MainActivity.this,"잘못된 요청입니다.",Toast.LENGTH_LONG).show();
                    }
                    return false;
                }
            });
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        //notificationAlert(intent);
        super.onNewIntent(intent);
    }

    private void notificationAlert(Intent intent) {
        if(alertDialog != null && alertDialog.isShowing()) {
            alertDialog.dismiss();
        }

        if(intent != null) {
            String title = "알림";
            if(intent.getStringExtra("title") != null)
                title = intent.getStringExtra("title");
            String body = intent.getStringExtra("body");
            String url = intent.getStringExtra("url");

            AlertDialog.Builder builder = new AlertDialog.Builder(this);

            builder.setTitle(title);
            builder.setMessage(body);

            if(url.toString().equals("@is_logout")) { //라이믹스 로그아웃 flag
//                FirebaseMessaging.getInstance().deleteToken();
//                FirebaseInstallations.getInstance().delete()
//                        .addOnCompleteListener(new OnCompleteListener<Void>() {
//                            @Override
//                            public void onComplete(@NonNull Task<Void> task) {
//                                if(task.isSuccessful()) {
//                                    Log.d("Installations", "Installation deleted");
//                                } else {
//                                    Log.e("Installations", "Unable to delete Installation");
//                                }
//                            }
//                        });

                builder.setPositiveButton("확인", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        System.exit(0);
                    }
                });
            } else {
                builder.setPositiveButton("확인", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        webView.loadUrl(url);
                    }
                });
            }
            builder.setNegativeButton("취소", null);
            alertDialog = builder.create();
            alertDialog.show();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig){
        super.onConfigurationChanged(newConfig);
    }

    //위치 권한 필요시 권한 요청 확인 메시지
    public boolean location_permission() {
        return ContextCompat.checkSelfPermission(MainActivity.this,Manifest.permission.ACCESS_FINE_LOCATION) == PERMISSION_GRANTED;
    }

    public boolean file_permission(){
//        Log.e("debug", String.valueOf(Build.VERSION.SDK_INT));

        if(Build.VERSION.SDK_INT >= 33 && (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) != PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PERMISSION_GRANTED)) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_AUDIO,
            }, 1);
            return false;
        } else if((Build.VERSION.SDK_INT >=23 && Build.VERSION.SDK_INT < 33) && (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PERMISSION_GRANTED)) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
            }, 1);
            return false;
        }else{
            return true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == 362) {
            if(grantResults.length > 0 && grantResults[0] == PERMISSION_GRANTED) {
                mCallBack.invoke(mOrigin, true, false);
            }
        }
    }

    private File create_image() throws IOException{
        @SuppressLint("SimpleDateFormat") String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "img_"+timeStamp+"_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return File.createTempFile(imageFileName,".jpg",storageDir);
    }

    @Override
    public boolean onKeyDown(int keyCode, @NonNull KeyEvent event){
        if(event.getAction() == KeyEvent.ACTION_DOWN){
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (webView.canGoBack()) {
                    webView.goBack();
                } else {
                    long curTime = System.currentTimeMillis();
                    long gapTime = curTime - backBtnTime;

                    if(0 <= gapTime && 2000 >= gapTime) {
                        ActivityCompat.finishAffinity(this);
                        System.exit(0);
                    } else {
                        backBtnTime = curTime;
                        Toast.makeText(this, "뒤로 버튼을 한번 더 누르시면 종료 됩니다.", Toast.LENGTH_SHORT).show();
                    }
                }
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }
}