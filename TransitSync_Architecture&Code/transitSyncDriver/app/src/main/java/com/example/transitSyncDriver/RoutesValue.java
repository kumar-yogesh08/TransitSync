package com.example.transitSyncDriver;


import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "routesValue")
public class RoutesValue {
    @PrimaryKey
    @NonNull
    private String routeId;

    @ColumnInfo(name = "price") // Optional: specify a different column name
    private String price;

    @ColumnInfo(name = "routeName")
    private String routeName;//Hub1Name:HubName2 name

    @ColumnInfo(name = "routesId")
    private String routesId;//HubId1:HubId2

    @ColumnInfo(name = "isSelected")
    private boolean isSelected;

    @ColumnInfo(name = "startHubLtLng")
    private String startHubLtLng;

    @ColumnInfo(name = "endHubLtLng")
    private String endHubLtLng;

    @ColumnInfo(name = "startHubId")
    private String startHubId;

    @ColumnInfo(name = "endHubId")
    private String endHubId;

    @ColumnInfo(name = "distance")
    private String distance;
     @ColumnInfo(name = "polyLine")
     private String polyLine;
    // Constructor
    public RoutesValue(String routeId, String price, String routeName, String routesId, String startHubLtLng, String endHubLtLng,
                       boolean isSelected, String startHubId, String endHubId, String distance, String polyLine) {
        this.routeId = routeId;
        this.price = price;
        this.routeName = routeName;
        this.routesId=routesId;
        this.startHubLtLng=startHubLtLng;
        this.endHubLtLng=endHubLtLng;
        this.isSelected=isSelected;
        this.startHubId=startHubId;
        this.endHubId=endHubId;
        this.distance=distance;

        this.polyLine = polyLine;
    }

    // Getter for routeId
    public String getRouteId() {
        return routeId;
    }

    // Setter for routeId
    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    // Getter for price
    public String getPrice() {
        return price;
    }

    // Setter for price
    public void setPrice(String price) {
        this.price = price;
    }

    // Getter for routeName
    public String getRouteName() {
        return routeName;
    }

    // Setter for routeName
    public void setRouteName(String routeName) {
        this.routeName = routeName;
    }

    public String getRoutesId() {
        return routesId;
    }

    public void setRoutesId(String routesId) {
        this.routesId = routesId;
    }

    public String getStartHubLtLng() {
        return startHubLtLng;
    }

    public String getEndHubLtLng() {
        return endHubLtLng;
    }

    public void setStartHubLtLng(String startHubLtLng) {
        this.startHubLtLng = startHubLtLng;
    }

    public void setEndHubLtLng(String endHubLtLng) {
        this.endHubLtLng = endHubLtLng;
    }

    public boolean getIsSelected(){
        return isSelected;
    }
    public void setIsSelected(boolean isSelected){
        this.isSelected=isSelected;
    }

    public String getStartHubId() {
        return startHubId;
    }

    public void setStartHubId(String startHubId) {
        this.startHubId = startHubId;
    }

    public String getEndHubId() {
        return endHubId;
    }

    public void setEndHubId(String endHubId) {
        this.endHubId = endHubId;
    }

    public String getDistance() {
        return distance;
    }

    public void setDistance(String distance) {
        this.distance = distance;
    }

    public String getPolyLine() {
        return polyLine;
    }


    public void setPolyLine(String polyLine) {
        this.polyLine = polyLine;
    }
}
