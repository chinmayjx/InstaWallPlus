package cj.instawall.plus;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.MarginPageTransformer;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.snackbar.Snackbar;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cj.instawall.plus.core.Instagram;
import cj.instawall.plus.core.Library;
import cj.instawall.plus.core.Reddit;

/**
 * The full screen photo viewer: swipe through the grid's photos, zoom, see the other photos of
 * the post, and act on the one shown.
 */
class Gallery {
    static final String TAG = "CJ";
    final ViewActivity activity;
    final Core core;
    final RVAdapter grid;
    final View root, backdrop, bottom;
    final ViewPager2 pager;
    final MaterialToolbar toolbar;
    final RecyclerView strip;
    final LinearLayout actions;
    final Handler main = new Handler(Looper.getMainLooper());
    final ExecutorService decoder = Executors.newFixedThreadPool(2);
    final ExecutorService thumbs = Executors.newFixedThreadPool(2);
    final PageAdapter pages = new PageAdapter();
    final StripAdapter stripAdapter = new StripAdapter();
    /** pages showing another photo of their post than the grid's, picked in the filmstrip */
    final Map<Integer, Path> picked = new HashMap<>();
    final Map<Integer, ZoomImageView> bound = new HashMap<>();
    boolean chromeShown = true;
    int position = 0;
    /** the post of the current photo, null if unknown */
    Library.Located located;

