/*
 *           Copyright © 2015-2017, 2019, 2021 Stanislav Petriakov
 *  Distributed under the Boost Software License, Version 1.0.
 *     (See accompanying file LICENSE_1_0.txt or copy at
 *           http://www.boost.org/LICENSE_1_0.txt)
 */

package com.keenfin.easypicker;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.hypertrack.hyperlog.HyperLog;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION;

public class PhotoPicker extends RecyclerView {
    private int mMaxPhotos = -1;
    private int mImagesPerRow, mImagesPerRowPortrait = Constants.IMAGES_PER_ROW_P, mImagesPerRowLandscape = Constants.IMAGES_PER_ROW_L;
    private String mNewPhotosDir = Constants.NEW_PHOTOS_SAVE_DIR;
    private int mColorPrimary, mColorAccent;
    private int mCameraRequest, mPickRequest;
    private final int mPermissionRequest = 936;
    private boolean mIsOneLine = false, mIsUsePreview = true, mDefaultPreview = false;
    private boolean mPrimaryColorDefined, mAccentColorDefined;
    boolean mIsNougat;

    private Context mContext;
    private PhotoAdapter mPhotoAdapter;
    private Drawable mNewPhotoIcon;

    public PhotoPicker(Context context) {
        this(context, false);
    }

    public PhotoPicker(Context context, boolean noControls) {
        super(context);
        mNewPhotoIcon = ContextCompat.getDrawable(context, R.drawable.ic_add_white_48dp);
        init(context, noControls);
    }

    public PhotoPicker(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public PhotoPicker(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context, attrs);
    }

    private void init(Context context, boolean noControls) {
        mIsNougat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N;
        mContext = context;
        mImagesPerRow = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE ? mImagesPerRowLandscape : mImagesPerRowPortrait;
        RecyclerView.LayoutManager layoutManager;

        if (mIsOneLine)
            layoutManager = new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false);
        else
            layoutManager = new GridLayoutManager(context, mImagesPerRow);

        setLayoutManager(layoutManager);

        int[] attrs = new int[]{R.attr.colorPrimary, R.attr.colorAccent};
        TypedArray styleable = context.obtainStyledAttributes(attrs);
        if (!mPrimaryColorDefined)
            mColorPrimary = getColor(styleable, 0, R.color.primary);
        if (!mAccentColorDefined)
            mColorAccent = getColor(styleable, 1, R.color.accent);
        styleable.recycle();

