package com.example.EyeKeeper;

import static android.os.SystemClock.sleep;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.res.AssetManager;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.speech.tts.TextToSpeech;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SizeF;
import android.view.Display;
import android.view.SurfaceView;
import android.widget.Toast;


import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.dnn.Net;
import org.opencv.imgproc.Imgproc;

import org.opencv.dnn.Dnn;
import org.opencv.utils.Converters;


import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;


public class WalkingActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {
    boolean detection=false;

    //timer 사용 final val
    private static final int MESSAGE_TIMER_START=100;
    private static final int MESSAGE_TIMER_STOP=-1;

    CameraBridgeViewBase cameraBridgeViewBase;
    BaseLoaderCallback baseLoaderCallback;
    Net tinyYolo;

    private static TextToSpeech tts;

    CameraManager manager;

    //distance calc val
    double focalLength;
    float sensor_height;
    int preview_height; //화면 높이
    double convertVal;

    TimerHandler timerHandler=null;

    //objectDetection global val(Handler)
    int object_height =-1; String objectName =null;
    boolean alreadyDetected =false;

    //모드 시작 알림
    boolean firstTime=true;

    int frameCnt=0;

    private static String getPath(String file, Context context) {
        AssetManager assetManager = context.getAssets();
        BufferedInputStream inputStream = null;
        try {
            // Read data from assets.
            inputStream = new BufferedInputStream(assetManager.open(file));
            byte[] data = new byte[inputStream.available()];
            inputStream.read(data);
            inputStream.close();
            // Create copy file in storage.
            File outFile = new File(context.getFilesDir(), file);
            FileOutputStream os = new FileOutputStream(outFile);
            os.write(data);
            os.close();
            return outFile.getAbsolutePath();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        tts=new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if(status!=TextToSpeech.ERROR)
                    tts.setLanguage(Locale.KOREAN);
            }
        });
        tts.setPitch(1.0f);
        tts.setSpeechRate(2.0f);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_walking);

        cameraBridgeViewBase = (CameraBridgeViewBase) findViewById(R.id.CameraView);
        cameraBridgeViewBase.setVisibility(SurfaceView.VISIBLE);
        cameraBridgeViewBase.setCvCameraViewListener(this);
        cameraBridgeViewBase.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_BACK); //후면카메라

        baseLoaderCallback = new BaseLoaderCallback(this) {
            @Override
            public void onManagerConnected(int status) {
                super.onManagerConnected(status);

                switch(status){
                    case BaseLoaderCallback.SUCCESS:
                        cameraBridgeViewBase.enableView();
                        break;
                    default:
                        super.onManagerConnected(status);
                        break;
                }

            }

        };


        //get Focal length, Sensor_height
        manager=(CameraManager) getSystemService(CAMERA_SERVICE);
        try {
            CameraCharacteristics characteristics=manager.getCameraCharacteristics("0");
            //초점거리 얻기
            float[] maxFocus = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
            focalLength =maxFocus[0];

            //image sensor
            SizeF size = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
            sensor_height=size.getHeight();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        //get xDPI
        Display display=getWindowManager().getDefaultDisplay();
        DisplayMetrics metrics = new DisplayMetrics();
        display.getMetrics(metrics);
        float xdpi=metrics.xdpi;

        //pixel to mm
        convertVal=25.4f/xdpi;

        //get display size
        android.graphics.Point displaySize=new android.graphics.Point();
        display.getRealSize(displaySize);
        int displayWidth=displaySize.x;
        int displayHeight=displaySize.y;

        Log.i("onCreate","display 가로: "+Integer.toString(displayWidth)+", display 세로: "+Integer.toString(displayHeight));

        timerHandler=new TimerHandler();
    }

    private class TimerHandler extends Handler{
        public void handleMessage(Message msg){
            switch(msg.what){
                case MESSAGE_TIMER_START:
                    detection=true;
                    frameCnt=0;
                    this.sendEmptyMessageDelayed(MESSAGE_TIMER_START,5000);
                    break;

                case MESSAGE_TIMER_STOP:
                    detection=false;
                    frameCnt=0;
                    this.removeMessages(MESSAGE_TIMER_START);
                    break;
            }
        }
    }



    public void objectDetect(){
        if(firstTime){
            tts.speak("보행모드가 실행됩니다.", TextToSpeech.QUEUE_FLUSH, null);
            firstTime=false;
        }else {
            if (objectName != null) {

                //frame size 얻는 코드 따로 빼기
                //frame width, height
                int[] arr = cameraBridgeViewBase.getFrameSize();
                String framemsg = "width: " + Integer.toString(arr[0]) + ", height: " + Integer.toString(arr[1]);
                Log.i("objectDetection", framemsg);

                //거리 측정
                double dist = 0;
                int realSize = 0;

                if (objectName == "Electric Scooter")
                    realSize = 1310;
                else if (objectName == "Bicycle")
                    realSize = 970;

                dist = focalLength * realSize * preview_height / (object_height * sensor_height);
                dist /= 10;
                //String info="fl: "+ Double.toString(focalLength)+"mm, real size: "+Integer.toString(realSize)+"mm, preview_height: "+Integer.toString(preview_height)+"pixels, odHeight: "+Integer.toString(object_height)+", sensor_height: "+Float.toString(sensor_height)+"mm";
                //Log.i("변수 정보",info);

                int distance = (int) Math.round(dist);
                //m 단위로 변경
                int meter = distance / 100;
                int cm = distance % 100;
                String msg = "전방" + Integer.toString(meter) + "m" + Integer.toString(cm) + "cm에" + objectName + "확인";
                tts.speak(msg, TextToSpeech.QUEUE_FLUSH, null);
            }
        }
    }


    //3프레임만 더 그리기
    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        Mat frame = inputFrame.rgba();
        Imgproc.cvtColor(frame, frame, Imgproc.COLOR_RGB2BGR);

        if(!alreadyDetected) {
            preview_height = frame.height();

            //Log 찍어서 frame size 출력
            Log.i("onCameraFrame","받은 frame 가로: "+Integer.toString(frame.cols())+", 세로: "+Integer.toString(frame.rows()));

            alreadyDetected=true;
        }

        if (!detection && frameCnt>3)
            return frame;

        if(!detection)
            frameCnt++;

        detection=false;

        //Dnn, NMS
        Mat imageBlob = Dnn.blobFromImage(frame, 0.00392, new Size(416,416),new Scalar(0, 0, 0),false, false);

        tinyYolo.setInput(imageBlob);

        java.util.List<Mat> result = new java.util.ArrayList<Mat>(2);

        List<String> outBlobNames = new java.util.ArrayList<>();
        outBlobNames.add(0, "yolo_16");
        outBlobNames.add(1, "yolo_23");

        tinyYolo.forward(result,outBlobNames);

        float confThreshold = 0.3f;

        List<Integer> clsIds = new ArrayList<>();
        List<Float> confs = new ArrayList<>();
        List<Rect> rects = new ArrayList<>();

        for (int i = 0; i < result.size(); ++i)
        {
            Mat level = result.get(i);

            for (int j = 0; j < level.rows(); ++j)
            {
                Mat row = level.row(j);
                Mat scores = row.colRange(5, level.cols());

                Core.MinMaxLocResult mm = Core.minMaxLoc(scores);

                float confidence = (float)mm.maxVal;

                Point classIdPoint = mm.maxLoc;

                if (confidence > confThreshold)
                {
                    int centerX = (int)(row.get(0,0)[0] * frame.cols());
                    int centerY = (int)(row.get(0,1)[0] * frame.rows());
                    int width   = (int)(row.get(0,2)[0] * frame.cols());
                    int height  = (int)(row.get(0,3)[0] * frame.rows());

                    int left    = centerX - width  / 2;
                    int top     = centerY - height / 2;

                    clsIds.add((int)classIdPoint.x);
                    confs.add((float)confidence);

                    rects.add(new Rect(left, top, width, height));
                }
            }
        }

        int ArrayLength = confs.size();
        if (ArrayLength>=1) {
            // Apply non-maximum suppression procedure.
            float nmsThresh = 0.1f;


            MatOfFloat confidences = new MatOfFloat(Converters.vector_float_to_Mat(confs));
            Rect[] boxesArray = rects.toArray(new Rect[0]);
            MatOfRect boxes = new MatOfRect(boxesArray);
            MatOfInt indices = new MatOfInt();

            //nms 수행
            Dnn.NMSBoxes(boxes, confidences, confThreshold, nmsThresh, indices);

            // Draw result boxes:
            int[] ind = indices.toArray();
            int heightTemp=-1;
            Rect boxMax=null;
            for (int i = 0; i < ind.length; ++i) {

                int idx = ind[i];
                Rect box = boxesArray[idx];
                int idGuy = clsIds.get(idx);

                List<String> cocoNames = Arrays.asList("Bicycle","Electric Scooter");

                //제일 큰 객체 저장
                if(heightTemp<box.height){
                    heightTemp=box.height;
                    boxMax=box;
                    objectName =cocoNames.get(idGuy);
                }
                object_height =heightTemp;
            }

            Imgproc.rectangle(frame, boxMax.tl(), boxMax.br(), new Scalar(255, 0, 0), 2);
        }else{
            //객체 검출 x
            object_height =-1; objectName =null;
        }

        if(frameCnt==0)
            objectDetect(); //가장 큰 장애물 음성안내 서비스

        return frame;
    }


    @Override
    public void onCameraViewStarted(int width, int height) {

        String tinyYoloCfg = getPath("yolov3-tiny_obj(walking).cfg",this);
        String tinyYoloWeights = getPath("yolov3-tiny_obj_final(walking).weights",this);

        tinyYolo = Dnn.readNetFromDarknet(tinyYoloCfg, tinyYoloWeights);
    }

    @Override
    public void onCameraViewStopped() {

    }


    @Override
    protected void onResume() {
        super.onResume();

        if (!OpenCVLoader.initDebug()){
            Toast.makeText(getApplicationContext(),"openCV 환경이 구성되지 않았습니다.", Toast.LENGTH_SHORT).show();
        }

        else
        {
            baseLoaderCallback.onManagerConnected(baseLoaderCallback.SUCCESS);
            timerHandler.sendEmptyMessage(MESSAGE_TIMER_START);
        }
    }

    @Override
    protected void onPause() {
        timerHandler.sendEmptyMessage(MESSAGE_TIMER_STOP);

        super.onPause();
        if(cameraBridgeViewBase!=null){
            cameraBridgeViewBase.disableView();
        }

        //관련 변수 초기화
        firstTime=true;
        object_height =-1; objectName =null;
        frameCnt=0;
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraBridgeViewBase!=null){
            cameraBridgeViewBase.disableView();
        }

        if(tts!=null){
            tts.stop();
            tts.shutdown();
            tts=null;
        }
    }
}