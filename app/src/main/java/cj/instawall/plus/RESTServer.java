package cj.instawall.plus;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import org.json.JSONException;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PipedOutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RESTServer {
    public static final String TAG = "CJ";
    InstaClient instaClient;
    ExecutorService executor;

    class ResponseWriter {
        PrintWriter printer;
        BufferedOutputStream out;

        public ResponseWriter(Socket client, int code) throws IOException {
            out = new BufferedOutputStream(client.getOutputStream());
            printer = new PrintWriter(out);
            printer.println("HTTP/1.1 " + code + " OK");
        }

        public void header(String name, String value) {
            printer.println(name + ": " + value);
        }

        public void body(String res) {
            header("content-length", String.valueOf(res.length()));
            printer.println("");
            printer.print(res);
            printer.close();
        }

        public void body(Path path) throws IOException {
            header("content-length", String.valueOf(Files.size(path)));
            printer.println("");
            printer.flush();
            Files.copy(path, out);
            out.close();
        }

        public void body(InputStream in, long size) throws IOException {
            header("content-length", String.valueOf(size));
            printer.println("");
            printer.flush();
            byte[] buf = new byte[1024];
            int cnt;
            while ((cnt = in.read(buf)) != -1) {
                out.write(buf, 0, cnt);
            }
            out.close();
        }
    }

    public RESTServer(InstaClient instaClient) {
        this.instaClient = instaClient;
        executor = Executors.newCachedThreadPool();
    }

    public void startListening() {
        executor.execute(this::start);
    }

    public static boolean isRunning = false;

    private void start() {
        Log.d(TAG, "RESTServer started");
        try {
            ServerSocket server = new ServerSocket(4444);
            isRunning = true;
            while (true) {
                Socket client = server.accept();
                executor.execute(() -> handleClient(client));
                Log.d(TAG, "connected to client");
            }
        } catch (Exception e) {
            Log.e(TAG, "RESTServer crashed: " + Log.getStackTraceString(e));
        }
    }

    void handleClient(Socket client) {
        try {
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(client.getInputStream()));
            String[] title = in.readLine().trim().split(" ");
            HashMap<String, String> headers = new HashMap<>();
            String line;
            while ((line = in.readLine()) != null) {
                if (line.isEmpty()) break;
                int ix = line.indexOf(':');
                headers.put(line.substring(0, ix).toLowerCase(Locale.ROOT), line.substring(ix + 1).trim());
            }
            String body = "";
            if (headers.containsKey("content-length")) {
                int contentLength = Integer.parseInt(headers.get("content-length"));
                StringBuilder sb = new StringBuilder();
                while (contentLength-- > 0) {
                    sb.append((char) in.read());
                }
                body = sb.toString();
            }
            Log.d(TAG, "title: " + Arrays.toString(title));
//            Log.d(TAG, "headers: " + headers);
//            Log.d(TAG, "body: " + body);
            processRequest(client, title, headers, body);
        } catch (Exception e) {
            Log.e(TAG, "handleClient: " + Log.getStackTraceString(e));
        }
    }

    void processRequest(Socket client, String[] title, HashMap<String, String> headers, String body) throws IOException, JSONException {
        String path = title[1];
        if (path.charAt(0) == '/') path = path.substring(1);
        if (path.charAt(path.length() - 1) == '/') path = path.substring(0, path.length() - 1);
        String[] pathTokens = path.split("/");
        if (title[0].equals("GET")) {
            switch (pathTokens[0]) {
                case "random-image":
                    sendFile(client, instaClient.act_getRandomImage());
                    break;
                case "image":
                    sendFile(client, Paths.get(instaClient.imagePath, pathTokens[1]));
                    break;
                case "collage":
                    sendString(client, "collage");
                    break;
                case "remove-border":
                    sendBitmap(client, CJImageUtil.removeWhiteBorder(instaClient.bitmapByFileName(pathTokens[1])));
                    break;
                case "set-wallpaper":
                    instaClient.act_setWallpaper(instaClient.pathByFileName(pathTokens[1]));
                    sendString(client, "OK boss");
                    break;
                case "wallpaper-by-code":
                    instaClient.act_setWallpaperFromCode(pathTokens[1]);
                    sendString(client, "OK boss");
                    break;
                default:
                    sendString(client, "404 NOT FOUND");
            }
        }
    }

    void sendString(Socket client, String response) throws IOException {
        ResponseWriter writer = new ResponseWriter(client, 200);
        writer.body(response);
    }

    void sendFile(Socket client, Path path) throws IOException {
        ResponseWriter writer = new ResponseWriter(client, 200);
        writer.header("content-type", "image/jpeg");
        writer.body(path);
    }

    void sendBitmap(Socket client, Bitmap img) throws IOException {
        ResponseWriter writer = new ResponseWriter(client, 200);
        writer.header("content-type", "image/jpeg");
        CountWriteReadStream out = new CountWriteReadStream();
        img.compress(Bitmap.CompressFormat.JPEG, 100, out);
        out.close();
        writer.body(out.getInputStream(), out.getCount());
    }
}
