package cj.instawall.plus;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InstaLinkReceiver extends Activity {
    public static final String TAG = "CJ";
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String url = getIntent().getStringExtra(Intent.EXTRA_TEXT);
        Log.d(TAG, "InstaLinkReceiver: " + url);
        Matcher matcher = Pattern.compile("instagram\\.com/(?:[^/]+/)?(?:p|reel|reels|tv)/([A-Za-z0-9_-]+)").matcher(url == null ? "" : url);
        if (!matcher.find()) {
            Log.e(TAG, "InstaLinkReceiver: not an instagram post link");
            Toast.makeText(this, "Not an Instagram post link", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        String code = matcher.group(1);
        Log.d(TAG, "Extracted code: " + code);
        Intent intent = new Intent(this, MainService.class);
        intent.setAction(MainService.WALLPAPER_FROM_CODE);
        intent.putExtra(Intent.EXTRA_TEXT, code);
        startForegroundService(intent);
        finish();
    }
}
