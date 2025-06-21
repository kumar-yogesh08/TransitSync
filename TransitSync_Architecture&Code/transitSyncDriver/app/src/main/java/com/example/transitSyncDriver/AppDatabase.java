package com.example.transitSyncDriver;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(entities = {RoutesValue.class},version = 2)
public abstract class AppDatabase extends RoomDatabase {
    public abstract RoutesValueDao routesValueDao();
    private static volatile AppDatabase INSTANCE;

    public static AppDatabase getDatabase(final android.content.Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = androidx.room.Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "user_database") // Database name
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
