package cj.instawall.plus;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

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
        Matcher matcher = Pattern.compile("www.instagram.com/p/([^/]*)/").matcher(url);
        matcher.find();
        String code = matcher.group(1);
        Log.d(TAG, "Extracted code: " + code);
        Intent intent = new Intent(this, MainService.class);
        intent.setAction(MainService.WALLPAPER_FROM_CODE);
        intent.putExtra(Intent.EXTRA_TEXT, code);
        startForegroundService(intent);
        finish();
    }
}
