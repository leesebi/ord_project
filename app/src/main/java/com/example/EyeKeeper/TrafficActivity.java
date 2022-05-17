package com.example.EyeKeeper;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.speech.tts.TextToSpeech;
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
import org.opencv.dnn.Dnn;
import org.opencv.dnn.Net;
import org.opencv.imgproc.Imgproc;
import org.opencv.utils.Converters;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class TrafficActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {

    //timer 사용 final val
    private static final int TRAFFIC_START=100;
    private static final int TRAFFIC_STOP=-1;


    CameraBridgeViewBase cameraBridgeViewBase;
    BaseLoaderCallback baseLoaderCallback;
    Net tinyYolo;

    private static TextToSpeech tts;

    TimerHandler trafficHandler=null;

    //traffic detection val
    boolean traffic =false; //신호등 인식 중 여부
    int trafficCount =0; //인식 불가 횟수 10회 넘어가면 (20초) 인식 해제
    boolean red=false;
    boolean green=false;
    String objectName=null;

    //frame 2초마다 탐지
    boolean detection=false;

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
        setContentView(R.layout.activity_traffic);

        cameraBridgeViewBase = (CameraBridgeViewBase) findViewById(R.id.TrafficView);
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

        trafficHandler=new TimerHandler(); //신호등 인식(2분마다 재인식)
    }

    private class TimerHandler extends Handler {
        public void handleMessage(Message msg) {
            switch (msg.what) {

                //2초마다 신호 파악
                case TRAFFIC_START:
                    //신호등 관련 변수 초기화
                    detection=true;
                    frameCnt=0; //다시 프레임 그리기
                    this.sendEmptyMessageDelayed(TRAFFIC_START,2000); //2초마다 신호 인식
                    break;

                case TRAFFIC_STOP:
                    detection=false;
                    frameCnt=0;
                    this.removeMessages(TRAFFIC_START);
                    break;
            }
        }
    }

    //신호등 감지
    //코드 계속 수정하면서 리팩토링 (안돌려봐서 모름 사실;;)
    public void trafficDetect(){
        if(firstTime) {
            tts.speak("횡단모드가 실행됩니다.", TextToSpeech.QUEUE_FLUSH, null);
            firstTime = false;
        } else {
            //신호등 탐지 x
            if (objectName == null) {
                //인식 중
                if (traffic) {
                    if (trafficCount == 10) {
                        red = false;
                        green = false;
                        traffic = false; //인식 해제

                        //10 frame동안 신호등 탐지 x -> 신호등이 없다는 판단의 근거
                        tts.speak("신호등을 재인식합니다.", TextToSpeech.QUEUE_FLUSH, null);
                        trafficCount = 0;
                    } else
                        trafficCount++; //기존 신호 변수 red,green은 유지 (잠시 프레임 아웃으로 감지되지 않을 수도 있음)
                }
            }

            //신호등 탐지 o
            //"Traffic Light Red", "Traffic Light Green","Traffic Light Black"
            else {
                if (!traffic) {
                    //인식중 x -> 인식 o
                    traffic = true;
                }
                if (objectName == "Traffic Light Red") {
                    if (green) {
                        tts.speak("빨간불로 변경되었습니다.", TextToSpeech.QUEUE_FLUSH, null);
                        green = false;
                    }
                    tts.speak("빨간불입니다.", TextToSpeech.QUEUE_FLUSH, null);
                    red=true;

                }
                else if (objectName == "Traffic Light Black") {
                    red = false;
                    green = true;
                    tts.speak("곧 빨간불로 변경됩니다.", TextToSpeech.QUEUE_FLUSH, null);
                }
                else if (objectName == "Traffic Light Green") {
                    if (red) {
                        tts.speak("초록불로 변경되었습니다.", TextToSpeech.QUEUE_FLUSH, null);
                        red = false;
                    }
                    tts.speak("초록불입니다.", TextToSpeech.QUEUE_FLUSH, null);
                    green=true;
                }
            }
        }

    }

    //2초마다 파악하는 거 이외에 3프레임 더 그리기 (얘는 object detection func 수행x)
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame){
        Mat frame=inputFrame.rgba();
        Imgproc.cvtColor(frame, frame, Imgproc.COLOR_RGB2BGR);

        //3frame 정도 더 그리기
        if(!detection && frameCnt>3)
            return frame;

        //2초마다 인식하는 게 아니면
        if(!detection)
            frameCnt++;

        detection=false;

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

                List<String> cocoNames = Arrays.asList("Traffic Light Red", "Traffic Light Green","Traffic Light Black");

                //제일 큰 객체 저장
                if(heightTemp<box.height){
                    boxMax=box;
                    objectName =cocoNames.get(idGuy);
                }
            }

            //Imgproc.putText(frame,objectName,boxMax.tl(),Core.FONT_HERSHEY_SIMPLEX, 2, new Scalar(255,255,0),2);
            Imgproc.rectangle(frame, boxMax.tl(), boxMax.br(), new Scalar(255, 0, 0), 2);

        }else{
            //객체 검출 x
            objectName =null;
        }

        if(frameCnt==0)
            trafficDetect();

        return frame;
    }

    @Override
    public void onCameraViewStarted(int width, int height) {

        String tinyYoloCfg = getPath("yolov3-tiny_obj.cfg",this);
        String tinyYoloWeights = getPath("yolov3-tiny_obj_final.weights",this);

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
            trafficHandler.sendEmptyMessage(TRAFFIC_START);
        }
    }

    @Override
    protected void onPause() {
        trafficHandler.sendEmptyMessage(TRAFFIC_STOP);

        super.onPause();
        if(cameraBridgeViewBase!=null){
            cameraBridgeViewBase.disableView();
        }

        //관련 변수 초기화
        firstTime=true;
        traffic =false;
        trafficCount =0;
        red=false;
        green=false;
        objectName=null;
        detection=false;
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