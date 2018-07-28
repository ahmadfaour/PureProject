package org.tensorflow.demo;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;

import java.io.InputStream;
import android.content.res.AssetManager;

public class StartActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        //code that displays the content in full screen mode
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);//int flag, int mask
        setContentView(R.layout.activity_start);

        ImageView screen=(ImageView) findViewById(R.id.i3);
        try {
            InputStream is = getAssets().open("u3.jpg");

            Bitmap bitmap = BitmapFactory.decodeStream(is);
            screen.setImageBitmap(bitmap);
        }catch (Exception e){

        }
        Button next=(Button) findViewById(R.id.btn);
        next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(StartActivity.this , MainActivity.class));
            }
        });
        next.setTextColor(Color.parseColor("#ffffffff"));
    }
}
