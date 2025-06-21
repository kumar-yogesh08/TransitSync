package com.example.transitSyncPassenger;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

public class IntelligentRouting extends AppCompatActivity {
    private AutoCompleteTextView startLocation;
    private AutoCompleteTextView endLocation;
    private TextView pathInformation;
    private TextView hubLatLngInformation;
    private TextView price_distanceInformation;
    private TextView showRoute;
    private ApiService apiService;
    private String gateWayUrl="Your_APi_gateway_Url";
    private Runnable autocompleteRunnable;
    private Handler updateAutoCompleteHandler;
    private List<String > hubNames=new ArrayList<>();
    private List<String> hubIds=new ArrayList<>();
    private String startHubName="";
    private String endHubName="";
    private String startHubId="";
    private String endHubId="";
    private String city="Tezpur";
    private Spinner citySpinner;
    private boolean isAutocompleteFromSelection=false;

    private interface ApiService {
        @POST("IntelligentRouting")
        Call<IntelligentRoutingResponse> getIntelligentRouting(@Body RoutingRequest request);
       @GET("RouteInfoFetch")
        Call<List<String>> getHubAutoComplete(
                @Query("autocomplete") String prefix,@Query("city") String city);
        @GET("RouteInfoFetch")
        Call<CitiesResponse> getCities(
                @Query("cities") String cities);

    }
    public class CitiesResponse {
        private List<String> cities;

        public List<String> getCities() {
            return cities;
        }

