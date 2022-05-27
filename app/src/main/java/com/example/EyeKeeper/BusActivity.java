package com.example.EyeKeeper;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class BusActivity extends AppCompatActivity {
    private Button but;
    //private NearBusStopThread nearBusThread;
    private BusStopInfoThread busInfoThread;

    private double longitude;
    private double latitude;

    //수정사항: GPS 버튼 누르는 거 없이 되도록 acticity 시작 시에
    //내 생각엔 onCreate 말고 onResume에서 실행되는 게 맞다고 생각함 ㅇㅇ -> listener x 
    //추가적으로 loading 아이콘 같은 거 돌아가는 게 있었으면 좋겠음
    @Override
    protected void onCreate(Bundle savedInstaceState) {
        super.onCreate(savedInstaceState);
        setContentView(R.layout.activity_bus);

        but = (Button) findViewById(R.id.gps_but);

        final LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        String locationProvider = LocationManager.NETWORK_PROVIDER;

        but.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                if ( Build.VERSION.SDK_INT >= 23 &&
                        ContextCompat.checkSelfPermission( getApplicationContext(), android.Manifest.permission.ACCESS_FINE_LOCATION ) != PackageManager.PERMISSION_GRANTED ) {
                    ActivityCompat.requestPermissions( BusActivity.this, new String[] {  android.Manifest.permission.ACCESS_FINE_LOCATION  },
                            0 );
                }
                else{
                    Location location = lm.getLastKnownLocation(locationProvider);
                    longitude = location.getLongitude();
                    latitude = location.getLatitude();

                    Log.d("gps","경도 "+ longitude + "위도" + latitude);

                    lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1, gpsLocationListener);
                    lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 1, gpsLocationListener);

                    /*nearBusThread=new NearBusStopThread();
                    nearBusThread.start();*/

                    busInfoThread=new BusStopInfoThread();
                    busInfoThread.run();

                    //이후 다음 layout으로 넘어가도록
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

    //Callable thread로 변경 필요
    public class NearBusStopThread extends Thread{
        @Override
        public void run() {
            try {
                StringBuilder urlBuilder = new StringBuilder("http://apis.data.go.kr/1613000/BusSttnInfoInqireService/getCrdntPrxmtSttnList");

                urlBuilder.append("?" + URLEncoder.encode("serviceKey","UTF-8") +"="+ BuildConfig.BUSSTOP_API_KEY); /*Service Key*/
                urlBuilder.append("&" + URLEncoder.encode("pageNo","UTF-8") + "=" + URLEncoder.encode("1", "UTF-8")); /*페이지번호*/
                urlBuilder.append("&" + URLEncoder.encode("numOfRows","UTF-8") + "=" + URLEncoder.encode("5", "UTF-8")); /*한 페이지 결과 수*/
                urlBuilder.append("&" + URLEncoder.encode("_type","UTF-8") + "=" + URLEncoder.encode("json", "UTF-8")); /*데이터 타입(xml, json)*/
                urlBuilder.append("&" + URLEncoder.encode("gpsLati","UTF-8") + "=" + URLEncoder.encode("36.7667765", "UTF-8")); /*WGS84 위도 좌표*/
                urlBuilder.append("&" + URLEncoder.encode("gpsLong","UTF-8") + "=" + URLEncoder.encode("127.2816568", "UTF-8")); /*WGS84 경도 좌표*/

                URL url = new URL(urlBuilder.toString());

                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Content-type", "application/json");

                BufferedReader rd;
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
                Log.e("BUS_API_TEST",data);
                rd.close();
                conn.disconnect();

            }catch(Exception e){
                e.printStackTrace();
            }

        }

    }

    public class BusStopInfoThread extends Thread{

        private NearBusStopThread nearBusStopThread;

        @Override
        public void run(){
            nearBusStopThread=new NearBusStopThread();
            nearBusStopThread.start();

            try {
                nearBusStopThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            //본격적 busStopInfo 실행


        }

    }

}

