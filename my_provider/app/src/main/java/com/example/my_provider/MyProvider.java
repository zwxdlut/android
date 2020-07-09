package com.example.my_provider;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class MyProvider extends ContentProvider {
    private static final String TAG = "MyProvider";

    @Override
    public boolean onCreate() {
        Log.d(TAG, "onCreate");
        return false;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        Log.d(TAG, "query: uri = " + uri + ", projection = " + projection + ", selection = " + selection + ", selectionArgs = " + selectionArgs + ", sortOrder = " + sortOrder);
        return null;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        Log.d(TAG, "getType: uri = " + uri);
        return null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        Log.d(TAG, "insert: uri = " + uri + ", values = " + values);
        return Uri.parse("content://new.my_provider");
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        Log.d(TAG, "delete: uri = " + uri + ", selection = " + selection + ", selectionArgs = " + selectionArgs);
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) {
        Log.d(TAG, "update: uri = " + uri + ", values = " + values + ", selection = " + selection + ", selectionArgs = " + selectionArgs);
        return 0;
    }
}
