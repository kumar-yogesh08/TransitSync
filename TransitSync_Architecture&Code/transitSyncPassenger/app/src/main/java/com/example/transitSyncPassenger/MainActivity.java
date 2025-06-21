package com.example.transitSyncPassenger;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.gson.annotations.SerializedName;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Formatter;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

public class MainActivity extends FragmentActivity implements OnMapReadyCallback {
    private static final String TAG = "MainActivity";
    private GoogleMap mMap;
    private static final int LOCATION_REQUEST_CODE = 10001;
    private LatLng userLocation;
    private List<LatLng> originalRoutePoints = new ArrayList<>();
    private List<LatLng> currentRoutePoints;
    private double distanceGreen=0;
    private LatLng prevRemoteLocation;
    private LatLng currentRemoteLocation;
//    private Handler handler = new Handler();
    private Handler updateLocationHandler;
    private String city="Tezpur";
    private Runnable periodicRunnable;
    private LatLng startLatLng,endLatLng;
    private Runnable autocompleteRunnable;
    private Polyline routePolyline,routePolyline2;
    private List<Polyline> routePolylines = new ArrayList<>();
    private List<Integer> routeColors;
    private ApiService1 apiService1;
    private ApiService2 apiService2;
    private double  distance=0.0;
    private String driverId;
    private Handler handlerBackground=new Handler();
    private List<String> routes=new ArrayList<>();
    private String selectedRoute;
    private boolean passengerIsOnRoute;
    private FusedLocationProviderClient fusedLocationProviderClient;
//    private Handler locationUpdateHandler = new Handler();
    private static final int UPDATE_INTERVAL = 1000;
    private boolean initialRouteFetched = false;
    private TextView requestButton;
    private static final String BASE_URL = "http://Ec2_server_IP:8080/";
    private String gateWayUrl="Your_Api_gatewayURl";
    private List<String > hubNames=new ArrayList<>();
    private List<String> hubIds=new ArrayList<>();
    private AutoCompleteTextView startLocation;
    private AutoCompleteTextView endLocation;
    private String startHubName="";
    private String endHubName="";
    private String startHubId="";
    private String endHubId="";
    private boolean isAutocompleteFromSelection=false;
    private String routeId;
    private LatLngBounds paddedBounds;
    private List<String> driverIdsInTransitList;
    private int i=0;
    private boolean flag=true;
    private ImageView profileIcon;
    private ImageView intelligentRouteIcon;
    private ImageView profileButton;
    private static class LocationMessage {
        @SerializedName("latitude")
        private double latitude;

        @SerializedName("longitude")
        private double longitude;
    }
    public static class CountMessage{
        @SerializedName("Count")
        private boolean active;
        @SerializedName("id")
        private String id;
        @SerializedName("routeId_startHub_passenger")//change name to passanger Id or routeID_passenger
        private String routeId_startHub_passenger;
        public CountMessage(boolean active,String id,String routeId_startHub_passenger)
        {
            this.active=active;
            this.id=id;
            this.routeId_startHub_passenger=routeId_startHub_passenger;
        }
        public boolean isActive()
        {
            return active;
        }
        public void setActive(boolean active,String id)
        {
            this.active=active;
            this.id=id;
        }
    }
    private interface ApiService1 {
        @GET("driverLocation")
        Call<LocationMessage> getRemoteLocation(@Query("driverId") String driverId);
        @POST("addPassenger")
        Call<CountMessage> sendCount(@Body CountMessage countMessage);
        @GET("getDriverId")
        Call<DriverResponse> getDriverId(@Query("routeId") String routeId,@Query("city") String city,@Query("startHubId") String startHubId);// this will be a list of strings
        @GET("getDriverIdsInTransit")
        Call<List<DriverResponse>> getDriverIdsInTransit(@Query("routeId") String routeId,@Query("city") String city,@Query("startHubId") String startHubId);
        @GET("getPassengerIdsForExpiryCheck")
        Call<List<PassengerIdsProcess>> getPassengerIdsForExpiryCheck(@Query("routeId") String routeId,@Query("startHubId") String startHubId);
        @POST("postdelExpiredCustomerIds")
        Call<Void> postDelExpiredCustomerIds(@Query("routeId") String routeId,@Query("startHubId") String startHubId,@Body DriverIdsRequest request);
//        @GET("driverGet")
//        Call<LocationMessage> getRemoteLocation(@Query("driverId") String driverId);
//        @POST("customerCount")
//        Call<CountMessage> sendCount(@Body CountMessage countMessage);

    }
    private interface ApiService2{
      //  @GET("AutoComplete")
        @GET("RouteInfoFetch")
        Call<List<geoSearch>> getGeoSearch(@Query("geoSearch") String LatLng, @Query("city") String city);
        @GET("RouteInfoFetch")
        Call<List<String>> getHubAutoComplete(
                @Query("autocomplete") String prefix,@Query("city") String city);
//        @GET("HubInfo")
        @GET("RouteInfoFetch")
        Call<HubInfoResponse> getHubInfo(
                @Query("hubId") String hubId,@Query("city") String city);
//        @GET("RoutesInfo")
        @GET("RouteInfoFetch")
        Call<List<String>> getRoutesInfo(@Query("routesId") String routesId,@Query("city") String city);// notes multiple routes so routesID which is why list of string polyLines
//        @GET("DriverInfo")

    }

//Note: make public if doesn't work

//
//    private final Runnable locationUpdateRunnable = new Runnable() {
//        @Override
//        public void run() {
//            fetchRemoteLocation();
//            locationUpdateHandler.postDelayed(this, UPDATE_INTERVAL);
//        }
//    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setupRetrofit();
        setupRouteColors();
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        requestButton = findViewById(R.id.requestButton);
        startLocation=findViewById(R.id.start_location);
        endLocation=findViewById(R.id.end_location);
        profileIcon=findViewById(R.id.profile_icon);
        intelligentRouteIcon=findViewById(R.id.intelligen_route_icon);
        updateLocationHandler=new Handler(Looper.getMainLooper());
//        profileButton=findViewById(R.id.profile_icon);
        // Set up autocomplete
        SharedPreferences sharedPref = getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE);
        String userCity = sharedPref.getString("userCity", "");
        if(userCity!=null){
            city=userCity;
        }
        setupAutocomplete(startLocation, true);
        setupAutocomplete(endLocation, false);
        intelligentRouteIcon.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, IntelligentRouting.class);
            startActivity(intent);

        });

