package com.example.transitSyncDriver;

import static androidx.constraintlayout.helper.widget.MotionEffect.TAG;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.gson.annotations.SerializedName;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import retrofit2.http.Query;

public class MainActivity extends AppCompatActivity {
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private static final int MAX_LOCATION_HISTORY = 10;
    private static final float MAX_ACCEPTABLE_SPEED = 50;
    private static final String BASE_URL = "http://Ec2_server_IP:8080/";
    private static final int REFRESH_INTERVAL = 10000;

    private ApiService apiService;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationRequest locationRequest;
    private List<LocationData> locationHistory;
    private long lastLocationTime;
    private String passengerCount = "0";
    private String driverPos="0";
    private TextView userCountText;
    private TextView currentRouteId;
    private ImageView profileIcon;
    private Socket socket;
    private String driverId = "driver001";
    private String selectedRouteId="";
    private Handler updateHandler;
    private Runnable updateDiverPosRunnable;
//    private Handler handler;
    private SharedPreferencesManager sharedPreferencesManager;
    private AppDatabase db;
    private String startLatLng;
    private String endLatLng;
    private TextView startRide;
    private volatile boolean flag=true;
    private volatile boolean flag2=true;
    private volatile boolean flag3=true;
    private String startHubId;
    private String switchHubId;
    private LatLng switchLatLngLt;
    private String endHubId;
    private LatLng startLatLngLt;
    private LatLng endLatLngLt;
    private  LatLng LocationLt;
    private String polyLine;
    private LatLngBounds paddedBounds;
    private int count=0;
    private TextView driverPosText;
    private Handler handlerBackground=new Handler();
    //    private Runnable recurringCheckTask;
//    private Runnable checkInTransit;
//    private Runnable checkReachEndHub;
    private static class PassengerCount {
        @SerializedName("count")
        private String count;
        public String getCount() {
            return count;
        }



        public PassengerCount() {
            this.count = "0";
        }
    }
    public static class DriverQueuePos{
        @SerializedName("driverPos")
        private String driverPos;

        public String getDriverPos() {
            return driverPos;
        }

        public void setDriverPos(String driverPos) {
            this.driverPos = driverPos;
        }
    }

    private static class LocationData {
        double latitude;
        double longitude;
        long timestamp;