        PhotoAdapter adapter = new PhotoAdapter(noControls);
        setAdapter(adapter);
    }

    private void init(Context context, AttributeSet attrs) {
        TypedArray styleable = context.obtainStyledAttributes(attrs, R.styleable.PhotoPicker, 0, 0);
        mImagesPerRowLandscape = styleable.getInt(R.styleable.PhotoPicker_photosPerRowLandscape, mImagesPerRowLandscape);
        mImagesPerRowPortrait = styleable.getInt(R.styleable.PhotoPicker_photosPerRowPortrait, mImagesPerRowPortrait);
        mIsOneLine = styleable.getBoolean(R.styleable.PhotoPicker_oneLineGallery, false);
        int icon = styleable.getResourceId(R.styleable.PhotoPicker_newPhotosIcon, R.drawable.ic_add_white_48dp);
        mNewPhotoIcon = ContextCompat.getDrawable(context, icon);
        boolean noControls = styleable.getBoolean(R.styleable.PhotoPicker_noControls, false);
        init(context, noControls);

        if (mPrimaryColorDefined = styleable.hasValue(R.styleable.PhotoPicker_primaryColor))
            mColorPrimary = getColor(styleable, R.styleable.PhotoPicker_primaryColor, R.color.primary);
        if (mAccentColorDefined = styleable.hasValue(R.styleable.PhotoPicker_accentColor))
            mColorAccent = getColor(styleable, R.styleable.PhotoPicker_accentColor, R.color.accent);

        mIsUsePreview = styleable.getBoolean(R.styleable.PhotoPicker_usePreview, true);
        mDefaultPreview = styleable.getBoolean(R.styleable.PhotoPicker_previewDefault, false);
        mMaxPhotos = styleable.getInt(R.styleable.PhotoPicker_maxPhotos, mMaxPhotos);
        mNewPhotosDir = styleable.getString(R.styleable.PhotoPicker_newPhotosDirectory);
        mNewPhotosDir = mNewPhotosDir == null ? Constants.NEW_PHOTOS_SAVE_DIR : mNewPhotosDir;
        styleable.recycle();
    }

    private int getColor(TypedArray array, int index, int defValue) {
        return array.getColor(index, mContext.getResources().getColor(defValue));
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        Bundle bundle = new Bundle();
        bundle.putParcelable("instanceState", super.onSaveInstanceState());
        bundle.putStringArrayList(Constants.BUNDLE_ATTACHED_IMAGES, mPhotoAdapter.getImagesPath());
        bundle.putInt(Constants.BUNDLE_CAMERA_REQUEST, mCameraRequest);
        bundle.putInt(Constants.BUNDLE_PICK_REQUEST, mPickRequest);

        if (mPhotoAdapter.getPhotoUri() != null)
            bundle.putString(Constants.BUNDLE_NEW_PHOTO_PATH, mPhotoAdapter.getPhotoUri().getPath());

        return bundle;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (state instanceof Bundle) {
            Bundle bundle = (Bundle) state;

            if (bundle.containsKey(Constants.BUNDLE_NEW_PHOTO_PATH))
                mPhotoAdapter.setPhotoUri(Uri.parse(bundle.getString(Constants.BUNDLE_NEW_PHOTO_PATH)));

            List<String> images = bundle.getStringArrayList(Constants.BUNDLE_ATTACHED_IMAGES);
            if (images == null)
                images = new ArrayList<>();
            mPhotoAdapter.restoreImages(images);
            mCameraRequest = bundle.getInt(Constants.BUNDLE_CAMERA_REQUEST);
            mPickRequest = bundle.getInt(Constants.BUNDLE_PICK_REQUEST);

            super.onRestoreInstanceState(bundle.getParcelable("instanceState"));
            return;
        }

        super.onRestoreInstanceState(state);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        super.onMeasure(widthSpec, heightSpec);
        if (!isInEditMode())
            mPhotoAdapter.measureParent();
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        mPhotoAdapter.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void setAdapter(Adapter adapter) {
        if (!isInEditMode()) {
            if (!(adapter instanceof PhotoAdapter))
                throw new IllegalArgumentException("You should not pass adapter to PhotoPicker. It uses specific one.");

            mPhotoAdapter = (PhotoAdapter) adapter;
        }

        super.setAdapter(adapter);
    }

    public void restoreImages(List<String> imagesPath) {
        mPhotoAdapter.restoreImages(imagesPath);
    }

    public void setMaxPhotos(int maxPhotos) {
        mMaxPhotos = maxPhotos;
    }

    public void setNewPhotosDirectory(String directoryName) {
        mNewPhotosDir = directoryName;
    }

    public void setUsePreview(boolean usePreview) {
        mIsUsePreview = usePreview;
    }

    public void setDefaultPreview(boolean defaultPreview) {
        mDefaultPreview = defaultPreview;
    }

    public void setNewPhotosDrawable(int drawableResourceId) {
        mPhotoAdapter.replaceNewPhotoIcon(drawableResourceId);
    }

    public ArrayList<String> getImagesPath() {
        return mPhotoAdapter.getImagesPath();
    }

    public class PhotoAdapter extends RecyclerView.Adapter<PhotoViewHolder> implements PhotoViewHolder.IViewHolderClick {
        private List<String> mImagesPath;
        private Uri mPhotoUri;

        private boolean mNoControls;

        public PhotoAdapter() {
            this(false);
        }

        public PhotoAdapter(boolean noControls) {
            mImagesPath = new ArrayList<>();
            mNoControls = noControls;

            if (!noControls)
                addNewPhotoIcon();
        }

        private void addNewPhotoIcon() {
            mImagesPath.add(0, null);
            notifyItemInserted(0);
        }

        protected void replaceNewPhotoIcon(int drawableResourceId) {
            if (!mNoControls && mImagesPath.size() > 0) {
                mNewPhotoIcon = ContextCompat.getDrawable(mContext, drawableResourceId);
                notifyItemChanged(0);
            }
        }

        @NonNull
        @Override
        public PhotoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.photo_item, parent, false);
            view.setBackgroundColor(mColorAccent);
            return new PhotoViewHolder(view);
        }

        @Override
        public void onBindViewHolder(final PhotoViewHolder holder, final int position) {
            holder.setOnClickListener(this);

            boolean isControl = position == 0 && !mNoControls;
            if (isControl)
                holder.setIcon(mNewPhotoIcon);
            else
                holder.loadPhoto(mImagesPath.get(position), getMeasuredWidth() / mImagesPerRow);

            holder.adjustControl(getMeasuredWidth() / mImagesPerRow, mColorPrimary, isControl, mIsOneLine, mNoControls);
        }

        @Override
        public int getItemCount() {
            return mImagesPath.size();
        }

        public ArrayList<String> getImagesPath() {
            ArrayList<String> images = new ArrayList<>(mImagesPath);

            if (!mNoControls)
                images.remove(0);

            return images;
        }

        protected void restoreImages(List<String> imagesPath) {
            for (String imagePath : imagesPath)
                addImage(imagePath);
        }

        public Uri getPhotoUri() {
            return mPhotoUri;
        }

        public void setPhotoUri(Uri photoUri) {
            this.mPhotoUri = photoUri;
        }

        @Override
        public void onItemClick(View caller, int position) {
            onItemClick(caller.getId(), position);
        }

        @RequiresApi(api = Build.VERSION_CODES.R)
        private void askForPermission() {
            String message = mContext.getString(R.string.manage_all_files_message);
            AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
            builder.setTitle(R.string.manage_all_files)
                   .setMessage(message)
                   .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                       @Override
                       public void onClick(DialogInterface dialogInterface, int i) {
                           Intent permission = new Intent(ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                           ((Activity) mContext).startActivityForResult(permission, mPermissionRequest);
                       }
                   })
                   .setNegativeButton(android.R.string.cancel, null)
                   .show();
        }

        private void onItemClick(int id, int position) {
            if (id == R.id.iv_photo) {
                if (position == 0 && !mNoControls) {
                    if (mMaxPhotos > -1 && getItemCount() - 1 >= mMaxPhotos) {
                        Toast.makeText(mContext, String.format(mContext.getString(R.string.max_photos), mMaxPhotos), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        if (!Environment.isExternalStorageManager()) {
                            askForPermission();
                            return;
                        }
                    }

                    AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
                    builder.setTitle(R.string.photo_add);
                    builder.setItems(R.array.report_add_photos, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int item) {
                            Intent intent;
                            Random randomizeRequest = new Random(System.currentTimeMillis());
                            switch (item) {
                                case 0:
                                    intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                                    File photo = new File(Environment.getExternalStoragePublicDirectory(
                                            Environment.DIRECTORY_DCIM), mNewPhotosDir);

                                    if (!photo.mkdirs() && !photo.exists()) {
                                        photo = new File(mContext.getExternalFilesDir(Environment.DIRECTORY_PICTURES), mNewPhotosDir);
                                        if (!photo.mkdirs() && !photo.exists()) {
                                            photo = mContext.getDir(mNewPhotosDir, 0);
                                            if (!photo.mkdirs() && !photo.exists()) {
                                                Toast.makeText(mContext, R.string.hw_error, Toast.LENGTH_SHORT).show();
                                                return;
                                            }
                                        }
                                    }

                                    photo = new File(photo, System.currentTimeMillis() + ".jpg");
                                    mPhotoUri = Uri.fromFile(photo);
                                    Uri uri = mIsNougat ? FileProvider.getUriForFile(mContext, getUriProviderAuthority(getContext()), photo) : mPhotoUri;
                                    intent.putExtra(MediaStore.EXTRA_OUTPUT, uri);
                                    intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                                    mCameraRequest = randomizeRequest.nextInt(0xffff);
                                    ((Activity) mContext).startActivityForResult(intent, mCameraRequest);
                                    break;
                                case 1:
                                    intent = new Intent(Intent.ACTION_GET_CONTENT);
                                    intent.setType("image/*");
                                    mPickRequest = randomizeRequest.nextInt(0xffff);
                                    ((Activity) mContext).startActivityForResult(
                                            Intent.createChooser(intent, mContext.getString(R.string.photo_pick)), mPickRequest);
                                    break;
                            }
                        }
                    });
                    builder.show();
                } else {
                    if (!mIsUsePreview)
                        return;

                    ArrayList<String> imagesPath = getImagesPath();
                    int offset = position - (mNoControls ? 0 : 1);
                    Intent preview;

                    if (!mDefaultPreview) {
                        preview = new Intent(getContext(), PreviewActivity.class);
                        preview.putExtra(Constants.BUNDLE_ATTACHED_IMAGES, imagesPath);
                        preview.putExtra(Constants.BUNDLE_NEW_PHOTO_PATH, offset);
                    } else {
                        preview = new Intent(Intent.ACTION_VIEW);

                        if (mIsNougat) {
                            File path = new File(imagesPath.get(offset));
                            preview.setDataAndType(FileProvider.getUriForFile(mContext, getUriProviderAuthority(getContext()), path), "image/*");
                            preview.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        } else
                            preview.setDataAndType(Uri.parse("file://" + imagesPath.get(offset)), "image/*");
                    }

                    getContext().startActivity(preview);
                }
            } else if (id == R.id.ib_remove) {
                if (position <= 0)
                    return;

                mImagesPath.remove(position);
                notifyItemRemoved(position);
                measureParent();
            }
        }

        void onActivityResult(int requestCode, int resultCode, Intent data) {
            if (resultCode == Activity.RESULT_OK) {
                String selectedImagePath;

                if (requestCode == mCameraRequest) {
                    selectedImagePath = mPhotoUri.getPath();
                    HyperLog.v("PhotoPicker", "camera result: " + selectedImagePath);
                    MediaScannerConnection.scanFile(mContext, new String[]{mPhotoUri.getPath()}, null,
                                                    new MediaScannerConnection.OnScanCompletedListener() {
                                                        public void onScanCompleted(String path, Uri uri) {}
                                                    });
                } else if (requestCode == mPickRequest) {
                    selectedImagePath = FileUtil.getRealPath(mContext, data.getData());
                    HyperLog.v("PhotoPicker", "picker result: " + selectedImagePath);
                } else if (requestCode == mPermissionRequest) {
                    onItemClick(R.id.iv_photo, 0);
                    return;
                } else {return;}

                addImage(selectedImagePath);
            }
        }

        private boolean addImage(String imagePath) {
            if (imagePath == null) {
                Toast.makeText(mContext, mContext.getString(R.string.photo_fail_attach), Toast.LENGTH_SHORT).show();
                return false;
            }

            boolean result = mImagesPath.add(imagePath);
            notifyItemInserted(mImagesPath.size() - 1);
            measureParent();

            return result;
        }

        void measureParent() {
            ViewGroup.LayoutParams params = getLayoutParams();
            int itemsCount = mIsOneLine ? 1 : mImagesPath.size();
            params.height = (int) Math.ceil(1f * itemsCount / mImagesPerRow) * getMeasuredWidth() / mImagesPerRow;
            setLayoutParams(params);
        }
    }

    public static String getUriProviderAuthority(Context context) {
        return context.getPackageName() + ".easypicker.provider";
    }
}
