package com.example.transitSyncDriver;

import android.content.SharedPreferences;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
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

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

public class SelectRoute extends AppCompatActivity implements OnMapReadyCallback {
    private AppDatabase db;
    private List<LatLng> originalRoutePoints = new ArrayList<>();
    private AutoCompleteTextView startLocation;
    private AutoCompleteTextView endLocation;
    private GoogleMap mMap;
    private String routeId;
    private Handler updateLocationHandler;
    private Runnable autocompleteRunnable;
    private String startHubName="";
    private String endHubName="";
    private String startHubId="";
    private String endHubId="";
    private String driverId="driver001";
    private List<String > hubNames=new ArrayList<>();
    private List<String> hubIds=new ArrayList<>();
    private boolean isAutocompleteFromSelection=false;
    private String gateWayUrl="Your_api_gateway_url";
    private String city="Tezpur";
    private ApiService apiService;
    private LatLng startLatLng,endLatLng;
    private List<Integer> routeColors;
    private List<String> routes=new ArrayList<>();
    private String selectedRoute;
    private List<Polyline> routePolylines = new ArrayList<>();
    private double  distance=0.0;
    private TextView selectRouteButton;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.select_route_activity);

        setupRetrofit();
        setupRouteColors();
        updateLocationHandler=new Handler(Looper.getMainLooper());
        startLocation=findViewById(R.id.start_location);
        endLocation=findViewById(R.id.end_location);
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        db = AppDatabase.getDatabase(getApplicationContext());
        setupAutocomplete(startLocation, true);
        setupAutocomplete(endLocation, false);
        selectRouteButton=findViewById(R.id.chooseRoute);
        selectRouteButton.setOnClickListener(v->{
            if(routeId==null){
                Toast.makeText(this, "Route not Selected", Toast.LENGTH_SHORT).show();
                return;
            }
            registerRoute();
        });


    }
    private static class InsertUserTask extends AsyncTask<RoutesValue, Void, Long> {
        private RoutesValueDao routesValueDao;

        InsertUserTask(RoutesValueDao routesValueDao) {
            this.routesValueDao = routesValueDao;
        }

        @Override
        protected Long doInBackground(RoutesValue... routesValues) {
            try {
                return routesValueDao.insertRoutesValue(routesValues[0]);

            } catch (Exception e) {
//                throw new RuntimeException(e);
                Log.e("InsertUserTask", "Error inserting RoutesValue: " + e.getMessage());
                return -1L;
            }
        }
    }
    private void registerRoute(){
      Call<RouteResponse> call=apiService.getRouteValues(routeId);
      call.enqueue(new Callback<RouteResponse>() {
          @Override
          public void onResponse(Call<RouteResponse> call, Response<RouteResponse> response) {
              if(response.isSuccessful()&&response.body()!=null){
                  RouteResponse data=response.body();

                  saveRoute(data);
              }

          }

          @Override
          public void onFailure(Call<RouteResponse> call, Throwable t) {
            runOnUiThread(()->{
                Toast.makeText(SelectRoute.this, "Something went wrong with getting selected Route ", Toast.LENGTH_SHORT).show();
            });
          }
      });
    }
    private void saveRoute(RouteResponse data){
        postData(data.polyline, data.cost, "transitDriver2@gmail.com", driverId,"V0030",
                data.startHubId,data.endHubId,data.startHubName, data.endHubName, data.startHubLatLng,data.endHubLatLng,data.startCity,data.endCity,data.distanceBtwHub);
    }
    public void postData(String routePolyline, String price,String email,String driverId,String vehicalNO,String startHubId,String endHubId,
                         String startHubName,String endHubName,String startHubLatLng,String endHubLatLng,String startCity,String endCity,String distanceBtwHub) {


        driverRoutePostClass request = new driverRoutePostClass(routePolyline, price,email,driverId,vehicalNO,startHubId,endHubId,startHubName,endHubName,startHubLatLng,endHubLatLng,startCity,endCity,distanceBtwHub);

        Call<ResponseBody> call = apiService.postData(request); // Use ResponseBody
        //move this inside onResponse
        new InsertUserTask(db.routesValueDao()){
            @Override
            protected void onPostExecute(Long success) {
                super.onPostExecute(success);
                // Call finish() here, now that you have access to the activity context.
                if(success>0){
                    Log.d("InsertionSucc","successfully isnerted");
                    //   RouteSelect.this.finish(); // Use RouteSelect.this to refer to the activity.
                }

            }
        }.execute(new RoutesValue(routeId, price,startHubName+":"+endHubName,startHubId+":"+endHubId,startHubLatLng,endHubLatLng,false,startHubId,endHubId,distanceBtwHub,routePolyline));

        call.enqueue(new Callback<ResponseBody>() { // Use ResponseBody
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {

                if (response.isSuccessful()) {
                    Log.d("Retrofit", "POST request successful! Status code: " + response.code());
                  //  storeSelectedRoute(routePolyline);
                    Toast.makeText(SelectRoute.this, "The Route has been updated Successfully", Toast.LENGTH_SHORT).show();
                    //finish();
//                    new InsertUserTask(db.routesValueDao()){
//                        @Override
//                        protected void onPostExecute(Long success) {
//                            super.onPostExecute(success);
//                            // Call finish() here, now that you have access to the activity context.
//                            if(success>0){
//                                Log.d("InsertionSucc","successfully isnerted");
//                                //   RouteSelect.this.finish(); // Use RouteSelect.this to refer to the activity.
//                            }
//
//                        }
//                    }.execute(new RoutesValue(routeId, price,startHubName+":"+endHubName,startHubId+":"+endHubId,startHubLatLng,endHubLatLng,false));

                    // 200 status received, no need to parse response body
                } else {
                    Log.e("Retrofit", "POST request failed. Status code: " + response.code());
                    Log.e("Retrofit", "Error body: " + response.errorBody());
                    Toast.makeText(SelectRoute.this, "Something Went Wrong Try again Later", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.e("Retrofit", "POST request failure: " + t.getMessage());
                Toast.makeText(SelectRoute.this, "Something Went Wrong Try again Later", Toast.LENGTH_SHORT).show();

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
            ArrayAdapter<String> emptyAdapter = new ArrayAdapter<>(SelectRoute.this,
                    android.R.layout.simple_dropdown_item_1line, new ArrayList<>());
            textView.setAdapter(emptyAdapter);
            textView.dismissDropDown();
            isAutocompleteFromSelection = false;
            Call<HubInfoResponse> call = apiService.getHubInfo(hubId,city);
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
                                            fetchRoutes();
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
    private void fetchHubPredictions(String query, AutoCompleteTextView textView, boolean isStartLocation) {

        Call<List<String>> call=apiService.getHubAutoComplete(query,city);
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
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(SelectRoute.this,
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
    private void fetchRoutes(){
        if(startHubId==null || endHubId==null)
        {
            runOnUiThread(()->{
                Toast.makeText(this, "StartHubId or endHubId missing", Toast.LENGTH_SHORT).show();
            });
            return;
        }
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
                String routesId = startHubId +":"+ endHubId;//say hid=Hun1:Hub2 or startHub:endHub(automatically gets decoded by ApiGateway)
                Call<List<String>> call = apiService.getRoutesInfo(routesId,city);
                call.enqueue(new Callback<List<String>>() {
                    @Override
                    public void onResponse(Call<List<String>> call, Response<List<String>> response) {
                        if (response.isSuccessful()) {
                            routes = response.body();
                            if (routes.isEmpty()) {
                                Toast.makeText(SelectRoute.this, "No routes found", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            if (routes != null) {
                                int i=0;
                                for (String route : routes) {
                                    //check Distance later for now just plot the polLines
                                    List<LatLng> points = decodePoly(route);
                                    for (LatLng point : points) {
                                        boundsBuilder.include(point);
                                    }
                                    // Draw the route with a unique color
                                    int colorIndex = i % routeColors.size();
                                    int routeColor = ContextCompat.getColor(SelectRoute.this, routeColors.get(colorIndex));

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
                                    i++;
                                }
                                // Set up the click listener for the polyLines
                                mMap.setOnPolylineClickListener(SelectRoute.this::handleRouteSelection);
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


            PolylineOptions polylineOptions = new PolylineOptions()
                    .addAll(originalRoutePoints)
                    .width(14)
                    .color(selectedRouteColor)
                    .geodesic(true);

            Polyline selectedPolyline = mMap.addPolyline(polylineOptions);

//            routeId=convertToSHA256(selectedRoute)+":Auto";
            routeId=convertToSHA256(selectedRoute);
            Log.d("sha256val",routeId);//routeId:startHubId/:InTransit=[driverId1,DriverId2,...]

        }

    }
    private void storeSelectedRoute(String encodedPath) {
        SharedPreferences prefs = getSharedPreferences("RoutePreferences", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("selected_route", encodedPath);
        editor.apply();

        Log.d("RouteStorage", "Route stored in SharedPreferences, length: " + encodedPath.length());
    }
    private void setupRetrofit(){
//        Retrofit retrofit = new Retrofit.Builder()
//                .baseUrl("http://13.232.192.22:8080/")
//                .addConverterFactory(GsonConverterFactory.create())
//                .build();
//
//        apiService1 = retrofit.create(ApiService1.class);
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(gateWayUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        apiService = retrofit.create(ApiService.class);
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
    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {

        mMap = googleMap;
        mMap.setOnMarkerDragListener(new GoogleMap.OnMarkerDragListener() {
            @Override
            public void onMarkerDragStart(Marker marker) {
                // Optional
            }

            @Override
            public void onMarkerDrag(Marker marker) {
                // Optional
            }

            @Override
            public void onMarkerDragEnd(Marker marker) {
                LatLng newPosition = marker.getPosition();
                if ("Start Location".equals(marker.getTitle())) {
                    if(calculateDistance(newPosition,startLatLng)<300.0) {
                        startLatLng = newPosition;
                    }
                    else {
                        runOnUiThread(()->{
                            Toast.makeText(SelectRoute.this, "The Marker Went Too far away from Intended Location", Toast.LENGTH_SHORT).show();});
                    }
                } else if ("End Location".equals(marker.getTitle())) {
                    if(calculateDistance(newPosition,endLatLng)<300.0){
                        endLatLng = newPosition;
                    }
                    else {
                        runOnUiThread(()->{Toast.makeText(SelectRoute.this, "The Marker Went Too far away from Intended Location", Toast.LENGTH_SHORT).show();});
                    }
                }
            }
        });
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

    private double calculateDistance(LatLng point1, LatLng point2) {
        float[] results = new float[1];
        Location.distanceBetween(
                point1.latitude, point1.longitude,
                point2.latitude, point2.longitude,
                results);
        return results[0];
    }
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
    private interface ApiService{
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
        @GET("RouteInfoFetch")
        Call<RouteResponse> getRouteValues(@Query("routeId") String routeId);
        @POST("DriverRouteSelect")
        Call<ResponseBody> postData(@Body driverRoutePostClass request);

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
    public class RouteResponse {
        private String polyline;
        private String cost;
        private String startHubId;
        private String endHubId;
        private String startHubName;
        private String endHubName;
        private String startHubLatLng;
        private String endHubLatLng;
        private String startCity;
        private String endCity;
        private String distanceBtwHub;

        // Getters
        public String getPolyline() {
            return polyline;
        }

        public String getCost() {
            return cost;
        }

        public String getStartHubId() {
            return startHubId;
        }

        public String getEndHubId() {
            return endHubId;
        }

        public String getStartHubName() {
            return startHubName;
        }

        public String getEndHubName() {
            return endHubName;
        }

        public String getStartHubLatLng() {
            return startHubLatLng;
        }

        public String getEndHubLatLng() {
            return endHubLatLng;
        }

        public String getStartCity() {
            return startCity;
        }

        public String getEndCity() {
            return endCity;
        }

        public String getDistanceBtwHub() {
            return distanceBtwHub;
        }

        // Optional: toString() for easy logging
        @Override
        public String toString() {
            return "RouteMappingResponse{" +
                    "polyline='" + polyline + '\'' +
                    ", cost='" + cost + '\'' +
                    ", startHubId='" + startHubId + '\'' +
                    ", endHubId='" + endHubId + '\'' +
                    ", startHubName='" + startHubName + '\'' +
                    ", endHubName='" + endHubName + '\'' +
                    ", startHubLatLng='" + startHubLatLng + '\'' +
                    ", endHubLatLng='" + endHubLatLng + '\'' +
                    ", startCity='" + startCity + '\'' +
                    ", endCity='" + endCity + '\'' +
                    ", distanceBtwHub='" + distanceBtwHub + '\'' +
                    '}';
        }
    }
    public class driverRoutePostClass {
        private String routePolyline;
        private String email;
        private String price;
        private String driverId;
        private String vehicleNO;
        private String startHubId;
        private String endHubId;
        private String startHubName;
        private String endHubName;
        private String startHubLatLng;
        private String endHubLatLng;
        private String startCity;
        private String endCity;
        private String distanceBtwHub;


        public driverRoutePostClass(String routePolyline, String price, String email,String driverId,String vehicleNo,String startHubId,String endHubId,String startHubName,
                                    String endHubName,String startHubLatLng,String endHubLatLng,String startCity,String endCity,String distanceBtwHub) {
            this.routePolyline = routePolyline;
            this.price = price;
            this.email = email;
            this.driverId=driverId;
            this.vehicleNO=vehicleNo;
            this.startHubId=startHubId;
            this.endHubId=endHubId;
            this.startHubName=startHubName;
            this.endHubName=endHubName;
            this.startHubLatLng=startHubLatLng;
            this.endHubLatLng=endHubLatLng;
            this.startCity=startCity;
            this.endCity=endCity;
            this.distanceBtwHub=distanceBtwHub;

        }

//        // Getters and setters (or use Lombok @Data)
//        public String getRoutePolyline() {
//            return routePolyline;
//        }
//
//        public void setRoutePolyline(String field1) {
//            this.routePolyline = field1;
//        }
//
//        public int getField2() {
//            return field2;
//        }
//
//        public void setField2(int field2) {
//            this.field2 = field2;
//        }
    }
}
