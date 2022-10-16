package cj.instawall.plus;

import java.util.concurrent.LinkedBlockingDeque;

public class LIFOBlockingQueue extends LinkedBlockingDeque<Runnable> {
    public static final String TAG = "CJ";

    @Override
    public boolean offer(Runnable runnable) {
        try {
            super.addFirst(runnable);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}