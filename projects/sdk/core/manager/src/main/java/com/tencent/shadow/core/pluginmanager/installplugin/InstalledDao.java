package com.tencent.shadow.core.pluginmanager.installplugin;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Pair;

import com.tencent.shadow.core.interface_.InstalledType;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class InstalledDao {

    private InstalledPluginDBHelper mDBHelper;

    public InstalledDao(InstalledPluginDBHelper dbHelper) {
        mDBHelper = dbHelper;
    }

    /**
     * 根据插件配置信息插入一组数据
     *
     * @param pluginConfig 插件配置信息
     * @return 安装后的信息
     */
    public InstalledPlugin insert(PluginConfig pluginConfig) {
        SQLiteDatabase db = mDBHelper.getWritableDatabase();
        Pair<InstalledPlugin, List<ContentValues>> pair = parseConfig(pluginConfig);
        List<ContentValues> contentValuesList = pair.second;
        db.beginTransaction();
        try {
            for (ContentValues contentValues : contentValuesList) {
                db.replace(InstalledPluginDBHelper.TABLE_NAME_MANAGER, null, contentValues);
            }
            //把最后一次uuid的插件安装时间作为所有相同uuid的插件的安装时间
            ContentValues values = new ContentValues();
            values.put(InstalledPluginDBHelper.COLUMN_INSTALL_TIME, pluginConfig.storageDir.lastModified());
            db.update(InstalledPluginDBHelper.TABLE_NAME_MANAGER, values, InstalledPluginDBHelper.COLUMN_UUID + " = ?", new String[]{pluginConfig.UUID});

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return pair.first;
    }

    /**
     * 删除UUID相关的数据
     *
     * @param UUID 插件的发布id
     * @return 影响的数据行数
     */
    public int deleteByUUID(String UUID) {
        SQLiteDatabase db = mDBHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            return db.delete(InstalledPluginDBHelper.TABLE_NAME_MANAGER, InstalledPluginDBHelper.COLUMN_UUID + " =?", new String[]{UUID});
        } finally {
            db.endTransaction();
            db.close();
        }
    }

    /**
     * 根据uuid和APPID获取对应的插件信息
     *
     * @param UUID  插件的发布id
     * @return 插件安装数据
     */
    public InstalledPlugin getInstalledPluginByUUID(String UUID) {
        SQLiteDatabase db = mDBHelper.getReadableDatabase();
        Cursor cursor = db.rawQuery("select * from shadowPluginManager where uuid = ?", new String[]{UUID});
        InstalledPlugin installedPlugin = new InstalledPlugin();
        installedPlugin.UUID = UUID;
        while (cursor.moveToNext()) {
            int type = cursor.getInt(cursor.getColumnIndex(InstalledPluginDBHelper.COLUMN_TYPE));
            if (type == InstalledType.TYPE_UUID) {
                installedPlugin.UUID_NickName = cursor.getString(cursor.getColumnIndex(InstalledPluginDBHelper.COLUMN_VERSION));
            } else {
                File pluginFile = new File(cursor.getString(cursor.getColumnIndex(InstalledPluginDBHelper.COLUMN_PATH)));
                String oDexPath = cursor.getString(cursor.getColumnIndex(InstalledPluginDBHelper.COLUMN_PLUGIN_ODEX));
                File oDexDir = oDexPath == null ? null : new File(oDexPath);
                String libPath = cursor.getString(cursor.getColumnIndex(InstalledPluginDBHelper.COLUMN_PLUGIN_LIB));
                File libDir = libPath == null ? null : new File(libPath);
                if (type == InstalledType.TYPE_PLUGIN_LOADER) {
                    installedPlugin.pluginLoaderFile = new InstalledPlugin.Part(type, pluginFile, oDexDir, libDir);
                } else if (type == InstalledType.TYPE_PLUGIN_RUNTIME) {
                    installedPlugin.runtimeFile = new InstalledPlugin.Part(type, pluginFile, oDexDir, libDir);
                } else {
                    String partKey = cursor.getString(cursor.getColumnIndex(InstalledPluginDBHelper.COLUMN_PARTKEY));

                    if (type == InstalledType.TYPE_PLUGIN) {
                        int columnIndex = cursor.getColumnIndex(InstalledPluginDBHelper.COLUMN_DEPENDSON);
                        boolean hasDependencies = !cursor.isNull(columnIndex);
                        String[] dependsOn;
                        if (hasDependencies) {
                            String string = cursor.getString(columnIndex);
                            try {
                                JSONArray jsonArray = new JSONArray(string);
                                dependsOn = new String[jsonArray.length()];
                                for (int i = 0; i < jsonArray.length(); i++) {
                                    dependsOn[i] = jsonArray.getString(i);
                                }
                            } catch (JSONException e) {
                                throw new RuntimeException(e);
                            }
                        } else {
                            dependsOn = null;
                        }
                        installedPlugin.plugins.put(partKey, new InstalledPlugin.PluginPart(type,pluginFile, oDexDir, libDir, dependsOn));
                    } else if (type == InstalledType.TYPE_INTERFACE) {
                        installedPlugin.interfaces.put(partKey, new InstalledPlugin.Part(type, pluginFile, oDexDir, libDir));
                    } else {
                        throw new RuntimeException("出现不认识的type==" + type);
                    }
                }
            }
        }
        cursor.close();
        return installedPlugin;
    }

    /**
     * 获取最近的插件列表数据
     *
     * @param limit 获取的数据数量
     * @return 插件列表数据
     */
    public List<InstalledPlugin> getLastPlugins(int limit) {
        SQLiteDatabase db = mDBHelper.getReadableDatabase();
        Cursor cursor = db.rawQuery("select uuid from shadowPluginManager where  type = ?   order by installedTime desc limit " + limit, new String[]{String.valueOf(InstalledType.TYPE_UUID)});
        List<String> uuids = new ArrayList<>();
        while (cursor.moveToNext()) {
            String uuid = cursor.getString(cursor.getColumnIndex(InstalledPluginDBHelper.COLUMN_UUID));
            uuids.add(uuid);
        }
        cursor.close();
        List<InstalledPlugin> installedPlugins = new ArrayList<>();
        for (String uuid : uuids) {
            installedPlugins.add(getInstalledPluginByUUID(uuid));
        }
        db.close();
        return installedPlugins;
    }


    public boolean updatePlugin(String UUID, String partKey, ContentValues contentValues) {
        SQLiteDatabase db = mDBHelper.getWritableDatabase();
        db.beginTransaction();
        int row = 0;
        try {
            row = db.update(InstalledPluginDBHelper.TABLE_NAME_MANAGER, contentValues,
                    InstalledPluginDBHelper.COLUMN_UUID + " = ? and " + InstalledPluginDBHelper.COLUMN_PARTKEY + " = ?",
                    new String[]{UUID, partKey});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return row > 0;
    }

    public boolean updatePlugin(String UUID, int type, ContentValues contentValues) {
        if (type != InstalledType.TYPE_PLUGIN_LOADER && type != InstalledType.TYPE_PLUGIN_RUNTIME) {
            throw new RuntimeException("不支持更新 type:" + type);
        }
        SQLiteDatabase db = mDBHelper.getWritableDatabase();
        db.beginTransaction();
        int row = 0;
        try {
            row = db.update(InstalledPluginDBHelper.TABLE_NAME_MANAGER, contentValues,
                    InstalledPluginDBHelper.COLUMN_UUID + " = ? and " + InstalledPluginDBHelper.COLUMN_TYPE + " = ?",
                    new String[]{UUID, String.valueOf(type)});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return row > 0;
    }

    private Pair<InstalledPlugin, List<ContentValues>> parseConfig(PluginConfig pluginConfig) {
        List<InstalledRow> installedRows = new ArrayList<>();
        InstalledPlugin installedPlugin = new InstalledPlugin();
        if (pluginConfig.pluginLoader != null) {
            installedPlugin.pluginLoaderFile = new InstalledPlugin.Part(InstalledType.TYPE_PLUGIN_LOADER, pluginConfig.pluginLoader.file, null, null);
            installedRows.add(new InstalledRow(pluginConfig.pluginLoader.hash, null, pluginConfig.pluginLoader.file.getAbsolutePath(), InstalledType.TYPE_PLUGIN_LOADER));
        }
        if (pluginConfig.runTime != null) {
            installedPlugin.runtimeFile = new InstalledPlugin.Part(InstalledType.TYPE_PLUGIN_RUNTIME, pluginConfig.runTime.file, null, null);
            installedRows.add(new InstalledRow(pluginConfig.runTime.hash, null, pluginConfig.runTime.file.getAbsolutePath(), InstalledType.TYPE_PLUGIN_RUNTIME));
        }
        if (pluginConfig.plugins != null) {
            Set<Map.Entry<String, PluginConfig.PluginFileInfo>> plugins = pluginConfig.plugins.entrySet();
            Map<String, InstalledPlugin.PluginPart> map = new HashMap<>();
            for (Map.Entry<String, PluginConfig.PluginFileInfo> plugin : plugins) {
                PluginConfig.PluginFileInfo fileInfo = plugin.getValue();
                map.put(plugin.getKey(), new InstalledPlugin.PluginPart(InstalledType.TYPE_PLUGIN,fileInfo.file, null, null, fileInfo.dependsOn));
                installedRows.add(new InstalledRow(fileInfo.hash, plugin.getKey(), fileInfo.dependsOn, fileInfo.file.getAbsolutePath(), InstalledType.TYPE_PLUGIN));
            }
            installedPlugin.plugins = map;
        }
        if (pluginConfig.interfaces != null) {
            Set<Map.Entry<String, PluginConfig.FileInfo>> plugins = pluginConfig.interfaces.entrySet();
            Map<String, InstalledPlugin.Part> map = new HashMap<>();
            for (Map.Entry<String, PluginConfig.FileInfo> plugin : plugins) {
                PluginConfig.FileInfo fileInfo = plugin.getValue();
                map.put(plugin.getKey(), new InstalledPlugin.Part(InstalledType.TYPE_INTERFACE, fileInfo.file, null, null));
                installedRows.add(new InstalledRow(fileInfo.hash, plugin.getKey(), fileInfo.file.getAbsolutePath(), InstalledType.TYPE_INTERFACE));
            }
            installedPlugin.interfaces = map;
        }
        InstalledRow uuidRow = new InstalledRow();
        uuidRow.type = InstalledType.TYPE_UUID;
        uuidRow.filePath = pluginConfig.UUID;
        installedRows.add(uuidRow);
        List<ContentValues> contentValues = new ArrayList<>();
        for (InstalledRow row : installedRows) {
            row.installedTime = pluginConfig.storageDir.lastModified();
            row.UUID = pluginConfig.UUID;
            row.version = pluginConfig.UUID_NickName;
            contentValues.add(row.toContentValues());
        }
        installedPlugin.UUID = pluginConfig.UUID;
        installedPlugin.UUID_NickName = pluginConfig.UUID_NickName;
        return new Pair<>(installedPlugin, contentValues);
    }


}
