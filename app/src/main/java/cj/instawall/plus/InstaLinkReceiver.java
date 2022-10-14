package cj.instawall.plus;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.Arrays;

public class InstaLinkReceiver extends Activity {
    public static final String TAG = "CJ";
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate: " + getIntent().getAction());
        Log.d(TAG, "onCreate: " + Arrays.toString(getIntent().getExtras().keySet().toArray()));
        Log.d(TAG, "onCreate: " + getIntent().getStringExtra(Intent.EXTRA_TEXT));
    }
}
