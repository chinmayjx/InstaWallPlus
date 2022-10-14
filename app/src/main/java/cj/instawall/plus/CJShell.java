package cj.instawall.plus;

import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CJShell {
    public static final String TAG = "CJ";
    Runtime runtime;

    public CJShell() {
        runtime = Runtime.getRuntime();
    }

    public void shellCMD(String cmd) {
        try {
            Process proc = runtime.exec(new String[]{"sh", "-c", cmd});

            BufferedReader out = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            String outLine;
            while ((outLine = out.readLine()) != null) {
                Log.d(TAG, "out: " + outLine);
            }
            BufferedReader err = new BufferedReader(new InputStreamReader(proc.getErrorStream()));
            String errLine;
            while ((errLine = err.readLine()) != null) {
                Log.d(TAG, "err: " + errLine);
            }
        } catch (IOException e) {
            Log.e(TAG, "shellCMD: " + Log.getStackTraceString(e));
        }
    }
}
