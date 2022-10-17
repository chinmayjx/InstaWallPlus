package cj.instawall.plus;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ViewActivity extends AppCompatActivity {
    public static final String TAG = "CJ";
    InstaClient instaClient;
    RecyclerView rv;
    Spinner gridFilter;
    RVAdapter rvAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view);
        try {
            instaClient = InstaClient.getInstance(this);
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (getSupportActionBar() != null) getSupportActionBar().hide();
        rv = findViewById(R.id.recycler_grid);
        rvAdapter = new RVAdapter(instaClient, rv);

        rv.setAdapter(rvAdapter);
        rv.setLayoutManager(new GridLayoutManager(this, 2));

        gridFilter = findViewById(R.id.grid_filter);
        ArrayAdapter adapter = new ArrayAdapter(this, android.R.layout.simple_spinner_item, rvAdapter.getAllDatasets());
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        gridFilter.setAdapter(adapter);
        gridFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                Log.d(TAG, "onItemSelected: " + i);
                rvAdapter.setCurrentDataset(i);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                Log.d(TAG, "onNothingSelected: ");
            }
        });
    }
}

class RVAdapter extends RecyclerView.Adapter<RVAdapter.RVHolder> {
    public static final String TAG = "CJ";
    InstaClient instaClient;
    final int MAX = 100;
    final int DEFAULT_RANDOM = 1000;
    RecyclerView rv;
    List<Boolean> requested = new ArrayList<>();
    List<Pair<Integer, Bitmap>> bitmaps;
    List<Path> paths;
    Consumer<Path> itemClickCallback = path -> {
        Log.d(TAG, "itemclick: " + path);
        instaClient.act_setWallpaper(path);
    };
    Handler handler;
    private Dataset currentDataset = null;
    ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new LIFOBlockingQueue());

    void setCurrentDataset(int pos) {
        currentDataset = Dataset.values()[pos];
        switch (currentDataset) {
            case Random:
                requested = Stream.generate(() -> false).limit(DEFAULT_RANDOM).collect(Collectors.toList());
                paths = Arrays.asList(new Path[DEFAULT_RANDOM]);
                break;
            case Downloaded:
                try {
                    File dir = new File(InstaClient.imagePath);
                    File[] files = dir.listFiles();
                    Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
                    paths = new ArrayList<Path>((int) dir.length());
                    for(File f: files) paths.add(Paths.get(f.getAbsolutePath()));

                    requested = Stream.generate(() -> false).limit(paths.size()).collect(Collectors.toList());
                } catch (Exception e) {
                    Log.e(TAG, "setCurrentDataset: " + Log.getStackTraceString(e));
                }
                break;
        }
        bitmaps = Stream.generate(() -> new Pair<Integer, Bitmap>(-1, null)).limit(MAX).collect(Collectors.toList());
        rv.scrollToPosition(0);
        notifyDataSetChanged();
    }

    enum Dataset {
        Random,
        Downloaded,
        Fails_quality_check
    }

    public ArrayList<String> getAllDatasets() {
        ArrayList<String> r = new ArrayList<>();
        Arrays.stream(Dataset.values()).forEach(x -> r.add(x.name().replace('_', ' ')));
        return r;
    }

    public RVAdapter(InstaClient instaClient, RecyclerView rv) {
        this.instaClient = instaClient;
        this.handler = new Handler();
        this.rv = rv;
    }

    public static class RVHolder extends RecyclerView.ViewHolder {
        ImageView iv;
        RVAdapter rvAdapter;

        public RVHolder(@NonNull View itemView, RVAdapter rvAdapter) {
            super(itemView);
            this.rvAdapter = rvAdapter;
            iv = itemView.findViewById(R.id.grid_iv);
            iv.setOnClickListener(v -> {
                Path p = rvAdapter.paths.get(getAdapterPosition());
                if (p != null) {
                    rvAdapter.itemClickCallback.accept(p);
                }
            });
        }
    }

    @NonNull
    @Override
    public RVHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.grid_item, parent, false);
        return new RVHolder(view, this);
    }

    void lazyLoadBitmap(RVHolder holder, int pos, Supplier<Path> getPath) {
        if (bitmaps.get(pos % MAX).first == pos) {
            handler.post(() -> holder.iv.setImageBitmap(bitmaps.get(pos % MAX).second));
        } else {
            handler.post(() -> holder.iv.setImageBitmap(null));
            if (!requested.get(pos)) {
                requested.set(pos, true);
                executor.execute(() -> {
                    try{
                        Path p = getPath.get();
                        paths.set(pos, p);
                        Bitmap b = BitmapFactory.decodeFile(p.toString());
                        if (b != null) {
                            int sw = 800;
                            int sh = (int) (sw * ((float) b.getHeight() / b.getWidth()));
                            b = Bitmap.createScaledBitmap(b, sw, sh, false);
                            int old = bitmaps.get(pos % MAX).first;
                            if (old >= 0) requested.set(old, false);
                            bitmaps.set(pos % MAX, new Pair<>(pos, b));
                            handler.post(() -> notifyItemChanged(pos));
                        }
                    }catch (Exception e){
                        Log.e(TAG, "lazyLoadBitmap: " + Log.getStackTraceString(e));
                    }
                });
            }
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RVHolder holder, int pos) {
        if (currentDataset == null) return;
        switch (currentDataset) {
            case Random:
                lazyLoadBitmap(holder, pos, () -> instaClient.act_getRandomImage());
                break;
            case Downloaded:
                lazyLoadBitmap(holder, pos, () -> paths.get(pos));
                break;

        }
    }

    @Override
    public int getItemCount() {
        return requested.size();
    }
}