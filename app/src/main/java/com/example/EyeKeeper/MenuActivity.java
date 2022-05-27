package com.example.EyeKeeper;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.Locale;
import static android.speech.tts.TextToSpeech.ERROR;


public class MenuActivity extends AppCompatActivity {
    private final int PERMISSION_CAMERA=1001;
    private final int PERMISSION_STORAGE=1002;
    static final int PERMISSION_REQUEST = 0x0000001;
    private PermissionSupport permission;
    private TextToSpeech tts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu);

        //permissionCheck();
        LinearLayout layout01 = (LinearLayout) findViewById(R.id.tutorial);
        LinearLayout layout02 = (LinearLayout) findViewById(R.id.trafficlight);
        LinearLayout layout03 = (LinearLayout) findViewById(R.id.object);
        LinearLayout layout04 = (LinearLayout) findViewById(R.id.bus);



        View.OnClickListener clickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switch (v.getId()){
                    case R.id.tutorial:
                        break;
                    case R.id.trafficlight:
                        Intent intent_traffic=new Intent(getApplicationContext(),TrafficActivity.class);
                        startActivity(intent_traffic);
                        break;
                    case R.id.object:
                        Intent intent_walking = new Intent(getApplicationContext(), WalkingActivity.class);
                        startActivity(intent_walking);
                        break;
                    case R.id.bus:
                        Intent intent_bus = new Intent(getApplicationContext(), BusStopActivity_ver2.class);
                        startActivity(intent_bus);
                        break;
                }
            }
        };

        layout01.setOnClickListener(clickListener);
        layout02.setOnClickListener(clickListener);
        layout03.setOnClickListener(clickListener);
        layout04.setOnClickListener(clickListener);

    }


    private void permissionCheck(){
        permission = new PermissionSupport(this, this);
        if(!permission.checkPermission()){
            permission.requestPermission();
        }
    }

//    @Override
//    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
//        //여기서도 리턴이 false로 들어온다면 (사용자가 권한 허용 거부)
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
//        if (!permission.permissionResult(requestCode, permissions, grantResults)) {
//            // 다시 permission 요청
//            Toast.makeText(getApplicationContext(), "권한이 필요합니다.", Toast.LENGTH_LONG).show();
//            finish();
//        }
//
//    }
}