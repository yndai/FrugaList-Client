package com.ryce.frugalist.model;

import android.graphics.Bitmap;

/**
 * Created by Tony on 2016-02-07.
 *
 * Base class for a product listing
 */
public abstract class AbstractListing {

    private String product;
    private Bitmap image;
    private String imageUrl;

    // cached thumbnail
    private Bitmap scaledImage;

    public AbstractListing(Bitmap image, String imageUrl, String product) {
        this.image = image;
        this.imageUrl = imageUrl;
        this.product = product;
    }

    public Bitmap getImage() {
        return image;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getProduct() {
        return product;
    }

    // convenience methods

    public Bitmap getThumbnail(int height, int width) {
        if (scaledImage == null)
            scaledImage = Bitmap.createScaledBitmap(image, width, height, false);
        return scaledImage;
    }

}