        LocationData(double latitude, double longitude, long timestamp) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.timestamp = timestamp;
        }
    }

    private interface ApiService {
        @GET("getPassengerCount")
        Call<PassengerCount> getPassengerCount(@Query("routeId") String routeId ,@Query("startHubId") String startHubId);
        @GET("addToQueue")
        Call<ResponseBody> queueDriver(@Query("routeId") String routeId,@Query("hubId") String HubId,@Query("driverId") String driverId,@Query("remove") Boolean popDriver);
        @GET("removeFromQueue")
        Call<ResponseBody> removeFromQueue(@Query("routeId") String routeId,@Query("startHubId")String startHubId,@Query("remove") Boolean popDriver);
        @GET("driverQueuePos")
        Call<DriverQueuePos> driverQueuePos(@Query("routeId") String routeId ,@Query("startHubId") String startHubId,@Query("driverId") String driverID);
    }

    private static class LocationMessage {
        @SerializedName("latitude")
        private double latitude;

        @SerializedName("longitude")
        private double longitude;

        @SerializedName("driverId")
        private String driverId;

        @SerializedName("routeId")
        private String routeId;
        LocationMessage(double latitude, double longitude, String driverId,String routeId) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.driverId = driverId;
            this.routeId=routeId;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeComponents();
        setupRetrofit();

        db = AppDatabase.getDatabase(getApplicationContext());
        RoutesValueDao routesValueDao = db.routesValueDao();

        sharedPreferencesManager = SharedPreferencesManager.getInstance(getApplicationContext());
        selectedRouteId = sharedPreferencesManager.getString("selectedRouteId", "");

        if (selectedRouteId == null || selectedRouteId.isEmpty()) {
            Toast.makeText(this, "Please create or select a route from profile", Toast.LENGTH_SHORT).show();
        } else {
            Executors.newSingleThreadExecutor().execute(() -> {
                RoutesValue routeValue = routesValueDao.getRoutesValueById(selectedRouteId);
                if (routeValue != null) {
                    String startLatLngStr = routeValue.getStartHubLtLng();
                    String endLatLngStr = routeValue.getEndHubLtLng();

                    String[] partsStart = startLatLngStr.split(",");
                    String[] partsEnd = endLatLngStr.split(",");

                    double latitudeStart = Double.parseDouble(partsStart[0].trim());
                    double longitudeStart = Double.parseDouble(partsStart[1].trim());
                    double latitudeEnd = Double.parseDouble(partsEnd[0].trim());
                    double longitudeEnd = Double.parseDouble(partsEnd[1].trim());

                    startHubId = routeValue.getStartHubId();
                    Log.d("startHubId",startHubId);
                    endHubId = routeValue.getEndHubId();
                    startLatLngLt = new LatLng(latitudeStart, longitudeStart);
                    endLatLngLt = new LatLng(latitudeEnd, longitudeEnd);
                    polyLine=routeValue.getPolyLine();

                    runOnUiThread(() -> currentRouteId.setText(selectedRouteId));
                }
            });
        }

        profileIcon.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, ExistingRoute.class);
            startActivity(intent);


        });

        startRide.setOnClickListener(view -> {
            Log.d("reachedhere", "reached here");
            flag=true;
            flag2=true;
            flag3=true;
            setupSocket();
            setupLocationServices();
            startPassengerCountUpdate();// move to driverPosUpdate if driverpos==1(0) then startPassengerCountUpdate remove call back when inTransit
        });
    }

    private void initializeComponents() {
        locationHistory = new ArrayList<>();
        lastLocationTime = 0;
        userCountText = findViewById(R.id.userCountText);
        profileIcon = findViewById(R.id.profileIcon);
        currentRouteId=findViewById(R.id.currentRouteId);
        driverPosText=findViewById(R.id.driverPosText);
        startRide=findViewById(R.id.startRide);
        updateHandler = new Handler(Looper.getMainLooper());
//        handler=new Handler();//backGround task so no mainLooper
    }

    private void setupRetrofit() {
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        apiService = retrofit.create(ApiService.class);
    }

    private void setupLocationServices() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
                .setMinUpdateIntervalMillis(5000)
                .setMaxUpdateDelayMillis(20000)
                .build();

        if (checkLocationPermission()) {

            startLocationUpdates();
        } else {
            requestLocationPermission();
        }
    }

    private boolean checkLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                LOCATION_PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates();
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        if(polyLine!=null) {
         Log.d("polyLine",polyLine);
            List<LatLng> routePoints = decodePoly(polyLine);
            //can add startHub latLng and end hub Lat Lng

            LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
            boundsBuilder.include(startLatLngLt);
            boundsBuilder.include(endLatLngLt);
            for (LatLng point : routePoints) {
                boundsBuilder.include(point);
                Log.d("RoutePoint", "Included point: " + point.latitude + ", " + point.longitude);
            }
            LatLngBounds bounds = boundsBuilder.build();
            paddedBounds = getPaddedBounds(bounds, 0.0009);
        }
        else {
            Toast.makeText(this, "Couldn't process the route", Toast.LENGTH_SHORT).show();
            return;
        }