    Gallery(ViewActivity activity, Core core, RVAdapter grid) {
        this.activity = activity;
        this.core = core;
        this.grid = grid;
        root = activity.findViewById(R.id.gallery);
        backdrop = activity.findViewById(R.id.gallery_backdrop);
        bottom = activity.findViewById(R.id.gallery_bottom);
        pager = activity.findViewById(R.id.gallery_pager);
        toolbar = activity.findViewById(R.id.gallery_toolbar);
        strip = activity.findViewById(R.id.gallery_strip);
        actions = activity.findViewById(R.id.gallery_actions);

        toolbar.setNavigationOnClickListener(v -> close());
        pager.setAdapter(pages);
        pager.setOffscreenPageLimit(1);
        pager.setPageTransformer(new MarginPageTransformer(dp(16)));
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int pos) {
                position = pos;
                // the neighbours start from the whole photo again
                for (Map.Entry<Integer, ZoomImageView> e : bound.entrySet())
                    if (e.getKey() != pos) e.getValue().resetZoom(false);
                // this can come in the middle of the pager's layout, where changing views gets them stuck unlaid
                main.post(() -> updateChrome());
            }
        });
        strip.setLayoutManager(new LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false));
        strip.setAdapter(stripAdapter);
        grid.onRemoved = pos -> {
            if (!isOpen()) return;
            shiftPicked(pos);
            pages.notifyItemRemoved(pos);
            if (grid.paths.isEmpty()) close();
            else main.post(this::updateChrome);
        };
    }

    int dp(float v) {
        return (int) (v * activity.getResources().getDisplayMetrics().density);
    }

    boolean isOpen() {
        return root.getVisibility() == View.VISIBLE;
    }

    void open(int pos) {
        picked.clear();
        pages.notifyDataSetChanged();
        pager.setCurrentItem(pos, false);
        position = pos;
        setChrome(true, false);
        backdrop.setAlpha(1);
        root.setVisibility(View.VISIBLE);
        root.setAlpha(0);
        pager.setScaleX(0.92f);
        pager.setScaleY(0.92f);
        root.animate().alpha(1).setDuration(160).start();
        pager.animate().scaleX(1).scaleY(1).setDuration(200).start();
        activity.onGalleryShown(true);
        main.post(this::updateChrome);
    }

    void close() {
        if (!isOpen()) return;
        // land the grid on the photo we ended up at
        if (position < grid.getItemCount()) activity.rv.scrollToPosition(position);
        root.animate().alpha(0).setDuration(140).withEndAction(() -> {
            root.setVisibility(View.GONE);
            root.setAlpha(1);
            bound.clear();
            pager.setAdapter(null);
            pager.setAdapter(pages);
        }).start();
        activity.onGalleryShown(false);
    }

    /** back: zoomed out first, then closed */
    void back() {
        ZoomImageView z = bound.get(position);
        if (z != null && z.isZoomed()) z.resetZoom(true);
        else close();
    }

    Path shown(int pos) {
        Path p = picked.get(pos);
        if (p != null) return p;
        return pos >= 0 && pos < grid.paths.size() ? grid.paths.get(pos) : null;
    }

    private void shiftPicked(int removed) {
        Map<Integer, Path> next = new HashMap<>();
        for (Map.Entry<Integer, Path> e : picked.entrySet()) {
            if (e.getKey() < removed) next.put(e.getKey(), e.getValue());
            else if (e.getKey() > removed) next.put(e.getKey() - 1, e.getValue());
        }
        picked.clear();
        picked.putAll(next);
    }

    // top bar, filmstrip, actions -----------------------------------------
    void updateChrome() {
        Path p = shown(position);
        located = p == null ? null : core.library.locate(p);
        RVAdapter.Dataset d = grid.getCurrentDataset();
        String source = located != null ? located.source.label() : "";
        if (d == RVAdapter.Dataset.Random) {
            toolbar.setTitle(source.isEmpty() ? "Random" : source);
            toolbar.setSubtitle(null);
        } else {
            toolbar.setTitle(String.format(Locale.US, "%d of %d", position + 1, grid.paths.size()));
            toolbar.setSubtitle(source.isEmpty() ? d.toString() : source + " · " + d);
        }
        boolean multi = located != null && located.post.images.size() > 1 && d != RVAdapter.Dataset.Trash;
        strip.setVisibility(multi ? View.VISIBLE : View.GONE);
        if (multi) {
            stripAdapter.show(located);
            strip.scrollToPosition(located.index);
            toolbar.setSubtitle(toolbar.getSubtitle() == null
                    ? String.format(Locale.US, "Photo %d of %d", located.index + 1, located.post.images.size())
                    : toolbar.getSubtitle() + String.format(Locale.US, " · %d/%d", located.index + 1, located.post.images.size()));
        }
        buildActions(d);
    }

    private void buildActions(RVAdapter.Dataset d) {
        actions.removeAllViews();
        if (d == RVAdapter.Dataset.Trash) {
            addAction("Restore", R.drawable.ic_restore, v -> {
                Path p = shown(position);
                if (p == null) return;
                core.restore(p);
                grid.removeIndex(position);
                snack("Restored");
            });
            addAction("Delete forever", R.drawable.ic_delete_forever, v -> {
                Path p = shown(position);
                if (p == null) return;
                new MaterialAlertDialogBuilder(activity)
                        .setTitle("Delete permanently?")
                        .setMessage("The photo is removed from this phone and won't be used as a wallpaper again.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Delete", (dl, w) -> {
                            core.deletePermanently(p);
                            grid.removeIndex(position);
                        }).show();
            });
            return;
        }
        addAction("Wallpaper", R.drawable.ic_wallpaper, v -> {
            Path p = shown(position);
            if (p == null) return;
            core.setWallpaper(p);
            snack("Setting as wallpaper");
        });
        if (located != null) addAction("Open post", R.drawable.ic_open_in_new, v -> openPost());
        addAction("Delete", R.drawable.ic_delete, v -> deleteShown());
    }

    private void addAction(String label, int icon, View.OnClickListener l) {
        MaterialButton b = (MaterialButton) LayoutInflater.from(activity).inflate(R.layout.gallery_action, actions, false);
        b.setText(label);
        b.setIconResource(icon);
        b.setOnClickListener(l);
        actions.addView(b);
    }

    private void deleteShown() {
        Path p = shown(position);
        if (p == null) return;
        core.delete(p);
        if (picked.containsKey(position)) {
            // a photo picked from the post's strip: back to the grid's photo of this page
            picked.remove(position);
            pages.notifyItemChanged(position);
            updateChrome();
        } else {
            grid.removeIndex(position);
        }
        snack("Moved to trash");
    }

    private void openPost() {
        if (located == null) return;
        String url = Reddit.SOURCE_ID.equals(located.source.id)
                ? "https://www.reddit.com/comments/" + located.post.id
                : "https://www.instagram.com/p/" + Instagram.mediaIdToShortcode(located.post.id) + "/";
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(activity, "No app to open " + url, Toast.LENGTH_SHORT).show();
        }
    }

    private void snack(String msg) {
        Snackbar.make(root, msg, Snackbar.LENGTH_SHORT).setAnchorView(bottom).show();
    }

    void toggleChrome() {
        setChrome(!chromeShown, true);
    }

    void setChrome(boolean show, boolean animate) {
        chromeShown = show;
        for (View v : new View[]{toolbar, bottom}) {
            v.animate().cancel();
            if (show) v.setVisibility(View.VISIBLE);
            if (animate) {
                v.animate().alpha(show ? 1 : 0).setDuration(150)
                        .withEndAction(() -> v.setVisibility(chromeShown ? View.VISIBLE : View.INVISIBLE)).start();
            } else {
                v.setAlpha(show ? 1 : 0);
                v.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
            }
        }
        float top = show ? toolbar.getHeight() : 0;
        for (ZoomImageView z : bound.values()) {
            z.mapTop = top;
            z.invalidate();
        }
    }

    // decoding ----------------------------------------------------------
    /** big enough to zoom into, small enough for memory (4k reddit wallpapers get halved) */
    static Bitmap decodeFull(Path p) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(p.toString(), o);
        if (o.outWidth <= 0) return null;
        int sample = 1;
        while (o.outWidth / sample > 4096 || o.outHeight / sample > 4096 || (long) (o.outWidth / sample) * (o.outHeight / sample) > 12_000_000L)
            sample *= 2;
        BitmapFactory.Options d = new BitmapFactory.Options();
        d.inSampleSize = sample;
        return BitmapFactory.decodeFile(p.toString(), d);
    }

    // pages ---------------------------------------------------------------
    class PageHolder extends RecyclerView.ViewHolder {
        final ZoomImageView image;
        final CircularProgressIndicator spinner;
        int boundPos = -1, token = 0;

        PageHolder(FrameLayout v, ZoomImageView image, CircularProgressIndicator spinner) {
            super(v);
            this.image = image;
            this.spinner = spinner;
        }
    }

    class PageAdapter extends RecyclerView.Adapter<PageHolder> {
        @NonNull
        @Override
        public PageHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            FrameLayout f = new FrameLayout(activity);
            f.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            ZoomImageView z = new ZoomImageView(activity);
            f.addView(z, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            CircularProgressIndicator s = new CircularProgressIndicator(activity);
            s.setIndeterminate(true);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.gravity = android.view.Gravity.CENTER;
            f.addView(s, lp);
            PageHolder h = new PageHolder(f, z, s);
            z.listener = new ZoomImageView.Listener() {
                @Override
                public void onSingleTap() {
                    toggleChrome();
                }

                @Override
                public void onZoomChanged(boolean zoomed) {
                    // zooming in is for looking at the photo, get out of the way
                    if (zoomed && chromeShown && h.boundPos == position) setChrome(false, true);
                }

                @Override
                public void onDismissProgress(float progress) {
                    backdrop.setAlpha(1 - progress);
                    float chrome = chromeShown ? Math.max(0, 1 - progress * 3) : 0;
                    toolbar.setAlpha(chrome);
                    bottom.setAlpha(chrome);
                }

                @Override
                public void onDismiss() {
                    close();
                }
            };
            return h;
        }

        @Override
        public void onBindViewHolder(@NonNull PageHolder h, int pos) {
            // the same page again (another photo of the post picked): keep showing the old one until the new is ready
            if (h.boundPos != pos) h.image.setBitmap(null);
            h.boundPos = pos;
            int token = ++h.token;
            bound.put(pos, h.image);
            h.image.mapTop = chromeShown ? toolbar.getHeight() : 0;
            h.spinner.setVisibility(View.VISIBLE);
            Path known = shown(pos);
            boolean random = grid.getCurrentDataset() == RVAdapter.Dataset.Random;
            decoder.execute(() -> {
                Path p = known;
                try {
                    if (p == null && random) {
                        // a random tile the grid hasn't filled yet
                        p = core.randomImage();
                        grid.setPath(pos, p);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "gallery: " + e);
                }
                Bitmap b = p == null ? null : decodeFull(p);
                Path fp = p;
                main.post(() -> {
                    if (h.token != token) return;
                    h.spinner.setVisibility(View.GONE);
                    if (b == null) {
                        Toast.makeText(activity, "Couldn't open this photo", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    h.image.setBitmap(b);
                    if (known == null && fp != null && pos == position) updateChrome();
                });
            });
        }

        @Override
        public void onViewRecycled(@NonNull PageHolder h) {
            if (bound.get(h.boundPos) == h.image) bound.remove(h.boundPos);
            h.boundPos = -1;
            h.token++;
            h.image.setBitmap(null);
        }

        @Override
        public int getItemCount() {
            return grid.paths == null ? 0 : grid.paths.size();
        }
    }

    // the post's other photos ------------------------------------------------
    class StripHolder extends RecyclerView.ViewHolder {
        final ImageView iv;
        final View selected;
        int token;

        StripHolder(View v) {
            super(v);
            iv = v.findViewById(R.id.strip_iv);
            selected = v.findViewById(R.id.strip_selected);
        }
    }

    class StripAdapter extends RecyclerView.Adapter<StripHolder> {
        Library.Located post;
        final Map<String, Bitmap> cache = new HashMap<>();
        int tokens = 0;

        void show(Library.Located p) {
            if (post == null || !post.post.id.equals(p.post.id) || post.source != p.source) cache.clear();
            post = p;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public StripHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            StripHolder h = new StripHolder(LayoutInflater.from(activity).inflate(R.layout.strip_item, parent, false));
            h.itemView.setOnClickListener(v -> pick(h.getBindingAdapterPosition()));
            return h;
        }

        @Override
        public void onBindViewHolder(@NonNull StripHolder h, int i) {
            Library.Located p = post;
            h.selected.setVisibility(i == p.index ? View.VISIBLE : View.GONE);
            h.iv.setAlpha(p.source.trash.isDeleted(p.post.id, p.post.images.get(i).id) ? 0.3f : 1f);
            String key = p.post.id + "/" + i;
            Bitmap c = cache.get(key);
            h.iv.setImageBitmap(c);
            if (c != null) return;
            int token = ++tokens;
            h.token = token;
            thumbs.execute(() -> {
                try {
                    // downloads the photos of the post that aren't on the phone yet
                    Path f = p.source.fetch(p.post, i);
                    Bitmap b = WallpaperSetter.decode(f, dp(56), dp(64));
                    main.post(() -> {
                        cache.put(key, b);
                        if (h.token == token) h.iv.setImageBitmap(b);
                    });
                } catch (Exception e) {
                    Log.e(TAG, "strip: " + e);
                }
            });
        }

        @Override
        public int getItemCount() {
            return post == null ? 0 : post.post.images.size();
        }

        void pick(int i) {
            Library.Located p = post;
            if (p == null || i < 0 || i == p.index) return;
            int page = position;
            thumbs.execute(() -> {
                try {
                    Path f = p.source.fetch(p.post, i);
                    main.post(() -> {
                        if (page != position) return;
                        picked.put(page, f);
                        pages.notifyItemChanged(page);
                        updateChrome();
                    });
                } catch (Exception e) {
                    main.post(() -> Toast.makeText(activity, "Couldn't download that photo", Toast.LENGTH_SHORT).show());
                }
            });
        }
    }
}
