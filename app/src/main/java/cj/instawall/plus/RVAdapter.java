package cj.instawall.plus;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONObject;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
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
    ClickAction currentClickAction;
    ArrayList<ClickAction> currentClickActions = new ArrayList<>();
    Runnable onEnterSelected, onExitSelected;
    ViewActivity activity;

    void removeIndex(int pos) {
        requested.remove(pos);
        paths.remove(pos);
        notifyItemRemoved(pos);
        int i = pos + 1;
        while (bitmaps.get(i % MAX).first - pos == 1) {
            bitmaps.set(pos % MAX, new Pair<>(bitmaps.get(i % MAX).first - 1, bitmaps.get(i % MAX).second));
            pos = i;
            i = pos + 1;
        }
        bitmaps.set(pos % MAX, new Pair<>(-1, null));
    }

    Consumer<Integer> itemClickCallback = pos -> {
        if (selected.size() > 0) {
            toggleSelection(pos);
            return;
        }
        if (currentClickAction == null) return;
        dispatchAction(currentClickAction, pos);

    };

    public void dispatchAction(ClickAction action, int pos) {
        Path p = paths.get(pos);
        if (p == null) return;
        switch (action) {
            case View:
                activity.showImageViewer();
                activity.imageViewer.loadBitmap(BitmapFactory.decodeFile(p.toString()));
                break;
            case Select:
                toggleSelection(pos);
                break;
            case Set_wallpaper:
                instaClient.act_setWallpaper(p);
                break;
            case Delete:
                instaClient.deleteImage(p);
                removeIndex(pos);
                break;
            case Restore:
                instaClient.restoreImage(p);
                removeIndex(pos);
                break;
        }
    }

    Handler handler;
    private Dataset currentDataset = null;
    ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new LIFOBlockingQueue());


    enum ClickAction {
        View,
        Select,
        Set_wallpaper,
        Delete,
        Restore,
        Permanently_delete;

        @NonNull
        @Override
        public String toString() {
            return this.name().replace('_', ' ');
        }
    }

    public void updateCurrentClickActions() {
        currentClickActions.clear();
        if (currentDataset != null)
            switch (currentDataset) {
                case Random:
                case Downloaded:
                case Recent_wallpapers:
                    currentClickActions.addAll(Arrays.asList(ClickAction.View, ClickAction.Set_wallpaper, ClickAction.Delete));
                    break;
                case Trash:
                    currentClickActions.addAll(Arrays.asList(ClickAction.View, ClickAction.Restore, ClickAction.Permanently_delete));
                    break;
                default:
                    currentClickActions.addAll(Arrays.asList(ClickAction.View, ClickAction.Set_wallpaper));
            }
    }

    void setCurrentClickAction(int pos) {
        if (pos < 0) return;
        currentClickAction = currentClickActions.get(pos);
    }

    void setCurrentDataset(int pos) {
        if (pos < 0) return;
        try {
            currentDataset = Dataset.values()[pos];
            switch (currentDataset) {
                case Random:
                    requested = Stream.generate(() -> false).limit(DEFAULT_RANDOM).collect(Collectors.toList());
                    paths = new ArrayList<>(Arrays.asList(new Path[DEFAULT_RANDOM]));
                    break;
                case Downloaded:
                    File dir = new File(InstaClient.imagePath);
                    File[] files = dir.listFiles();
                    Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
                    paths = new ArrayList<Path>((int) dir.length());
                    for (File f : files) paths.add(Paths.get(f.getAbsolutePath()));

                    requested = Stream.generate(() -> false).limit(paths.size()).collect(Collectors.toList());
                    break;
                case Recent_wallpapers:
                    paths = new ArrayList<>();
                    Files.readAllLines(Paths.get(InstaClient.filesDir, InstaClient.username, "recent_wallpapers.txt")).forEach(l -> paths.add(Paths.get(l)));
                    Collections.reverse(paths);
                    requested = Stream.generate(() -> false).limit(paths.size()).collect(Collectors.toList());
                    break;
                case Trash:
                    ArrayList<File> f1 = new ArrayList<>();
                    Iterator<String> it = InstaClient.getDeletedImages().keys();
                    while (it.hasNext()) {
                        JSONObject jo = InstaClient.getDeletedImages().getJSONObject(it.next());
                        File tmp = new File(Paths.get(InstaClient.deletedImagePath, jo.getString("fileName")).toString());
                        if (!tmp.exists()) continue;
                        tmp.setLastModified(jo.getLong("ts"));
                        f1.add(tmp);
                    }
                    f1.sort(Comparator.comparingLong(File::lastModified).reversed());
                    paths = new ArrayList<Path>((int) f1.size());
                    for (File f : f1) paths.add(Paths.get(f.getAbsolutePath()));
                    requested = Stream.generate(() -> false).limit(paths.size()).collect(Collectors.toList());
                    break;

            }
            updateCurrentClickActions();
            bitmaps = Stream.generate(() -> new Pair<Integer, Bitmap>(-1, null)).limit(MAX).collect(Collectors.toList());
            rv.scrollToPosition(0);
            notifyDataSetChanged();
        } catch (Exception e) {
            Log.e(TAG, "setCurrentDataset: " + Log.getStackTraceString(e));
        }
    }

    enum Dataset {
        Random,
        Downloaded,
        Recent_wallpapers,
        Fails_quality_check,
        Trash;

        @NonNull
        @Override
        public String toString() {
            return this.name().replace('_', ' ');
        }
    }

    public RVAdapter(InstaClient instaClient, ViewActivity activity) {
        this.instaClient = instaClient;
        this.handler = new Handler();
        this.rv = activity.rv;
        this.activity = activity;
    }

    public void clearSelection() {
        Integer[] tmp = new Integer[selected.size()];
        selected.toArray(tmp);
        selected.clear();
        for (Integer i : tmp) {
            notifyItemChanged(i);
        }
        if (onExitSelected != null) onExitSelected.run();
    }

    public void toggleSelection(int pos) {
        if (selected.contains(pos)) {
            selected.remove(pos);
            if (selected.size() == 0 && onExitSelected != null) onExitSelected.run();
        } else {
            if (selected.size() == 0 && onEnterSelected != null) onEnterSelected.run();
            selected.add(pos);
        }
        notifyItemChanged(pos);
    }

    public void showActionDialog(int pos) {
        ArrayList<ClickAction> tmp = new ArrayList<>(currentClickActions);
        tmp.add(0, ClickAction.Select);
        new AlertDialog.Builder(activity).setTitle(paths.get(pos).getFileName().toString())
                .setAdapter(new ArrayAdapter<ClickAction>(rv.getContext(), android.R.layout.simple_list_item_1, tmp), (dialog, i) -> {
                    dispatchAction(tmp.get(i), pos);
                }).create().show();
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
            iv.setOnClickListener(v -> {
                rvAdapter.itemClickCallback.accept(getAdapterPosition());
            });
            iv.setOnLongClickListener(v -> {
//                rvAdapter.toggleSelection(getAdapterPosition());
                rvAdapter.showActionDialog(getAdapterPosition());
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
                            b = CJImageUtil.removeWhiteBorder(Bitmap.createScaledBitmap(b, sw, sh, false));
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
            case Trash:
            case Recent_wallpapers:
                lazyLoadBitmap(holder, pos, () -> paths.get(pos));
                break;

        }
    }

    @Override
    public int getItemCount() {
        return requested.size();
    }
}