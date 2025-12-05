package com.example.ecowattchtechdemo;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.palette.graphics.Palette;

import java.util.HashMap;
import java.util.Map;

public class ThemeManager {
    private final Context context;
    private final SharedPreferences prefs;
    private final String username;

    public ThemeManager(Context context) {
        this.context = context;

        // Get current username for user-specific theme storage
        SharedPreferences userPrefs = context.getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE);
        this.username = userPrefs.getString("Username", "");

        this.prefs = context.getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE);
    }

    private Map<String, Integer> loadThemeColorsFromPrefs() {
        Map<String, Integer> themeColors = new HashMap<>();

        // Safely parse color strings (fall back to defaults)
        // tag everything in activities that needs to change, using these keys
        // strings on the left are the tags, ones on the right
        //     are keys for the SharedPreferences (now user-specific)
        themeColors.put("primary_color", getColorFromPrefs("primary_color", "#FFFFFF"));
        themeColors.put("secondary_color", getColorFromPrefs("secondary_color", "#AAAAAA"));
        themeColors.put("accent_color", getColorFromPrefs("accent_color", "#CD232E"));

        themeColors.put("primary_text", getColorFromPrefs("primary_color", "#FFFFFF"));
        themeColors.put("secondary_text", getColorFromPrefs("secondary_color", "#AAAAAA"));
        themeColors.put("accent_text", getColorFromPrefs("accent_color", "#CD232E"));

        themeColors.put("background_main", getColorFromPrefs("background_main", "#1B1B1B"));
        themeColors.put("background_light", getColorFromPrefs("background_light", "#262626"));

        return themeColors;
    }

    private int getColorFromPrefs(String key, String defaultHex) {
        try {
            // Use user-specific key if username is available
            String userKey = username.isEmpty() ? key : key + "_" + username;
            return Color.parseColor(prefs.getString(userKey, defaultHex));
        } catch (Exception e) {
            return Color.parseColor(defaultHex);
        }
    }

    /**
     * applyTheme() - call in onCreate(), after setContentView().
     * or just anytime the theme is changed.
     */
    public void applyTheme() {
        View root = ((Activity) context).findViewById(android.R.id.content);
        Map<String, Integer> themeColors = loadThemeColorsFromPrefs();
        applyThemeRecursively(root, themeColors);
    }

    public void applyTheme(Map<String, Integer> themeColors) {
        View root = ((Activity) context).findViewById(android.R.id.content);
        applyThemeRecursively(root, themeColors);
    }


    private void applyThemeRecursively(View view, Map<String, Integer> colors) {
        if (view == null) return;

        Object tag = view.getTag();

        // text color updates
        if (view instanceof TextView) {
            if ("primary_text".equals(tag)) {
                ((TextView) view).setTextColor(colors.get("primary_text"));
            } else if ("secondary_text".equals(tag)) {
                ((TextView) view).setTextColor(colors.get("secondary_text"));
            } else if ("accent_text".equals(tag)) {
                ((TextView) view).setTextColor(colors.get("accent_text"));
            } else if ("dynamic_text".equals(tag)) {
                // get background color
                getBackgroundColor(view, backgroundColor -> {
                    // compute brightness of background color
                    double luminance = (0.299 * Color.red(backgroundColor) +
                            0.587 * Color.green(backgroundColor) +
                            0.114 * Color.blue(backgroundColor)) / 255;

                    // if bright, set text to black, otherwise white
                    if (luminance > 0.5) {
                        ((TextView) view).setTextColor(Color.BLACK);
                    } else {
                        ((TextView) view).setTextColor(Color.WHITE);
                    }
                });
            }
        }

        // change background
        if (view instanceof RelativeLayout) {
            if ("background_main".equals(tag)) {
                ((RelativeLayout) view).setBackgroundColor(colors.get("background_main"));
            }
        }

        // images
        if (view instanceof ImageView) {
            if ("primary_color".equals(tag)) {
                ((ImageView) view).setColorFilter(colors.get("primary_color"));
            } else if ("secondary_color".equals(tag)) {
                ((ImageView) view).setColorFilter(colors.get("secondary_color"));
            } else if ("accent_color".equals(tag)) {
                ((ImageView) view).setColorFilter(colors.get("accent_color"));
            }
        }

        // todo: buttons
        // they have both xml backgrounds and text
        // both need to be changed
        if (view instanceof LinearLayout) {
            if ("background_light".equals(tag)) {
                ((LinearLayout) view).setBackgroundColor(colors.get("background_light"));
            }
        }

        // gradient handler
        Drawable bg = view.getBackground();
        if (bg != null) {
            bg = bg.mutate();

            if ("circle_gradient".equals(tag)) {
                if (bg instanceof GradientDrawable) {
                    ((GradientDrawable) bg).setColors(new int[]{
                            colors.get("primary_color"),
                            colors.get("accent_color")
                    });
                } else if (bg instanceof LayerDrawable) {
                    LayerDrawable layers = (LayerDrawable) bg;
                    for (int i = 0; i < layers.getNumberOfLayers(); i++) {
                        Drawable layer = layers.getDrawable(i);
                        if (layer instanceof GradientDrawable) {
                            ((GradientDrawable) layer).setColors(new int[]{
                                    colors.get("primary_color"),
                                    colors.get("accent_color")
                            });
                        }
                    }
                }
            } // circle gradient handler
        }

        // Recurse into child views
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyThemeRecursively(group.getChildAt(i), colors);
            }
        }
    }

    // find background color of a textview
    private void getBackgroundColor(View view, BackgroundColorCallback callback) {
        // check for background in this view
        Drawable background = view.getBackground();

        if (background != null) { // background found, get color
            // get dominant background color
            getDominantColorFromDrawable(background, color -> {
                callback.onColorReady(color);
            });
            return;
        }

        // no background found - check parent view
        View parentView = (View) view.getParent();

        if (parentView != null) {
            getBackgroundColor(parentView, callback);
            return;
        }

        // default to black
        callback.onColorReady(Color.BLACK);
    }

    public interface BackgroundColorCallback {
        void onColorReady(int color);
    }

    public static Bitmap drawableToBitmap(Drawable drawable) {
        // make a safe copy of the drawable so the original is not modified
        Drawable copy = drawable.getConstantState() != null
                ? drawable.getConstantState().newDrawable().mutate()
                : drawable.mutate();

        int width = copy.getIntrinsicWidth() > 0 ? copy.getIntrinsicWidth() : 100;
        int height = copy.getIntrinsicHeight() > 0 ? copy.getIntrinsicHeight() : 100;

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        copy.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        copy.draw(canvas);

        return bitmap;
    }


    public static void getDominantColorFromDrawable(Drawable drawable, DominantColorCallback callback) {
        Bitmap bitmap = drawableToBitmap(drawable);

        Palette.from(bitmap).generate(palette -> {
            int defaultColor = Color.BLACK; // fallback if palette fails
            int dominant = palette.getDominantColor(defaultColor);
            callback.onColorExtracted(dominant);
        });
    }

    public interface DominantColorCallback {
        void onColorExtracted(int color);
    }

    // Save theme colors to SharedPreferences (user-specific)
    public void saveThemeColor(String key, String hexColor) {
        // Use user-specific key if username is available
        String userKey = username.isEmpty() ? key : key + "_" + username;
        prefs.edit().putString(userKey, hexColor).apply();
    }
}
