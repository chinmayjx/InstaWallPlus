package cj.instawall.plus.core;

public interface Sleeper {
    void sleep(long ms) throws InterruptedException;

    Sleeper REAL = Thread::sleep;
}
