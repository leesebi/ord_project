package com.example.EyeKeeper;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;

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

        permissionCheck();

        LinearLayout layout01 = (LinearLayout) findViewById(R.id.tutorial);
        LinearLayout layout02 = (LinearLayout) findViewById(R.id.trafficlight);
        LinearLayout layout03 = (LinearLayout) findViewById(R.id.object);

        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if(status != ERROR) {
                    // 언어를 선택한다.
                    tts.setLanguage(Locale.KOREAN);
                }
            }
        });

        tts.setPitch(1.0f);
        tts.setSpeechRate(2.0f);

        View.OnClickListener clickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switch (v.getId()){
                    case R.id.tutorial:
                        Log.d("log01","click layout01");
                        break;
                    case R.id.trafficlight:
                        Log.d("log02","click layout02");
                        Intent intent_traffic=new Intent(getApplicationContext(),TrafficActivity.class);
                        startActivity(intent_traffic);
                        break;
                    case R.id.object:
                        Log.d("log03","click layout03");
                        Intent intent_walking = new Intent(getApplicationContext(), WalkingActivity.class);
                        startActivity(intent_walking);
                        break;
                }
            }
        };

        layout01.setOnClickListener(clickListener);
        layout02.setOnClickListener(clickListener);
        layout03.setOnClickListener(clickListener);
    }

    private void permissionCheck(){

        permission = new PermissionSupport(this, this);

        if(!permission.checkPermission()){
            permission.requestPermission();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        //여기서도 리턴이 false로 들어온다면 (사용자가 권한 허용 거부)
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (!permission.permissionResult(requestCode, permissions, grantResults)) {
            // 다시 permission 요청
            permission.requestPermission();
        }

    }

}