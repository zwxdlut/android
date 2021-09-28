package com.storage.dao;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "cloud_info")
public class CloudInfo {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String name;

    public String url;

    public CloudInfo(String name, String url) {
        this.name = name;
        this.url = url;
    }
}

