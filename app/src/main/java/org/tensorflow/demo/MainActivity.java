package org.tensorflow.demo;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import org.tensorflow.contrib.android.TensorFlowInferenceInterface;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import ar.com.hjg.pngj.ImageInfo;
import ar.com.hjg.pngj.ImageLineByte;
import ar.com.hjg.pngj.PngWriter;

public class MainActivity extends AppCompatActivity {

    private static final String MODEL_FILE = "file:///android_asset/stylize_quantized.pb";
    private static final String INPUT_NODE = "input";
    private static final String STYLE_NODE = "style_num";
    private static final String OUTPUT_NODE = "transformer/expand/conv3/conv/Sigmoid";
    private TensorFlowInferenceInterface inferenceInterface;
    protected int desiredSize = 1000;
    private int[] intValues;
    private float[] floatValues;
    private int cnt = 0;
    private int blockSize = 500;
    private int overlay = 100;
    BitmapRegionDecoder mDecoder;
    private Canvas canvas;
    private Bitmap res_bitmap;
    private static final String PERMISSION_STORAGE = Manifest.permission.WRITE_EXTERNAL_STORAGE;
    private static final int PERMISSIONS_REQUEST = 1;
    File mFile;
    ImageInfo imgi;
    PngWriter pngw;
    private String root = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "tensorflow";
    ImageView image;
    Uri imageUri;
    private static final int PICK_IMAGE = 100;
    private static final int STYLES = 200;
    boolean image_picked = false;
    ProgressBar progressBar;
    ProgressBarTask progTask;


    private class ProgressBarTask extends AsyncTask<Integer, Integer, Integer> {

        @Override
        protected void onPreExecute() {
            //textView.setText("Hello !!!");
            progressBar.setVisibility(View.VISIBLE);
            super.onPreExecute();
        }

        @Override
        protected Integer doInBackground(Integer... ints) {
            stylize();
            return 0;
        }


        @Override
        protected void onPostExecute(Integer result) {
            progressBar.setVisibility(View.INVISIBLE);
        }
    }


    /**
     * initialization of the buttons and the imageView;
     *
     * @param savedInstanceState
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        //code that displays the content in full screen mode
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);//int flag, int mask

        setContentView(R.layout.activity_main);
        if (!hasPermission()) {
            requestPermission();
        }
        image = (ImageView) findViewById(R.id.imageView);
        final Button pickImageButton = (Button) findViewById(R.id.pickPhoto);
//        final ProgressBar prgBar=(ProgressBar) findViewById(R.id.prgrsBar);
        pickImageButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                openGallery();
            }
        });
        Button btn = (Button) findViewById(R.id.pick_styles);

        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (image_picked)
                    startActivityForResult((new Intent(MainActivity.this, StylesActivity.class)), STYLES);
            }
        });
        Button rotatebutton = (Button) findViewById(R.id.rotate);
        rotatebutton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (image_picked) {
                    Bitmap bitmapFromAsset = ((BitmapDrawable) image.getDrawable()).getBitmap();
                    Matrix mat = new Matrix();
                    mat.postRotate(90);
                    Bitmap bMapRotate = Bitmap.createBitmap(bitmapFromAsset, 0, 0, bitmapFromAsset.getWidth(),
                            bitmapFromAsset.getHeight(), mat, true);
                    image.setImageBitmap(bMapRotate);
                }
            }
        });
        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        progressBar.setVisibility(View.INVISIBLE);

    }

    /**
     * Moving to gallery to pick a photo;
     */
    private void openGallery() {
        Intent gallery =
                new Intent(Intent.ACTION_PICK,
                        android.provider.MediaStore.Images.Media.INTERNAL_CONTENT_URI);
        startActivityForResult(gallery, 100);
    }

    /**
     * After we finish our request the first thing the app does is
     * starting this function.
     * If the resultcode is equal to RESULT_OK then our request has successfully
     * completed; else an error happened.
     * If the requestcode=100 then we have finished the request of choosing an image from gallery
     * If the requestcode=200 the we have finished the request of selecting styles
     *
     * @param requestCode the request we have back from;
     * @param resultCode  the result of our request;
     * @param data        the data we get from the request we have done;
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == PICK_IMAGE) {
            //viewing an the image;
            imageUri = data.getData();
            image.setImageURI(imageUri);
            image_picked = true;
        }
        if (resultCode == RESULT_OK && requestCode == STYLES) {
            //stylize the photo with the selected filters
            progTask = new ProgressBarTask();
            progTask.execute(0);
        }
    }


    private void stylize() {
        inferenceInterface = new TensorFlowInferenceInterface(getAssets(), MODEL_FILE);
        createDecoder();
        res_bitmap = Bitmap.createBitmap(mDecoder.getWidth(), blockSize, Config.ARGB_8888);
        canvas = new Canvas(res_bitmap);
        intValues = new int[blockSize * blockSize];
        floatValues = new float[blockSize * blockSize * 3];


        String fname = "stylized" + (cnt++) + ".png";
        createNewPNG(fname);

        mFile = new File(root + File.separator + fname);
        imgi = new ImageInfo(mDecoder.getWidth(), mDecoder.getHeight(), 8, false);
        pngw = new PngWriter(mFile, imgi);

        divideAndStylize(mDecoder.getWidth(), mDecoder.getHeight());

        Bitmap result = BitmapFactory.decodeFile(root + File.separator + fname);

        image.setImageBitmap(result);
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
//            InputStream is = getAssets().open("ch1.jpg");
            InputStream is = getContentResolver().openInputStream(imageUri);

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
        inferenceInterface.feed(STYLE_NODE, GlobalVariables.getStyleVals(), GlobalVariables.getNumStyles());

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
}
