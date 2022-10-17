package cj.instawall.plus;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class RVAdapter extends RecyclerView.Adapter<RVAdapter.RVHolder> {
    public static final String TAG = "CJ";
    InstaClient instaClient;
    final int MAX = 100;
    final int DEFAULT_RANDOM = 1000;
    RecyclerView rv;
    List<Boolean> requested = new ArrayList<>();
    Set<Integer> selected = new HashSet<>();
    List<Pair<Integer, Bitmap>> bitmaps;
    List<Path> paths;
    ClickAction currentClickAction = ClickAction.Set_wallpaper;
    Runnable onEnterSelected, onExitSelected;
    Consumer<Integer> itemClickCallback = pos -> {
        if (selected.size() > 0) {
            toggleSelection(pos);
            return;
        }
        if (paths.get(pos) == null) return;
        switch (currentClickAction) {
            case Set_wallpaper:
                instaClient.act_setWallpaper(paths.get(pos));
                break;
            case Delete:
                Log.d(TAG, "delete: " + paths.get(pos));
                break;
        }
    };
    Handler handler;
    private Dataset currentDataset = null;
    ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new LIFOBlockingQueue());


    enum ClickAction {
        Set_wallpaper,
        Delete
    }

    public ArrayList<String> getClickActions() {
        ArrayList<String> r = new ArrayList<>();
        Arrays.stream(ClickAction.values()).forEach(x -> r.add(x.name().replace('_', ' ')));
        return r;
    }

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
                    for (File f : files) paths.add(Paths.get(f.getAbsolutePath()));

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

    public void clearSelection() {
        Integer[] tmp = new Integer[selected.size()];
        selected.toArray(tmp);
        selected.clear();
        for (Integer i : tmp) {
            notifyItemChanged(i);
        }
        if(onExitSelected != null) onExitSelected.run();
    }

    public void toggleSelection(int pos) {
        if (selected.contains(pos)) {
            selected.remove(pos);
            if(selected.size() == 0 && onExitSelected != null) onExitSelected.run();
        } else {
            if(selected.size() == 0 && onEnterSelected != null) onEnterSelected.run();
            selected.add(pos);
        }
        notifyItemChanged(pos);
    }

    public static class RVHolder extends RecyclerView.ViewHolder {
        ImageView iv;
        View overlay;
        RVAdapter rvAdapter;

        public RVHolder(@NonNull View itemView, RVAdapter rvAdapter) {
            super(itemView);
            this.rvAdapter = rvAdapter;
            iv = itemView.findViewById(R.id.grid_iv);
            overlay = itemView.findViewById(R.id.grid_overlay);
            Log.d(TAG, "RVHolder: " + rvAdapter.selected);
            iv.setOnClickListener(v -> {
                rvAdapter.itemClickCallback.accept(getAdapterPosition());
            });
            iv.setOnLongClickListener(v -> {
                rvAdapter.toggleSelection(getAdapterPosition());
                return true;
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
                    try {
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
                    } catch (Exception e) {
                        Log.e(TAG, "lazyLoadBitmap: " + Log.getStackTraceString(e));
                    }
                });
            }
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RVHolder holder, int pos) {
        if (currentDataset == null) return;
        if (selected.contains(pos)) {
            holder.overlay.setVisibility(View.VISIBLE);
        } else {
            holder.overlay.setVisibility(View.GONE);
        }
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