//        profileButton.setOnClickListener(v -> {
//            Intent intent = new Intent(MainActivity.this, ProfileActivity.class);
//            startActivity(intent);
//        });
        requestButton.setOnClickListener(v -> {
            if(!passengerIsOnRoute){
                runOnUiThread(()->{
                    Toast.makeText(this, "You are not on the route", Toast.LENGTH_SHORT).show();
                });
                return;
            }
            if (routeId != null) {
                fetchDriverId(routeId);

                //
            }
            else{
                Toast.makeText(this, "Select a Route", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupAutocomplete(AutoCompleteTextView textView, boolean isStartLocation) {
        textView.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence query, int i, int i1, int i2) {
                // Hide icons when user starts typing
                profileIcon.setVisibility(View.GONE);
                intelligentRouteIcon.setVisibility(View.GONE);
                if (autocompleteRunnable != null) {
                    updateLocationHandler.removeCallbacks(autocompleteRunnable);
                }
                String queryStr = query.toString();
                String relevantSelection = isStartLocation ? startHubName : endHubName;// this is because after onClick item the query in the autoComplete resets again making a api call, this prevents it
                autocompleteRunnable = () -> {
                    if (queryStr.length() > 2 && !queryStr.equals(relevantSelection)) {
                        fetchHubPredictions(queryStr, textView, isStartLocation);
                    }
                };
                updateLocationHandler.postDelayed(autocompleteRunnable, 1500); // 1.5 seconds delay
            }

            @Override
            public void afterTextChanged(android.text.Editable editable) {
            }
        });
    //Things to be inserted here
        textView.setOnItemClickListener((parent, view, position, id) -> {
            // AutocompletePrediction item = (AutocompletePrediction) parent.getItemAtPosition(position);
            // String placeId = item.getPlaceId();
            String hubId = hubIds.get(position);
            if (isStartLocation) {
                startHubName = (String) parent.getItemAtPosition(position);
                textView.setText(startHubName);
                startHubId = hubId;
            } else {
                endHubName = (String) parent.getItemAtPosition(position);
                textView.setText(endHubName);
                endHubId = hubId;
            }

            // Immediately clear focus to dismiss the keyboard
            textView.clearFocus();
            hubNames.clear();
            hubIds.clear();

            // Clear the adapter data and dismiss the dropdown

            textView.clearListSelection();
            ArrayAdapter<String> emptyAdapter = new ArrayAdapter<>(MainActivity.this,
                    android.R.layout.simple_dropdown_item_1line, new ArrayList<>());
            textView.setAdapter(emptyAdapter);
            textView.dismissDropDown();
            isAutocompleteFromSelection = false;
            Call<HubInfoResponse> call = apiService2.getHubInfo(hubId,city);
            call.enqueue(new Callback<HubInfoResponse>() {
                @Override
                public void onResponse(Call<HubInfoResponse> call, Response<HubInfoResponse> response) {
                    if (response.isSuccessful()) {
                        HubInfoResponse data = response.body();
                        hubIds.clear();
                        hubNames.clear();
            //To do fetch the hub hsetdata get HubLatLong and store, city not in need now

                        if (data != null) {

                            String[] parts = data.getLatLng().split(",");
                            if(isStartLocation){

                                if (parts.length == 2) {
                                    try {
                                        double latitude = Double.parseDouble(parts[0].trim());
                                        double longitude = Double.parseDouble(parts[1].trim());
                                        startLatLng = new LatLng(latitude, longitude);
                                    } catch (NumberFormatException e) {
                                        System.err.println("Error parsing latitude or longitude: " + startLatLng.toString());
                                        startLatLng = null; // Or handle the error as needed
                                    }
                                } else {
                                    System.err.println("Invalid hubLtLong format: " + startLatLng.toString());
                                    startLatLng = null; // Or handle the error as needed
                                }
                            } else{
                                if (parts.length == 2) {
                                    try {
                                        double latitude = Double.parseDouble(parts[0].trim());
                                        double longitude = Double.parseDouble(parts[1].trim());
                                        endLatLng = new LatLng(latitude, longitude);
                                        if(updateLocationHandler!=null &&autocompleteRunnable!=null) {

                                            updateLocationHandler.removeCallbacks(autocompleteRunnable);
                                            
                                        }
                                        startTracking();
                                        } catch (NumberFormatException e) {
                                        System.err.println("Error parsing latitude or longitude: " + endLatLng.toString());
                                        endLatLng = null; // Or handle the error as needed
                                    }
                                } else {
                                    System.err.println("Invalid hubLtLong format: " + endLatLng.toString());
                                    endLatLng = null; // Or handle the error as needed
                                }
                            }


//                            for (String val:HubInfoResponse){
//                                String [] value=val.split(":");
//                                String name=value[1];
//                                String hubId=value[2];
//                                hubNames.add(name);
//                                hubIds.add(hubId);
//                            }
                        }
//

                        System.out.println("AutoComplete successfully: " + response.body());
                    } else {
                        System.err.println("Failed to AutoComplete: " + response.code());
                    }
                }

                @Override
                public void onFailure(Call<HubInfoResponse> call, Throwable t) {
                    t.printStackTrace();
                }
            });

        });
    }


    private void setupRetrofit(){
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        apiService1 = retrofit.create(ApiService1.class);
        Retrofit retrofit2 = new Retrofit.Builder()
                .baseUrl(gateWayUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        apiService2 = retrofit2.create(ApiService2.class);
    }
    private void setupRouteColors() {
        routeColors = Arrays.asList(
                R.color.red_500,
                R.color.teal_700,
                R.color.design_default_color_primary,
                R.color.design_default_color_secondary,
                R.color.black
        );
    }
    private void fetchHubPredictions(String query, AutoCompleteTextView textView, boolean isStartLocation) {

        Call<List<String>> call=apiService2.getHubAutoComplete(query,city);
        call.enqueue(new Callback<List<String>>() {
            @Override
            public void onResponse(Call<List<String>> call, Response<List<String>> response) {
                if (response.isSuccessful()) {
                    List<String> data=response.body();
                    hubIds.clear();
                    hubNames.clear();

                    if (data!=null)
                    {
                        for (String val:data){
                            String [] value=val.split(":");//String=prefix:hubName:hubId
                            String name=value[1];
                            String hubId=value[2];
                            hubNames.add(name);
                            hubIds.add(hubId);
                        }
                    }
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(MainActivity.this,
                            android.R.layout.simple_dropdown_item_1line, hubNames);
                    textView.setAdapter(adapter);
                    textView.showDropDown();

                    System.out.println("AutoComplete successfully: " + response.body());
                } else {
                    System.err.println("Failed to AutoComplete: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<List<String>> call, Throwable t) {
                t.printStackTrace();
            }
        });

    }
    private void startTracking() {
        if(updateLocationHandler!=null &&autocompleteRunnable!=null) {
            updateLocationHandler.removeCallbacks(autocompleteRunnable);
        }
        if (!initialRouteFetched) {
            initialRouteFetched=true;
            fetchUserLocation();
        }

//        locationUpdateHandler.postDelayed(locationUpdateRunnable, UPDATE_INTERVAL);
    }

    private void fetchUserLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            fusedLocationProviderClient.getLastLocation()
                    .addOnSuccessListener(this, location -> {
                        if (location != null) {
                            userLocation = new LatLng(location.getLatitude(), location.getLongitude());
                            // Only fetch initial route once
//                            if (!initialRouteFetched) {
//                                initialRouteFetched = true;
//                            }

                            if(startHubId!=null && endHubId!=null) {

                                LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
                                if (startLatLng != null && endLatLng != null) {
                                    mMap.clear();
                                    boundsBuilder.include(startLatLng);
                                    boundsBuilder.include(endLatLng);
                                    mMap.addMarker(new MarkerOptions()
                                            .position(startLatLng)
                                            .title("Start Location"));

                                    mMap.addMarker(new MarkerOptions()
                                            .position(endLatLng)
                                            .title("Destination"));
                                String routesId = startHubId +":"+ endHubId;//say hid=Hun1:Hub2 or startHub:endHub
                                    /*The  getRoutesInfo request gets all the routes in the key startHub:EndHub and plot them as there can be multiple routes between two hubs,
                                     the user then clicks the Route (handleRouteSelection) they want to take, which then triggers the fetch driver inTransit of that route and further processes
                                */
                                Call<List<String>> call = apiService2.getRoutesInfo(routesId,city);

                                call.enqueue(new Callback<List<String>>() {
                                    @Override
                                    public void onResponse(Call<List<String>> call, Response<List<String>> response) {
                                        if (response.isSuccessful()) {
                                            routes = response.body();
                                            if (routes.isEmpty()) {
                                                Toast.makeText(MainActivity.this, "No routes found", Toast.LENGTH_SHORT).show();
                                                return;
                                            }
                                            if (routes != null) {
                                                int i=0;
                                                for (String route : routes) {
                                                    //check Distance latter for now just plot the polLines
                                                    List<LatLng> points = decodePoly(route);
                                                    for (LatLng point : points) {
                                                        boundsBuilder.include(point);
                                                    }
                                                    // Draw the route with a unique color
                                                    int colorIndex = i % routeColors.size();
                                                    int routeColor = ContextCompat.getColor(MainActivity.this, routeColors.get(colorIndex));

                                                    PolylineOptions polylineOptions = new PolylineOptions()
                                                            .addAll(points)
                                                            .width(10)
                                                            .color(routeColor)
                                                            .geodesic(true)
                                                            .clickable(true);

                                                    Polyline polyline = mMap.addPolyline(polylineOptions);
                                                    // Tag the polyline with its index for identification in the click handler
                                                    polyline.setTag(i);

                                                    // Add to our list of polylines
                                                    routePolylines.add(polyline);
                                                    // Optional: Add route number marker at the midpoint
                                                    if (points.size() > 0) {
                                                        int midPointIndex = points.size() / 2;
                                                        LatLng midPoint = points.get(midPointIndex);
                                                        double calculatedDis=calculateDistance(userLocation,midPoint);
                                                        if(calculatedDis>distance){
                                                            distance=calculatedDis;
                                                            polyline.setWidth(14);
                                                        }

                                                    }
                                                    i++;
                                                }
                                                // Set up the click listener for the polyLines
                                                mMap.setOnPolylineClickListener(MainActivity.this::handleRouteSelection);
                                                // Move camera to show all routes
                                                LatLngBounds bounds = boundsBuilder.build();
                                                int padding = 100; // Padding around routes in pixels
                                                CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, padding);
                                                mMap.animateCamera(cameraUpdate);
                                            }
                                        }
                                    }

                                    @Override
                                    public void onFailure(Call<List<String>> call, Throwable t) {

                                    }
                                });
                            }

                            }



                        }
                    });
        }
    }
    private void handleRouteSelection(Polyline polyline) {

        // Get the route index from the polyline tag// don't need these here
        Integer routeIndex = (Integer) polyline.getTag();
        selectedRoute=routes.get(routeIndex);
        if (routeIndex == null) return;
        if (startLatLng != null && endLatLng != null) {
            LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
            mMap.clear();
            boundsBuilder.include(startLatLng);
            boundsBuilder.include(endLatLng);
            mMap.addMarker(new MarkerOptions()
                    .position(startLatLng)
                    .title("Start Location"));

            mMap.addMarker(new MarkerOptions()
                    .position(endLatLng)
                    .title("Destination"));
            originalRoutePoints = decodePoly(selectedRoute);

            //also can be achived by List<LatLng> points=polyLine.getPonts();
            for (LatLng point : originalRoutePoints) {
                boundsBuilder.include(point);
                Log.d("RoutePoint", "Included point: " + point.latitude + ", " + point.longitude);
            }
            LatLngBounds bounds = boundsBuilder.build();
            // Draw the route with a unique color
            int selectedRouteColor = ContextCompat.getColor(this, R.color.selectedRouteColour);
            paddedBounds = getPaddedBounds(bounds, 0.0009); // padding of ~22m

            PolylineOptions polylineOptions = new PolylineOptions()
                    .addAll(originalRoutePoints)
                    .width(14)
                    .color(selectedRouteColor)
                    .geodesic(true);

            Polyline selectedPolyline = mMap.addPolyline(polylineOptions);
            passengerIsOnRoute=paddedBounds.contains(userLocation);
            if(!passengerIsOnRoute){
              runOnUiThread(()->{
                  Toast.makeText(this, "You are not on the route", Toast.LENGTH_SHORT).show();
              });
              //return;
            }
//            routeId=convertToSHA256(selectedRoute)+":Auto";
            routeId=convertToSHA256(selectedRoute);

/*
This Updates a potential passenger on the key->routeId:startHubId:passenger notifying driver first in Queue of its presence, This periodically sends its Activity to server, if it
becomes inactive it would be cleaned by the server.
 */
            // ScheduledExecutorService for periodic execution
            ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
            scheduler.scheduleWithFixedDelay(()->{
                CountMessage countMessage=new CountMessage(true,"passengerId1",routeId+":"+startHubId+":passenger");
                Call<CountMessage> call=apiService1.sendCount(countMessage);
                call.enqueue(new Callback<CountMessage>() {
                    @Override
                    public void onResponse(Call<CountMessage> call, Response<CountMessage> response) {
                        if (response.isSuccessful()) {
                            System.out.println("Count sent successfully: " + response.body());
                        } else {
                            System.err.println("Failed to send count: " + response.code());
                        }
                    }

                    @Override
                    public void onFailure(Call<CountMessage> call, Throwable t) {
                        t.printStackTrace();
                    }
                });
            },0,5, TimeUnit.SECONDS);
            Log.d("sha256val",routeId);//routeId:startHubId/:InTransit=[driverId1,DriverId2,...]
            fetchDriverIdsInTransit(routeId);// The inconsistency can happen in comparison, ie lets say the driver end of route select chooses a route, if You compare the sha256 then it can be a possibility that
            startPassengerExpiryCheck(routeId,startHubId);
            //the polyline might be different(generally google's are consistent) but can differ due to updates in the algorithm , so can't just compare sha256 first compare if true well, else check computationally, decode polyline get all poylines from hub1:hub2 and if 90 percent match then say route exists matvh on basis of distance array of points if sum of distances is less than a value
      //call fetchDRiverIdsIInTransit
        }

    }
    public void startPassengerExpiryCheck(String routeId,String startHubId){
        Runnable passengerExpiryCheck = new Runnable() {
            @Override
            public void run() {
                fetchPassengerIds(routeId,startHubId);
                handlerBackground.postDelayed(this, 10000*10);
            }
        };
        handlerBackground.post(passengerExpiryCheck);
    }
    public void fetchPassengerIds(String routeId,String startHubId){
        Call<List<PassengerIdsProcess>> call = apiService1.getPassengerIdsForExpiryCheck(routeId, startHubId);
        call.enqueue(new Callback<List<PassengerIdsProcess>>() {
            @Override
            public void onResponse(Call<List<PassengerIdsProcess>> call, Response<List<PassengerIdsProcess>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<PassengerIdsProcess> passengerIdsProcess = response.body();
                    List<String> expiredIds = new ArrayList<>();
                    long currentTime = System.currentTimeMillis();

                    // Iterate over the response data and check expiration
                    for (PassengerIdsProcess passenger : passengerIdsProcess) {
                        long expirationTime = passenger.getScore(); // score is the expiration time
                        if (expirationTime <= currentTime) {
                            expiredIds.add(passenger.passengerId); // Add to expired list if the score has passed
                        }
                        postDelExpiredCustomerIds(expiredIds,routeId,startHubId);
                    }

                    // Now you have the expired IDs in the expiredIds list
                    // You can further process or use this list, for example, display or send to the server
                    System.out.println("Expired IDs: " + expiredIds);
                } else {
                    // Handle error
                    System.out.println("Error fetching passenger IDs or empty response");
                }
                }


            @Override
            public void onFailure(Call<List<PassengerIdsProcess>> call, Throwable t) {
                Log.e(TAG, "Failed to fetch passenger ids", t);
                Toast.makeText(MainActivity.this, "Failed to get available drivers", Toast.LENGTH_SHORT).show();
            }
        });
    }
    /*
    A Socialist function that cleans up the expired customers Ids on the same route reducing the load on the server
     */
    public void postDelExpiredCustomerIds(List<String> expiredIds,String routeId, String startHubId){
        DriverIdsRequest request = new DriverIdsRequest(expiredIds);
        Call<Void> call=apiService1.postDelExpiredCustomerIds(routeId,startHubId,request);
        call.enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                Log.d("successExpired","successExpired");
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.d("failedExpired","FailedExpired");
            }
        });

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
 //First Fetch drivers in transit , if no drivers in transit or all drivers have passed , prompt a request Message.
    private void fetchDriverIdsInTransit(String routeId) {
        Call<List<DriverResponse>> call = apiService1.getDriverIdsInTransit(routeId, city, startHubId);
        call.enqueue(new Callback<List<DriverResponse>>() {
            @Override
            public void onResponse(Call<List<DriverResponse>> call, Response<List<DriverResponse>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<DriverResponse> driverResponses = response.body();
                    driverIdsInTransitList = new ArrayList<>();
                    for (DriverResponse dr : driverResponses) {
                        driverIdsInTransitList.add(dr.getDriverId());
                    }
                    if (!driverIdsInTransitList.isEmpty()) {
                        // Start with the first driver
                        i = 0;
                        driverId = driverIdsInTransitList.get(i);

                        // Start tracking this driver
                        startDriverTracking(driverId);
                    }
                    else{
                        Toast.makeText(MainActivity.this, "No drivers in Transit click Request", Toast.LENGTH_SHORT).show();
                    }
                }
            }

            @Override
            public void onFailure(Call<List<DriverResponse>> call, Throwable t) {
                Log.e(TAG, "Failed to fetch drivers in transit", t);
                Toast.makeText(MainActivity.this, "Failed to get available drivers", Toast.LENGTH_SHORT).show();
            }
        });
    }
    private void startDriverTracking(String driverId) {
//        this.driverId = driverId;
        if (updateLocationHandler != null && periodicRunnable != null) {
            updateLocationHandler.removeCallbacks(periodicRunnable);
            periodicRunnable = null;
        }
        // Create periodic tracking task
        periodicRunnable = new Runnable() {
            @Override
            public void run() {
                fetchRemoteLocation(driverId);
                updateLocationHandler.postDelayed(this, UPDATE_INTERVAL);
            }
        };

        // Start periodic tracking
        updateLocationHandler.post(periodicRunnable);
    }
    //when request is clicked
    private void fetchDriverId(String routeId) {
        if(driverIdsInTransitList!=null) {
            driverIdsInTransitList.clear();// clear the drivers in transit list as now the request has been clicked
        }
        // Store route ID for later use
        this.routeId = routeId;
        Log.d("reachedhere","reachedhere");
        // Reset driver tracking state
        prevRemoteLocation = null;
        currentRemoteLocation = null;

        // Cancel any existing location updates
        if (updateLocationHandler != null && periodicRunnable != null) {
            updateLocationHandler.removeCallbacks(periodicRunnable);
            periodicRunnable = null;
        }

        Call<DriverResponse> call = apiService1.getDriverId(routeId, city, startHubId);
        call.enqueue(new Callback<DriverResponse>() {
            @Override
            public void onResponse(Call<DriverResponse> call, Response<DriverResponse> response) {
                Log.d("driverId","driverId:");
                if (response.isSuccessful() && response.body() != null) {
                    driverId = response.body().getDriverId();
                    Log.d("driverId","driverId:");
                    if (driverId != null) {
                        currentRoutePoints = new ArrayList<>(originalRoutePoints);
                        Log.d("driverId",driverId);
                        // Start tracking this driver
                        startDriverTracking(driverId);
                    }
                }
            }

            @Override
            public void onFailure(Call<DriverResponse> call, Throwable t) {
                Log.e(TAG, "Failed to fetch driver", t);
                updateLocationHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, "No drivers available, trying again in 10 secs", Toast.LENGTH_SHORT).show();
                        fetchDriverId(routeId);
                    }
                }, 20000); // 10,000 milliseconds = 10 seconds
            }
        });
    }
    private void fetchRemoteLocation(String driverId) {
        Call<LocationMessage> call = apiService1.getRemoteLocation(driverId);
        call.enqueue(new Callback<LocationMessage>() {
            @Override
            public void onResponse(Call<LocationMessage> call, Response<LocationMessage> response) {
                if (response.isSuccessful() && response.body() != null) {
                    if (currentRemoteLocation != null) {
                        prevRemoteLocation = currentRemoteLocation;
                    }

                    LocationMessage location = response.body();
                    LatLng remoteLocation = new LatLng(location.latitude, location.longitude);
                    Log.d("remoteLocation", "Latitude: " + remoteLocation.latitude);
                    Log.d("remoteLocation", "Longitude: " + remoteLocation.longitude);
                    if (prevRemoteLocation == null) {
                        // First location update for this driver
                        double remoteDis = calculateDistance(startLatLng, remoteLocation) + 200.0;
                        double userDis = calculateDistance(userLocation, remoteLocation);

                        if (remoteDis > userDis) {
                            // Driver has passed the user or is too far away
                            moveToNextDriver();
                            return;
                        } else {
                            // Driver is suitable, store location
                            prevRemoteLocation = remoteLocation;
                            currentRemoteLocation = remoteLocation;
                        }
                    } else {
                        // Subsequent location update
                        currentRemoteLocation = remoteLocation;
                    }

                    // Process this location update
                    updateRouteCalculation(remoteLocation);
                }
                else if (response.code() == 404) {
                    // Handle 404 Not Found - Driver's location not available
                    Log.w(TAG, "Remote driver location not found (Status Code: 404) for driver ID: " + driverId);
                    Toast.makeText(MainActivity.this, "Driver went offline", Toast.LENGTH_SHORT).show();
                    fetchDriverIdsInTransit(routeId);

                } else {
                    // Handle other error codes (e.g., 500 Internal Server Error)
                    Log.e(TAG, "Failed to fetch remote location. Status Code: " + response.code());
                    Toast.makeText(MainActivity.this, "Failed to fetch remote location", Toast.LENGTH_SHORT).show();
                    // You might want to retry the request or inform the user.
                }
            }

            @Override
            public void onFailure(Call<LocationMessage> call, Throwable t) {
                Log.e(TAG, "Failed to fetch remote location", t);
            }
        });
    }
    private void moveToNextDriver() {
        // Stop current tracking
        if (updateLocationHandler != null && periodicRunnable != null) {
            updateLocationHandler.removeCallbacks(periodicRunnable);
            periodicRunnable = null;
        }

        // If we have a list of drivers in transit
        if (driverIdsInTransitList != null && !driverIdsInTransitList.isEmpty()) {
            i++;

            // Check if there are more drivers to try
            if (i < driverIdsInTransitList.size()) {
                // Try the next driver in the list
                final int nextIndex = i;

                // Start in a new call stack to avoid potential recursion issues
                updateLocationHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        prevRemoteLocation = null;
                        currentRemoteLocation = null;
                        startDriverTracking(driverIdsInTransitList.get(nextIndex));
                    }
                });
            } else {
                // No more drivers in our list, try to get a new driver
                updateLocationHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, "No more drivers in transit,  Click on request", Toast.LENGTH_SHORT).show();
                        //fetchDriverId(routeId);
                    }
                });
            }
        }
        else {
            //Request a new Driver
                runOnUiThread(() -> {Toast.makeText(MainActivity.this, "Request a new driver", Toast.LENGTH_SHORT).show();});

        }
    }

    private void updateRouteCalculation(LatLng remoteLocation) {
        if (originalRoutePoints.isEmpty() || userLocation == null) return;

        double prevDistance = calculateDistance(prevRemoteLocation, userLocation);
        double netDistance = calculateDistance(currentRemoteLocation, prevRemoteLocation);

        // Find closest points on the original route
        int closestToUser = findClosestPointIndex(userLocation, originalRoutePoints);
        int closestToRemote = findClosestPointIndex(remoteLocation, originalRoutePoints);

        if ((netDistance > prevDistance) || (closestToRemote > closestToUser)) {
            // Driver has passed the user or is moving away
            moveToNextDriver();
        }
        if(originalRoutePoints == null||closestToUser>=originalRoutePoints.size()||closestToRemote>=originalRoutePoints.size()){
            return;
        }

        // Update the map
        updateMapDisplay(remoteLocation,closestToRemote,closestToUser);
    }

    private void updateMapDisplay(LatLng remoteLocation,int closestToRemote,int closestToUser) {
        if (mMap == null) return;

        mMap.clear();
        if (routePolyline != null) {
            routePolyline.remove();
        }
        if (routePolyline2 != null) {
            routePolyline2.remove();
        }
//        currentRoutePoints.subList(0,closestToRemote+1).clear();
//        currentRoutePoints.addAll(0,new ArrayList<>(Arrays.asList(remoteLocation, originalRoutePoints.get(closestToRemote))));
//        currentRoutePoints.add(closestToUser,userLocation);
        // Add markers
        mMap.addMarker(new MarkerOptions().position(userLocation).title("Your Location"));
//            mMap.addMarker(new MarkerOptions().position(remoteLocation).title("Remote Device"));

        List<LatLng> bluePolylineRoute=new ArrayList<>(originalRoutePoints.subList(closestToRemote,originalRoutePoints.size()));//closest to remote , as start hub is the starting point so it will be first in the list
        if(bluePolylineRoute.size()>1) {


            PolylineOptions polylineOptionsblue = new PolylineOptions()
                    .addAll(bluePolylineRoute)
                    .color(0xFF0000FF)
                    .width(10);

            routePolyline = mMap.addPolyline(polylineOptionsblue);
        }
        if(calculateDistance(remoteLocation,originalRoutePoints.get(closestToRemote))<20)
        {
            mMap.addMarker(new MarkerOptions().position(originalRoutePoints.get(closestToRemote)).title("Remote Device"));


        }

        else{
            mMap.addMarker(new MarkerOptions().position(remoteLocation).title("Remote Device"));
            List<LatLng> greenPolylineRoute=new ArrayList<>(Arrays.asList(remoteLocation, originalRoutePoints.get(closestToRemote)));
            if(greenPolylineRoute.size()==2)
            {

                PolylineOptions polylineOptionsgreen = new PolylineOptions()
                        .addAll(greenPolylineRoute)
                        .color(0xFF00FF0)
                        .width(10);

                routePolyline2 = mMap.addPolyline(polylineOptionsgreen);
            }

        }

    }

    private int findClosestPointIndex(LatLng target, List<LatLng> points) {
        int closestIndex = 0;
        double minDistance = Double.MAX_VALUE;

        for (int i = 0; i < points.size(); i++) {
            double distance = calculateDistance(target, points.get(i));
            if (distance < minDistance) {
                minDistance = distance;
                closestIndex = i;
            }
        }

        return closestIndex;
    }

    private double calculateDistance(LatLng point1, LatLng point2) {
        float[] results = new float[1];
        Location.distanceBetween(
                point1.latitude, point1.longitude,
                point2.latitude, point2.longitude,
                results);
        return results[0];
    }

    // Original helper methods remain the same
    private String getDirectionsUrl(LatLng origin, LatLng dest, String apiKey) {
        String strOrigin = "origin=" + origin.latitude + "," + origin.longitude;
        String strDest = "destination=" + dest.latitude + "," + dest.longitude;
        String mode = "mode=driving";
        String parameters = strOrigin + "&" + strDest + "&" + mode + "&key=" + apiKey;
        return "https://maps.googleapis.com/maps/api/directions/json?" + parameters;
    }


    // Method to convert a string to SHA-256 hash
