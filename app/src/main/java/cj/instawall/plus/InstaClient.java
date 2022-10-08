package cj.instawall.plus;

import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

public class InstaClient {
    final String TAG = "CJ";
    String cookies;
    Map<String, String> headers;

    public InstaClient(String cookies, Map<String, String> headers) {
        this.cookies = cookies;
        this.headers = headers;
    }

    public void getUserInfo() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL("https://i.instagram.com/api/v1/users/web_profile_info/?username=chinmayjain08");
                    HttpURLConnection con = (HttpURLConnection) url.openConnection();
                    headers.keySet().forEach(h -> {
                        con.setRequestProperty(h, headers.get(h));
                    });
                    con.setRequestProperty("cookie", cookies);
                    String res = new BufferedReader(new InputStreamReader(con.getInputStream(),
                            StandardCharsets.UTF_8)).lines()
                            .collect(Collectors.joining("\n"));
                    Log.d(TAG, res);
                } catch (Exception e) {
                    Log.e(TAG, "getUserInfo: ", e);
                }

            }
        }).start();
    }

    public void getSavedPosts() {
        getUserInfo();
    }
}
