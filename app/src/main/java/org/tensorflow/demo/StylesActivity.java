package org.tensorflow.demo;

import android.content.Intent;
import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.app.UiModeManager;
import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import org.tensorflow.contrib.android.TensorFlowInferenceInterface;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

public class StylesActivity extends AppCompatActivity {
    //Sliders
    private ImageGridAdapter adapter;
    private GridView grid;
    private boolean allZero = false;
    private static final boolean NORMALIZE_SLIDERS = true;
    private int lastOtherStyle = 1;

    /**
     * initializing the buttons, the sliders ( where we pick the filters we desire to
     * apply on the image we picked from gallery)
     * @param savedInstanceState
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_styles);

        Button btn = (Button)findViewById(R.id.done);
        btn.setTextColor(Color.parseColor("#ffffffff"));
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent data = new Intent();
                String text = "Result to be returned....";
                data.setData(Uri.parse(text));
                setResult(RESULT_OK, data);
                finish();
            }
        });
        GlobalVariables.initialStyleVals();
        //initialization of sliders
        initSliders();

    }

    /**
     * initialization of the sliders of styles
     */
    private void initSliders() {
        //-----------Sliders-----------------------//
        adapter = new ImageGridAdapter();
        grid = (GridView) findViewById(R.id.grid_layout);
        grid.setAdapter(adapter);
        grid.setOnTouchListener(gridTouchAdapter);
        // Change UI on Android TV
        UiModeManager uiModeManager = (UiModeManager) getSystemService(UI_MODE_SERVICE);
        if (uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) {
            DisplayMetrics displayMetrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
            int styleSelectorHeight = displayMetrics.heightPixels;
            int styleSelectorWidth = displayMetrics.widthPixels - styleSelectorHeight;
            RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(styleSelectorWidth, ViewGroup.LayoutParams.MATCH_PARENT);

            // Calculate number of style in a row, so all the style can show up without scrolling
            int numOfStylePerRow = 5;
            while (styleSelectorWidth / numOfStylePerRow * Math.ceil((float) (adapter.getCount() - 2) / numOfStylePerRow) > styleSelectorHeight) {
                numOfStylePerRow++;
            }
            grid.setNumColumns(numOfStylePerRow);
            layoutParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            grid.setLayoutParams(layoutParams);
        }
    }

    /**
     * It's called when we change the rate of a filter we select
     */
    private final View.OnTouchListener gridTouchAdapter =
            new View.OnTouchListener() {
                ImageSlider slider = null;

                @Override
                public boolean onTouch(final View v, final MotionEvent event) {
                    switch (event.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            for (int i = 0; i < GlobalVariables.getNumStyles(); ++i) {
                                final ImageSlider child = adapter.items[i];
                                final Rect rect = new Rect();
                                child.getHitRect(rect);
                                if (rect.contains((int) event.getX(), (int) event.getY())) {
                                    slider = child;
                                    slider.setHilighted(true);
                                }
                            }
                            break;

                        case MotionEvent.ACTION_MOVE:
                            if (slider != null) {
                                final Rect rect = new Rect();
                                slider.getHitRect(rect);

                                final float newSliderVal =
                                        (float)
                                                Math.min(
                                                        1.0,
                                                        Math.max(
                                                                0.0, 1.0 - (event.getY() - slider.getTop()) / slider.getHeight()));
                                setStyle(slider, newSliderVal);
                            }
                            break;

                        case MotionEvent.ACTION_UP:
                            if (slider != null) {
                                slider.setHilighted(false);
                                slider = null;
                            }
                            break;

                        default: // fall out
                    }
                    return true;
                }
            };

    /**
     * returning the bitmap of the filter which exist in filePath
     * @param context
     * @param filePath the path of filter
     * @return  filter bitmap
     */
    public static Bitmap getBitmapFromAsset(final Context context, final String filePath) {
        final AssetManager assetManager = context.getAssets();

        Bitmap bitmap = null;
        try {
            final InputStream inputStream = assetManager.open(filePath);
            bitmap = BitmapFactory.decodeStream(inputStream);
        } catch (final IOException e) {
            e.printStackTrace();
        }

        return bitmap;
    }

    class ImageSlider extends ImageView {
        private float value = 0.0f;
        private boolean hilighted = false;

        private final Paint boxPaint;
        private final Paint linePaint;

        public ImageSlider(final Context context) {
            super(context);
            value = 0.0f;

            boxPaint = new Paint();
            boxPaint.setColor(Color.BLACK);
            boxPaint.setAlpha(128);

            linePaint = new Paint();
            linePaint.setColor(Color.WHITE);
            linePaint.setStrokeWidth(10.0f);
            linePaint.setStyle(Style.STROKE);
        }

