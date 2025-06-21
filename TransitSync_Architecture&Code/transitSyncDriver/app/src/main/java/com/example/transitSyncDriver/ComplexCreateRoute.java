package com.example.transitSyncDriver;
import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.location.Location;
import android.media.Image;
import com.google.maps.android.PolyUtil;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;

import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.AddressComponent;
import com.google.android.libraries.places.api.model.AutocompletePrediction;
import com.google.android.libraries.places.api.model.AutocompleteSessionToken;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.net.FetchPlaceRequest;
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Formatter;
import java.util.List;

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

public class ComplexCreateRoute extends AppCompatActivity implements OnMapReadyCallback {
    private com.example.transitSyncDriver.AppDatabase db;
    private GoogleMap mMap;
    private AutoCompleteTextView startLocation, endLocation;
    private TextView showRouteButton;
    private PlacesClient placesClient;
    private LatLng startLatLng, endLatLng;
    private LatLng startLatLngInitial,endLatLngInitial;
    private AutocompleteSessionToken sessionToken;
    private Handler handler = new Handler();
    private Runnable autocompleteRunnable;
    List<String> placeNames=new ArrayList<>();
    List<AutocompletePrediction> predictions=new ArrayList<>();
    private boolean isAutocompleteFromSelection = false;
    private String gateWayUrl="Your_api gateway_url";
    private String startHubId="";
    private String endHubId="";
    private String startHubName = "";
    private String endHubName = "";
    private String driverId="driver001";
    private String distanceBtwHub;
    private EditText routePrice;
    private String origin;
    private String destination;
    private String startCity="";
    private String routeId="";
    private String endCity="";
    private List<LatLng> combinedPoints=new ArrayList<>();
    private List<String> complexPolyLines=new ArrayList<>();
    private DirectionsResponse initialdirectionsResponse;
    private boolean flagInitial=true;
    private String transitionHubName;
    private String transitionHubId;
    private LatLng transitionLatLng;
    private String initialEndHubName;
    private String initialEndHubId;
    private int count=0;
    private String apiKey;
    private Marker startMarker;
    private Marker endMarker;
    private String durationBtwHub="";
    private DirectionsApiService directionsApiService;
    private List<Integer> routeColors;
    private List<Route> routes=new ArrayList<>();
    private List<Polyline> routePolylines = new ArrayList<>();
    private List<List<LatLng>> routePoints = new ArrayList<>();
    private int selectedRouteIndex = -1;
    private static final float SELECTED_ROUTE_WIDTH = 16f;
    private static final float UNSELECTED_ROUTE_WIDTH = 8f;
    private String encodedSelectedPath;
    private List<String> polylineoverViews=new ArrayList<>();
    private MyApi api;
    private boolean flag=false;
    private ImageView addButton;
    private ImageView undoButton;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.create_route_activity);

        // Initialize Places API
        if (!Places.isInitialized()) {
            apiKey = getApiKeyFromManifest();
            if (apiKey != null && !apiKey.isEmpty()) {
                Places.initialize(getApplicationContext(), apiKey);
            } else {
                System.err.println("Google Maps API key not found in manifest.");
            }
        }
        placesClient = Places.createClient(this);
        sessionToken = AutocompleteSessionToken.newInstance();
        // Initialize Retrofit for Directions API
        setupRetrofit();

        // Initialize route colors
        setupRouteColors();
        // Get references to UI elements
        startLocation = findViewById(R.id.start_location);
        endLocation = findViewById(R.id.end_location);
        showRouteButton = findViewById(R.id.show_route);
        routePrice=findViewById(R.id.route_Price);
        addButton=findViewById(R.id.addButton);
        undoButton=findViewById(R.id.undoButton);
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        db = AppDatabase.getDatabase(getApplicationContext());
        //new UpdateIsSelectedTask(db.routesValueDao(), "routeId", true).execute();
        // Set up autocomplete
        setupAutocomplete(startLocation, true);
        setupAutocomplete(endLocation, false);

        // Show route when button is clicked
        showRouteButton.setOnClickListener(v -> {
            String price=routePrice.getText().toString();
            if(count>0&&count<5&&transitionLatLng!=null&&endLatLng!=null&&showRouteButton.getText().toString().equals("Show Route")){
                fetchDirectionsAndDrawRoutes(transitionLatLng,endLatLng);
            }
            if(showRouteButton.getText().toString().equals("Choose Route")&&flag){
               combinedPoints.clear();
                if(count>0&&count<5){
                    for(String polyLine:complexPolyLines){
//                        combinedPoints.addAll(decodePolyline(polyLine));
                        Log.d("testVAls","PolyLines:"+polyLine);
                        List<LatLng> points=PolyUtil.decode(polyLine);
                        combinedPoints.addAll(points);
                    }
                    encodedSelectedPath=PolyUtil.encode(combinedPoints);
                    Log.d("EncodedFinalVal","val:"+encodedSelectedPath);
                    if(isValidPrice(price)&&startHubId!=null&&endHubId!=null&&startHubName!=null&&endHubName!=null&&origin!=null&&destination!=null&&startCity!=null&&endCity!=null) {

                        // Store the route path in shared preferences (as an example)
                        origin = startLatLng.latitude + "," + startLatLng.longitude;
                        destination = endLatLng.latitude + "," + endLatLng.longitude;
                        postData(encodedSelectedPath, price, "transitDriver1@gmail.com", driverId,"V0020",
                                startHubId,endHubId,startHubName,endHubName,origin,destination,startCity,endCity,distanceBtwHub);
                    }
                }



                if(count==0&&isValidPrice(price)&&startHubId!=null&&endHubId!=null&&startHubName!=null&&endHubName!=null&&origin!=null&&destination!=null&&startCity!=null&&endCity!=null) {

                    // Store the route path in shared preferences (as an example)
                    postData(encodedSelectedPath, price, "transitDriver1@gmail.com", driverId,"V0020",
                            startHubId,endHubId,startHubName,endHubName,origin,destination,startCity,endCity,distanceBtwHub);
                }
            }

            else if (startLatLng != null && endLatLng != null &&flag&&count==0) {
//                drawRoute();
                fetchDirectionsAndDrawRoutes(startLatLng,endLatLng);

            } else {
                Toast.makeText(this, "Please enter valid locations", Toast.LENGTH_SHORT).show();
            }
        });
        addButton.setOnClickListener(v->{
            if(encodedSelectedPath==null||startHubId==null||endHubId==null){
                Toast.makeText(this, "Select a route", Toast.LENGTH_SHORT).show();
                return;
            }
            if(count==0&&endHubId!=null&&endHubName!=null){
                initialEndHubName=endHubName;
                initialEndHubId=endHubId;
            }
            createComplexRoute();
        });
        undoButton.setOnClickListener(v->{
            mMap.clear();
            complexPolyLines.clear();
            if(initialEndHubId!=null&&initialEndHubName!=null) {
                endHubName = initialEndHubName;
                endHubId=initialEndHubId;
                count=0;
                endLocation.setText(endHubName);
                startLocation.setText(startHubName);
            }
            if(initialdirectionsResponse!=null){
                drawMultipleRoutesOnMap(initialdirectionsResponse,true);
            }
            else{
                Toast.makeText(this, "Something went Wrong", Toast.LENGTH_SHORT).show();
            }
        });
    }
    private void createComplexRoute(){
//        if(encodedSelectedPath!=complexPolyLines.get(complexPolyLines.size()-1));
        polylineoverViews.clear();
        routePolylines.clear();
        showRouteButton.setText("Show Route");

        mMap.clear();
        for(String polyLine:complexPolyLines){
            List<LatLng> points = decodePolyline(polyLine);


            int routeColor = ContextCompat.getColor(this, R.color.selectedRouteColour );

            PolylineOptions polylineOptions = new PolylineOptions()
                    .addAll(points)
                    .width(10)
                    .color(routeColor)
                    .geodesic(true);

            Polyline polyline = mMap.addPolyline(polylineOptions);

        }
        count++;
        if(count>4) {
            Toast.makeText(this, "Made more than 5 modifications try again", Toast.LENGTH_SHORT).show();
            return;
        }
        startLocation.setText(endHubName);
        endLocation.setText("");
        transitionLatLng=endLatLng;
        transitionHubName=endHubName;
        setupAutocomplete(endLocation,false);

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

    private void setupRetrofit() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://maps.googleapis.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        directionsApiService = retrofit.create(DirectionsApiService.class);
        Retrofit retrofit2 = new Retrofit.Builder()
                .baseUrl(gateWayUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        api = retrofit2.create(MyApi.class);
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
    private void setupAutocomplete(AutoCompleteTextView textView, boolean isStartLocation) {
        textView.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

            @Override
            public void onTextChanged(CharSequence query, int i, int i1, int i2) {
                if (autocompleteRunnable != null) {
                    handler.removeCallbacks(autocompleteRunnable);
                }
                String queryStr = query.toString();
                //The relevantSelection is used in if statement because on clicking the dropDown The SETTEXT makes the onChangeListener trigger again and hence again making an api call
                String relevantSelection = isStartLocation ? startHubName : endHubName;
                autocompleteRunnable = () -> {
                    if (queryStr.length() > 4 && !queryStr.equals(relevantSelection)) {
                        fetchPlacePredictions(queryStr, textView, isStartLocation);
                    }
                };
                handler.postDelayed(autocompleteRunnable, 1500); // 1.5 seconds delay
            }

            @Override
            public void afterTextChanged(android.text.Editable editable) {}
        });

        textView.setOnItemClickListener((parent, view, position, id) -> {
            // AutocompletePrediction item = (AutocompletePrediction) parent.getItemAtPosition(position);
            // String placeId = item.getPlaceId();
            String placeId=predictions.get(position).getPlaceId();
            if (isStartLocation) {
                startHubName = (String) parent.getItemAtPosition(position);
                textView.setText(startHubName);
                startHubId=placeId;
            } else {
                endHubName = (String) parent.getItemAtPosition(position);
                textView.setText(endHubName);
                endHubId=placeId;
            }

            // Immediately clear focus to dismiss the keyboard
            textView.clearFocus();
            placeNames.clear();
            predictions.clear();

            // Clear the adapter data and dismiss the dropdown

            textView.clearListSelection();
            ArrayAdapter<String> emptyAdapter = new ArrayAdapter<>(ComplexCreateRoute.this,
                    android.R.layout.simple_dropdown_item_1line, new ArrayList<>());
            textView.setAdapter(emptyAdapter);
            textView.dismissDropDown();
            isAutocompleteFromSelection = false;

            FetchPlaceRequest request = FetchPlaceRequest.newInstance(placeId, Arrays.asList(Place.Field.ADDRESS_COMPONENTS,
                    Place.Field.LAT_LNG));
            placesClient.fetchPlace(request).addOnSuccessListener(response -> {

                Place place = response.getPlace();
                if (isStartLocation) {
                    startLatLng = place.getLatLng();
                    startLatLngInitial=startLatLng;
//                    if(setInitial){
//
//                    }


                    if (startLatLng != null) {
                        MarkerOptions startMarkerOptions = new MarkerOptions()
                                .position(startLatLng)
                                .title("Start Location")
                                .draggable(true)
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN));
                        startMarker=mMap.addMarker(startMarkerOptions);
                        // Add the marker to the map here if needed
                    } else {
                        Log.e("PlaceError", "startLatLng is null for place: " + place.getName());
                        Toast.makeText(this, "Could not get location from selected place", Toast.LENGTH_SHORT).show();
                    }

                    checkForExistingHub(startLatLng,true);//No two hubs can be with a radius of 200 meters force the driver to a Already existing hub(stops clustering of Hub)
                    // Optionally, you might want to move the camera to the start location
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(startLatLng, 15));
                    for (AddressComponent component : place.getAddressComponents().asList()) {
                        if (component.getTypes().contains("locality")) {
                            startCity = component.getName();
                            break;
                        }
                    }
                } else {
                    endLatLng = place.getLatLng();
//                    if(setInitial) {
//
//                    }
                    endLatLngInitial = endLatLng;
//                    transitionLatLng=endLatLng;
                    //check if there is a hub in a 300 meter radius move the marker to it and fix it
                    MarkerOptions startMarkerOptions = new MarkerOptions()
                            .position(endLatLng)
                            .title("End Location")
                            .draggable(true)
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)); // Customize the color
                    endMarker=mMap.addMarker(startMarkerOptions);
                    checkForExistingHub(endLatLng,false);//No two hubs can be with a radius of 200 meters force the driver to a Already existing hub(stops clustering of Hub)
                    // Optionally, you might want to move the camera to the start location
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(endLatLng, 15));
                    for (AddressComponent component : place.getAddressComponents().asList()) {
                        if (component.getTypes().contains("locality")) {
                            endCity = component.getName();
                            break;
                        }
                    }

                }

            }).addOnFailureListener(exception -> {
                Toast.makeText(ComplexCreateRoute.this, "Error fetching location", Toast.LENGTH_SHORT).show();
            });
        });
    }

    public void checkForExistingHub(LatLng hubLatLng,Boolean isStartLocation){
        String geoSearch=hubLatLng.latitude+","+hubLatLng.longitude;
        if(isStartLocation) {
            Log.d("ReachedPoint1","Reached Inside isStartLocation");
            Call<List<HubResponse>> call = api.getNearestHub(startCity,geoSearch,"400" );//200 meters
            call.enqueue(new Callback<List<HubResponse>>() {
                @Override
                public void onResponse(Call<List<HubResponse>> call, Response<List<HubResponse>> response) {
                    if(response.isSuccessful()&&response.body()!=null){
                        List<HubResponse> data=response.body();
                        Log.d("ReachedPoint1","SuccessFull response");
                        Log.d("datasizeee","dataSize:"+data.size());
                        if(data.size()==1) {
                            HubResponse hubResponse=data.get(0);
                            LatLng latLng = new LatLng(hubResponse.latLng.lat, hubResponse.latLng.lng);
                            //shortest distance hub find
                            startLatLng = latLng;
                            String hubName_Id = hubResponse.hubNameHubId;
                            startHubName = hubName_Id.split(":")[0];
                            startLocation.setText(startHubName);
                            startHubId = hubName_Id.split(":")[1];
                            if(startMarker!=null) {
                                startMarker.remove();
                                startMarker = null;
                            }
                            MarkerOptions startMarkerOptions = new MarkerOptions()
                                    .position(startLatLng)
                                    .title("Start Location")
                                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)); // Customize the color
                            startMarker=mMap.addMarker(startMarkerOptions);
                        }
                        else{
                            runOnUiThread(()->{
                                Toast.makeText(ComplexCreateRoute.this, "Found Multiple hubs near, Select specific hub", Toast.LENGTH_SHORT).show();
                            });
                        }

                    }

                }

                @Override
                public void onFailure(Call<List<HubResponse>> call, Throwable t) {
                    Toast.makeText(ComplexCreateRoute.this, "Failed To check hub", Toast.LENGTH_SHORT).show();
                    Log.d("FailedHere","Failed To check hub");
                }
            });

        }
        else {
            Call<List<HubResponse>> call = api.getNearestHub(endCity, geoSearch, "400");//200 meters
            call.enqueue(new Callback<List<HubResponse>>() {
                @Override
                public void onResponse(Call<List<HubResponse>> call, Response<List<HubResponse>> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        List<HubResponse> data = response.body();
                        if (data.size() == 1) {
                            HubResponse hubResponse = data.get(0);
                            LatLng latLng = new LatLng(hubResponse.latLng.lat, hubResponse.latLng.lng);
                            endLatLng = latLng;
                            String hubName_Id = hubResponse.hubNameHubId;
                            endHubName = hubName_Id.split(":")[0];
                            endLocation.setText(endHubName);
                            endHubId = hubName_Id.split(":")[1];
                            if(endMarker!=null){
                                endMarker.remove();;
                                endMarker = null;
                            }
                            MarkerOptions startMarkerOptions = new MarkerOptions()
                                    .position(endLatLng)
                                    .title("End Location")
                                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)); // Customize the color
                            endMarker=mMap.addMarker(startMarkerOptions);
                        }
                        else {
                            runOnUiThread(() -> {
                                Toast.makeText(ComplexCreateRoute.this, "Found Multiple hubs near, Select specific hub", Toast.LENGTH_SHORT).show();
                            });
                        }

                    }
                    flag=true;// allow show route button to function
                }

                @Override
                public void onFailure(Call<List<HubResponse>> call, Throwable t) {

                }
            });
        }
    }
    private void fetchPlacePredictions(String query, AutoCompleteTextView textView, boolean isStartLocation) {
        FindAutocompletePredictionsRequest request = FindAutocompletePredictionsRequest.builder()
                .setSessionToken(sessionToken)
                .setQuery(query)
                .setCountries("IN")
                .build();

        placesClient.findAutocompletePredictions(request).addOnSuccessListener(response -> {
            placeNames.clear();
            predictions.clear();
            Gson gson = new Gson();
            String json = gson.toJson(response);
            Log.d("AutoCompleteResponse", "JSON Response: " + json);
            for (AutocompletePrediction prediction : response.getAutocompletePredictions()) {
                placeNames.add(prediction.getFullText(null).toString());
                predictions.add(prediction);
            }

            // Attach adapter to AutoCompleteTextView
            ArrayAdapter<String> adapter = new ArrayAdapter<>(ComplexCreateRoute.this,
                    android.R.layout.simple_dropdown_item_1line, placeNames);
            textView.setAdapter(adapter);
            if (textView != null && textView.isAttachedToWindow()) {
                textView.showDropDown();
            }
        }).addOnFailureListener(e -> {
            Toast.makeText(ComplexCreateRoute.this, "Failed to fetch places", Toast.LENGTH_SHORT).show();
        });
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
                Log.d("startALtLngInitital","startLAt:"+startLatLngInitial.toString());
                double distance;
                LatLng newPosition = marker.getPosition();
                if ("Start Location".equals(marker.getTitle())&&startLatLngInitial!=null&newPosition!=null) {
                    distance=calculateDistance(newPosition,startLatLngInitial);
                    if(distance<300.0&&distance>0) {
                        Log.d("ReachedPoint1","reachedPoint1");
                        startLatLng = newPosition;
                        checkForExistingHub(startLatLng,true);
                    }
                    else {
                        runOnUiThread(()->{Toast.makeText(ComplexCreateRoute.this, "The Marker Went Too far away from Intended Location", Toast.LENGTH_SHORT).show();});
                    }
                } else if ("End Location".equals(marker.getTitle())&&endLatLngInitial!=null&newPosition!=null) {
                    distance=calculateDistance(newPosition,endLatLngInitial);
                    if(distance<300.0&&distance>0){
                        endLatLng = newPosition;
                        checkForExistingHub(endLatLng,false);
                    }
                    else {
                        runOnUiThread(()->{Toast.makeText(ComplexCreateRoute.this, "The Marker Went Too far away from Intended Location", Toast.LENGTH_SHORT).show();});
                    }
                }
            }
        });
    }

    private void fetchDirectionsAndDrawRoutes(LatLng startLatLng,LatLng endLatLng ) {
        if (startLatLng == null || endLatLng == null) return;

        // Show loading indicator
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Fetching routes...");
        progressDialog.show();

        // Create the request parameters
        origin = startLatLng.latitude + "," + startLatLng.longitude;
        destination = endLatLng.latitude + "," + endLatLng.longitude;
        String alternatives = "true"; // Request alternative routes

        // Make the API call using Retrofit
        Call<DirectionsResponse> call = directionsApiService.getDirections(
                origin,
                destination,
                "driving",
                alternatives,
                apiKey
        );

        call.enqueue(new Callback<DirectionsResponse>() {
            @Override
            public void onResponse(Call<DirectionsResponse> call, Response<DirectionsResponse> response) {
                progressDialog.dismiss();

                if (response.isSuccessful() && response.body() != null) {

                    DirectionsResponse directionsResponse = response.body();
                    if(flagInitial){
                        initialdirectionsResponse=directionsResponse;
//                        flagInitial=false;
                    }
                    Gson gson = new Gson();
                    String json = gson.toJson(directionsResponse);
                    Log.d("DirectionsResponse", "JSON Response: " + json);

                    drawMultipleRoutesOnMap(directionsResponse,false);
                } else {
                    Toast.makeText(ComplexCreateRoute.this,
                            "Error: " + response.code() + " " + response.message(),
                            Toast.LENGTH_SHORT).show();
                    Log.e("Directions API", "Error: " + response.errorBody());
                }
            }

            @Override
            public void onFailure(Call<DirectionsResponse> call, Throwable t) {
                progressDialog.dismiss();
                Toast.makeText(ComplexCreateRoute.this,
                        "Network error: " + t.getMessage(),
                        Toast.LENGTH_SHORT).show();
                Log.e("Directions API", "Failure: " + t.getMessage());
            }
        });
    }

    private void drawMultipleRoutesOnMap(DirectionsResponse directionsResponse,Boolean setInitial) {
        // Clear the map
        if(setInitial){
            mMap.clear();
        }
        routes.clear();
        routes = directionsResponse.getRoutes();
        if (routes.isEmpty()) {
            Toast.makeText(this, "No routes found", Toast.LENGTH_SHORT).show();
            return;
        }

        LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
        boundsBuilder.include(startLatLng);
        boundsBuilder.include(endLatLng);

        // Add markers for start and end locations
        mMap.addMarker(new MarkerOptions()
                .position(startLatLng)
                .title("Start Location"));

        mMap.addMarker(new MarkerOptions()
                .position(endLatLng)
                .title("Destination"));

        // Create a string to display route information
        StringBuilder routeInfo = new StringBuilder("Available Routes:\n");

        // Draw each route with a different color
        for (int i = 0; i < routes.size(); i++) {
            Route route = routes.get(i);

            // Get route details
            Leg leg = route.getLegs().get(0);
            String distance = leg.getDistance().getText();
            String duration = leg.getDuration().getText();

            // Add to route info
            routeInfo.append("Route ").append(i + 1).append(": ")
                    .append(distance).append(", ").append(duration).append("\n");

            // Decode the polyline
            List<LatLng> points = decodePolyline(route.getOverviewPolyline().getPoints());
            polylineoverViews.add(route.getOverviewPolyline().getPoints());

            routePoints.add(points);
            // Add all points to the bounds
            for (LatLng point : points) {
                boundsBuilder.include(point);
            }

            // Draw the route with a unique color
            int colorIndex = i % routeColors.size();
            int routeColor = ContextCompat.getColor(this, routeColors.get(colorIndex));

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

                mMap.addMarker(new MarkerOptions()
                        .position(midPoint)
                        .title("Route " + (i + 1))
                        .snippet(distance + ", " + duration)
                        .icon(BitmapDescriptorFactory.defaultMarker(
                                BitmapDescriptorFactory.HUE_AZURE)));
            }
        }

        flagInitial=false;
        mMap.setOnPolylineClickListener(this::handleRouteSelection);
        if (polylineoverViews != null) {
            Log.d("PolylineOverview", "Polyline Overviews: " + polylineoverViews.toString());
        } else {
            Log.d("PolylineOverview", "Polyline overviews is null");
        }
        // Show route information
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Routes Information");
        builder.setMessage(routeInfo.toString());
        builder.setPositiveButton("OK", null);
        builder.show();

        // Move camera to show all routes
        LatLngBounds bounds = boundsBuilder.build();
        int padding = 100; // Padding around routes in pixels
        CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, padding);
        mMap.animateCamera(cameraUpdate);
    }
    private void handleRouteSelectionComplex(Polyline polyline){

    }
    private void handleRouteSelection(Polyline polyline) {
        // Get the route index from the polyline tag
        Integer routeIndex = (Integer) polyline.getTag();
        if (routeIndex == null) return;
        int i=0;
        // Reset all polyLines to normal width
        if (polylineoverViews != null) {
            Log.d("PolylineOverview", "Polyline Overviews: " + polylineoverViews.toString());
        } else {
            Log.d("PolylineOverview", "Polyline overviews is null");
        }
        if (routePolylines != null) {
            Log.d("PolylineOverview", "RoutePolyLine Overviews: " + routePolylines.toString());
        } else {
            Log.d("PolylineOverview", "RoutePolyLine overviews is null");
        }
        for (Polyline line : routePolylines) {
            line.setWidth(UNSELECTED_ROUTE_WIDTH);
            // Draw the route with a unique color
            int colorIndex = i % routeColors.size();
            int routeColor = ContextCompat.getColor(this, routeColors.get(colorIndex));
            line.setColor(routeColor);
            i++;
        }

        i=0;
        // Set the selected polyline to be thicker
        polyline.setWidth(SELECTED_ROUTE_WIDTH);
        int selectedrouteColor = ContextCompat.getColor(this, R.color.Green);
        polyline.setColor(selectedrouteColor);
        // Update selected route index
        selectedRouteIndex = routeIndex;
        //Get Route Distance and Duration
        Route route = routes.get(selectedRouteIndex);
        // Get route details
        Leg leg = route.getLegs().get(0);
        // distanceBtwHub = leg.getDistance().getText()+distanceBtwHub;
        if(distanceBtwHub == null) {
            Log.d("distance", "distancewalainide");
            distanceBtwHub = leg.getDistance().getText();
        }
        Log.d("distance", "distance:" + distanceBtwHub);

        // Convert km to meters to work with integers
        String currentDistanceStr = leg.getDistance().getText().replaceAll("[^\\d.]", "");
        int currentDistanceMeters = (int)(Double.parseDouble(currentDistanceStr) * 1000);

        if(distanceBtwHub != null) {
            String existingDistanceStr = distanceBtwHub.replaceAll("[^\\d.]", "");
            int existingDistanceMeters = (int)(Double.parseDouble(existingDistanceStr) * 1000);
            int totalDistanceMeters = existingDistanceMeters + currentDistanceMeters;

            // Convert back to km for display
            double totalDistanceKm = totalDistanceMeters / 1000.0;
            if(totalDistanceKm == (int)totalDistanceKm) {
                distanceBtwHub = (int)totalDistanceKm + " km";
            } else {
                distanceBtwHub = String.format("%.1f km", totalDistanceKm);
            }
        }


        durationBtwHub = leg.getDuration().getText();
        // Get the selected route's points
        //  List<LatLng> selectedRoutePoints = routePoints.get(selectedRouteIndex);

//        // Encode the points to a polyline string
//        encodedSelectedPath = encodePolyline(selectedRoutePoints);
        encodedSelectedPath=polylineoverViews.get(selectedRouteIndex);
        Log.d("CountVAl","Count:"+count+"complexPolyLinesSize:"+complexPolyLines.size());
        if (count < complexPolyLines.size()) {
            complexPolyLines.set(count, encodedSelectedPath);
        }
        else {
            complexPolyLines.add(count, encodedSelectedPath);
        }
//      complexPolyLines.add(encodedSelectedPath);
        showRouteButton.setText("Choose Route");
        routeId=convertToSHA256(encodedSelectedPath);
        Log.d("encodedPolyline",encodedSelectedPath);
        // Store in Redis (would require a Redis client implementation)
        //   storeRouteInRedis(encodedSelectedPath);

        // Show a confirmation message
        Toast.makeText(this, "Route " + (selectedRouteIndex + 1) + " selected", Toast.LENGTH_SHORT).show();
    }

    // Store the selected route in SharedPreferences (for local storage)
    private void storeSelectedRoute(String encodedPath) {
        SharedPreferences prefs = getSharedPreferences("RoutePreferences", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("selected_route", encodedPath);
        editor.apply();

        Log.d("RouteStorage", "Route stored in SharedPreferences, length: " + encodedPath.length());
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

public static String encodePolyline(List<LatLng> points) {
    if (points == null || points.isEmpty()) {
        return "";
    }

    StringBuilder result = new StringBuilder();

    int prevLat = 0;
    int prevLng = 0;

    for (LatLng point : points) {
        // Convert to E5 (multiply by 1e5 and round)
        int lat = (int) Math.round(point.latitude * 1e5);
        int lng = (int) Math.round(point.longitude * 1e5);

        // Calculate deltas
        int deltaLat = lat - prevLat;
        int deltaLng = lng - prevLng;

        // Encode deltas
        encodeValue(result, deltaLat);
        encodeValue(result, deltaLng);

        // Update previous values
        prevLat = lat;
        prevLng = lng;
    }

    return result.toString();
}

    private static void encodeValue(StringBuilder result, int value) {
        // Step 1: Shift left by 1
        int encoded = value << 1;

        // Step 2: If negative, invert all bits
        if (value < 0) {
            encoded = ~encoded;
        }

        // Step 3: Break into 5-bit chunks
        while (encoded >= 0x20) {
            result.append((char) (((encoded & 0x1F) | 0x20) + 63));
            encoded >>>= 5;
        }

        result.append((char) (encoded + 63));
    }


    // Method to decode polyline points
    private List<LatLng> decodePolyline(String encoded) {
        List<LatLng> poly = new ArrayList<>();
        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;

        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;

            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;

            LatLng p = new LatLng((double) lat / 1E5, (double) lng / 1E5);
            poly.add(p);
        }

        return poly;
    }
    public boolean isValidPrice(String price) {
        if (price == null) return false;

        // Matches:
        // - "0"
        // - Any number from "1" to "999" without leading zeros
        return price.matches("^(0|[1-9][0-9]{0,2})$");
    }
    public void postData(String routePolyline, String price,String email,String driverId,String vehicalNO,String startHubId,String endHubId,
                         String startHubName,String endHubName,String startHubLatLng,String endHubLatLng,String startCity,String endCity,String distanceBtwHub) {

        driverRoutePostClass request = new driverRoutePostClass(routePolyline, price,email,driverId,vehicalNO,startHubId,endHubId,startHubName,endHubName,startHubLatLng,endHubLatLng,startCity,endCity,distanceBtwHub);

        Call<ResponseBody> call = api.postData(request); // Use ResponseBody
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
                    storeSelectedRoute(encodedSelectedPath);
                    Toast.makeText(ComplexCreateRoute.this, "The Route has been updated Successfully", Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(ComplexCreateRoute.this, "Something Went Wrong Try again Later", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.e("Retrofit", "POST request failure: " + t.getMessage());
                Toast.makeText(ComplexCreateRoute.this, "Something Went Wrong Try again Later", Toast.LENGTH_SHORT).show();

            }
        });
    }
    private double calculateDistance(LatLng point1, LatLng point2) {
        if(point1==null||point2==null)
        {
            return -1;
        }
        float[] results = new float[1];
        Location.distanceBetween(
                point1.latitude, point1.longitude,
                point2.latitude, point2.longitude,
                results);
        return results[0];
    }


    private String getApiKeyFromManifest() {
        try {
            ApplicationInfo appInfo = getPackageManager().getApplicationInfo(
                    getPackageName(), PackageManager.GET_META_DATA);
            if (appInfo.metaData != null) {
                return appInfo.metaData.getString("com.google.android.geo.API_KEY");
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return null; // API key not found
    }
    // Retrofit interface for the Directions API
    public interface DirectionsApiService {
        @GET("maps/api/directions/json")
        Call<DirectionsResponse> getDirections(
                @Query("origin") String origin,
                @Query("destination") String destination,
                @Query("mode") String mode,
                @Query("alternatives") String alternatives,
                @Query("key") String apiKey
        );
    }

    // POJO classes for Retrofit to parse the Directions API response
    public static class DirectionsResponse {
        private List<Route> routes = new ArrayList<>();
        private String status;

        public List<Route> getRoutes() {
            return routes;
        }

        public String getStatus() {
            return status;
        }
    }

    public static class Route {
        private List<Leg> legs = new ArrayList<>();
        private OverviewPolyline overview_polyline;
        private String summary;

        public List<Leg> getLegs() {
            return legs;
        }

        public OverviewPolyline getOverviewPolyline() {
            return overview_polyline;
        }

        public String getSummary() {
            return summary;
        }
    }

    public static class Leg {
        private Distance distance;
        private Duration duration;
        private String start_address;
        private String end_address;

        public Distance getDistance() {
            return distance;
        }

        public Duration getDuration() {
            return duration;
        }

        public String getStartAddress() {
            return start_address;
        }

        public String getEndAddress() {
            return end_address;
        }
    }

    public static class Distance {
        private String text;
        private int value;

        public String getText() {
            return text;
        }

        public int getValue() {
            return value;
        }
    }

    public static class Duration {
        private String text;
        private int value;

        public String getText() {
            return text;
        }

        public int getValue() {
            return value;
        }
    }

    public static class OverviewPolyline {
        private String points;

        public String getPoints() {
            return points;
        }
    }
    // 2. Create a data class for your request body (if sending JSON)
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


    }
    public class HubResponse {

        @SerializedName("hubName:HubId")
        private String hubNameHubId;

        @SerializedName("latLng")
        private LatLng latLng;

        @SerializedName("distance")
        private double distance;

        // Getter and Setter for hubNameHubId
        public String getHubNameHubId() {
            return hubNameHubId;
        }

        public void setHubNameHubId(String hubNameHubId) {
            this.hubNameHubId = hubNameHubId;
        }

        // Getter and Setter for latLng
        public LatLng getLatLng() {
            return latLng;
        }

        public void setLatLng(LatLng latLng) {
            this.latLng = latLng;
        }

        // Getter and Setter for distance
        public double getDistance() {
            return distance;
        }

        public void setDistance(double distance) {
            this.distance = distance;
        }

        // Inner class for latLng
        public class LatLng {

            @SerializedName("lat")
            private double lat;

            @SerializedName("lng")
            private double lng;

            // Getter and Setter for lat
            public double getLat() {
                return lat;
            }

            public void setLat(double lat) {
                this.lat = lat;
            }

            // Getter and Setter for lng
            public double getLng() {
                return lng;
            }

            public void setLng(double lng) {
                this.lng = lng;
            }
        }
    }

    public interface NearestHubCallback {
        void onHubFound(boolean isNearest); // Called when a result is successfully obtained
        void onError(String errorMessage);   // Called when an error occurs
    }
    public interface MyApi {
        @POST("DriverRouteSelect")
        Call<ResponseBody> postData(@Body driverRoutePostClass request); // JSON POST
        @GET("RouteInfoFetch")
        Call<List<HubResponse>> getNearestHub(@Query("city") String city,@Query("geoSearch") String geoSearch,@Query("radius") String radius);
    }

}
