package com.example.transitSyncDriver;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class ExistingRoute extends AppCompatActivity {
    private RecyclerView recyclerView;
    private TextAdapter adapter;
    private List<TextAdapter.ItemData> dataList;
    private ImageView createRouteButton;
    private TextView selectRouteButton;
    private AppDatabase db;
    private int selectedPosition = RecyclerView.NO_POSITION;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.existing_routes);

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        dataList = new ArrayList<>(); // Initialize dataList
        adapter = new TextAdapter(dataList);  // Initialize adapter *before* setting it on the RecyclerView
        recyclerView.setAdapter(adapter);

        db = AppDatabase.getDatabase(getApplicationContext());
        // Initialize the button and set the click listener
        createRouteButton = findViewById(R.id.createRoute);
        selectRouteButton=findViewById(R.id.selectRoute);
        selectRouteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Start the Create Route Activity here
                Intent intent = new Intent(ExistingRoute.this, SelectRoute.class);
                startActivity(intent);
                Toast.makeText(ExistingRoute.this, "Select Route button clicked", Toast.LENGTH_SHORT).show();
            }
        });
        createRouteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Start the Create Route Activity here
                Intent intent = new Intent(ExistingRoute.this, ComplexCreateRoute.class);
                startActivity(intent);
                Toast.makeText(ExistingRoute.this, "Create Route button clicked", Toast.LENGTH_SHORT).show();
            }
        });

        // Fetch data from the database and update the RecyclerView
        loadRoutesFromDatabase();
    }

    private void loadRoutesFromDatabase() {
        new GetAllRouteValuesTask(db.routesValueDao()).execute();
    }

    private void updateUIWithRoutes(List<RoutesValue> routesValues) {
        dataList.clear();
        selectedPosition = RecyclerView.NO_POSITION; // Reset selected position
        for (RoutesValue route : routesValues) {
            String[] routeNames = route.getRouteName().split(":");
            String[] routeIds = route.getRoutesId().split(":");
            String startHubFullName = (routeNames.length > 0) ? routeNames[0] : "Unknown Start";
            String startHubName=startHubFullName.split(",")[0];
            String endHubFullName = (routeNames.length > 1) ? routeNames[1] : "Unknown End";
            String endHubName=endHubFullName.split(",")[0];
            String routeId = route.getRouteId();

            dataList.add(new TextAdapter.ItemData(startHubName, endHubName, route.getPrice(), routeId));
            if (route.getIsSelected()) {
                selectedPosition = dataList.size() - 1; // Find the selected item's position
            }
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                adapter.setSelectedPosition(selectedPosition);  // Pass selected position to adapter
                adapter.notifyDataSetChanged();
            }
        });
    }

    // AsyncTask to fetch all routes from the database
    private class GetAllRouteValuesTask extends AsyncTask<Void, Void, List<RoutesValue>> {
        private RoutesValueDao routesValueDao;

        GetAllRouteValuesTask(RoutesValueDao routesValueDao) {
            this.routesValueDao = routesValueDao;
        }

        @Override
        protected List<RoutesValue> doInBackground(Void... voids) {
            try {
                return routesValueDao.getAllRoutesValues();
            } catch (Exception e) {
                Log.e("GetAllRouteValuesTask", "Error fetching routes: " + e.getMessage());
                return null;
            }
        }

        @Override
        protected void onPostExecute(List<RoutesValue> routesValues) {
            if (routesValues != null) {
                updateUIWithRoutes(routesValues);
            } else {
                Toast.makeText(ExistingRoute.this, "Failed to load routes", Toast.LENGTH_SHORT).show();
            }
        }
    }
        public class ItemData {
            String startHub;
            String endHub;
            String price;
            String routeId; // Added routeId

            public ItemData(String startHub, String endHub, String price, String routeId) {
                this.startHub = startHub;
                this.endHub = endHub;
                this.price = price;
                this.routeId = routeId;
            }
        }

    }
