/*
 *           Copyright © 2015-2016, 2019, 2021 Stanislav Petriakov
 *  Distributed under the Boost Software License, Version 1.0.
 *     (See accompanying file LICENSE_1_0.txt or copy at
 *           http://www.boost.org/LICENSE_1_0.txt)
 */

package com.keenfin.easypickerdemo;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v7.app.AppCompatActivity;

import com.hypertrack.hyperlog.HyperLog;
import com.keenfin.easypicker.PhotoPicker;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements LoaderManager.LoaderCallbacks<Cursor> {
    private PhotoPicker mPhotoPickerStrip;
    private PhotoPicker mPhotoPickerGrid;
    private PhotoPicker mPhotoPickerGridNoControls;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mPhotoPickerStrip = findViewById(R.id.pp_easypicker_strip);
        mPhotoPickerGrid = findViewById(R.id.pp_easypicker_grid);
        mPhotoPickerGridNoControls = findViewById(R.id.pp_easypicker_grid_no_controls);

        int permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 0);
        } else {
            if (savedInstanceState == null)
                getSupportLoaderManager().initLoader(0, null, this);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case 0:
                if ((grantResults.length > 0) && (grantResults[0] == PackageManager.PERMISSION_GRANTED))
                    getSupportLoaderManager().initLoader(0, null, this);
                break;
            default:
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        mPhotoPickerStrip.onActivityResult(requestCode, resultCode, data);
        mPhotoPickerGrid.onActivityResult(requestCode, resultCode, data);
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        Uri sourceUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        return new CursorLoader(this, sourceUri, new String[]{MediaStore.MediaColumns.DATA}, null, null, MediaStore.Audio.Media.TITLE);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        if (data.moveToFirst()) {
            ArrayList<String> images = new ArrayList<>();

            int i = 0;
            do {
                images.add(data.getString(0));
                i++;
            } while (data.moveToNext() && i < 6);

            mPhotoPickerGridNoControls.restoreImages(images);
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {

    }

    @Override
    protected void onStop() {
        super.onStop();
        HyperLog.getDeviceLogsInFile(this);
    }
}
