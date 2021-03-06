package com.nctu_android.test;

import android.os.Vibrator;
import android.app.Service;

import java.net.URISyntaxException;
import java.util.ArrayList;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.location.Location;
import android.location.LocationManager;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.ImageButton;
import android.location.Criteria;
import android.location.LocationListener;
import android.database.sqlite.SQLiteDatabase;
import android.view.View.OnClickListener;
import android.view.View;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.model.CameraPosition;


import java.text.DecimalFormat;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;



//map的activity
public class MapsActivity extends FragmentActivity {

    private GoogleMap mMap; // Might be null if Google Play services APK is not available.
    private UiSettings uisettings;
    private MyLocationListener mll;
    private LocationManager mgr;
    private String best;

    ImageButton Bag;
    Button Battle;
    SQLiteDatabase db;
    ArrayList<String> idlist;
    ArrayList<String> idlist2;

    private boolean isIn;
    ArrayList<String> poslist = new ArrayList<String>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //開啟db
        DBOpenHelper openhelper = new DBOpenHelper(this);
        db = openhelper.getWritableDatabase();

        //設定layout
        setContentView(R.layout.activity_maps);
        setUpMapIfNeeded();

        //設定map
        mgr = (LocationManager)getSystemService(LOCATION_SERVICE);
        mll = new MyLocationListener();
        uisettings = mMap.getUiSettings();

        //抓取使用者位置
        Location location = mgr.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if (location == null) {
            location = mgr.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        }
        if (location != null) {
            //跳出toast顯示目前經緯度
            Toast t = Toast.makeText(MapsActivity.this, showLocation(location), Toast.LENGTH_LONG);
            t.show();
            LatLng now_location = new LatLng(location.getLatitude(),location.getLongitude());

            // Move the center position
            CameraPosition.Builder cpb =new CameraPosition.Builder();
            cpb.target(now_location);
            cpb.zoom(16f);
            cpb.bearing(0);

            CameraPosition cpnctu = cpb.build();
            CameraUpdate initloc = CameraUpdateFactory. newCameraPosition(cpnctu);
            mMap.animateCamera(initloc);

        } else {
            Toast t = Toast.makeText(MapsActivity.this,"無法取得座標",Toast.LENGTH_LONG);
            t.show();
        }

        //bag button
        Bag = (ImageButton) findViewById(R.id.btnBag);
        Bag.setOnClickListener(btnBag);

