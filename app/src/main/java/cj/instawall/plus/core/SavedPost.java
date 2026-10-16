package cj.instawall.plus.core;

import java.util.Collections;
import java.util.List;

/** A saved post from any source; carousels and galleries have several images. */
public final class SavedPost {
    public final String sourceId;
    public final String id;
    public final List<ImageRef> images;

    public SavedPost(String sourceId, String id, List<ImageRef> images) {
        this.sourceId = sourceId;
        this.id = id;
        this.images = Collections.unmodifiableList(images);
    }

    public int indexOf(String imageId) {
        for (int i = 0; i < images.size(); i++) if (images.get(i).id.equals(imageId)) return i;
        return -1;
    }
}
