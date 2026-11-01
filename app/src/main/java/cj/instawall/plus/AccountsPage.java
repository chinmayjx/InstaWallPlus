package cj.instawall.plus;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Locale;

import cj.instawall.plus.core.Reddit;

/** Every Instagram and Reddit account, with add, open, log out and remove. */
class AccountsPage {
    final ViewActivity activity;
    final Core core;
    final View root;
    final LinearLayout instagramList, redditList;
    /** an account was logged out or removed while open: the grid reloads on close */
    boolean changed = false;

    AccountsPage(ViewActivity activity, Core core) {
        this.activity = activity;
        this.core = core;
        root = activity.findViewById(R.id.accounts_page);
        instagramList = activity.findViewById(R.id.instagram_accounts);
        redditList = activity.findViewById(R.id.reddit_accounts);
        ((MaterialToolbar) activity.findViewById(R.id.accounts_toolbar)).setNavigationOnClickListener(v -> close());
        activity.findViewById(R.id.add_instagram).setOnClickListener(v -> activity.addInstagramAccount());
        activity.findViewById(R.id.add_reddit).setOnClickListener(v -> activity.addRedditAccount());
    }

    boolean isOpen() {
        return root.getVisibility() == View.VISIBLE;
    }

    void open() {
        refresh();
        root.setVisibility(View.VISIBLE);
        activity.onOverlayShown(true);
    }

    void close() {
        root.setVisibility(View.GONE);
        activity.onOverlayShown(false);
        if (changed) activity.reloadGrid();
        changed = false;
    }

    static String label(String service, String user) {
        return (Reddit.SOURCE_ID.equals(service) ? "u/" : "@") + user;
    }

    void refresh() {
        instagramList.removeAllViews();
        redditList.removeAllViews();
        for (String[] a : core.accounts.allAccounts()) {
            LinearLayout list = Reddit.SOURCE_ID.equals(a[0]) ? redditList : instagramList;
            list.addView(row(a[0], a[1]));
        }
        if (instagramList.getChildCount() == 0) instagramList.addView(empty());
        if (redditList.getChildCount() == 0) redditList.addView(empty());
    }

    private View empty() {
        TextView t = new TextView(activity);
        t.setText("No accounts yet");
        t.setTextColor(activity.getColor(R.color.iw_on_surface_dim));
        t.setPadding(0, 12, 0, 12);
        return t;
    }

    private View row(String service, String user) {
        View v = LayoutInflater.from(activity).inflate(R.layout.account_row, instagramList, false);
        ((ImageView) v.findViewById(R.id.account_icon)).setImageResource(Reddit.SOURCE_ID.equals(service) ? R.drawable.ic_reddit : R.drawable.ic_instagram);
        ((TextView) v.findViewById(R.id.account_name)).setText(label(service, user));
        int posts = core.sourceFor(service, user).posts.size();
        ((TextView) v.findViewById(R.id.account_info)).setText(String.format(Locale.US, "%,d saved posts", posts));

        v.findViewById(R.id.account_more).setOnClickListener(b -> {
            PopupMenu m = new PopupMenu(activity, b);
            m.getMenu().add(0, 0, 0, "Open " + (Reddit.SOURCE_ID.equals(service) ? "Reddit" : "Instagram"));
            m.getMenu().add(0, 1, 1, "Log out");
            m.getMenu().add(0, 2, 2, "Remove account");
            m.setOnMenuItemClickListener(i -> {
                if (i.getItemId() == 0) activity.openAccount(service, user);
                else if (i.getItemId() == 1) confirmLogout(service, user);
                else confirmRemove(service, user, posts);
                return true;
            });
            m.show();
        });
        return v;
    }

    private void confirmLogout(String service, String user) {
        new MaterialAlertDialogBuilder(activity)
                .setTitle("Log out of " + label(service, user) + "?")
                .setMessage("Its synced posts stay on the phone and come back if you log in again.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Log out", (d, w) -> {
                    if (Reddit.SOURCE_ID.equals(service)) core.logoutReddit(user);
                    else core.logoutInstagram(user);
                    changed = true;
                    refresh();
                }).show();
    }

    private void confirmRemove(String service, String user, int posts) {
        new MaterialAlertDialogBuilder(activity)
                .setTitle("Remove " + label(service, user) + "?")
                .setMessage(String.format(Locale.US, "Logs out and deletes its %,d saved posts and their photos from this phone. This can't be undone.", posts))
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Remove", (d, w) -> {
                    if (Core.syncing) {
                        Toast.makeText(activity, "Wait for the sync to finish", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    new Thread(() -> {
                        String message = "Removed " + label(service, user);
                        try {
                            core.removeAccount(service, user);
                        } catch (Exception e) {
                            Log.e(Core.TAG, "removeAccount: " + Log.getStackTraceString(e));
                            message = "Logged out, but deleting its files failed: " + e.getMessage();
                        }
                        String done = message;
                        activity.runOnUiThread(() -> {
                            changed = true;
                            refresh();
                            Toast.makeText(activity, done, Toast.LENGTH_SHORT).show();
                        });
                    }).start();
                }).show();
    }
}
