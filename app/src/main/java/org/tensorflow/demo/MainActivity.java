package org.tensorflow.demo;

import android.Manifest;
import android.app.UiModeManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.media.Image;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.Type;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.view.Display;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.tensorflow.contrib.android.TensorFlowInferenceInterface;
import org.tensorflow.demo.env.Logger;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Vector;

import ar.com.hjg.pngj.ImageInfo;
import ar.com.hjg.pngj.ImageLineByte;
import ar.com.hjg.pngj.PngWriter;

public class MainActivity extends AppCompatActivity {


    private static final Logger LOGGER = new Logger();
    private static final String MODEL_FILE = "file:///android_asset/stylize_quantized.pb";
    private static final String INPUT_NODE = "input";
    private static final String STYLE_NODE = "style_num";
    private static final String OUTPUT_NODE = "transformer/expand/conv3/conv/Sigmoid";
    private static final int NUM_STYLES = 26;
    private TensorFlowInferenceInterface inferenceInterface;
    protected int desiredSize = 1000;
    private final float[] styleVals = new float[NUM_STYLES];
    private int[] intValues;
    private float[] floatValues;
    private int cnt = 0;
    private int blockSize = 500;
    private int overlay = 200;
    BitmapRegionDecoder mDecoder;
    private Canvas canvas;
    private Bitmap res_bitmap;
    private static final String PERMISSION_STORAGE = Manifest.permission.WRITE_EXTERNAL_STORAGE;
    private static final int PERMISSIONS_REQUEST = 1;
    File mFile;
    ImageInfo imgi;
    PngWriter pngw;
    private String root = "/sdcard/tensorflow";
//    private String root = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "tensorflow";