public static String convertToSHA256(String input) {
    try {
        // Get instance of SHA-256
        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        // Apply the hash function to the input, encoding as UTF-8
        byte[] hashBytes = digest.digest(input.getBytes("UTF-8"));

        // Convert byte array to hexadecimal string using Formatter
        Formatter formatter = new Formatter();
        for (byte b : hashBytes) {
            formatter.format("%02x", b);
        }
        String hexString = formatter.toString();
        formatter.close();

        // Truncate the hexadecimal string to 10 characters
        if (hexString.length() > 32) {
            return hexString.substring(0, 32);
        } else {
            return hexString; // Or handle this case as needed (e.g., return null, throw an exception)
        }


    } catch (NoSuchAlgorithmException e) {
        System.err.println("Error: Algorithm not found: " + e.getMessage());
        return null;
    } catch (UnsupportedEncodingException e) {
        System.err.println("Error: Unsupported encoding: " + e.getMessage());
        return null;
    }catch (Exception e) {
        System.err.println("An unexpected error occurred: " + e.getMessage());
        return null;
    }
}
    private String getApiKeyFromManifest(Context context) {
        try {
            ApplicationInfo ai = context.getPackageManager().getApplicationInfo(
                    context.getPackageName(), PackageManager.GET_META_DATA);
            Bundle bundle = ai.metaData;
            return bundle.getString("com.google.android.geo.API_KEY");
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "API Key not found in manifest", e);
            return null;
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
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            enableMyLocation();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_REQUEST_CODE);
        }
    }

    private void enableMyLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
            mMap.getUiSettings().setMyLocationButtonEnabled(true);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == LOCATION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableMyLocation();
            } else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (updateLocationHandler != null && periodicRunnable != null) {
            updateLocationHandler.removeCallbacks(periodicRunnable);
        }
        super.onDestroy();
    }


    public class geoSearch {
        @SerializedName("hubName:HubId")
        private String hubNameHubId;

        @SerializedName("distance")
        private double distance;

        public String getHubNameHubId() {
            return hubNameHubId;
        }

        public void setHubNameHubId(String hubNameHubId) {
            this.hubNameHubId = hubNameHubId;
        }

        public double getDistance() {
            return distance;
        }

        public void setDistance(double distance) {
            this.distance = distance;
        }

        // You might want to add a constructor and/or a toString() method for easier debugging.

        @Override
        public String toString() {
            return "geoSearch{" +
                    "hubNameHubId='" + hubNameHubId + '\'' +
                    ", distance=" + distance +
                    '}';
        }
    }

    public class HubInfoResponse {
        @SerializedName("name")
        private String name;

        @SerializedName("city")
        private String city;

        @SerializedName("LatLng")
        private String latLng;

        @SerializedName("id")
        private String id;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getCity() {
            return city;
        }

        public void setCity(String city) {
            this.city = city;
        }

        public String getLatLng() {
            return latLng;
        }

        public void setLatLng(String latLng) {
            this.latLng = latLng;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        @Override
        public String toString() {
            return "HubInfoResponse{" +
                    "name='" + name + '\'' +
                    ", city='" + city + '\'' +
                    ", latLng='" + latLng + '\'' +
                    ", id='" + id + '\'' +
                    '}';
        }
    }
    public class DriverResponse {
        @SerializedName("driverId")
        private String driverId;

        public String getDriverId() {
            return driverId;
        }

        public void setDriverId(String driverId) {
            this.driverId = driverId;
        }
    }
    public class PassengerIdsProcess {
        private String passengerId;
        private long score;

        // Getter and Setter
        public String getPassengerId() {
            return passengerId;
        }

        public void setPassengerId(String passengerId) {
            this.passengerId = passengerId;
        }

        public long getScore() {
            return score;
        }

        public void setScore(long score) {
            this.score = score;
        }
    }
    public class DriverIdsRequest {
        private List<String> driverIds;

        public DriverIdsRequest(List<String> driverIds) {
            this.driverIds = driverIds;
        }

        public List<String> getDriverIds() {
            return driverIds;
        }
    }



    }

