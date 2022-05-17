package com.example.EyeKeeper;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;

public class SplashActivity extends AppCompatActivity{

    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        Loadingstart();
    }

    private void Loadingstart(){
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
<<<<<<< HEAD
                Intent intent = new Intent(getApplicationContext(), MenuActivity.class);
=======
                Intent intent = new Intent(getApplicationContext(), MainActivity.class);
>>>>>>> 60d7a311f6f452d093a386e52247db3cd967d756
                startActivity(intent);
                finish();
            }
        },2000);
    }

}
