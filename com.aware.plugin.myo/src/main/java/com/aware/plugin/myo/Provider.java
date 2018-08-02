package com.aware.plugin.myo;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.provider.BaseColumns;
import android.support.annotation.Nullable;
import android.util.Log;

import com.aware.Aware;
import com.aware.utils.DatabaseHelper;

import java.util.HashMap;

public class Provider extends ContentProvider {

    /**
     * Authority of this content provider
     */
    public static String AUTHORITY = "com.aware.plugin.myo.provider.myo";

    /**
     * ContentProvider database version. Increment every time you modify the database structure
     */
    public static final int DATABASE_VERSION = 1;

    /**
     * Database stored in external folder: /AWARE/myo.db
     */
    public static final String DATABASE_NAME = "plugin_myo.db";

    //Database table names
    public static final String DB_TBL_MYO = "plugin_myo";

    //ContentProvider query indexes
    private static final int TABLE_MYO_DIR = 1;
    private static final int TABLE_MYO_ITEM = 2;

    /**
     * Database tables:
     * - plugin_myo
     */
    public static final String[] DATABASE_TABLES = {
        DB_TBL_MYO
    };

    //These are columns that we need to sync data, don't change this!
    public interface AWAREColumns extends BaseColumns {
        String _ID = "_id";
        String TIMESTAMP = "timestamp";
        String DEVICE_ID = "device_id";
    }

    /**
     * Myo plugin table
     */
    public static final class Myo_Data implements AWAREColumns {
        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/" + DB_TBL_MYO);
        public static final String CONTENT_TYPE = ContentResolver.CURSOR_DIR_BASE_TYPE + "/vnd.com.aware.plugin.myo.provider.plugin_myo";
        public static final String CONTENT_ITEM_TYPE = ContentResolver.CURSOR_ITEM_BASE_TYPE + "/vnd.com.aware.plugin.myo.provider.plugin_myo";

        //Note: integers and strings don't need a type prefix_
        public static final String MYO_DATA = "myo_data";
    }

    //Define each database table fields
    private static final String DB_TBL_TEMPLATE_FIELDS =
        Myo_Data._ID + " integer primary key autoincrement," +
        Myo_Data.TIMESTAMP + " real default 0," +
        Myo_Data.DEVICE_ID + " text default ''," +
        Myo_Data.MYO_DATA + " text default ''";

    /**
     * Share the fields with AWARE so we can replicate the table schema on the server
     */
    public static final String[] TABLES_FIELDS = {
            DB_TBL_TEMPLATE_FIELDS
    };

    //Helper variables for ContentProvider - DO NOT CHANGE
    private UriMatcher sUriMatcher;
    private DatabaseHelper dbHelper;
    private static SQLiteDatabase database;
    private void initialiseDatabase() {
        if (dbHelper == null)
            dbHelper = new DatabaseHelper(getContext(), DATABASE_NAME, null, DATABASE_VERSION, DATABASE_TABLES, TABLES_FIELDS);
        if (database == null)
            database = dbHelper.getWritableDatabase();
    }
    //--

    //For each table, create a hashmap needed for database queries
    private HashMap<String, String> tableMyoHash;

    /**
     * Returns the provider authority that is dynamic
     * @return
     */
    public static String getAuthority(Context context) {
        AUTHORITY = context.getPackageName() + ".provider.myo";
        return AUTHORITY;
    }

    @Override
    public boolean onCreate() {
        //This is a hack to allow providers to be reusable in any application/plugin by making the authority dynamic using the package name of the parent app
        AUTHORITY = getContext().getPackageName() + ".provider.myo";

        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

        //For each table, add indexes DIR and ITEM
        sUriMatcher.addURI(AUTHORITY, DATABASE_TABLES[0], TABLE_MYO_DIR);
        sUriMatcher.addURI(AUTHORITY, DATABASE_TABLES[0] + "/#", TABLE_MYO_ITEM);

        //Create each table hashmap so Android knows how to insert data to the database. Put ALL table fields.
        tableMyoHash = new HashMap<>();
        tableMyoHash.put(Myo_Data._ID, Myo_Data._ID);
        tableMyoHash.put(Myo_Data.TIMESTAMP, Myo_Data.TIMESTAMP);
        tableMyoHash.put(Myo_Data.DEVICE_ID, Myo_Data.DEVICE_ID);
        tableMyoHash.put(Myo_Data.MYO_DATA, Myo_Data.MYO_DATA);

        return true;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        initialiseDatabase();

        database.beginTransaction();

        int count;
        switch (sUriMatcher.match(uri)) {

            //Add each table DIR case, increasing the index accordingly
            case TABLE_MYO_DIR:
                count = database.delete(DATABASE_TABLES[0], selection, selectionArgs);
                break;

            default:
                database.endTransaction();
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        database.setTransactionSuccessful();
        database.endTransaction();

        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    @Nullable
    @Override
    public Uri insert(Uri uri, ContentValues initialValues) {

        initialiseDatabase();

        ContentValues values = (initialValues != null) ? new ContentValues(initialValues) : new ContentValues();

        database.beginTransaction();

        switch (sUriMatcher.match(uri)) {

            //Add each table DIR case
            case TABLE_MYO_DIR:
                long _id = database.insert(DATABASE_TABLES[0], Myo_Data.DEVICE_ID, values);
                database.setTransactionSuccessful();
                database.endTransaction();
                if (_id > 0) {
                    Uri dataUri = ContentUris.withAppendedId(Myo_Data.CONTENT_URI, _id);
                    getContext().getContentResolver().notifyChange(dataUri, null);
                    return dataUri;
                }
                database.endTransaction();
                throw new SQLException("Failed to insert row into " + uri);
            default:
                database.endTransaction();
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    @Nullable
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {

        initialiseDatabase();

        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        switch (sUriMatcher.match(uri)) {

            //Add all tables' DIR entries, with the right table index
            case TABLE_MYO_DIR:
                qb.setTables(DATABASE_TABLES[0]);
                qb.setProjectionMap(tableMyoHash); //the hashmap of the table
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        //Don't change me
        try {
            Cursor c = qb.query(database, projection, selection, selectionArgs,
                    null, null, sortOrder);
            c.setNotificationUri(getContext().getContentResolver(), uri);
            return c;
        } catch (IllegalStateException e) {
            if (Aware.DEBUG)
                Log.e(Aware.TAG, e.getMessage());
            return null;
        }
    }

    @Nullable
    @Override
    public String getType(Uri uri) {
        switch (sUriMatcher.match(uri)) {

            //Add each table indexes DIR and ITEM
            case TABLE_MYO_DIR:
                return Myo_Data.CONTENT_TYPE;
            case TABLE_MYO_ITEM:
                return Myo_Data.CONTENT_ITEM_TYPE;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {

        initialiseDatabase();

        database.beginTransaction();

        int count;
        switch (sUriMatcher.match(uri)) {

            //Add each table DIR case
            case TABLE_MYO_DIR:
                count = database.update(DATABASE_TABLES[0], values, selection, selectionArgs);
                break;

            default:
                database.endTransaction();
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        database.setTransactionSuccessful();
        database.endTransaction();

        getContext().getContentResolver().notifyChange(uri, null);

        return count;
    }
}