//        fusedLocationClient.getLastLocation()
//                .addOnSuccessListener(this, this::processNewLocation);
//paddedBounds.contains(location) if true then Toast you are already on the route stop clicking startRide
        fusedLocationClient.requestLocationUpdates(locationRequest,
                new LocationCallback() {
                    @Override
                    public void onLocationResult(LocationResult locationResult) {
                        if (locationResult != null) {
                            Log.d("Locationupdate","Done");
                            processNewLocation(locationResult.getLastLocation());
                        }
                    }
                }, Looper.getMainLooper());
    }

    private void setupSocket() {
        try {
            socket = IO.socket(BASE_URL);
            socket.on(Socket.EVENT_CONNECT, args ->
                    Log.d("Socket.IO", "Connected to server"));
            socket.on(Socket.EVENT_CONNECT_ERROR, args ->
                    Log.e("Socket.IO", "Connection Error: " + args[0]));
            socket.on(Socket.EVENT_DISCONNECT, args ->
                    Log.d("Socket.IO", "Disconnected from server"));
            socket.connect();
        } catch (URISyntaxException e) {
            Log.e("Socket.IO", "Socket initialization error", e);
        }
    }

    private void startPassengerCountUpdate() {
        Runnable updateCountRunnable = new Runnable() {
            @Override
            public void run() {
                fetchPassengerCount();
                updateHandler.postDelayed(this, REFRESH_INTERVAL);
            }
        };
        updateHandler.post(updateCountRunnable);
    }

    private void fetchPassengerCount() {
        if((selectedRouteId==null)||(startHubId==null)){
            return;
        }
        Call<PassengerCount> call = apiService.getPassengerCount(selectedRouteId,startHubId);
        call.enqueue(new Callback<PassengerCount>() {
            @Override
            public void onResponse(Call<PassengerCount> call, Response<PassengerCount> response) {
                if (response.isSuccessful() && response.body() != null) {
                    passengerCount = response.body().getCount();
                    updatePassengerCountUI(passengerCount);
                } else {
                    handlePassengerCountError("Server error: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<PassengerCount> call, Throwable t) {
                handlePassengerCountError(t.getMessage());
            }
        });
    }
    private void startDriverPosUpdate() {
        updateDiverPosRunnable = new Runnable() {
            @Override
            public void run() {
                fetchDriverQueuePos();
                handlerBackground.postDelayed(this, 10000);
            }
        };
        handlerBackground.post(updateDiverPosRunnable);
    }

    private void fetchDriverQueuePos() {
        if((selectedRouteId==null)||(startHubId==null)){
            return;
        }
        Log.d("driverstartHub","val:"+startHubId);
        Call<DriverQueuePos> call = apiService.driverQueuePos(selectedRouteId,startHubId,driverId);
        call.enqueue(new Callback<DriverQueuePos>() {
            @Override
            public void onResponse(Call<DriverQueuePos> call, Response<DriverQueuePos> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Log.d("DriverPosUpdate","driverPos:"+driverPos);
                    driverPos = response.body().getDriverPos();
                    updateDriverPosUI(driverPos);
                } else {
                    handleDriverPosError("Server error: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<DriverQueuePos> call, Throwable t) {
                Log.d("driverPosFAluier","failer");
                handleDriverPosError(t.getMessage());
            }
        });
    }
    private void updatePassengerCountUI(String count) {
        runOnUiThread(() -> {
            try {
                userCountText.setText("Passenger Count: " + count);
            } catch (Exception e) {
                Log.e("UI Update", "Error updating passenger count", e);
            }
        });
    }
    private void updateDriverPosUI(String count) {
//        if(driverPos=="1"){
//            startPassengerCountUpdate();
//        }
        runOnUiThread(() -> {
            try {
                driverPosText.setText(driverPos);
            } catch (Exception e) {
                Log.e("UI Update", "Error updating passenger count", e);
            }
        });
    }

    private void handlePassengerCountError(String error) {
        Log.e("Passenger Count", "Error: " + error);
        runOnUiThread(() -> {
            try {
                userCountText.setText("Passenger Count: " + passengerCount + " (Last Known)");
//                Toast.makeText(MainActivity.this,
//                        "Unable to update passenger count",
//                        Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Log.e("UI Update", "Error showing error message", e);
            }
        });
    }
    private void handleDriverPosError(String error) {
        Log.e("DriverPos", "Error: " + error);
        runOnUiThread(() -> {
            try {
                driverPosText.setText(driverPos + " (Last Known)");
//                Toast.makeText(MainActivity.this,
//                        "Unable to update passenger count",
//                        Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Log.e("UI Update", "Error showing error message", e);
            }
        });
    }
//    public void startDrive()

    private boolean isLocationValid(LocationData newLocation) {
        if (locationHistory.isEmpty()) {
            return true;
        }

        LocationData lastLocation = locationHistory.get(0);
        float[] results = new float[1];
        Location.distanceBetween(
                lastLocation.latitude, lastLocation.longitude,
                newLocation.latitude, newLocation.longitude,
                results
        );

        float distance = results[0];
        long timeDiff = newLocation.timestamp - lastLocation.timestamp;
        if (timeDiff == 0) return false;

        float speed = (distance / timeDiff) * 1000;

        if (speed > MAX_ACCEPTABLE_SPEED) {
            Log.d("Location", "Detected abnormal speed: " + speed + " m/s");
            return false;
        }

        return true;
    }

    private void processNewLocation(Location location) {
        if (location == null) return;

        if(flag) {
            LocationLt = new LatLng(location.getLatitude(), location.getLongitude());
           double val=calculateDistance(LocationLt,startLatLngLt);
            if(flag2) {// this flag is only used once
                double distanceStart=calculateDistance(LocationLt, startLatLngLt);
                double distanceEnd=calculateDistance(LocationLt, endLatLngLt);
            if ( distanceStart>distanceEnd ) {
                if(distanceStart<100||distanceEnd<100) {
                    flag2 = false;//Decide start and end station queuing

                }
                else{
                    Toast.makeText(this, "Reach near the hub for queuing", Toast.LENGTH_SHORT).show();
                    return;
                }
                switchLatLngLt = startLatLngLt;
                startLatLngLt = endLatLngLt;
                endLatLngLt = switchLatLngLt;
                switchHubId = startHubId;
                startHubId = endHubId;
                endHubId = switchHubId;

            }

        }
            if (calculateDistance(LocationLt, startLatLngLt) < 100.0&&startHubId!=null) {
                Log.d("startHubId",startHubId);

                Call<ResponseBody> call = apiService.queueDriver(selectedRouteId, startHubId, driverId,false);
                call.enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                        if (response.isSuccessful()) {
                            Log.d("driverAdded", "driver added to stationary queue");
                           runOnUiThread(()->{Toast.makeText(MainActivity.this, "Added To Queue", Toast.LENGTH_SHORT).show();});
                            startDriverPosUpdate();
                            flag = false;//added to queue stop trying to add
                            Log.d("FLAG_VALUE", "after set: " + flag);
                            flag3=true;//Get ready for checking in transit
                        }
                    }

                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {
                    Log.d("faulier","failerAtaddDriver");
                    }
                });
            }
            else {
                Toast.makeText(this, "Reach near the hub for queuing", Toast.LENGTH_SHORT).show();
            }
        }

/*Note: PaddedBounds /bounds builder is used to contain the driver within the bounds, bounds Builder forms a rectangular box, so if you want more granular control,
divide the route by picking out 10 to 12 points, make bounds containment , max containment can be checked for N/2 LatLng builder containment for N points, but considering
that driver end of the device will be computationally slow(cheaper) reduce computation as much on driver end , 10 to 12 bounds will be enough on city level to check for
containment for a route.
*/


        double distanceFromHub;
        if(!flag) {//stationary queuing done
            LocationLt = new LatLng(location.getLatitude(), location.getLongitude());
            //also check make count =2
           // if stored distance index polyline 0 distance increases and polyline 1 distance decreases
            distanceFromHub = calculateDistance(LocationLt, startLatLngLt);
            if (flag3 && distanceFromHub > 200&&paddedBounds.contains(LocationLt)) {
                Call<ResponseBody> call = apiService.queueDriver(selectedRouteId, startHubId, driverId, true);//add polyline index if 0 remove from both queue
                call.enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                        if (response.isSuccessful()) {
                            Log.d("driverAdded", "driver added to transit queue");
                              if (handlerBackground != null && updateDiverPosRunnable != null) {
                                        handlerBackground.removeCallbacks(updateDiverPosRunnable);
                                    }
                            runOnUiThread(() -> {
                                driverPosText.setText("InT"); // InTransit
                                Toast.makeText(MainActivity.this, "Driver in Transit", Toast.LENGTH_SHORT).show();
                            });
                            flag3 = false;//In transit
                            //flag4=true;
                        }
                    }

                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {

                    }
                });
            }
        }//move this to the bottom if, not in transit why store the location in redis?

        //if the vehicle in Transit and reaches the other station call this after d/v time interval take v=40km/hr get distance form database//add flag 4
        if(!flag3&&calculateDistance(new LatLng(location.getLatitude(),location.getLongitude()),endLatLngLt)<200.0){
            Call<ResponseBody> call = apiService.removeFromQueue(selectedRouteId, startHubId, true);
            call.enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                    if (response.isSuccessful()) {
                        Log.d("driverAdded", "driver switched to queue");

                        flag = true;
                        //flag4=false;
                    }
                }

                @Override
                public void onFailure(Call<ResponseBody> call, Throwable t) {

                }
            });
        
            switchLatLngLt=startLatLngLt;
            startLatLngLt=endLatLngLt;
            endLatLngLt=switchLatLngLt;
            switchHubId=startHubId;
            startHubId=endHubId;
            endHubId=switchHubId;
            flag=true;
            flag3=true;
        }


        try {
            LocationData newLocation = new LocationData(
                    location.getLatitude(),
                    location.getLongitude(),
                    System.currentTimeMillis()
            );

            if (System.currentTimeMillis() - lastLocationTime < 500) return;

            if (isLocationValid(newLocation)&&selectedRouteId!=null) {
                if(!paddedBounds.contains(LocationLt)) {
                    count++;
                    if (count > 2) {
                        Call<ResponseBody> call = apiService.removeFromQueue(selectedRouteId, startHubId, true);
                        call.enqueue(new Callback<ResponseBody>() {
                            @Override
                            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                                if (response.isSuccessful()) {
                                    Log.d("driverAdded", "driver switched to queue");
                                    Toast.makeText(MainActivity.this, "Moved away from the route removed from queue", Toast.LENGTH_SHORT).show();
                                    flag = true;
                                    //flag4=false;
                                }
                            }

                            @Override
                            public void onFailure(Call<ResponseBody> call, Throwable t) {

                            }
                        });
                        // try to remove form handler call back later
                    }
                }
                updateLocationHistory(newLocation);
                sendLocationUpdate(new LocationMessage(
                        newLocation.latitude,
                        newLocation.longitude,
                        driverId,selectedRouteId
                ));
                lastLocationTime = System.currentTimeMillis();
            } else if (!locationHistory.isEmpty()&&selectedRouteId!=null) {
                LocationData lastValid = locationHistory.get(0);
                sendLocationUpdate(new LocationMessage(
                        lastValid.latitude,
                        lastValid.longitude,
                        driverId,
                        selectedRouteId
                ));
            }
        } catch (Exception e) {
            Log.e("Location", "Error processing location", e);
        }
    }
   // public void

    private void sendLocationUpdate(LocationMessage location) {
        try {
            JSONObject locationJson = new JSONObject();
            locationJson.put("latitude", location.latitude);
            locationJson.put("longitude", location.longitude);
            locationJson.put("driverId", location.driverId);
            locationJson.put("routeId",location.routeId);
            if (socket != null && socket.connected()) {
                socket.emit("location_update", locationJson);
            } else {
                Log.w("Socket.IO", "Socket not connected, location update skipped");
            }
        } catch (Exception e) {
            Log.e("Socket.IO", "Error sending location update", e);
        }
    }
    private double calculateDistance(LatLng point1, LatLng point2) {
        float[] results = new float[1];
        Location.distanceBetween(
                point1.latitude, point1.longitude,
                point2.latitude, point2.longitude,
                results);
        return results[0];
    }
    public LatLngBounds getPaddedBounds(LatLngBounds bounds, double padding) {
        LatLng southwest = new LatLng(
                bounds.southwest.latitude - padding,
                bounds.southwest.longitude - padding
        );
        LatLng northeast = new LatLng(
                bounds.northeast.latitude + padding,
                bounds.northeast.longitude + padding
        );
        return new LatLngBounds(southwest, northeast);
    }
    private void updateLocationHistory(LocationData newLocation) {
        locationHistory.add(0, newLocation);
        if (locationHistory.size() > MAX_LOCATION_HISTORY) {
            locationHistory.remove(locationHistory.size() - 1);
        }
    }
    private List<LatLng> decodePoly(String encoded) {
        List<LatLng> poly = new ArrayList<>();
        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;

        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1F) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;

            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1F) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;

            double finalLat = lat / 1E5;
            double finalLng = lng / 1E5;
            poly.add(new LatLng(finalLat, finalLng));
        }

        return poly;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cleanupResources();
    }


    @Override
    protected void onResume() {
        super.onResume();

        Executors.newSingleThreadExecutor().execute(() -> {
            selectedRouteId = sharedPreferencesManager.getString("selectedRouteId", "");
            RoutesValueDao routesValueDao = db.routesValueDao();
            if (selectedRouteId != null ) {
                RoutesValue routeValue = routesValueDao.getRoutesValueById(selectedRouteId);
                if (routeValue != null) {
                    String startLatLngStr = routeValue.getStartHubLtLng();
                    String endLatLngStr = routeValue.getEndHubLtLng();

                    String[] partsStart = startLatLngStr.split(",");
                    String[] partsEnd = endLatLngStr.split(",");

                    try {
                        double latitudeStart = Double.parseDouble(partsStart[0].trim());
                        double longitudeStart = Double.parseDouble(partsStart[1].trim());
                        double latitudeEnd = Double.parseDouble(partsEnd[0].trim());
                        double longitudeEnd = Double.parseDouble(partsEnd[1].trim());

                        startHubId = routeValue.getStartHubId();
                        endHubId = routeValue.getEndHubId();
                        startLatLngLt = new LatLng(latitudeStart, longitudeStart);
                        endLatLngLt = new LatLng(latitudeEnd, longitudeEnd);
                        polyLine=routeValue.getPolyLine();
                        runOnUiThread(() -> currentRouteId.setText(selectedRouteId));
                    } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                        Log.e("MainActivity", "Error parsing LatLng strings", e);
                        // Handle the error appropriately, maybe set a default value or show an error message
                    }
                }
            }
        });
    }
    private void cleanupResources() {
        if (socket != null) {
            socket.disconnect();
            socket.off();
        }

        if (updateHandler != null) {
            updateHandler.removeCallbacksAndMessages(null);
        }

        if (fusedLocationClient != null) {
            fusedLocationClient.removeLocationUpdates(new LocationCallback() {});
        }
    }
}