package com.example.app.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.example.app.R;

public final class Ui {
    private Ui() {}

    public static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static GradientDrawable background(int color, float radiusDp, Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, Math.round(radiusDp)));
        return drawable;
    }

    public static GradientDrawable borderedBackground(int fill, int stroke, float radiusDp, Context context) {
        GradientDrawable drawable = background(fill, radiusDp, context);
        drawable.setStroke(dp(context, 1), stroke);
        return drawable;
    }

    public static TextView text(Context context, String value, float sp, int color, boolean bold) {
        TextView text = new TextView(context);
        text.setText(value);
        text.setTextSize(sp);
        text.setTextColor(color);
        text.setTypeface(Typeface.create("sans", bold ? Typeface.BOLD : Typeface.NORMAL));
        text.setLineSpacing(0, 1.15f);
        return text;
    }

    public static TextView title(Context context, String overline, String heading) {
        LinearLayout wrapper = new LinearLayout(context);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(context, heading, 27, color(context, R.color.ink), true);
        title.setTag(wrapper);
        return title;
    }

    public static LinearLayout page(Context context) {
        LinearLayout page = new LinearLayout(context);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(context, 20), dp(context, 28), dp(context, 20), dp(context, 30));
        page.setBackgroundColor(color(context, R.color.cream));
        return page;
    }

    public static LinearLayout header(Context context, String overline, String heading) {
        LinearLayout block = new LinearLayout(context);
        block.setOrientation(LinearLayout.VERTICAL);
        TextView top = text(context, overline.toUpperCase(), 10, color(context, R.color.muted), true);
        top.setLetterSpacing(.08f);
        TextView title = text(context, heading, 27, color(context, R.color.ink), true);
        block.addView(top);
        block.addView(title, margin(-1, -2, 0, 4, 0, 0, context));
        return block;
    }

    public static TextInputLayout input(Context context, String hint, boolean multiline) {
        TextInputLayout layout = new TextInputLayout(context);
        layout.setHint(hint);
        layout.setHintTextColor(ColorStateList.valueOf(color(context, R.color.ink)));
        layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        layout.setBoxCornerRadii(dp(context, 15), dp(context, 15), dp(context, 15), dp(context, 15));
        layout.setBoxStrokeColor(color(context, R.color.violet));
        TextInputEditText editText = new TextInputEditText(context);
        editText.setTextColor(color(context, R.color.ink));
        editText.setHintTextColor(color(context, R.color.ink));
        if (multiline) {
            editText.setMinLines(3);
            editText.setGravity(Gravity.TOP);
        } else {
            editText.setSingleLine(true);
        }
        layout.addView(editText);
        return layout;
    }

    public static MaterialButton primaryButton(Context context, String label) {
        MaterialButton button = new MaterialButton(context);
        button.setText(label);
        button.setTextColor(color(context, R.color.ink));
        button.setTextSize(12);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setBackgroundTintList(ContextCompat.getColorStateList(context, R.color.lime));
        button.setCornerRadius(dp(context, 16));
        button.setMinHeight(dp(context, 52));
        return button;
    }

    public static MaterialButton softButton(Context context, String label) {
        MaterialButton button = new MaterialButton(context);
        button.setText(label);
        button.setTextColor(color(context, R.color.violet));
        button.setTextSize(11);
        button.setAllCaps(false);
        button.setBackgroundTintList(ContextCompat.getColorStateList(context, R.color.violet_soft));
        button.setCornerRadius(dp(context, 14));
        return button;
    }

    public static LinearLayout row(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    public static Space spacer(Context context) {
        Space space = new Space(context);
        space.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1));
        return space;
    }

    public static LinearLayout.LayoutParams margin(
            int width,
            int height,
            int left,
            int top,
            int right,
            int bottom,
            Context context
    ) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                width < 0 ? width : dp(context, width),
                height < 0 ? height : dp(context, height)
        );
        params.setMargins(dp(context, left), dp(context, top), dp(context, right), dp(context, bottom));
        return params;
    }

    public static int color(Context context, int resource) {
        return ContextCompat.getColor(context, resource);
    }

    public static void setPadding(View view, int horizontal, int vertical) {
        Context context = view.getContext();
        view.setPadding(dp(context, horizontal), dp(context, vertical), dp(context, horizontal), dp(context, vertical));
    }
}

