package cj.instawall.plus;

public abstract class CJImageProvider {
    public abstract CJImage getNextImage();
    public abstract CJImage getPrevImage();
    public abstract boolean hasNextImage();
    public abstract boolean hasPrevImage();
}
