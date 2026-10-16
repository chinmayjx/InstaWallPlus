package cj.instawall.plus;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.Locale;

import cj.instawall.plus.core.Reddit;

/**
 * Every account with its NSFW mark, and the NSFW mode switch. Showing NSFW things (turning the
 * mode on, unmarking an NSFW account) needs the fingerprint; hiding them doesn't.
 */
class AccountsPage {
    final ViewActivity activity;
    final Core core;
    final View root;
    final LinearLayout instagramList, redditList;
    final MaterialSwitch nsfwSwitch;
    final TextView nsfwSummary;
    /** the mode or a mark changed while open: the grid reloads on close */
    boolean changed = false;

    AccountsPage(ViewActivity activity, Core core) {
        this.activity = activity;
        this.core = core;
        root = activity.findViewById(R.id.accounts_page);
        instagramList = activity.findViewById(R.id.instagram_accounts);
        redditList = activity.findViewById(R.id.reddit_accounts);
        nsfwSwitch = activity.findViewById(R.id.nsfw_switch);
        nsfwSummary = activity.findViewById(R.id.nsfw_summary);
        ((MaterialToolbar) activity.findViewById(R.id.accounts_toolbar)).setNavigationOnClickListener(v -> close());
        activity.findViewById(R.id.nsfw_card).setOnClickListener(v -> activity.toggleNsfwMode());
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
        boolean mode = core.accounts.nsfwMode();
        nsfwSwitch.setChecked(mode);
        nsfwSummary.setText(mode ? "On: only NSFW accounts are used" : "Off: NSFW accounts are hidden");
        instagramList.removeAllViews();
        redditList.removeAllViews();
        for (String[] a : core.accounts.allAccounts()) {
            LinearLayout list = Reddit.SOURCE_ID.equals(a[0]) ? redditList : instagramList;
            list.addView(row(a[0], a[1], mode));
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

    private View row(String service, String user, boolean mode) {
        View v = LayoutInflater.from(activity).inflate(R.layout.account_row, instagramList, false);
        boolean nsfw = core.accounts.isNsfw(service, user);
        boolean used = nsfw == mode;
        ((ImageView) v.findViewById(R.id.account_icon)).setImageResource(Reddit.SOURCE_ID.equals(service) ? R.drawable.ic_reddit : R.drawable.ic_instagram);
        ((TextView) v.findViewById(R.id.account_name)).setText(label(service, user));
        int posts = core.sourceFor(service, user).posts.size();
        String where = used ? "in use" : nsfw ? "used in NSFW mode" : "used in normal mode";
        ((TextView) v.findViewById(R.id.account_info)).setText(String.format(Locale.US, "%,d saved posts · %s", posts, where));
        v.setAlpha(used ? 1f : 0.55f);

        Chip chip = v.findViewById(R.id.account_nsfw);
        chip.setChecked(nsfw);
        chip.setOnClickListener(c -> {
            // the chip flipped itself; put it back until it's decided
            chip.setChecked(nsfw);
            if (nsfw) {
                activity.biometricAuth.authenticate("Unmark " + label(service, user) + " as NSFW",
                        () -> activity.runOnUiThread(() -> setNsfw(service, user, false)));
            } else {
                setNsfw(service, user, true);
            }
        });

        v.findViewById(R.id.account_more).setOnClickListener(b -> {
            PopupMenu m = new PopupMenu(activity, b);
            m.getMenu().add(0, 0, 0, "Open " + (Reddit.SOURCE_ID.equals(service) ? "Reddit" : "Instagram"));
            m.getMenu().add(0, 1, 1, "Log out");
            m.setOnMenuItemClickListener(i -> {
                if (i.getItemId() == 0) activity.openAccount(service, user);
                else confirmLogout(service, user);
                return true;
            });
            m.show();
        });
        return v;
    }

    private void setNsfw(String service, String user, boolean nsfw) {
        core.setNsfw(service, user, nsfw);
        changed = true;
        refresh();
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
}