        @Override
        public void onDraw(final Canvas canvas) {
            super.onDraw(canvas);
            final float y = (1.0f - value) * canvas.getHeight();

            // If all sliders are zero, don't bother shading anything.
            if (!allZero) {
                canvas.drawRect(0, 0, canvas.getWidth(), y, boxPaint);
            }

            if (value > 0.0f) {
                canvas.drawLine(0, y, canvas.getWidth(), y, linePaint);
            }

            if (hilighted) {
                canvas.drawRect(0, 0, getWidth(), getHeight(), linePaint);
            }
        }

        @Override
        protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }

        public void setValue(final float value) {
            this.value = value;
            postInvalidate();
        }

        public void setHilighted(final boolean highlighted) {
            this.hilighted = highlighted;
            this.postInvalidate();
        }
    }

    /**
     * building the sliders
     */
    private class ImageGridAdapter extends BaseAdapter {
        final StylesActivity.ImageSlider[] items = new StylesActivity.ImageSlider[GlobalVariables.getNumStyles()];
        final ArrayList<Button> buttons = new ArrayList<>();
        {

            for (int i = 0; i < GlobalVariables.getNumStyles(); ++i) {


                if (items[i] == null) {
                    final StylesActivity.ImageSlider slider = new StylesActivity.ImageSlider(StylesActivity.this);
                    final Bitmap bm =
                            getBitmapFromAsset(StylesActivity.this, "thumbnails/style" + i + ".jpg");
                    slider.setImageBitmap(bm);

                    items[i] = slider;
                }
            }
        }

        /**
         *
         * @return number of styles
         */
        @Override
        public int getCount() {
            return  GlobalVariables.getNumStyles();
        }

        /**
         *
         * @param position - the position of the slider
         * @return the position of the slider
         */
        @Override
        public Object getItem(final int position) {
            if (position < buttons.size()) {
                return buttons.get(position);
            } else {
                return items[position - buttons.size()];
            }
        }

        @Override
        public long getItemId(final int position) {
            return getItem(position).hashCode();
        }

        @Override
        public View getView(final int position, final View convertView, final ViewGroup parent) {
            if (convertView != null) {
                return convertView;
            }
            return (View) getItem(position);
        }
    }

    /**
     *
     * @param slider the image sliders
     * @param value the value to be updated
     */
    private void setStyle(final ImageSlider slider, final float value) {
        slider.setValue(value);

        if (NORMALIZE_SLIDERS) {
            // Slider vals correspond directly to the input tensor vals, and normalization is visually
            // maintained by remanipulating non-selected sliders.
            float otherSum = 0.0f;

            for (int i = 0; i < GlobalVariables.getNumStyles(); ++i) {
                if (adapter.items[i] != slider) {
                    otherSum += adapter.items[i].value;
                }
            }

            if (otherSum > 0.0) {
                float highestOtherVal = 0;
                final float factor = otherSum > 0.0f ? (1.0f - value) / otherSum : 0.0f;
                for (int i = 0; i < GlobalVariables.getNumStyles(); ++i) {
                    final ImageSlider child = adapter.items[i];
                    if (child == slider) {
                        continue;
                    }
                    final float newVal = child.value * factor;
                    child.setValue(newVal > 0.01f ? newVal : 0.0f);

                    if (child.value > highestOtherVal) {
                        lastOtherStyle = i;
                        highestOtherVal = child.value;
                    }
                }
            } else {
                // Everything else is 0, so just pick a suitable slider to push up when the
                // selected one goes down.
                if (adapter.items[lastOtherStyle] == slider) {
                    lastOtherStyle = (lastOtherStyle + 1) % GlobalVariables.getNumStyles();
                }
                adapter.items[lastOtherStyle].setValue(1.0f - value);
            }
        }

        final boolean lastAllZero = allZero;
        float sum = 0.0f;
        for (int i = 0; i < GlobalVariables.getNumStyles(); ++i) {
            sum += adapter.items[i].value;
        }
        allZero = sum == 0.0f;

        // Now update the values used for the input tensor. If nothing is set, mix in everything
        // equally. Otherwise everything is normalized to sum to 1.0.
        for (int i = 0; i < GlobalVariables.getNumStyles(); ++i) {
            GlobalVariables.getStyleVals()[i] = allZero ? 1.0f / GlobalVariables.getNumStyles() : adapter.items[i].value / sum;

            if (lastAllZero != allZero) {
                adapter.items[i].postInvalidate();
            }
        }
    }



}
