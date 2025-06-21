package com.example.transitSyncDriver;
import static com.google.maps.android.Context.getApplicationContext;

import android.graphics.Color;
import android.os.AsyncTask;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class TextAdapter extends RecyclerView.Adapter<TextAdapter.ViewHolder> {
    private AppDatabase db;
    private SharedPreferencesManager sharedPreferencesManager;
    // Assuming your data is now a list of objects with startHub, endHub, and price
    public static class ItemData {
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

    private List<ItemData> dataList;
    private int selectedPosition = RecyclerView.NO_POSITION;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(int position, ItemData itemData); // Pass the whole data item
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public TextAdapter(List<ItemData> dataList) {
        this.dataList = dataList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_text, parent, false);
        db = AppDatabase.getDatabase(parent.getContext());
        sharedPreferencesManager = SharedPreferencesManager.getInstance(parent.getContext());
        return new ViewHolder(view);



    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ItemData currentItem = dataList.get(position);
        holder.textViewStartHub.setText(currentItem.startHub);
        holder.textViewEndHub.setText(currentItem.endHub);
        holder.textViewPrice.setText(currentItem.price);
        holder.textViewRouteId.setText(currentItem.routeId); // Set the routeId

        if (position == selectedPosition) {
            holder.itemView.setBackgroundColor(Color.parseColor("#2196F3")); // Set blue background for the entire item
//            // Optional: Set text colors if needed
//
//            holder.textViewStartHub.setTextColor(Color.WHITE);
//            holder.textViewEndHub.setTextColor(Color.WHITE);
//            holder.textViewPrice.setTextColor(Color.WHITE);
//            holder.textViewRouteId.setTextColor(Color.WHITE);

        } else {
            holder.itemView.setBackgroundColor(Color.TRANSPARENT); // Reset background
            holder.textViewStartHub.setTextColor(Color.BLACK);
            holder.textViewEndHub.setTextColor(Color.DKGRAY);
            holder.textViewPrice.setTextColor(Color.parseColor("#008000")); // Example: Green for price
            holder.textViewRouteId.setTextColor(Color.BLACK);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(holder.getAdapterPosition(), currentItem);
            }
            // Update the database here
            new UpdateIsSelectedTask(db.routesValueDao(), currentItem.routeId, true).execute();
            sharedPreferencesManager.saveString("selectedRouteId",currentItem.routeId);
            notifyItemChanged(selectedPosition);
            selectedPosition = position;
            notifyItemChanged(selectedPosition);
        });
    }
    private static class UpdateIsSelectedTask extends AsyncTask<Void, Void, Integer> {
        private RoutesValueDao routesValueDao;
        private String routeId;
        private boolean isSelected;

        public UpdateIsSelectedTask(RoutesValueDao routesValueDao, String routeId, boolean isSelected) {
            this.routesValueDao = routesValueDao;
            this.routeId = routeId;
            this.isSelected = isSelected;
        }

        @Override
        protected Integer doInBackground(Void... voids) {
            try {
                routesValueDao.updateIsSelected(routeId, isSelected);
                return 1;
            } catch (Exception e) {
                Log.e("UpdateIsSelectedTask", "Error updating isSelected: " + e.getMessage());
                return 0; // Ensure you return a value on error
            }
        }

        @Override
        protected void onPostExecute(Integer success) {
            if (success>0) {
                Log.d("UpdateIsSelectedTask", "Successfully updated isSelected for routeId: " + routeId);
            } else {
                Log.e("UpdateIsSelectedTask", "Failed to update isSelected for routeId: " + routeId);
            }
        }
    }
    @Override
    public int getItemCount() {
        return dataList.size();
    }
    public void setSelectedPosition(int position) {
        this.selectedPosition = position;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textViewStartHub;
        TextView textViewEndHub;
        TextView textViewPrice;
        TextView textViewRouteId; // Added textViewRouteId

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            textViewStartHub = itemView.findViewById(R.id.textViewStartHub);
            textViewEndHub = itemView.findViewById(R.id.textViewEndHub);
            textViewPrice = itemView.findViewById(R.id.textViewPrice);
            textViewRouteId = itemView.findViewById(R.id.textViewRouteId); // Find the new TextView
        }
    }
}