    //Silders
    private ImageGridAdapter adapter;
    private GridView grid;
    private boolean allZero = false;
    private static final boolean NORMALIZE_SLIDERS = true;
    private int lastOtherStyle = 1;
    private final OnTouchListener gridTouchAdapter =
            new OnTouchListener() {
                ImageSlider slider = null;

                @Override
                public boolean onTouch(final View v, final MotionEvent event) {
                    switch (event.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            for (int i = 0; i < NUM_STYLES; ++i) {
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


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (!hasPermission()) {
            requestPermission();
        }
        ImageView image = (ImageView) findViewById(R.id.imageView);
        inferenceInterface = new TensorFlowInferenceInterface(getAssets(), MODEL_FILE);
        for (int i = 0; i < styleVals.length; i++) {
            styleVals[i] = 0;
        }
        styleVals[0] = 1.0f;

        createDecoder();
        res_bitmap = Bitmap.createBitmap(mDecoder.getWidth(), blockSize, Config.ARGB_8888);
        canvas = new Canvas(res_bitmap);
        intValues = new int[blockSize * blockSize];
        floatValues = new float[blockSize * blockSize * 3];


        String fname = "stylized"+ (cnt++) +".png";
        createNewPNG(fname);

        mFile = new File(root + File.separator + fname);
        imgi = new ImageInfo(mDecoder.getWidth(), mDecoder.getHeight(), 8, false);
        pngw = new PngWriter(mFile, imgi);

        divideAndStylize(mDecoder.getWidth(), mDecoder.getHeight());

        Bitmap result = BitmapFactory.decodeFile(root + File.separator + fname);

        image.setImageBitmap(result);

//        Button saveBtn = (Button) findViewById(R.id.saveBtn);
//        saveBtn.setOnClickListener(new OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                if (res_bitmap != null) {
//                    // TODO(andrewharp): Save as jpeg with guaranteed unique filename.
//                    ImageUtils.saveBitmap(res_bitmap, "stylized" + frameNum + ".png");
//                    Toast.makeText(
//                            MainActivity.this,
//                            "Saved image to: /sdcard/tensorflow/" + "stylized" + frameNum + ".png",
//                            Toast.LENGTH_LONG)
//                            .show();
//                }
//                frameNum++;
//            }
//        });


    }


    private void createNewPNG(String fname) {
        Bitmap newBm = Bitmap.createBitmap(mDecoder.getWidth(), mDecoder.getHeight(), Config.ARGB_8888);
        File myDir = new File(root);
        myDir.mkdirs();

        File file = new File(myDir, fname);
        if (file.exists()) file.delete();
        try {
            FileOutputStream out = new FileOutputStream(file);
            newBm.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.flush();
            out.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void createDecoder() {
//        InputStream is = null;
        try {
            InputStream is = getAssets().open("man1.jpg");
            mDecoder = BitmapRegionDecoder.newInstance(new BufferedInputStream(is), true);


        } catch (IOException e) {
            throw new RuntimeException("Could not create BitmapRegionDecoder", e);
        }
    }


    private void divideAndStylize(int bmWidth, int bmHeight) {
        int y = 0;
        while (y < bmHeight && y + blockSize <= bmHeight) {
            int x = 0;
            while (x < bmWidth && x + blockSize <= bmWidth) {
//                Bitmap origBlock = Bitmap.createBitmap(bitmap, x, y, blockSize, blockSize);
                Bitmap origBlock = mDecoder.decodeRegion(new Rect(x, y, x + blockSize, y + blockSize), null);
                stylizeImage(origBlock); //changes intValues
                Bitmap stylizedBlock = Bitmap.createBitmap(intValues, blockSize, blockSize, Config.ARGB_8888);
                mergeOverlap(stylizedBlock, x, y);
                drawNewBlock(stylizedBlock, x, y);
                x = x - overlay + blockSize;
            }
            if (x < bmWidth && x + blockSize > bmWidth) {
                handleLastHorizontalBlock(bmWidth, x, y);
            }
            int next_y = y - overlay + blockSize;
            if ((next_y) + blockSize <= bmHeight) {
                writeToPNG(blockSize - overlay);
            } else {
                int height1 = bmHeight - next_y;
                int y_cut = blockSize - height1 - overlay;
                int cut_overlay = overlay;
                if (y_cut < 0) {
                    cut_overlay = next_y - (bmHeight - blockSize);
                }
                writeToPNG(blockSize - overlay - cut_overlay);
            }
            y = y - overlay + blockSize;

        }
        if (y < bmHeight && y + blockSize >= bmHeight) {
            handleLastVerticalBlock(bmWidth, bmHeight, y);
        }
        pngw.end();
    }


    private void writeToPNG(int rows) {
        for (int i = 0; i < rows; i++) {
            Bitmap bmp = Bitmap.createBitmap(res_bitmap, 0, i, mDecoder.getWidth(), 1);
            int[] pixelArray = new int[bmp.getWidth() * bmp.getHeight()];
            bmp.getPixels(pixelArray, 0, bmp.getWidth(), 0, 0, bmp.getWidth(), bmp.getHeight());
            ByteBuffer byteBuffer = ByteBuffer.allocate(pixelArray.length * 4);
            IntBuffer intBuffer = byteBuffer.asIntBuffer();
            intBuffer.put(pixelArray);
            byte[] bytes = byteBuffer.array();

            byte[] byteline = new byte[mDecoder.getWidth() * 3];
            int channels = 3;
            bmp.recycle();
            int idx = 1;
            for (int m = 0; m < mDecoder.getWidth(); m++) {
                byteline[m * channels] = bytes[idx];
                byteline[m * channels + 1] = bytes[idx + 1];
                byteline[m * channels + 2] = bytes[idx + 2];
                idx += channels + 1;
//            idx++;
            }

            ImageLineByte ilin = new ImageLineByte(imgi, byteline);
            pngw.writeRow(ilin);
        }
        if (blockSize - rows > 0) {
            Bitmap bmp = Bitmap.createBitmap(res_bitmap, 0, rows, mDecoder.getWidth(), blockSize - rows);
            canvas.drawBitmap(bmp, 0, 0, null);
            bmp.recycle();
        }
    }

    private void handleBottomCorner(int bmWidth, int bmHeight, int x, int y) {
        int height1 = bmHeight - y;
        int width1 = bmWidth - x; //width from x to last
        int x_block = bmWidth - blockSize;
        int y_block = bmHeight - blockSize;
//        Bitmap origBlock = Bitmap.createBitmap(bitmap, x_block, y_block, blockSize, blockSize);
        Bitmap origBlock = mDecoder.decodeRegion(new Rect(x_block, y_block, x_block + blockSize, y_block + blockSize), null);
        stylizeImage(origBlock); //changes intValues
        Bitmap stylizedBlock = Bitmap.createBitmap(intValues, blockSize, blockSize, Config.ARGB_8888);
        int x_cut = stylizedBlock.getWidth() - width1 - overlay;
        int y_cut = stylizedBlock.getHeight() - height1 - overlay;
        int x_overlay = overlay, y_overlay = overlay;
        if (x_cut < 0) {
            x_overlay = x - (bmWidth - blockSize);
            x_cut = 0;
        }
        if (y_cut < 0) {
            y_cut = 0;
            y_overlay = y - (bmHeight - blockSize);
        }
        Bitmap stylizedBlock_cut = Bitmap.createBitmap(stylizedBlock, x_cut, y_cut, width1 + x_overlay, height1 + y_overlay);

        Bitmap prev_bm = Bitmap.createBitmap(res_bitmap, x - x_overlay, y_overlay, x_overlay, height1);
        Bitmap new_bm = Bitmap.createBitmap(stylizedBlock_cut, 0, y_overlay, x_overlay, height1);
        Bitmap res = horizontalMerge(prev_bm, new_bm);
        canvas.drawBitmap(res, x - x_overlay, y_overlay, null);
        //---------------------------------------------
        prev_bm = Bitmap.createBitmap(res_bitmap, x, 0, width1, y_overlay);
        new_bm = Bitmap.createBitmap(stylizedBlock_cut, x_overlay, 0, width1, y_overlay);
        res = verticalMerge(prev_bm, new_bm);
        canvas.drawBitmap(res, x, 0, null);
        //----------------------------------------------
        prev_bm = Bitmap.createBitmap(res_bitmap, x - x_overlay, 0, x_overlay, y_overlay);
        new_bm = Bitmap.createBitmap(stylizedBlock_cut, 0, 0, x_overlay, y_overlay);
        res = diagonalMerge(prev_bm, new_bm);
        canvas.drawBitmap(res, x - x_overlay, 0, null);
        Bitmap newBlock = Bitmap.createBitmap(stylizedBlock_cut, x_overlay, y_overlay, width1, height1);
        canvas.drawBitmap(newBlock, x, y_overlay, null);

    }


    private void handleLastVerticalBlock(int bmWidth, int bmHeight, int y) {
        int height1 = bmHeight - y; //width from x to last
        int block_size = blockSize;
        Bitmap stylizedBlock_cut = null;
        for (int x = 0; x < bmWidth; x += blockSize - overlay) { //bottom right corner
            int x_block = x;
            int y_block = bmHeight - block_size;
            if (x + blockSize > bmWidth) {

                handleBottomCorner(bmWidth, bmHeight, x, y);
                writeToPNG(stylizedBlock_cut.getHeight());
                return;
            }
//            Bitmap origBlock = Bitmap.createBitmap(bitmap, x_block, y_block, block_size, block_size);
            Bitmap origBlock = mDecoder.decodeRegion(new Rect(x_block, y_block, x_block + blockSize, y_block + blockSize), null);
            stylizeImage(origBlock); //changes intValues
            Bitmap stylizedBlock = Bitmap.createBitmap(intValues, block_size, block_size, Config.ARGB_8888);
            int y_cut = stylizedBlock.getHeight() - height1 - overlay;
            int cut_overlay = overlay;
            if (y_cut < 0) {
                y_cut = 0;
                cut_overlay = y - (bmHeight - block_size);
                stylizedBlock_cut = stylizedBlock;
            } else {
                stylizedBlock_cut = Bitmap.createBitmap(stylizedBlock, 0, y_cut, block_size, height1 + cut_overlay);
            }
            if (x == 0) {
                Bitmap prev_bm = Bitmap.createBitmap(res_bitmap, 0, 0, block_size, cut_overlay);
                Bitmap new_bm = Bitmap.createBitmap(stylizedBlock_cut, 0, 0, block_size, cut_overlay);
                Bitmap res = verticalMerge(prev_bm, new_bm);
                canvas.drawBitmap(res, 0, 0, null);
                Bitmap newBlock = Bitmap.createBitmap(stylizedBlock_cut, 0, cut_overlay, block_size, height1);
                canvas.drawBitmap(newBlock, x, cut_overlay, null);
            } else {
                Bitmap prev_bm = Bitmap.createBitmap(res_bitmap, x, cut_overlay, overlay, height1);
                Bitmap new_bm = Bitmap.createBitmap(stylizedBlock_cut, 0, cut_overlay, overlay, height1);
                Bitmap res = horizontalMerge(prev_bm, new_bm);
                canvas.drawBitmap(res, x, cut_overlay, null);
                //---------------------------------------------
                prev_bm = Bitmap.createBitmap(res_bitmap, x + overlay, 0, block_size - overlay, cut_overlay);
                new_bm = Bitmap.createBitmap(stylizedBlock_cut, overlay, 0, block_size - overlay, cut_overlay);
                res = verticalMerge(prev_bm, new_bm);
                canvas.drawBitmap(res, x + overlay, 0, null);
                //----------------------------------------------
                prev_bm = Bitmap.createBitmap(res_bitmap, x, 0, overlay, cut_overlay);
                new_bm = Bitmap.createBitmap(stylizedBlock_cut, 0, 0, overlay, cut_overlay);
                res = diagonalMerge(prev_bm, new_bm);
                canvas.drawBitmap(res, x, 0, null);
                Bitmap newBlock = Bitmap.createBitmap(stylizedBlock_cut, overlay, cut_overlay, block_size - overlay, height1);
                canvas.drawBitmap(newBlock, x + overlay, cut_overlay, null);
            }
        }


    }


    private void handleLastHorizontalBlock(int bmWidth, int x, int y) {
        int width1 = bmWidth - x; //width from x to last
        int block_size = blockSize;
//        Bitmap origBlock = Bitmap.createBitmap(bitmap, bmWidth - block_size, y, block_size, block_size);
        Bitmap origBlock = mDecoder.decodeRegion(new Rect(bmWidth - block_size, y, bmWidth - block_size + blockSize, y + blockSize), null);
        stylizeImage(origBlock); //changes intValues
        Bitmap stylizedBlock = Bitmap.createBitmap(intValues, block_size, block_size, Config.ARGB_8888);
        Bitmap stylizedBlock_cut;
        int x_cut = stylizedBlock.getWidth() - width1 - overlay;
        int cut_overlay = overlay;
        if (stylizedBlock.getWidth() - width1 - overlay < 0) {
            x_cut = 0;
            cut_overlay = x - (bmWidth - block_size);
            stylizedBlock_cut = stylizedBlock;
        } else {
            stylizedBlock_cut = Bitmap.createBitmap(stylizedBlock, x_cut, 0, width1 + cut_overlay, block_size);
        }
        if (y == 0) {
            Bitmap prev_bm = Bitmap.createBitmap(res_bitmap, x - cut_overlay, 0, cut_overlay, block_size);
            Bitmap new_bm = Bitmap.createBitmap(stylizedBlock_cut, 0, 0, cut_overlay, block_size);
            Bitmap res = horizontalMerge(prev_bm, new_bm);
            canvas.drawBitmap(res, x - cut_overlay, 0, null);
            Bitmap newBlock = Bitmap.createBitmap(stylizedBlock_cut, cut_overlay, 0, width1, block_size);
            canvas.drawBitmap(newBlock, x, 0, null);
        } else {
            Bitmap prev_bm = Bitmap.createBitmap(res_bitmap, x, 0, width1, cut_overlay);
            Bitmap new_bm = Bitmap.createBitmap(stylizedBlock_cut, cut_overlay, 0, width1, cut_overlay);
            Bitmap res = verticalMerge(prev_bm, new_bm);
            canvas.drawBitmap(res, x, 0, null);
            //---------------------------------------------
            prev_bm = Bitmap.createBitmap(res_bitmap, x - cut_overlay, cut_overlay, cut_overlay, block_size - cut_overlay);
            new_bm = Bitmap.createBitmap(stylizedBlock_cut, 0, cut_overlay, cut_overlay, block_size - cut_overlay);
            res = horizontalMerge(prev_bm, new_bm);
            canvas.drawBitmap(res, x - cut_overlay, cut_overlay, null);
            //----------------------------------------------
            prev_bm = Bitmap.createBitmap(res_bitmap, x - cut_overlay, 0, cut_overlay, cut_overlay);
            new_bm = Bitmap.createBitmap(stylizedBlock_cut, 0, 0, cut_overlay, cut_overlay);
            res = diagonalMerge(prev_bm, new_bm);
            canvas.drawBitmap(res, x - cut_overlay, 0, null);
            Bitmap newBlock = Bitmap.createBitmap(stylizedBlock_cut, cut_overlay, cut_overlay, width1, block_size - cut_overlay);
            canvas.drawBitmap(newBlock, x, cut_overlay, null);
        }
    }

    private void mergeOverlap(Bitmap stylizedBlock, int x, int y) {
        if (x == 0 && y == 0) {
            return;
        }
        if (x != 0 && y == 0) {
            Bitmap prev_bm = Bitmap.createBitmap(res_bitmap, x, 0, overlay, blockSize);
            Bitmap new_bm = Bitmap.createBitmap(stylizedBlock, 0, 0, overlay, blockSize);
            Bitmap res = horizontalMerge(prev_bm, new_bm);
            canvas.drawBitmap(res, x, 0, null);

        }
        if (x == 0 && y != 0) {
            Bitmap prev_bm = Bitmap.createBitmap(res_bitmap, x, 0, blockSize, overlay);
            Bitmap new_bm = Bitmap.createBitmap(stylizedBlock, 0, 0, blockSize, overlay);
            Bitmap res = verticalMerge(prev_bm, new_bm);
            canvas.drawBitmap(res, x, 0, null);
        }
        if (x != 0 && y != 0) {
            Bitmap prev_bm = Bitmap.createBitmap(res_bitmap, x + overlay, 0, blockSize - overlay, overlay);
            Bitmap new_bm = Bitmap.createBitmap(stylizedBlock, overlay, 0, blockSize - overlay, overlay);
            Bitmap res = verticalMerge(prev_bm, new_bm);
            canvas.drawBitmap(res, x + overlay, 0, null);
            //---------------------------------------------
            prev_bm = Bitmap.createBitmap(res_bitmap, x, overlay, overlay, blockSize - overlay);
            new_bm = Bitmap.createBitmap(stylizedBlock, 0, overlay, overlay, blockSize - overlay);
            res = horizontalMerge(prev_bm, new_bm);
            canvas.drawBitmap(res, x, overlay, null);
            //----------------------------------------------
            prev_bm = Bitmap.createBitmap(res_bitmap, x, 0, overlay, overlay);
            new_bm = Bitmap.createBitmap(stylizedBlock, 0, 0, overlay, overlay);
            res = diagonalMerge(prev_bm, new_bm);
            canvas.drawBitmap(res, x, 0, null);


        }
    }

    private void drawNewBlock(Bitmap stylizedBlock, int x, int y) {
        int width_tmp = 0, height_tmp = 0;
        int x_tmp = 0, y_tmp = 0;
        if (x == 0 && y == 0) {
            x_tmp = 0;
            y_tmp = 0;
            height_tmp = blockSize;
            width_tmp = blockSize;
        }
        if (x == 0 && y != 0) {
            x_tmp = 0;
            y_tmp = overlay;
            height_tmp = blockSize - overlay;
            width_tmp = blockSize;
        }
        if (x != 0 && y == 0) {
            x_tmp = overlay;
            y_tmp = 0;
            height_tmp = blockSize;
            width_tmp = blockSize - overlay;
        }
        if (x != 0 && y != 0) {
            x_tmp = overlay;
            y_tmp = overlay;
            height_tmp = blockSize - overlay;
            width_tmp = blockSize - overlay;
        }

        Bitmap crop_stylizedBlock = Bitmap.createBitmap(stylizedBlock, x_tmp, y_tmp, width_tmp, height_tmp);
        canvas.drawBitmap(crop_stylizedBlock, x + x_tmp, y_tmp, null);
    }

    static void getFloatVals(Bitmap bm, float[] floatVals) {
        int[] pixels = new int[bm.getWidth() * bm.getHeight()];
        bm.getPixels(pixels, 0, bm.getWidth(), 0, 0, bm.getWidth(), bm.getHeight());
        for (int i = 0; i < pixels.length; ++i) {
            final int val = pixels[i];
            floatVals[i * 3] = ((val >> 16) & 0xFF) / 255.0f;
            floatVals[i * 3 + 1] = ((val >> 8) & 0xFF) / 255.0f;
            floatVals[i * 3 + 2] = (val & 0xFF) / 255.0f;
        }
    }

    private Bitmap verticalMerge(Bitmap prev_bm, Bitmap new_bm) {
        int width = prev_bm.getWidth();
        int height = prev_bm.getHeight();
        int length = width * height;
        float[] prev_vals = new float[length * 3];
        float[] new_vals = new float[length * 3];
        getFloatVals(prev_bm, prev_vals);
        getFloatVals(new_bm, new_vals);
        int row;
        for (int i = 0; i < length; i++) {
            row = i / width;
            float factor = (0.8f * row) / (height) + 0.1f;
            new_vals[i * 3] = (factor) * new_vals[i * 3] + (1 - factor) * prev_vals[i * 3];
            new_vals[i * 3 + 1] = (factor) * new_vals[i * 3 + 1] + (1 - factor) * prev_vals[i * 3 + 1];
            new_vals[i * 3 + 2] = (factor) * new_vals[i * 3 + 2] + (1 - factor) * prev_vals[i * 3 + 2];

        }
        int[] new_intVals = new int[length];
        for (int i = 0; i < new_intVals.length; ++i) {
            new_intVals[i] =
                    0xFF000000
                            | (((int) (new_vals[i * 3] * 255)) << 16)
                            | (((int) (new_vals[i * 3 + 1] * 255)) << 8)
                            | ((int) (new_vals[i * 3 + 2] * 255));
        }
        return (Bitmap.createBitmap(new_intVals, width, height, Config.ARGB_8888));
    }

    private Bitmap horizontalMerge(Bitmap prev_bm, Bitmap new_bm) {
        int width = prev_bm.getWidth();
        int height = prev_bm.getHeight();
        int length = width * height;
        float[] prev_vals = new float[length * 3];
        float[] new_vals = new float[length * 3];
        getFloatVals(prev_bm, prev_vals);
        getFloatVals(new_bm, new_vals);
        int col = 0;
        for (int i = 0; i < length; i++) {
            float factor = (0.8f * col) / (width) + 0.1f;
            new_vals[i * 3] = (factor) * new_vals[i * 3] + (1 - factor) * prev_vals[i * 3];
            new_vals[i * 3 + 1] = (factor) * new_vals[i * 3 + 1] + (1 - factor) * prev_vals[i * 3 + 1];
            new_vals[i * 3 + 2] = (factor) * new_vals[i * 3 + 2] + (1 - factor) * prev_vals[i * 3 + 2];
            col = (col + 1) % width;
        }
        int[] new_intVals = new int[length];
        for (int i = 0; i < new_intVals.length; ++i) {
            new_intVals[i] =
                    0xFF000000
                            | (((int) (new_vals[i * 3] * 255)) << 16)
                            | (((int) (new_vals[i * 3 + 1] * 255)) << 8)
                            | ((int) (new_vals[i * 3 + 2] * 255));
        }
        return (Bitmap.createBitmap(new_intVals, width, height, Config.ARGB_8888));
    }

    private Bitmap diagonalMerge(Bitmap prev_bm, Bitmap new_bm) {
        int width = prev_bm.getWidth();
        int height = prev_bm.getHeight();
        int length = width * height;
        float[] prev_vals = new float[length * 3];
        float[] new_vals = new float[length * 3];
        getFloatVals(prev_bm, prev_vals);
        getFloatVals(new_bm, new_vals);
        int x = 0;
        int row = 0;
        int col = 0;
        for (int i = 0; i < length; i++) {
            row = i / width;
            if (col < row) {
                x = col;
            } else {
                x = row;
            }
            float factor = (0.8f * x) / (width) + 0.1f;
            new_vals[i * 3] = (factor) * new_vals[i * 3] + (1 - factor) * prev_vals[i * 3];
            new_vals[i * 3 + 1] = (factor) * new_vals[i * 3 + 1] + (1 - factor) * prev_vals[i * 3 + 1];
            new_vals[i * 3 + 2] = (factor) * new_vals[i * 3 + 2] + (1 - factor) * prev_vals[i * 3 + 2];
            col = (col + 1) % width;
        }
        int[] new_intVals = new int[length];
        for (int i = 0; i < new_intVals.length; ++i) {
            new_intVals[i] =
                    0xFF000000
                            | (((int) (new_vals[i * 3] * 255)) << 16)
                            | (((int) (new_vals[i * 3 + 1] * 255)) << 8)
                            | ((int) (new_vals[i * 3 + 2] * 255));
        }
        return (Bitmap.createBitmap(new_intVals, width, height, Config.ARGB_8888));

    }

    private void stylizeImage(Bitmap bitmap) {
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        for (int i = 0; i < intValues.length; ++i) {
            final int val = intValues[i];
            floatValues[i * 3] = ((val >> 16) & 0xFF) / 255.0f;
            floatValues[i * 3 + 1] = ((val >> 8) & 0xFF) / 255.0f;
            floatValues[i * 3 + 2] = (val & 0xFF) / 255.0f;
        }

        inferenceInterface.feed(
                INPUT_NODE, floatValues, 1, bitmap.getWidth(), bitmap.getHeight(), 3);
        inferenceInterface.feed(STYLE_NODE, styleVals, NUM_STYLES);

        inferenceInterface.run(new String[]{OUTPUT_NODE}, false);
        inferenceInterface.fetch(OUTPUT_NODE, floatValues);

        for (int i = 0; i < intValues.length; ++i) {
            intValues[i] =
                    0xFF000000
                            | (((int) (floatValues[i * 3] * 255)) << 16)
                            | (((int) (floatValues[i * 3 + 1] * 255)) << 8)
                            | ((int) (floatValues[i * 3 + 2] * 255));
        }
    }

    private void requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (shouldShowRequestPermissionRationale(PERMISSION_STORAGE)) {
                Toast.makeText(MainActivity.this,
                        "Camera AND storage permission are required for this demo", Toast.LENGTH_LONG).show();
            }
            requestPermissions(new String[]{PERMISSION_STORAGE}, PERMISSIONS_REQUEST);
        }
    }

    private boolean hasPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(PERMISSION_STORAGE) == PackageManager.PERMISSION_GRANTED;
        } else {
            return true;
        }
    }

    public static Bitmap getBitmapFromAsset(final Context context, final String filePath) {
        final AssetManager assetManager = context.getAssets();

        Bitmap bitmap = null;
        try {
            final InputStream inputStream = assetManager.open(filePath);
            bitmap = BitmapFactory.decodeStream(inputStream);
        } catch (final IOException e) {
            LOGGER.e("Error opening bitmap!", e);
        }

        return bitmap;
    }


    /*Sliders*/

//    private void initSliders() {
//        //-----------Sliders-----------------------//
//        adapter = new ImageGridAdapter();
//        grid = (GridView) findViewById(R.id.grid_layout);
//        grid.setAdapter(adapter);
//        grid.setOnTouchListener(gridTouchAdapter);
//        // Change UI on Android TV
//        UiModeManager uiModeManager = (UiModeManager) getSystemService(UI_MODE_SERVICE);
//        if (uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) {
//            DisplayMetrics displayMetrics = new DisplayMetrics();
//            getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
//            int styleSelectorHeight = displayMetrics.heightPixels;
//            int styleSelectorWidth = displayMetrics.widthPixels - styleSelectorHeight;
//            RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(styleSelectorWidth, ViewGroup.LayoutParams.MATCH_PARENT);
//
//            // Calculate number of style in a row, so all the style can show up without scrolling
//            int numOfStylePerRow = 3;
//            while (styleSelectorWidth / numOfStylePerRow * Math.ceil((float) (adapter.getCount() - 2) / numOfStylePerRow) > styleSelectorHeight) {
//                numOfStylePerRow++;
//            }
//            grid.setNumColumns(numOfStylePerRow);
//            layoutParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
//            grid.setLayoutParams(layoutParams);
//
//        }
//    }

    private class ImageSlider extends ImageView {
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
//            setMeasuredDimension(getMeasuredWidth(), getMeasuredWidth());
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

    private class ImageGridAdapter extends BaseAdapter {
        final ImageSlider[] items = new ImageSlider[NUM_STYLES];
//        final ArrayList<Button> buttons = new ArrayList<>();

        {
//            final Button sizeButton =
//                    new Button(MainActivity.this) {
//                        @Override
//                        protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
//                            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
//                            setMeasuredDimension(getMeasuredWidth(), getMeasuredWidth());
//                        }
//                    };
//            sizeButton.setText("" + desiredSize);
//            sizeButton.setOnClickListener(
//                    new OnClickListener() {
//                        @Override
//                        public void onClick(final View v) {
//                            desiredSizeIndex = (desiredSizeIndex + 1) % SIZES.length;
//                            desiredSize = SIZES[desiredSizeIndex];
//                            sizeButton.setText("" + desiredSize);
//                            sizeButton.postInvalidate();
//                        }
//                    });
//
//            final Button saveButton =
//                    new Button(MainActivity.this) {
//                        @Override
//                        protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
//                            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
//                            setMeasuredDimension(getMeasuredWidth(), getMeasuredWidth());
//                        }
//                    };
//            saveButton.setText("save");
//            saveButton.setTextSize(12);
//
//            saveButton.setOnClickListener(
//                    new OnClickListener() {
//                        @Override
//                        public void onClick(final View v) {
//                            if (textureCopyBitmap != null) {
//                                // TODO(andrewharp): Save as jpeg with guaranteed unique filename.
//                                ImageUtils.saveBitmap(textureCopyBitmap, "stylized" + frameNum + ".png");
//                                Toast.makeText(
//                                        MainActivity.this,
//                                        "Saved image to: /sdcard/tensorflow/" + "stylized" + frameNum + ".png",
//                                        Toast.LENGTH_LONG)
//                                        .show();
//                            }
//                        }
//                    });
//
//            buttons.add(sizeButton);
//            buttons.add(saveButton);

            for (int i = 0; i < NUM_STYLES; ++i) {
                LOGGER.v("Creating item %d", i);

                if (items[i] == null) {
                    final ImageSlider slider = new ImageSlider(MainActivity.this);
                    final Bitmap bm =
                            getBitmapFromAsset(MainActivity.this, "thumbnails/style" + i + ".jpg");
                    slider.setImageBitmap(bm);

                    items[i] = slider;
                }
            }
        }

        @Override
        public int getCount() {
//            return buttons.size() + NUM_STYLES;
            return NUM_STYLES;
        }

        @Override
        public Object getItem(final int position) {
//            if (position < buttons.size()) {
//                return buttons.get(position);
//            } else {
//                return items[position - buttons.size()];
//            }
            return items[position];
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

    private void setStyle(final ImageSlider slider, final float value) {
        slider.setValue(value);

        if (NORMALIZE_SLIDERS) {
            // Slider vals correspond directly to the input tensor vals, and normalization is visually
            // maintained by remanipulating non-selected sliders.
            float otherSum = 0.0f;

            for (int i = 0; i < NUM_STYLES; ++i) {
                if (adapter.items[i] != slider) {
                    otherSum += adapter.items[i].value;
                }
            }

            if (otherSum > 0.0) {
                float highestOtherVal = 0;
                final float factor = otherSum > 0.0f ? (1.0f - value) / otherSum : 0.0f;
                for (int i = 0; i < NUM_STYLES; ++i) {
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
                    lastOtherStyle = (lastOtherStyle + 1) % NUM_STYLES;
                }
                adapter.items[lastOtherStyle].setValue(1.0f - value);
            }
        }

        final boolean lastAllZero = allZero;
        float sum = 0.0f;
        for (int i = 0; i < NUM_STYLES; ++i) {
            sum += adapter.items[i].value;
        }
        allZero = sum == 0.0f;

        // Now update the values used for the input tensor. If nothing is set, mix in everything
        // equally. Otherwise everything is normalized to sum to 1.0.
        for (int i = 0; i < NUM_STYLES; ++i) {
            styleVals[i] = allZero ? 1.0f / NUM_STYLES : adapter.items[i].value / sum;

            if (lastAllZero != allZero) {
                adapter.items[i].postInvalidate();
            }
        }
    }
}
