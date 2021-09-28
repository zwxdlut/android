package com.storage.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface CloudInfoDao {
    @Query("SELECT url FROM cloud_info WHERE name = :name")
    String query(String name);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(CloudInfo cloudInfo);

    @Query("DELETE FROM cloud_info WHERE name = :name")
    int delete(String name);
}