        //battle button
        Battle = (Button) findViewById(R.id.btnBattle);
        Battle.setOnClickListener(btnBattle);



    }

    @Override
    protected void onResume() {
        super.onResume();
        setUpMapIfNeeded();

        //開啟db
        DBOpenHelper openhelper = new DBOpenHelper(MapsActivity.this);
        db = openhelper.getWritableDatabase();

        // Show Current Location
        mMap.setMyLocationEnabled(true);
        uisettings.setMyLocationButtonEnabled(true);

        isIn = true;

        Criteria criteria = new Criteria();
        criteria.setAccuracy(Criteria.ACCURACY_FINE);
        best = mgr.getBestProvider(criteria, true);
        if (best != null) {
            //每移動10m做一次處理
            mgr.requestLocationUpdates(best, 1000,5, mll);
        } else {
            mgr.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 5, mll);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        //db.close();
        mgr.removeUpdates(mll);
    }

    @Override
    protected void onStop() {
        super.onStop();
        db.close();
    }

    //偵測到移動時的處理
    class MyLocationListener implements LocationListener {
        @Override
        public void onLocationChanged(Location location) {

            if (location != null) {
                //顯示目前經緯度
                Toast t = Toast.makeText(MapsActivity.this, showLocation(location), Toast.LENGTH_SHORT);
                t.show();

                //移動鏡頭
                CameraPosition.Builder cpb =new CameraPosition.Builder();
                LatLng now_location = new LatLng(location.getLatitude(),location.getLongitude());
                cpb.target(now_location);
                float zoom = mMap.getCameraPosition().zoom;
                cpb.zoom(zoom);
                cpb.bearing(0);
                CameraPosition cpnctu = cpb.build();
                CameraUpdate initloc = CameraUpdateFactory. newCameraPosition(cpnctu);
                mMap.animateCamera(initloc);

                //***********判斷附近是否有monster可以捕捉***********//
                boolean allnotin = true;
                for( String monster: poslist) {

                    String[] tokens = monster.split(",");
                    String id = tokens[0];
                    String name = tokens[1];
                    String x = tokens[2];
                    String y = tokens[3];

                    Location dest = new Location(location);
                    dest.setLatitude(Double.parseDouble(x));
                    dest.setLongitude(Double.parseDouble(y));

                    float dist = location.distanceTo(dest);
                    if (dist < 10.0) {
                        if (isIn == false) {
                            //如果有monser可以捕捉則跳出dialog
                            dialog(id,name);
                            isIn = true;
                            break;
                        }
                        allnotin = false;
                    }
                }
                if(allnotin)isIn = false;

            } else {
                Toast t = Toast.makeText(MapsActivity.this,"無法取得座標",Toast.LENGTH_LONG);
                t.show();
            }
        }
        @Override
        public void onProviderDisabled(String provider) {
        }
        @Override
        public void onProviderEnabled(String provider) {
        }
        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }
    }


    private void setUpMapIfNeeded() {
        // Do a null check to confirm that we have not already instantiated the map.
        if (mMap == null) {
            // Try to obtain the map from the SupportMapFragment.
            mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map))
                    .getMap();
            mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL );
            // Check if we were successful in obtaining the map.
            if (mMap != null) {
                setUpMap();
            }
        }
    }


    private void setUpMap() {

        idlist = MonsterDB.getIDList(db);

        //將所有monster以marker的方式標記在map上
        for( String id:idlist){
            String pos= MonsterDB.getPosition(db,id);
            String name= MonsterDB.getName(db,id);

            String source = id+","+name+","+pos;
            poslist.add(source);

            String[] tokens = pos.split(",");
            String x = tokens[0];
            String y = tokens[1];

            MarkerOptions mo = new MarkerOptions();
            LatLng ll = new LatLng(Double.parseDouble(x),Double.parseDouble(y));
            mo.position(ll);
            mo.title(name);
            String uri="@drawable/" + id;
            mo.icon(BitmapDescriptorFactory.fromResource(getResources().getIdentifier(uri, null, getPackageName())));

            mMap.addMarker(mo);

        }
    }

    //顯示經位度的方式
    public String showLocation(Location location) {
        DecimalFormat formatter = new DecimalFormat("#.###");
        StringBuffer msg = new StringBuffer();
        msg.append("\n( ");
        msg.append(formatter.format( location.getLatitude() ));
        msg.append(" , ");
        msg.append(formatter.format( location.getLongitude() ));
        msg.append(" ) \n");
        return msg.toString();
    }

    //當點選bag圖示時的處理
    OnClickListener btnBag = new OnClickListener() {
        @Override
        public void onClick(View v) {

            //取得使用者擁有的monster的id
            idlist2 = BagDB.getIDList(db);
            int [] imageIds = null;
            int columns = 5;
            int n = idlist2.size();
            int i = 0;
            imageIds = new int[n];

            //找出其對應的圖片
            for( String id:idlist2) {
                String uri="@drawable/" + id;
                int source = getResources().getIdentifier(uri, null, getPackageName());
                imageIds[i] = source;
                i = i+1;
            }

            //使用intent將資訊傳給bag activity
            Intent intent = new Intent();
            intent.setClass(MapsActivity.this, BagActivity.class);
            intent.putExtra("KEY_IDS", imageIds);
            intent.putExtra("KEY_COLUMNS", columns);
            startActivity(intent);

        }
    };

    //當點選battle圖示時的處理
    OnClickListener btnBattle = new OnClickListener() {
        @Override
        public void onClick(View v) {

            //socket connect
            mSocket.connect();
            attemptSend();

            idlist2 = BagDB.getIDList(db);
            ArrayList<String> namelist = new ArrayList<String>();
            //找出其對應的名字
            for( String id:idlist2) {
                namelist.add(MonsterDB.getName(db,id));
            }

            final String name[] = new String[ namelist.size() ];
            namelist.toArray(name);

            //選擇出戰怪物
            new AlertDialog.Builder(MapsActivity.this).setTitle("出戰怪物")
                    .setSingleChoiceItems(
                            name, 0,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    Toast t = Toast.makeText(MapsActivity.this, "你選擇了"+name[which], Toast.LENGTH_SHORT);
                                    t.show();

                                    dialog.dismiss();

                                    //使用intent將資訊傳給battle activity
                                    Intent intent = new Intent();
                                    intent.setClass(MapsActivity.this, Battle.class);
                                    startActivity(intent);

                                }
                            })
                    .setNegativeButton("逃跑", null).show();


        }
    };


    //捕捉與否的dialog
    public void dialog(final String MonsterId,String MonsterName){
        final String name = MonsterName;
        final String id = MonsterId;

        AlertDialog.Builder dialog = new AlertDialog.Builder(MapsActivity.this);
        dialog.setTitle("野生的怪物出現了");
        dialog.setMessage("是"+name+"!!");

        //如果點選放棄時的操作
        dialog.setPositiveButton("放棄", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                Toast t = Toast.makeText(MapsActivity.this, name+"逃跑了", Toast.LENGTH_SHORT);
                t.show();
            }
        });

        //如果點選捕捉時的操作
        dialog.setNegativeButton("捕捉", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                String id = MonsterDB.getId(db, name);
                BagDB.addMonster(db,id);
                Toast t = Toast.makeText(MapsActivity.this, "獲得了" + name, Toast.LENGTH_SHORT);
                t.show();
            }
        });

        //遇到monster時手機會震動
        setVibrate(1000);
        dialog.setCancelable(false);
        dialog.show();
    }

    //控制震動的function
    public void setVibrate(int time){
        Vibrator myVibrator = (Vibrator) getSystemService(Service.VIBRATOR_SERVICE);
        myVibrator.vibrate(time);
    }

    //socket
    private Socket mSocket;
    {
        try {
            mSocket = IO.socket("http://apppp.ngrok.io");
        } catch (URISyntaxException e) {}
    }

    private void attemptSend() {
        String message = "testtttttttttt";

        mSocket.emit("new message", message);
    }
}

