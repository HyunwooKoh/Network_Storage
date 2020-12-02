package tv.formuler.service.gtv.networkstorage;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.TextUtils;

import java.util.ArrayList;


public class NetworkStorageDbHelper extends SQLiteOpenHelper {
    public static final String NET_STORAGE_DB_DIR = "/data/db";
    public static final String NET_STORAGE_DB_NAME = "net_storage.db";
    public static final int NET_STORAGE_DB_VER = 1;
    public static final String NET_STORAGE_TB = "net_storage_tb";

    public static final String COL_ID = "_id";
    public static final String COL_NAME = "name";
    public static final String COL_URL = "url";
    public static final String COL_USER = "user";
    public static final String COL_PASSWORD = "password";
    public static final String COL_RECONNECT = "reconnect";
    public static final String COL_NETWORK_INFO = "network_info";
    public static final String COL_RESERVE1 = "reserve1";
    public static final String COL_ISMOUNTED = "ismounted"; // [ADD] aloys harold - column to dictate Internet disconnect error

    private SQLiteDatabase mDb;

    public NetworkStorageDbHelper(Context context, SQLiteDatabase.CursorFactory factory) {
        super(context, (NET_STORAGE_DB_DIR + "/" + NET_STORAGE_DB_NAME), factory, NET_STORAGE_DB_VER);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        StringBuffer buffer = new StringBuffer("CREATE TABLE IF NOT EXISTS ");
        buffer.append(NET_STORAGE_TB);
        buffer.append(" (" + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, ");
        buffer.append(COL_NAME + " TEXT, ");
        buffer.append(COL_URL + " TEXT, ");
        buffer.append(COL_USER + " TEXT, ");
        buffer.append(COL_PASSWORD + " TEXT, ");
        buffer.append(COL_RECONNECT + " INTEGER, ");
        buffer.append(COL_NETWORK_INFO + " TEXT, ");
        buffer.append(COL_RESERVE1 + " TEXT, ");
        buffer.append(COL_ISMOUNTED + " INTEGER)");
        db.execSQL(buffer.toString());
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }

    public void openDb() {
        if (mDb == null) {
            mDb = getWritableDatabase();
        } else {
            if (!mDb.isOpen()) {
                onOpen(mDb);
            }
        }
    }

    public void releaseDb() {
        if (mDb != null && mDb.isOpen()) {
            mDb.close();
            mDb = null;
        }
    }

    public boolean insertNetStorage(String name, String url, String username, String password, boolean isReconnect, String networkInfo, String reserve, boolean isMounted) {
        try {
            String sql = "INSERT INTO " + NET_STORAGE_TB + " VALUES(null";
            sql += checkAndInsertBuildNullString(name);
            sql += checkAndInsertBuildNullString(url);
            sql += checkAndInsertBuildNullString(username);
            sql += checkAndInsertBuildNullString(password);
            sql += ", " +  (isReconnect == true ? 1 : 0);
            sql += checkAndInsertBuildNullString(networkInfo);
            sql += checkAndInsertBuildNullString(reserve);
            sql += ", " +  (isMounted == true ? 1 : 0);
            sql += ");";
            mDb.execSQL(sql);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    public boolean updateNetStorage(NetStorageDbInfo info) {
        try {
            String sql = "UPDATE " + NET_STORAGE_TB + " SET ";
            sql += COL_NAME + "='" + info.getName() + "'";
            sql += checkAndUpdateBuildNullString(COL_URL, info.getUrl());
            sql += checkAndUpdateBuildNullString(COL_USER, info.getUsername());
            sql += checkAndUpdateBuildNullString(COL_PASSWORD, info.getPassword());
            sql += ", " + COL_RECONNECT + "=" + (info.isReconnect() == true ? 1 : 0);
            sql += checkAndUpdateBuildNullString(COL_NETWORK_INFO, info.getNetworkInfo());
            sql += checkAndUpdateBuildNullString(COL_RESERVE1, info.getReserve1());
            sql += ", " + COL_ISMOUNTED + "=" + (info.isMounted() == true ? 1 : 0);
            sql += " WHERE " + COL_ID + "=" + info.getId() + ";";
            mDb.execSQL(sql);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    public boolean deleteNetStorage(NetStorageDbInfo info) {
        try {
            String sql = "DELETE FROM " + NET_STORAGE_TB + " WHERE "  + COL_ID + "=" + info.getId();
            mDb.execSQL(sql);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    public ArrayList<NetStorageDbInfo> getNetStorageList() {
        Cursor cursor = null;

        try {
            String sql = "SELECT * FROM " + NET_STORAGE_TB;
            cursor = mDb.rawQuery(sql, null);
            if (cursor != null && cursor.getCount() > 0) {
                ArrayList<NetStorageDbInfo> infoList = new ArrayList<>();
                while (cursor.moveToNext()) {
                    NetStorageDbInfo info = new NetStorageDbInfo();
                    info.setId(cursor.getInt(0));
                    info.setName(cursor.getString(1));
                    info.setUrl(cursor.getString(2));
                    info.setUsername(cursor.getString(3));
                    info.setPassword(cursor.getString(4));
                    info.setIsReconnect(cursor.getInt(5));
                    info.setNetworkInfo(cursor.getString(6));
                    info.setReserve1(cursor.getString(7));
                    info.setIsMounted(cursor.getInt(8));
                    infoList.add(info);
                }
                return infoList;
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

    public NetStorageDbInfo getNetStorage(int id) {
        Cursor cursor = null;

        try {
            String sql = "SELECT * FROM " + NET_STORAGE_TB + " WHERE " + COL_ID + "=" + id;
            cursor = mDb.rawQuery(sql, null);
            if (cursor != null && cursor.getCount() > 0) {
                while (cursor.moveToNext()) {
                    NetStorageDbInfo info = new NetStorageDbInfo();
                    info.setId(cursor.getInt(0));
                    info.setName(cursor.getString(1));
                    info.setUrl(cursor.getString(2));
                    info.setUsername(cursor.getString(3));
                    info.setPassword(cursor.getString(4));
                    info.setIsReconnect(cursor.getInt(5));
                    info.setNetworkInfo(cursor.getString(6));
                    info.setReserve1(cursor.getString(7));
                    info.setIsMounted(cursor.getInt(8));
                    return info;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return null;
    }

    public NetStorageDbInfo getNetStorage(String name) {
        Cursor cursor = null;
        if (TextUtils.isEmpty(name)) {
            return null;
        }

        try {
            name = name.replaceAll("'", "''");
            String sql = "SELECT * FROM " + NET_STORAGE_TB + " WHERE " + COL_NAME + "='" + name + "'";
            cursor = mDb.rawQuery(sql, null);
            if (cursor != null && cursor.getCount() > 0) {
                while (cursor.moveToNext()) {
                    NetStorageDbInfo info = new NetStorageDbInfo();
                    info.setId(cursor.getInt(0));
                    info.setName(cursor.getString(1));
                    info.setUrl(cursor.getString(2));
                    info.setUsername(cursor.getString(3));
                    info.setPassword(cursor.getString(4));
                    info.setIsReconnect(cursor.getInt(5));
                    info.setNetworkInfo(cursor.getString(6));
                    info.setReserve1(cursor.getString(7));
                    info.setIsMounted(cursor.getInt(8));
                    return info;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return null;
    }

    private String checkAndInsertBuildNullString(String value) {
        if (TextUtils.isEmpty(value)) {
            return ", " + null;
        } else {
            value = value.replaceAll("\'", "\'\'");
            return ", '" + value + "'";
        }
    }

    private String checkAndUpdateBuildNullString(String key, String value) {
        if (TextUtils.isEmpty(value)) {
            return ", " + key + "=" + null;
        } else {
            value = value.replaceAll("\'", "\'\'");
            return ", " + key + "='" + value + "'";
        }
    }
}
