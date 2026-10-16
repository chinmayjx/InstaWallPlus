package cj.instawall.plus.core;

import java.nio.file.Path;

/** Image files are named POSTID_IMAGEID.jpg */
public final class ImageName {
    public final String postId, imageId;

    private ImageName(String postId, String imageId) {
        this.postId = postId;
        this.imageId = imageId;
    }

    public static String fileName(String postId, String imageId) {
        return postId + "_" + imageId + ".jpg";
    }

    public static ImageName of(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        int us = name.indexOf('_');
        if (us < 0) throw new IllegalArgumentException("not an image file name: " + file);
        return new ImageName(name.substring(0, us), name.substring(us + 1));
    }
}
