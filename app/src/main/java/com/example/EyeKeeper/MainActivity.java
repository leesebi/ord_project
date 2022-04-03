package com.example.EyeKeeper;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.res.AssetManager;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.SurfaceView;
import android.widget.Toast;


import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
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

//TimerTask 말고 Handler 사용

//camera->camera2
//handler background 돌아가는 거 방지

public class MainActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {
    private static final int MESSAGE_TIMER_START=100;

    CameraBridgeViewBase cameraBridgeViewBase;
    BaseLoaderCallback baseLoaderCallback;
    Net tinyYolo;

    //odService global val
    int odHeight=-1;
    String odName;

    //focalLength
    float focalLength;

    //tts
    private TextToSpeech tts;

    //camera
    Camera camera;
    TimerHandler timerHandler=null;

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
            // Return a path to file which may be read in common way.
            return outFile.getAbsolutePath();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        cameraBridgeViewBase = (JavaCameraView)findViewById(R.id.CameraView);
        cameraBridgeViewBase.setVisibility(SurfaceView.VISIBLE);
        cameraBridgeViewBase.setCvCameraViewListener(this);

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

        //timer 구현
        timerHandler=new TimerHandler();

        //tts 구현
        tts=new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if(status!=TextToSpeech.ERROR)
                    tts.setLanguage(Locale.KOREAN);
            }
        });
        tts.setPitch(1.0f);
        tts.setSpeechRate(2.0f);

        //focalLenght
        try{
            camera= Camera.open();
        }catch(Exception e){
            Log.e("camera","camera Open error");
        }

        try{
            focalLength=camera.getParameters().getFocalLength();
            Log.e("focal Length",Float.toString(focalLength));
        }catch(Exception e){
            Log.e("onCameraFrame","getFocalLength");
        }

        //focalLength=camera.getParameters().getFocalLength();
        //Log.d("focal Lenght",Float.toString(focalLength));
    }

    private class TimerHandler extends Handler{

        public void handleMessage(Message msg){
            switch(msg.what){
                case MESSAGE_TIMER_START:
                    odService();
                    //Log.d("timer","timer 수행");
                    this.sendEmptyMessageDelayed(MESSAGE_TIMER_START,5000);
                    break;
            }
        }
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
/*        try{
            focalLength=camera.getParameters().getFocalLength();
        }catch(Exception e){
            Log.e("onCameraFrame","getFocalLength");
        }*/

        int heightTemp=-1;

        Mat frame = inputFrame.rgba();

        Imgproc.cvtColor(frame, frame, Imgproc.COLOR_RGBA2RGB);
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
            for (int i = 0; i < ind.length; ++i) {

                int idx = ind[i];
                Rect box = boxesArray[idx];
                int idGuy = clsIds.get(idx);
                float conf = confs.get(idx);

                //List<String> cocoNames = Arrays.asList("a person", "a bicycle", "a motorbike", "an airplane", "a bus", "a train", "a truck", "a boat", "a traffic light", "a fire hydrant", "a stop sign", "a parking meter", "a car", "a bench", "a bird", "a cat", "a dog", "a horse", "a sheep", "a cow", "an elephant", "a bear", "a zebra", "a giraffe", "a backpack", "an umbrella", "a handbag", "a tie", "a suitcase", "a frisbee", "skis", "a snowboard", "a sports ball", "a kite", "a baseball bat", "a baseball glove", "a skateboard", "a surfboard", "a tennis racket", "a bottle", "a wine glass", "a cup", "a fork", "a knife", "a spoon", "a bowl", "a banana", "an apple", "a sandwich", "an orange", "broccoli", "a carrot", "a hot dog", "a pizza", "a doughnut", "a cake", "a chair", "a sofa", "a potted plant", "a bed", "a dining table", "a toilet", "a TV monitor", "a laptop", "a computer mouse", "a remote control", "a keyboard", "a cell phone", "a microwave", "an oven", "a toaster", "a sink", "a refrigerator", "a book", "a clock", "a vase", "a pair of scissors", "a teddy bear", "a hair drier", "a toothbrush");
                List<String> cocoNames = Arrays.asList("bicycle","scooter");
                //여기서 bounding box 크기 큰 것만 객체 이름, 높이 가져감
                if(heightTemp<box.height){
                    heightTemp=box.height;
                    odName=cocoNames.get(idGuy);
                }
                odHeight=heightTemp;

                int intConf = (int) (conf * 100);
                Imgproc.putText(frame,cocoNames.get(idGuy) + " " + intConf + "%",box.tl(),Core.FONT_HERSHEY_SIMPLEX, 2, new Scalar(255,255,0),2);
                Imgproc.rectangle(frame, box.tl(), box.br(), new Scalar(255, 0, 0), 2);

            }
        }else{
            //검출 안 될때
            odHeight=-1; odName=null;
        }

        return frame;

        //frame에서 bounding box 안 그릴 때
/*        nowFrame=inputFrame;
        Mat frame=inputFrame.rgba();
        return frame;*/
    }


    @Override
    public void onCameraViewStarted(int width, int height) {

        String tinyYoloCfg = getPath("yolov3-tiny_obj.cfg",this);
        String tinyYoloWeights = getPath("yolov3-tiny_obj_final.weights",this);

        tinyYolo = Dnn.readNetFromDarknet(tinyYoloCfg, tinyYoloWeights);

        //timerHandler.sendEmptyMessage(MESSAGE_TIMER_START);
        //camera=Camera.open();
    }

    @Override
    public void onCameraViewStopped() {

    }


    @Override
    protected void onResume() {
        super.onResume();

        if (!OpenCVLoader.initDebug()){
            Toast.makeText(getApplicationContext(),"There's a problem, yo!", Toast.LENGTH_SHORT).show();
        }

        else
        {
            baseLoaderCallback.onManagerConnected(baseLoaderCallback.SUCCESS);
            timerHandler.sendEmptyMessage(MESSAGE_TIMER_START);
        }
    }

    //3초마다 frame 받아서 해당 frame에 object detection
    private void odService(){
        //현재 받아온 odHeight, odNames 바탕으로 tts 해보기
        if(odName!=null) {
            //거리 측정
            double distance = 0; //초기화
            if(odName=="scooter"){
                Log.d("scooter","scooter 계산");
                int realSize=1310;
                distance=focalLength*96*realSize/(25.4*odHeight);
            }else if(odName=="bicycle"){
                Log.d("bicycle","bicycle 계산");
                int realSize=540;
                distance=focalLength*96*realSize/(25.4*odHeight);
            }
            int dist=(int)Math.round(distance); //cm
            String msg="전방"+Integer.toString(dist)+"cm에 "+odName+"가 있습니다";
            tts.speak(msg, TextToSpeech.QUEUE_FLUSH, null);
        }else{ //확인용 -> 실서비스에서 제거
            String msg="관찰된 장애물이 없습니다";
            tts.speak(msg,TextToSpeech.QUEUE_FLUSH,null);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(cameraBridgeViewBase!=null){

            cameraBridgeViewBase.disableView();
        }

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
        //handler destroy?
    }
}