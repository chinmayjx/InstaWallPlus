package cj.instawall.plus;

import android.content.Context;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.CancellationSignal;
import android.util.Log;

import java.util.concurrent.Executors;

public class BiometricAuth {
    public static final String TAG = "CJ";
    Context context;
    BiometricManager biometricManager;

    public BiometricAuth(Context context) {
        this.context = context;
        biometricManager = (BiometricManager) context.getSystemService(Context.BIOMETRIC_SERVICE);
    }

    public void authenticate(String title, Runnable successCallback) {
        authenticate(title, successCallback, () -> {
        });
    }

    public void authenticate(String title, Runnable successCallback, Runnable failureCallback) {
        BiometricPrompt prompt = new BiometricPrompt.Builder(context).setTitle(title).setAllowedAuthenticators(BiometricManager.Authenticators.DEVICE_CREDENTIAL | BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.BIOMETRIC_WEAK).build();
        prompt.authenticate(new CancellationSignal(), Executors.newSingleThreadExecutor(), new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                Log.d(TAG, "onAuthenticationError: " + errorCode + " " + errString);
                failureCallback.run();
            }

            // help and failed attempts aren't the end, the prompt stays open for another try;
            // only an error (cancelled, too many attempts, ...) is a failure
            @Override
            public void onAuthenticationHelp(int helpCode, CharSequence helpString) {
                super.onAuthenticationHelp(helpCode, helpString);
                Log.d(TAG, "onAuthenticationHelp: " + helpString);
            }

            @Override
            public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                Log.d(TAG, "onAuthenticationSucceeded: ");
                successCallback.run();
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                Log.d(TAG, "onAuthenticationFailed: ");
            }
        });
    }
}
