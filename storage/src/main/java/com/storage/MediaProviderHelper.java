package com.storage;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.MediaStore;
import android.util.Log;

import androidx.exifinterface.media.ExifInterface;

import com.storage.util.Constant;
import com.storage.util.DateComparator;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class MediaProviderHelper {
    private static final String TAG = "DVR-" + MediaProviderHelper.class.getSimpleName();
    private Uri REMOVABLE_IMAGE_CONTENT_URI = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
    private Uri REMOVABLE_VIDEO_CONTENT_URI = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
    private Context context = null;
    private File imageDir = null;
    private File videoDir = null;
    private Uri imageContentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
    private Uri videoContentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
    private ContentResolver resolver = null;
    private SimpleDateFormat exifDateFormat = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault());
    private SimpleDateFormat queryDateFormat = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
    private IMediaCallback mediaCallback = null;

    private ContentObserver contentObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange, Uri uri) {
            super.onChange(selfChange, uri);

            if (null != mediaCallback) {
                String type = uri.toString();

                if (type.startsWith(imageContentUri.toString())) {
                    mediaCallback.onChanged(MediaBean.Type.IMAGE, uri);
                } else if (type.startsWith(videoContentUri.toString())) {
                    mediaCallback.onChanged(MediaBean.Type.VIDEO, uri);
                }
            }
        }
    };

    public interface IMediaCallback {
        void onChanged(int type, Uri uri);
    }

    public static String copyFile(String srcPath, String dstDir) {
        if (null == srcPath ||  null == dstDir) {
            Log.e(TAG, "copyFile: The source or destination path is null!");
            return null;
        }

        // check the source path
        File dir = new File(srcPath);
        if (!dir.exists() || !dir.isFile()) {
            Log.e(TAG, "copyFile: The source file isn't exist or a normal file, srcPath = " +  srcPath);
            return null;
        }

        // check the destination directory
        dir = new File(dstDir);
        if (!dir.exists() && !dir.mkdirs()) {
            Log.e(TAG, "copyFile: The destination directory isn't exist or make failed, dstDir = " +  dstDir);
            return null;
        }

        // check if copy the same file
        String dstPath = dstDir + srcPath.substring(srcPath.lastIndexOf(File.separator));
        if (dstPath.equals(srcPath)) {
            return srcPath;
        }

        // copy the file
        try {
            FileInputStream fis = new FileInputStream(srcPath);
            FileOutputStream fos = new FileOutputStream(dstPath);
            byte[] buf = new byte[4096];
            int i = 0;

            while (-1 != (i = fis.read(buf))) {
                fos.write(buf, 0, i);
                fos.flush();
            }

            fis.close();
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        return dstPath;
    }

    public static Boolean copyFile(String path, OutputStream os) {
        if (null == path || null == os) {
            Log.e(TAG, "copyFile: The source or destination path is null!");
            return false;
        }

        // check the source path
        File dir = new File(path);
        if (!dir.exists() || !dir.isFile()) {
            Log.e(TAG, "copyFile: The source file isn't exist or a normal file, path = " +  path);
            return false;
        }

        try {
            FileInputStream fis = new FileInputStream(path);
            byte[] buf = new byte[4096];
            int i = 0;

            while (-1 != (i = fis.read(buf))) {
                os.write(buf, 0, i);
                os.flush();
            }

            fis.close();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    public MediaProviderHelper(Context context) {
        // initialize the image directory
        imageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        if (!imageDir.exists()) {
            if (imageDir.mkdir()) {
                Log.e(TAG, "MediaProviderHelper: make external storage public pictures directory!");
            } else {
                Log.e(TAG, "MediaProviderHelper: make external storage public pictures directory failed!");
            }
        }


        // initialize the video directory
        videoDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
        if (!videoDir.exists()) {
            if (videoDir.mkdir()) {
                Log.e(TAG, "MediaProviderHelper: make external storage public movies directory!");
            } else {
                Log.e(TAG, "MediaProviderHelper: make external storage public movies directory failed!");
            }
        }

        // initialize the removable media content uris
        StorageManager sm = context.getSystemService(StorageManager.class);
        Set<String> volumeNames = MediaStore.getExternalVolumeNames(context);
        for (String volumeName : volumeNames) {
            Uri uri = MediaStore.Images.Media.getContentUri(volumeName);
            StorageVolume volume = sm.getStorageVolume(uri);
            if (volume.isRemovable()) {
                REMOVABLE_IMAGE_CONTENT_URI = uri;
            }

            uri = MediaStore.Video.Media.getContentUri(volumeName);
            volume = sm.getStorageVolume(uri);
            if (volume.isRemovable()) {
                REMOVABLE_VIDEO_CONTENT_URI = uri;
            }
        }

        this.context = context;
        resolver = context.getContentResolver();
        resolver.registerContentObserver(imageContentUri, true, contentObserver);
        resolver.registerContentObserver(videoContentUri, true, contentObserver);
    }

    public void destroy() {
        resolver.unregisterContentObserver(contentObserver);
    }

    public boolean setImageDir(String dir) {
        Log.i(TAG, "setImageDir: dir = " + dir);

        if (null == dir) {
            return false;
        }

        File path = new File(dir);

        if (!path.exists()) {
            if (path.mkdirs()) {
                Log.e(TAG, "setImageDir: make directory " + path);
            } else {
                Log.e(TAG, "setImageDir: make directory " + path + " failed!");

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                        || (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q && Environment.isExternalStorageLegacy())) {
                    Log.e(TAG, "setVideoDir: Build.VERSION.SDK_INT " + Build.VERSION.SDK_INT);
                    return false;
                }
            }
        }

        imageDir = path;

        StorageManager sm = context.getSystemService(StorageManager.class);
        StorageVolume volume = sm.getStorageVolume(path);

        if (volume.isRemovable()) {
            imageContentUri = REMOVABLE_IMAGE_CONTENT_URI;
            resolver.registerContentObserver(imageContentUri, true, contentObserver);
        }

        return true;
    }

    public boolean setVideoDir(String dir) {
        Log.i(TAG, "setVideoDir: dir = " + dir);

        if (null == dir) {
            return false;
        }

        File path = new File(dir);

        if (!path.exists()) {
            if (path.mkdirs()) {
                Log.e(TAG, "setVideoDir: make directory " + path);
            } else {
                Log.e(TAG, "setVideoDir: make directory " + path + " failed!");

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                        || (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q && Environment.isExternalStorageLegacy())) {
                    Log.e(TAG, "setVideoDir: Build.VERSION.SDK_INT =  " + Build.VERSION.SDK_INT);
                    return false;
                }
            }
        }

        videoDir = path;

        StorageManager sm = context.getSystemService(StorageManager.class);
        StorageVolume volume = sm.getStorageVolume(path);

        if (volume.isRemovable()) {
            videoContentUri = REMOVABLE_VIDEO_CONTENT_URI;
            resolver.registerContentObserver(videoContentUri, true, contentObserver);
        }

        return true;
    }

    public void setMediaCallback(IMediaCallback callback) {
        this.mediaCallback = callback;
    }

    public MediaBean insert(int type, String path, String url) {
        if (null == path) {
            Log.e(TAG, "insert: The path is null!");
            return null;
        }

        File file = new File(path);
        if (!file.exists() || !file.isFile()) {
            Log.e(TAG, "insert: The file isn't exist or a normal file, path = " +  path);
            return null;
        }

        String dir = null;
        Uri uri = null;

        if (MediaBean.Type.IMAGE == type) {
            dir = imageDir.getAbsolutePath();
            uri = imageContentUri;
        } else if (MediaBean.Type.VIDEO == type) {
            dir = videoDir.getAbsolutePath();
            uri = videoContentUri;
        } else {
            return null;
        }

        String name = file.getName();
        String newPath = dir + File.separator + name;
        String title = name.substring(0, name.lastIndexOf("."));
        MediaBean bean = new MediaBean();
        ContentValues values = new ContentValues();
        Uri itemUri = null;

        // insert the media to database
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            String root = Environment.getExternalStorageDirectory().getAbsolutePath();

            if (dir.startsWith(root)) {
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, dir.substring(root.length()));
            }
        }

        values.put(MediaStore.MediaColumns.DATA, newPath);
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        values.put(MediaStore.MediaColumns.TITLE, title);
        itemUri = resolver.insert(uri, values);
        Log.i(TAG, "insert: insert the media to database, itemUri = " + itemUri);

        if (null == itemUri) {
            return null;
        }

        // write the media to file
        if (!newPath.equals(path)) {
            try {
                OutputStream os = resolver.openOutputStream(itemUri);

                if (null == os || !copyFile(path, os)) {
                    return null;
                }

                Log.i(TAG, "insert: write the media to file, newPath = " + newPath);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                return null;
            }
        }

        bean.setType(type);
        bean.setPath(newPath);
        bean.setName(name);
        bean.setTitle(title);
        bean.setUrl(url);
        bean.setUri(itemUri);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            itemUri = MediaStore.setRequireOriginal(itemUri);
        }

        // update the media
        try {
            ParcelFileDescriptor pfd = resolver.openFileDescriptor(itemUri, "r");

            if (null == pfd) {
                return null;
            }

            long size = pfd.getStatSize();
            String attr = null;

            values.clear();
            values.put(MediaStore.MediaColumns.SIZE, size);
            bean.setSize((int)size);

            if (MediaBean.Type.IMAGE == type) {
                ExifInterface exif = new ExifInterface(pfd.getFileDescriptor());

                attr = exif.getAttribute(ExifInterface.TAG_IMAGE_WIDTH);
                if (null != attr) {
                    int width = Integer.parseInt(attr);
                    values.put(MediaStore.Images.Media.WIDTH, width);
                    bean.setWidth(width);
                }

                attr = exif.getAttribute(ExifInterface.TAG_IMAGE_LENGTH);
                if (null != attr) {
                    int height = Integer.parseInt(attr);
                    values.put(MediaStore.Images.Media.HEIGHT, height);
                    bean.setHeight(height);
                }

                attr = exif.getAttribute(ExifInterface.TAG_DATETIME);
                if (null != attr) {
                    try {
                        Date date = exifDateFormat.parse(attr);
                        if (null != date) {
                            values.put(MediaStore.Images.Media.DATE_TAKEN, date.getTime());
                            bean.setTime(date.getTime());
                        }
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                }

                double[] latLong = exif.getLatLong();
                if (null != latLong) {
                    values.put(MediaStore.Images.Media.LATITUDE, latLong[0]);
                    values.put(MediaStore.Images.Media.LONGITUDE, latLong[1]);
                    bean.setLatitude(latLong[0]);
                    bean.setLongitude(latLong[1]);
                }

                resolver.update(itemUri, values, null, null);
                Log.i(TAG, "insert: update the media to database");
                bean.setThumbnailPath(newPath);
            } else if (MediaBean.Type.VIDEO == type) {
                MediaMetadataRetriever mmr = new MediaMetadataRetriever();

                mmr.setDataSource(pfd.getFileDescriptor());
                attr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
                if (null != attr) {
                    int width = Integer.parseInt(attr);
                    values.put(MediaStore.Video.Media.WIDTH, width);
                    bean.setWidth(width);
                }

                attr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
                if (null != attr) {
                    int height = Integer.parseInt(attr);
                    values.put(MediaStore.Video.Media.HEIGHT, height);
                    bean.setHeight(height);
                }

                attr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                if (null != attr) {
                    values.put(MediaStore.Video.Media.DURATION, attr);
                    bean.setDuration(Long.parseLong(attr));
                }

                resolver.update(itemUri, values, null, null);
                Log.i(TAG, "insert: update the media to database");

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    Bitmap thumbnail = mmr.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);

                    Log.i(TAG, "insert: the video thumbnail(MediaMetadataRetriever) = " + thumbnail);

                    if (null != thumbnail) {
                        String thumbnailPath = newPath.substring(0, newPath.lastIndexOf('.')) + ".jpg";
                        Uri thumbnailUri = null;

                        // insert the video thumbnail to database
                        values.clear();
                        values.put(MediaStore.Video.Thumbnails.DATA, thumbnailPath);
                        values.put(MediaStore.Video.Thumbnails.VIDEO_ID, ContentUris.parseId(uri));
                        values.put(MediaStore.Video.Thumbnails.WIDTH, thumbnail.getWidth());
                        values.put(MediaStore.Video.Thumbnails.HEIGHT, thumbnail.getHeight());
                        thumbnailUri = resolver.insert(MediaStore.Video.Thumbnails.EXTERNAL_CONTENT_URI, values);
                        Log.i(TAG, "insert: insert the video thumbnail to database, thumbnailUri = " + thumbnailUri);

                        if (null != thumbnailUri) {
                            // write the video thumbnail to file
                            try {
                                OutputStream os = resolver.openOutputStream(thumbnailUri);

                                if (null != os) {
                                    thumbnail.compress(Bitmap.CompressFormat.JPEG, 100, os);
                                    os.flush();
                                    os.close();
                                    Log.i(TAG, "insert: write the video thumbnail to file, thumbnailPath = " + thumbnailPath);
                                    bean.setThumbnailPath(thumbnailPath);
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }

            pfd.close();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        return bean;
    }

    public int delete(MediaBean bean) {
        if (null == bean) {
            Log.e(TAG, "delete: The bean is null!");
            return 0;
        }

        String path = bean.getPath();
        if (null == path) {
            Log.e(TAG, "delete: The path is null!");
            return 0;
        }

        int ret = 0;
        Uri uri = bean.getUri();

        if (null != uri) {
            // delete the media from database
            ret = resolver.delete(uri, null, null);

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                    || (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q
                    && Environment.isExternalStorageLegacy())) {
                File file = new File(path);

                // delete the media file
                if (file.exists() && file.isFile() && !file.delete()) {
                    Log.e(TAG, "delete: failed, path = " + path);
                }
            }

            // delete the video thumbnail
            if (MediaBean.Type.VIDEO == bean.getType() && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                String thumbnailPath = bean.getThumbnailPath();

                // delete the video thumbnail from database
                resolver.delete(MediaStore.Video.Thumbnails.EXTERNAL_CONTENT_URI,
                        MediaStore.Video.Thumbnails.VIDEO_ID + " = ?",
                        new String[]{String.valueOf(ContentUris.parseId(uri))});

                // delete the video thumbnail file
                if (null != thumbnailPath) {
                    File file = new File(thumbnailPath);

                    // The video thumbnail will be deleted automatically
                    // because of the video item, but we still check and delete it just in case.
                    if (file.exists() && file.isFile() && !file.delete()) {
                        Log.e(TAG, "delete: failed, thumbnailPath = " +  thumbnailPath);
                    }
                }
            }
        }

        //resolver.notifyChange(uri, contentObserver);

        return ret;
    }

    public int delete(List<MediaBean> beans) {
        if (null == beans) {
            Log.e(TAG, "delete: The beans is null!");
            return 0;
        }

        int ret = 0;

        for (int i = beans.size() - 1; i >= 0; i--) {
            Log.i(TAG, "delete: i = " + i);
            ret += delete(beans.get(i));
        }

        return ret;
    }

    // TODO:
    public int update(MediaBean bean) {
        if (null == bean) {
            Log.e(TAG, "update: The bean is null!");
            return 0;
        }

        return 0;
    }

    public int update(List<MediaBean> beans) {
        if (null == beans) {
            Log.e(TAG, "update: The beans is null!");
            return 0;
        }

        int ret = 0;

        for (MediaBean bean : beans) {
            ret += update(bean);
        }

        return ret;
    }

    public List<MediaBean> query(int type, String pathFilter, int order) {
        Cursor cursor = prepare(type, pathFilter, order);
        if (null == cursor) {
            Log.e(TAG, "query: The cursor is null!");
            return null;
        }

        int idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID);
        int dataColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA);
        int displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME);
        int titleColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.TITLE);
        int widthColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH);
        int heightColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT);
        int sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE);
        int dateTakenColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN);
        int latitudeColumn = 0;
        int longitudeColumn = 0;
        int durationColumn = 0;
        List<MediaBean> beans = new ArrayList<>();

        if (MediaBean.Type.IMAGE == type) {
            latitudeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.LATITUDE);
            longitudeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.LONGITUDE);
        } else if (MediaBean.Type.VIDEO == type) {
            durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION);
        }

        while (cursor.moveToNext()) {
            MediaBean bean = new MediaBean();

            bean.setType(type);
            bean.setPath(cursor.getString(dataColumn));
            bean.setName(cursor.getString(displayNameColumn));
            bean.setTitle(cursor.getString(titleColumn));
            bean.setWidth(cursor.getInt(widthColumn));
            bean.setHeight(cursor.getInt(heightColumn));
            bean.setSize(cursor.getInt(sizeColumn));
            bean.setTime(cursor.getLong(dateTakenColumn));

            if (MediaBean.Type.IMAGE == type) {
                double latitude = cursor.getDouble(latitudeColumn);
                double longitude = cursor.getDouble(longitudeColumn);
                Uri uri = ContentUris.withAppendedId(imageContentUri, cursor.getLong(idColumn));

                if (0 == latitude && 0 == longitude) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        uri = MediaStore.setRequireOriginal(uri);
                    }

                    try {
                        InputStream is = resolver.openInputStream(uri);

                        if (null != is) {
                            ExifInterface exif = new ExifInterface(is);
                            double[] latLong = exif.getLatLong();

                            if (null != latLong) {
                                latitude = latLong[0];
                                longitude = latLong[1];
                            }

                            is.close();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                bean.setLatitude(latitude);
                bean.setLongitude(longitude);
                bean.setUri(uri);
                bean.setThumbnailPath(bean.getPath());
            } else if (MediaBean.Type.VIDEO == type) {
                long id = cursor.getLong(idColumn);

                bean.setDuration(cursor.getLong(durationColumn));
                bean.setUri(ContentUris.withAppendedId(videoContentUri, id));

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    Cursor thumbnailCursor = resolver.query(
                            MediaStore.Video.Thumbnails.EXTERNAL_CONTENT_URI,
                            new String[]{MediaStore.Video.Thumbnails.DATA},
                            MediaStore.Video.Thumbnails.VIDEO_ID + " = ?",
                            new String[]{String.valueOf(id)},
                            null);

                    if (null != thumbnailCursor && thumbnailCursor.moveToFirst()) {
                        bean.setThumbnailPath(thumbnailCursor.getString(thumbnailCursor.getColumnIndexOrThrow(MediaStore.Video.Thumbnails.DATA)));
                        thumbnailCursor.close();
                    }
                }
            }

            beans.add(bean);
        }

        cursor.close();

        return beans;
    }

    public Map<String, List<MediaBean>> queryDateMap(int type, String pathFilter, int order) {
        Cursor cursor = prepare(type, pathFilter, order);
        if (null == cursor) {
            Log.e(TAG, "queryDateMap: The cursor is null!");
            return null;
        }

        int idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID);
        int dataColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA);
        int displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME);
        int titleColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.TITLE);
        int widthColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH);
        int heightColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT);
        int sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE);
        int dateTakenColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN);
        int latitudeColumn = 0;
        int longitudeColumn = 0;
        int durationColumn = 0;
        Map<String, List<MediaBean>> beansMap = null;

        if (Constant.OrderType.ASCENDING == order || Constant.OrderType.DESCENDING == order) {
            beansMap = new TreeMap<>(new DateComparator(queryDateFormat, order));
        } else {
            beansMap = new TreeMap<>();
        }

        if (MediaBean.Type.IMAGE == type) {
            latitudeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.LATITUDE);
            longitudeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.LONGITUDE);
        } else if (MediaBean.Type.VIDEO == type) {
            durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION);
        }

        while (cursor.moveToNext()) {
            MediaBean bean = new MediaBean();
            String date = queryDateFormat.format(new Date(cursor.getLong(dateTakenColumn)));

            bean.setType(type);
            bean.setPath(cursor.getString(dataColumn));
            bean.setName(cursor.getString(displayNameColumn));
            bean.setTitle(cursor.getString(titleColumn));
            bean.setWidth(cursor.getInt(widthColumn));
            bean.setHeight(cursor.getInt(heightColumn));
            bean.setSize(cursor.getInt(sizeColumn));
            bean.setTime(cursor.getLong(dateTakenColumn));

            if (MediaBean.Type.IMAGE == type) {
                double latitude = cursor.getDouble(latitudeColumn);
                double longitude = cursor.getDouble(longitudeColumn);
                Uri uri = ContentUris.withAppendedId(imageContentUri, cursor.getLong(idColumn));

                if (0 == latitude && 0 == longitude) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        uri = MediaStore.setRequireOriginal(uri);
                    }

                    try {
                        InputStream is = resolver.openInputStream(uri);

                        if (null != is) {
                            ExifInterface exif = new ExifInterface(is);
                            double[] latLong = exif.getLatLong();

                            if (null != latLong) {
                                latitude = latLong[0];
                                longitude = latLong[1];
                            }

                            is.close();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                bean.setLatitude(latitude);
                bean.setLongitude(longitude);
                bean.setUri(uri);
                bean.setThumbnailPath(bean.getPath());
            } else if (MediaBean.Type.VIDEO == type) {
                long id = cursor.getLong(idColumn);

                bean.setDuration(cursor.getLong(durationColumn));
                bean.setUri(ContentUris.withAppendedId(videoContentUri, id));

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    Cursor thumbnailCursor = resolver.query(
                            MediaStore.Video.Thumbnails.EXTERNAL_CONTENT_URI,
                            new String[]{MediaStore.Video.Thumbnails.DATA},
                            MediaStore.Video.Thumbnails.VIDEO_ID + " = ?",
                            new String[]{String.valueOf(id)},
                            null);

                    if (null != thumbnailCursor && thumbnailCursor.moveToFirst()) {
                        bean.setThumbnailPath(thumbnailCursor.getString(thumbnailCursor.getColumnIndexOrThrow(MediaStore.Video.Thumbnails.DATA)));
                        thumbnailCursor.close();
                    }
                }
            }

            if (beansMap.containsKey(date)) {
                beansMap.get(date).add(bean);
            } else {
                List<MediaBean> beans = new ArrayList<>();
                beans.add(bean);
                beansMap.put(date, beans);
            }
        }

        cursor.close();

        return beansMap;
    }

    private Cursor prepare(int type, String pathFilter, int order) {
        String selection = null;
        String sortOrder = null;
        Uri uri = null;
        List<String> projection = new ArrayList<>();

        if (MediaBean.Type.IMAGE == type) {
            uri = imageContentUri;
            projection.add(MediaStore.Images.Media.LATITUDE);
            projection.add(MediaStore.Images.Media.LONGITUDE);
        } else if (MediaBean.Type.VIDEO == type) {
            uri = videoContentUri;
            projection.add(MediaStore.Video.Media.DURATION);
        } else {
            return null;
        }

        projection.add(MediaStore.MediaColumns._ID);
        projection.add(MediaStore.MediaColumns.DATA);
        projection.add(MediaStore.MediaColumns.DISPLAY_NAME);
        projection.add(MediaStore.MediaColumns.TITLE);
        projection.add(MediaStore.MediaColumns.WIDTH);
        projection.add(MediaStore.MediaColumns.HEIGHT);
        projection.add(MediaStore.MediaColumns.SIZE);
        projection.add(MediaStore.MediaColumns.DATE_TAKEN);
        selection = (null == pathFilter ? null : MediaStore.MediaColumns.DATA + " LIKE '%" + pathFilter + "%'");

        if (Constant.OrderType.ASCENDING == order) {
            sortOrder = MediaStore.MediaColumns.DATE_TAKEN;
        } else if (Constant.OrderType.DESCENDING == order) {
            sortOrder = MediaStore.MediaColumns.DATE_TAKEN + " DESC";
        }

        return resolver.query(
                uri,
                projection.toArray(new String[0]),
                selection,
                null,
                sortOrder);
    }
}
