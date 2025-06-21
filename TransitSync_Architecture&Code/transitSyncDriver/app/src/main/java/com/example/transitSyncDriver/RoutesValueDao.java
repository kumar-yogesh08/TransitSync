package com.example.transitSyncDriver;


import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;



@Dao
public interface RoutesValueDao {
    @Query("SELECT * FROM routesValue")
    List<RoutesValue> getAllRoutesValues();

    @Query("SELECT * FROM routesValue WHERE routeId = :routeId")
    RoutesValue getRoutesValueById(String routeId);

    @Insert
    long insertRoutesValue(RoutesValue routeValue);

    @Insert
    void insertMultipleRoutesValues(List<RoutesValue> routesValues);

    @Update
    void updateRoutesValue(RoutesValue routeValue);

    @Delete
    void deleteRoutesValue(RoutesValue routeValue);

    @Query("DELETE FROM routesValue WHERE routeId = :routeId")
    void deleteRoutesValueById(String routeId);

    //You can add more complex queries here
    //Example:
    @Query("SELECT * FROM routesValue WHERE routeName = :routeName")
    List<RoutesValue> getRoutesValuesByRouteName(String routeName);

    @Query("SELECT * FROM routesValue WHERE routesId = :routesId")
    List<RoutesValue> getRoutesValuesByRoutesId(String routesId);

    @Query("SELECT * FROM routesValue WHERE isSelected = :isSelected")
    List<RoutesValue> getRoutesValuesByIsSelected(boolean isSelected);

    @Query("UPDATE routesValue SET isSelected = CASE WHEN routeId = :routeId THEN :isSelected ELSE 0 END")
    int updateIsSelected(String routeId, boolean isSelected);

//
//    @Query("SELECT startHubLtLng, endHubLtLng FROM routesValue WHERE routeId = :routeId")
//    LatLngPair getLatLngByRouteId(String routeId);


//    class LatLngPair {
//        public String startHubLatLng;
//        public String endHubLatLng;
//    }

}
