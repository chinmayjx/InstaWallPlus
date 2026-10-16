package cj.instawall.plus;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
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


import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
    Core core;
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

    /** told when a photo leaves the grid, so the gallery can follow */
    Consumer<Integer> onRemoved;

    /** a random tile's photo, picked by the gallery before the grid got to it */
    synchronized void setPath(int pos, Path p) {
        if (pos < paths.size() && paths.get(pos) == null) paths.set(pos, p);
    }

    void removeIndex(int pos) {
        requested.remove(pos);
        paths.remove(pos);
        notifyItemRemoved(pos);
        if (onRemoved != null) onRemoved.accept(pos);
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
        if (p == null && action != ClickAction.View) return;
        switch (action) {
            case View:
                activity.gallery.open(pos);
                break;
            case Select:
                toggleSelection(pos);
                break;
            case Set_wallpaper:
                core.setWallpaper(p);
                break;
            case Delete:
                core.delete(p);
                removeIndex(pos);
                break;
            case Restore:
                core.restore(p);
                removeIndex(pos);
                break;
            case Permanently_delete:
                new MaterialAlertDialogBuilder(activity)
                        .setTitle("Delete permanently?")
                        .setMessage("The photo is removed from this phone and won't be used as a wallpaper again.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Delete", (d, w) -> {
                            core.deletePermanently(p);
                            removeIndex(pos);
                        })
                        .show();
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

    void setCurrentDataset(Dataset dataset) {
        try {
            currentDataset = dataset;
            switch (dataset) {
                case Random:
                    // filled lazily with random images
                    paths = new ArrayList<>(Arrays.asList(new Path[core.loggedIn() ? DEFAULT_RANDOM : 0]));
                    break;
                case Downloaded:
                    paths = core.library.downloaded();
                    break;
                case Recent_wallpapers:
                    paths = core.recentWallpapers();
                    break;
                case Trash:
                    paths = core.library.trash();
                    break;
            }
            paths = new ArrayList<>(paths);
            requested = Stream.generate(() -> false).limit(paths.size()).collect(Collectors.toList());
            updateCurrentClickActions();
            bitmaps = Stream.generate(() -> new Pair<Integer, Bitmap>(-1, null)).limit(MAX).collect(Collectors.toList());
            rv.scrollToPosition(0);
            notifyDataSetChanged();
        } catch (Exception e) {
            Log.e(TAG, "setCurrentDataset: " + Log.getStackTraceString(e));
        }
    }

    Dataset getCurrentDataset() {
        return currentDataset == null ? Dataset.Random : currentDataset;
    }

    enum Dataset {
        Random,
        Downloaded,
        Recent_wallpapers,
        Trash;

        @NonNull
        @Override
        public String toString() {
            return this.name().replace('_', ' ');
        }
    }

    public RVAdapter(Core core, ViewActivity activity) {
        this.core = core;
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
        String[] labels = tmp.stream().map(ClickAction::toString).toArray(String[]::new);
        new MaterialAlertDialogBuilder(activity)
                .setItems(labels, (dialog, i) -> dispatchAction(tmp.get(i), pos))
                .show();
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
                        if (p == null) return;
                        synchronized (this) {
                            if (pos < paths.size()) paths.set(pos, p);
                        }
                        // big (4k reddit) images: decode near the tile size
                        Bitmap b = WallpaperSetter.decode(p, 800, 800);
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
                // the gallery may have picked this tile's photo already
                lazyLoadBitmap(holder, pos, () -> {
                    Path known = paths.get(pos);
                    return known != null ? known : randomImage();
                });
                break;
            case Downloaded:
            case Trash:
            case Recent_wallpapers:
                lazyLoadBitmap(holder, pos, () -> paths.get(pos));
                break;

        }
    }

    private Path randomImage() {
        try {
            return core.randomImage();
        } catch (Exception e) {
            Log.e(TAG, "randomImage: " + e);
            return null;
        }
    }

    @Override
    public int getItemCount() {
        return requested.size();
    }
}