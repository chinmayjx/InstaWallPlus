package cj.instawall.plus.core;

/** One image of a saved post. */
public final class ImageRef {
    public final String id;
    public final String url;
    public final int width, height;

    public ImageRef(String id, String url, int width, int height) {
        this.id = id;
        this.url = url;
        this.width = width;
        this.height = height;
    }
}