        public void setCities(List<String> cities) {
            this.cities = cities;
        }
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_intelligent_routing);
        startLocation=findViewById(R.id.start_location);
        endLocation=findViewById(R.id.end_location);
        pathInformation=findViewById(R.id.pathInformation);
        price_distanceInformation=findViewById(R.id.price_distanceInformation);
        hubLatLngInformation=findViewById(R.id.hubLatLngInformation);
        citySpinner = findViewById(R.id.citySpinner);
        showRoute=findViewById(R.id.show_route);
        Retrofit retrofit2 = new Retrofit.Builder()
                .baseUrl(gateWayUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        apiService = retrofit2.create(ApiService.class);
        getCities();
        updateAutoCompleteHandler=new Handler(Looper.getMainLooper());
        setupAutocomplete(startLocation, true);
        setupAutocomplete(endLocation, false);
        showRoute.setOnClickListener(v->findRoute(startHubName,endHubName));
        citySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedCity = parent.getItemAtPosition(position).toString();
                city=selectedCity;
                // Store in SharedPreferences
                SharedPreferences sharedPref = getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPref.edit();
                editor.putString("userCity", selectedCity);
                editor.apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

    }
    private void getCities() {

        Call<CitiesResponse> call = apiService.getCities("true");
        call.enqueue(new Callback<CitiesResponse>() {
            @Override
            public void onResponse(Call<CitiesResponse> call, Response<CitiesResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<String> cities = response.body().getCities();

                    // Create ArrayAdapter
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(
                            IntelligentRouting.this,  // or getActivity() in fragment
                            android.R.layout.simple_spinner_item,
                            cities
                    );
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

                    // Set adapter to spinner
                    citySpinner.setAdapter(adapter);
                }
            }

            @Override
            public void onFailure(Call<CitiesResponse> call, Throwable t) {
                Toast.makeText(IntelligentRouting.this, "Failed to fetch cities", Toast.LENGTH_SHORT).show();
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
                if (autocompleteRunnable != null) {
                    updateAutoCompleteHandler.removeCallbacks(autocompleteRunnable);
                }
                String queryStr = query.toString();
                String relevantSelection = isStartLocation ? startHubName : endHubName;// this is because after onClick item the query in the autoComplete resets again making a api call, this prevents it
                autocompleteRunnable = () -> {
                    if (queryStr.length() > 2 && !queryStr.equals(relevantSelection)) {
                        fetchHubPredictions(queryStr, textView, isStartLocation);
                    }
                };
                updateAutoCompleteHandler.postDelayed(autocompleteRunnable, 1500); // 1.5 seconds delay
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
            ArrayAdapter<String> emptyAdapter = new ArrayAdapter<>(IntelligentRouting.this,
                    android.R.layout.simple_dropdown_item_1line, new ArrayList<>());
            textView.setAdapter(emptyAdapter);
            textView.dismissDropDown();
            isAutocompleteFromSelection = false;



        });
    }
    private void fetchHubPredictions(String query, AutoCompleteTextView textView, boolean isStartLocation) {

        Call<List<String>> call = apiService.getHubAutoComplete(query, city);
        call.enqueue(new Callback<List<String>>() {
            @Override
            public void onResponse(Call<List<String>> call, Response<List<String>> response) {
                if (response.isSuccessful()) {
                    List<String> data = response.body();
                    hubIds.clear();
                    hubNames.clear();

                    if (data != null) {
                        for (String val : data) {
                            String[] value = val.split(":");//String=prefix:hubName:hubId
                            String name = value[1];
                            String hubId = value[2];
                            hubNames.add(name);
                            hubIds.add(hubId);
                        }
                    }
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(IntelligentRouting.this,
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
    public void findRoute(String startHubName, String endHubName) {
        if (startHubName == null || endHubName == null) {
            Toast.makeText(this, "Enter Start Location and End Location", Toast.LENGTH_SHORT).show();
            return;
        }
        RoutingRequest request = new RoutingRequest(startHubName, endHubName);
        Call<IntelligentRoutingResponse> call = apiService.getIntelligentRouting(request);
        call.enqueue(new Callback<IntelligentRoutingResponse>() {
            @Override
            public void onResponse(Call<IntelligentRoutingResponse> call, Response<IntelligentRoutingResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    IntelligentRoutingResponse routeResponse = response.body();
                    processAndDisplayRoute(routeResponse); // Extract and format the data
                } else {
                    Toast.makeText(IntelligentRouting.this, "Route not found", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<IntelligentRoutingResponse> call, Throwable t) {
                Toast.makeText(IntelligentRouting.this, "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

    }

    private void processAndDisplayRoute(IntelligentRoutingResponse routeResponse) {
        // 1. Process and set path text (normal text)
        List<String> path = routeResponse.getPath();
        StringBuilder pathBuilder = new StringBuilder();
        if (path != null && !path.isEmpty()) {
            for (int i = 0; i < path.size(); i++) {
                pathBuilder.append((i+1)+") "+path.get(i));
                if (i < path.size() - 1) {
                    pathBuilder.append("  →  ");  // nice spacing
                }
            }
        }
        pathInformation.setText(pathBuilder.toString());

        // 2. Process LatLngs and create clickable spans
        List<String> hubLatLngs = routeResponse.getHubLatLngs();
        SpannableStringBuilder latLngBuilder = new SpannableStringBuilder();
        if (hubLatLngs != null && !hubLatLngs.isEmpty()) {
            for (int i = 0; i < hubLatLngs.size(); i++) {
                final String latLng = hubLatLngs.get(i);

                int start = latLngBuilder.length();
                latLngBuilder.append((i+1)+") "+latLng);
                int end = latLngBuilder.length();

                // ClickableSpan for opening Google Maps
                latLngBuilder.setSpan(new ClickableSpan() {
                    @Override
                    public void onClick(@NonNull View widget) {
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        String geoUri = "geo:" + latLng + "?q=" + latLng;
                        intent.setData(Uri.parse(geoUri));
                        if (intent.resolveActivity(getPackageManager()) != null) {
                            startActivity(intent);
                        } else {
                            Toast.makeText(getApplicationContext(),
                                    "No Maps application found", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void updateDrawState(@NonNull TextPaint ds) {
                        super.updateDrawState(ds);
                        ds.setColor(Color.BLUE);       // Link color
                        ds.setUnderlineText(true);     // Underline
                    }
                }, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

                if (i < hubLatLngs.size() - 1) {
                    latLngBuilder.append("  →  ");  // add spacing and arrow between coords
                }
            }
        }
        hubLatLngInformation.setText(latLngBuilder, TextView.BufferType.SPANNABLE);
        hubLatLngInformation.setMovementMethod(LinkMovementMethod.getInstance());  // enable click

        // 3. Show price and distance neatly formatted
        double distance = routeResponse.getTotalDistance();
        double price = routeResponse.getTotalPrice();

        String priceDistanceText = String.format(
                "Price: %.2f\nDistance: %.2f km",
                price, distance
        );
        price_distanceInformation.setText(priceDistanceText);

        // Optional: debug log and toast summary
        String resultSummary = pathBuilder.toString() + "\n" +
                latLngBuilder.toString() + "\n" +
                priceDistanceText;
        Log.d("Route Info", resultSummary);
        Toast.makeText(this, "Route loaded", Toast.LENGTH_SHORT).show();
    }


    public class IntelligentRoutingResponse {

        @SerializedName("path")
        private List<String> path;

        @SerializedName("hubLatLngs")
        private List<String> hubLatLngs;

        @SerializedName("total_distance")
        private Double totalDistance;

        @SerializedName("total_price")
        private Double totalPrice;

        public List<String> getPath() {
            return path;
        }

        public void setPath(List<String> path) {
            this.path = path;
        }

        public List<String> getHubLatLngs() {
            return hubLatLngs;
        }

        public void setHubLatLngs(List<String> hubLatLngs) {
            this.hubLatLngs = hubLatLngs;
        }

        public Double getTotalDistance() {
            return totalDistance;
        }

        public void setTotalDistance(Double totalDistance) {
            this.totalDistance = totalDistance;
        }

        public Double getTotalPrice() {
            return totalPrice;
        }

        public void setTotalPrice(Double totalPrice) {
            this.totalPrice = totalPrice;
        }

        @Override
        public String toString() {
            return "MyResponse{" +
                    "path=" + path +
                    ", hubLatLngs=" + hubLatLngs +
                    ", totalDistance=" + totalDistance +
                    ", totalPrice=" + totalPrice +
                    "}";
        }
    }
    public class RoutingRequest{
        private String start;
        private String end;

        public RoutingRequest(String start,String end) {
            this.start = start;
            this.end=end;
        }

        public String getStartHubName() {
            return start;
        }

        public String getEndHubName() {
            return end;
        }
        public void setStartHubName(String start) {
            this.start = start;
        }
        public void setEndHubName(String end) {
            this.end = end;
        }
    }
}
