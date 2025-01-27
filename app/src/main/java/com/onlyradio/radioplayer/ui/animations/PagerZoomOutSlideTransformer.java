package com.onlyradio.radioplayer.ui.animations;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.viewpager2.widget.ViewPager2;

public class PagerZoomOutSlideTransformer implements ViewPager2.PageTransformer {
    @Override
    public void transformPage(@NonNull View page, float position) {
        if (position >= -1.0F || position <= 1.0F) {
            float height = (float)page.getHeight();
            float scaleFactor = Math.max(0.85F, 1.0F - Math.abs(position));
            float vertMargin = height * (1.0F - scaleFactor) / 2.0F;
            float horzMargin = (float)page.getWidth() * (1.0F - scaleFactor) / 2.0F;
            page.setPivotY(0.5F * height);
            if (position < 0.0F) {
                page.setTranslationX(horzMargin - vertMargin / 2.0F);
            } else {
                page.setTranslationX(-horzMargin + vertMargin / 2.0F);
            }

            page.setScaleX(scaleFactor);
            page.setScaleY(scaleFactor);
            page.setAlpha(0.5F + (scaleFactor - 0.85F) / 0.14999998F * 0.5F);
        }
    }
}