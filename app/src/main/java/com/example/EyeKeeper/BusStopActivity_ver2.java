package com.example.EyeKeeper;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;


public class BusStopActivity_ver2 extends AppCompatActivity {
    private Button but;
    private Button but1;

    //GPS
    private double longitude;
    private double latitude;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bus);

        but = (Button) findViewById(R.id.gps_but);
        but1 = (Button) findViewById(R.id.intent_but);

        but1.setOnClickListener(new View.OnClickListener(){
            public void onClick(View view){
                Intent intent = new Intent(getApplicationContext(), BusInfoActivity.class);
                startActivity(intent);
            }
        });


        //location 위한 변수 (network 필요)
        final LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        String locationProvider = LocationManager.NETWORK_PROVIDER;

        but.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                if (Build.VERSION.SDK_INT >= 23 &&
                        ContextCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(BusStopActivity_ver2.this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                            0);
                } else {
                    Location location = lm.getLastKnownLocation(locationProvider);
                    longitude = location.getLongitude();
                    latitude = location.getLatitude();

                    Log.d("gps", "경도: " + longitude + ", 위도: " + latitude);

                    //onResume시에 변경
                    lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1, gpsLocationListener);
                    lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 1, gpsLocationListener);

                    //sync

                    List<BusStop> busStops;

/*                    BusStopThread bst=new BusStopThread();
                    try {
                        busStops=bst.call();
                        for(BusStop bs:busStops)
                            Log.i("busStop",bs.getStr());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }*/

                    ExecutorService executorService= Executors.newSingleThreadExecutor();

                    BusStopThread busStopThread=new BusStopThread();
                    Future<List<BusStop>> future=executorService.submit(busStopThread);

                    try {
                        busStops=(List<BusStop>)future.get();

                        for(BusStop bs:busStops)
                            Log.i("busStop",bs.getStr());

                    } catch (ExecutionException e) {
                        e.printStackTrace();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

        final LocationListener gpsLocationListener = new LocationListener() {
            public void onLocationChanged(Location location) {
                longitude= location.getLongitude();
                latitude = location.getLatitude();
                Log.d("gps","경도:"+ longitude + ", 위도:" + latitude);

            }
            public void onStatusChanged(String provider, int status, Bundle extras) {
            }
            public void onProviderEnabled(String provider) {
            }
            public void onProviderDisabled(String provider) {
            }
        };

    public class BusStopThread implements Callable<List<BusStop>>{

        @Override
        public List<BusStop> call() throws Exception {
            StringBuilder urlBuilder = new StringBuilder("http://apis.data.go.kr/1613000/BusSttnInfoInqireService/getCrdntPrxmtSttnList");

            urlBuilder.append("?" + URLEncoder.encode("serviceKey","UTF-8") +"="+ BuildConfig.BUSSTOP_API_KEY); /*Service Key*/
            urlBuilder.append("&" + URLEncoder.encode("pageNo","UTF-8") + "=" + URLEncoder.encode("1", "UTF-8")); /*페이지번호*/
            urlBuilder.append("&" + URLEncoder.encode("numOfRows","UTF-8") + "=" + URLEncoder.encode("5", "UTF-8")); /*한 페이지 결과 수*/
            urlBuilder.append("&" + URLEncoder.encode("_type","UTF-8") + "=" + URLEncoder.encode("json", "UTF-8")); /*데이터 타입(xml, json)*/
            urlBuilder.append("&" + URLEncoder.encode("gpsLati","UTF-8") + "=" + URLEncoder.encode(Double.toString(latitude), "UTF-8")); /*WGS84 위도 좌표*/
            urlBuilder.append("&" + URLEncoder.encode("gpsLong","UTF-8") + "=" + URLEncoder.encode(Double.toString(longitude), "UTF-8")); /*WGS84 경도 좌표/*

           /* urlBuilder.append("&" + URLEncoder.encode("gpsLati","UTF-8") + "=" + URLEncoder.encode("37.5557965", "UTF-8")); *//*WGS84 위도 좌표*//*
            urlBuilder.append("&" + URLEncoder.encode("gpsLong","UTF-8") + "=" + URLEncoder.encode("126.9723378", "UTF-8")); *//*WGS84 경도 좌표*/


            URL url = new URL(urlBuilder.toString());
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Content-type", "application/json");

            BufferedReader rd;
            Log.e("error code:", String.valueOf(conn.getResponseCode()));

            if (conn.getResponseCode() >= 200 && conn.getResponseCode() <= 300) {
                rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            } else {
                rd = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
            }
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = rd.readLine()) != null) {
                sb.append(line);
            }

            String data=sb.toString();
            rd.close();
            conn.disconnect();

            Log.e("BUS_API_TEST",data);

            //json Parsing (refactoring 필요)
            JSONObject jsonObject=new JSONObject(data);
            JSONArray jsonArray=jsonObject.getJSONObject("response").getJSONObject("body").getJSONObject("items").getJSONArray("item");

            List<BusStop> busStopList=new ArrayList<>();

            for(int i=0;i<jsonArray.length();i++){
                jsonObject=jsonArray.getJSONObject(i);

                //BusStop 객체에 넣기
                String nodeid=jsonObject.getString("nodeid");
                String nodenm=jsonObject.getString("nodenm");
                String nodeno=jsonObject.getString("nodeno");
                String citycode=jsonObject.getString("citycode");

                busStopList.add(new BusStop(nodeid,nodenm,nodeno,citycode));
            }

            for(BusStop bs:busStopList)
                Log.i("버스정류장 정보",bs.getStr());

            return busStopList;
        }
    }

    public class BusStop{
        public String nodeid;
        public String nodenm;
        public String nodeno;
        public String citycode;

        public BusStop(String nodeid, String nodenm, String nodeno, String citycode) {
            this.nodeid = nodeid;
            this.nodenm = nodenm;
            this.nodeno = nodeno;
            this.citycode = citycode;
        }


        public String getStr(){
            return nodeid+", "+nodenm+", "+nodeno+", "+citycode;
        }
    }
}
