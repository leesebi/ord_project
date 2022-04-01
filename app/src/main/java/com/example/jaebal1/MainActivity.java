package com.example.jaebal1;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Environment;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Toast;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.*;
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
import org.w3c.dom.Text;


import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.TimerTask;


//TimerTask 말고 Handler 사용

public class MainActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {
    private static final int MESSAGE_TIMER_START=100;

    CameraBridgeViewBase cameraBridgeViewBase;
    BaseLoaderCallback baseLoaderCallback;
    //boolean startYolo = true;
    //boolean firstTimeYolo = false;

    Net tinyYolo;

    //odService global val
    int odHeight=-1; //sync 해결
    String odName;

    //tts
    private TextToSpeech tts;

    //CameraBridgeViewBase.CvCameraViewFrame nowFrame=null;

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

        //System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
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
        tts.setSpeechRate(1.0f);

    }

    private class TimerHandler extends Handler{

        public void handleMessage(Message msg){
            switch(msg.what){
                case MESSAGE_TIMER_START:
                    odService();
                    Log.d("timer","timer 수행");
                    this.sendEmptyMessageDelayed(MESSAGE_TIMER_START,5000);
                    break;
            }
        }
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        int heightTemp=-1;

        Mat frame = inputFrame.rgba();
        //frame.copyTo(nowFrame);
        //nowFrame=frame.clone();

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
            float nmsThresh = 0.45f;


            MatOfFloat confidences = new MatOfFloat(Converters.vector_float_to_Mat(confs));

            Rect[] boxesArray = rects.toArray(new Rect[0]);

            MatOfRect boxes = new MatOfRect(boxesArray);

            MatOfInt indices = new MatOfInt();

            Dnn.NMSBoxes(boxes, confidences, confThreshold, nmsThresh, indices);

            // Draw result boxes:
            int[] ind = indices.toArray();
            for (int i = 0; i < ind.length; ++i) {

                int idx = ind[i];
                Rect box = boxesArray[idx];
                int idGuy = clsIds.get(idx);
                float conf = confs.get(idx);

                List<String> cocoNames = Arrays.asList("a person", "a bicycle", "a motorbike", "an airplane", "a bus", "a train", "a truck", "a boat", "a traffic light", "a fire hydrant", "a stop sign", "a parking meter", "a car", "a bench", "a bird", "a cat", "a dog", "a horse", "a sheep", "a cow", "an elephant", "a bear", "a zebra", "a giraffe", "a backpack", "an umbrella", "a handbag", "a tie", "a suitcase", "a frisbee", "skis", "a snowboard", "a sports ball", "a kite", "a baseball bat", "a baseball glove", "a skateboard", "a surfboard", "a tennis racket", "a bottle", "a wine glass", "a cup", "a fork", "a knife", "a spoon", "a bowl", "a banana", "an apple", "a sandwich", "an orange", "broccoli", "a carrot", "a hot dog", "a pizza", "a doughnut", "a cake", "a chair", "a sofa", "a potted plant", "a bed", "a dining table", "a toilet", "a TV monitor", "a laptop", "a computer mouse", "a remote control", "a keyboard", "a cell phone", "a microwave", "an oven", "a toaster", "a sink", "a refrigerator", "a book", "a clock", "a vase", "a pair of scissors", "a teddy bear", "a hair drier", "a toothbrush");

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
        }

        return frame;
/*        nowFrame=inputFrame;
        Mat frame=inputFrame.rgba();
        return frame;*/
    }


    @Override
    public void onCameraViewStarted(int width, int height) {

        String tinyYoloCfg = getPath("yolov3-tiny.cfg",this);
        String tinyYoloWeights = getPath("yolov3-tinyy.weights",this);

        tinyYolo = Dnn.readNetFromDarknet(tinyYoloCfg, tinyYoloWeights);
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
       /* //gloval val nowFrame 사용

        if(nowFrame!=null) {
            Mat frame = nowFrame.rgba();
            //nowFrame으로 objectDetection
            Imgproc.cvtColor(frame, frame, Imgproc.COLOR_RGBA2RGB);

            Mat imageBlob = Dnn.blobFromImage(frame, 0.00392, new Size(416, 416), new Scalar(0, 0, 0),false,false);

            tinyYolo.setInput(imageBlob);

            java.util.List<Mat> result = new java.util.ArrayList<Mat>(2);

            List<String> outBlobNames = new java.util.ArrayList<>();
            outBlobNames.add(0, "yolo_16");
            outBlobNames.add(1, "yolo_23");

            tinyYolo.forward(result, outBlobNames);

            float confThreshold = 0.3f;

            List<Integer> clsIds = new ArrayList<>();
            List<Float> confs = new ArrayList<>();
            List<Rect> rects = new ArrayList<>();

            for (int i = 0; i < result.size(); ++i) {

                Mat level = result.get(i);

                for (int j = 0; j < level.rows(); ++j) {
                    Mat row = level.row(j);
                    Mat scores = row.colRange(5, level.cols());

                    Core.MinMaxLocResult mm = Core.minMaxLoc(scores);

                    float confidence = (float) mm.maxVal;

                    Point classIdPoint = mm.maxLoc;

                    if (confidence > confThreshold) {
                        int centerX = (int) (row.get(0, 0)[0] * frame.cols());
                        int centerY = (int) (row.get(0, 1)[0] * frame.rows());
                        int width = (int) (row.get(0, 2)[0] * frame.cols());
                        int height = (int) (row.get(0, 3)[0] * frame.rows());

                        int left = centerX - width / 2;
                        int top = centerY - height / 2;

                        clsIds.add((int) classIdPoint.x);
                        confs.add((float) confidence);

                        rects.add(new Rect(left, top, width, height));
                    }
                }
            }
            int ArrayLength = confs.size();
            if (ArrayLength >= 1) {
                // Apply non-maximum suppression procedure.
                float nmsThresh = 0.45f;

                MatOfFloat confidences = new MatOfFloat(Converters.vector_float_to_Mat(confs));

                Rect[] boxesArray = rects.toArray(new Rect[0]);

                MatOfRect boxes = new MatOfRect(boxesArray);

                MatOfInt indices = new MatOfInt();

                Dnn.NMSBoxes(boxes, confidences, confThreshold, nmsThresh, indices);

                // Draw result boxes:
                int[] ind = indices.toArray();
                for (int i = 0; i < ind.length; ++i) {

                    int idx = ind[i];
                    Rect box = boxesArray[idx];

                    int idGuy = clsIds.get(idx);

                    float conf = confs.get(idx);

                    List<String> cocoNames = Arrays.asList("a person", "a bicycle", "a motorbike", "an airplane", "a bus", "a train", "a truck", "a boat", "a traffic light", "a fire hydrant", "a stop sign", "a parking meter", "a car", "a bench", "a bird", "a cat", "a dog", "a horse", "a sheep", "a cow", "an elephant", "a bear", "a zebra", "a giraffe", "a backpack", "an umbrella", "a handbag", "a tie", "a suitcase", "a frisbee", "skis", "a snowboard", "a sports ball", "a kite", "a baseball bat", "a baseball glove", "a skateboard", "a surfboard", "a tennis racket", "a bottle", "a wine glass", "a cup", "a fork", "a knife", "a spoon", "a bowl", "a banana", "an apple", "a sandwich", "an orange", "broccoli", "a carrot", "a hot dog", "a pizza", "a doughnut", "a cake", "a chair", "a sofa", "a potted plant", "a bed", "a dining table", "a toilet", "a TV monitor", "a laptop", "a computer mouse", "a remote control", "a keyboard", "a cell phone", "a microwave", "an oven", "a toaster", "a sink", "a refrigerator", "a book", "a clock", "a vase", "a pair of scissors", "a teddy bear", "a hair drier", "a toothbrush");

                    int intConf = (int) (conf * 100);

                    //Imgproc.putText(frame, cocoNames.get(idGuy) + " " + intConf + "%", box.tl(), Core.FONT_HERSHEY_SIMPLEX, 2, new Scalar(255, 255, 0), 2);
                    String boxInfo = cocoNames.get(idGuy) + ": (" + Integer.toString(box.x) + ", " + Integer.toString(box.y) + ")";
                    Log.d("bounding box", boxInfo);

                    //Imgproc.rectangle(frame, box.tl(), box.br(), new Scalar(255, 0, 0), 2);

                }
            }
        }*/
        //현재 받아온 odHeight, odNames 바탕으로 tts 해보기
        String msg="전방에 "+odName+"가 있습니다 높이는"+ Integer.toString(odHeight)+"입니다";
        tts.speak(msg,TextToSpeech.QUEUE_FLUSH,null);

        //삼각형 비례로 높이 구하기 (높이가 다름)
/*        if(odName=="Scotter"){

        }else(odName=="Bicycle"){

        }*/
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