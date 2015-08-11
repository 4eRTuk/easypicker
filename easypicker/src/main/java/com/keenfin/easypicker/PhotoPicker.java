/*
 *           Copyright © 2015 Stanislav Petriakov
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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class PhotoPicker extends RecyclerView {
    private int mMaxPhotos = -1;
    private int mRowHeight;
    private int mImagesPerRow, mImagesPerRowPortrait = Constants.IMAGES_PER_ROW_P, mImagesPerRowLandscape = Constants.IMAGES_PER_ROW_L;
    private String mNewPhotosDir = Constants.NEW_PHOTOS_SAVE_DIR;
    private int mColorPrimary, mColorAccent;
    private boolean mIsOneLine = false;

    private Context mContext;
    private PhotoAdapter mPhotoAdapter;

    public PhotoPicker(Context context) {
        super(context);
        init(context);
        mColorPrimary = mContext.getResources().getColor(R.color.primary);
        mColorAccent = mContext.getResources().getColor(R.color.accent);
    }

    public PhotoPicker(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public PhotoPicker(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context, attrs);
    }

    private void init(Context context) {
        mContext = context;

        PhotoAdapter adapter = new PhotoAdapter();
        setAdapter(adapter);

        mImagesPerRow = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE ? mImagesPerRowLandscape : mImagesPerRowPortrait;
        setHasFixedSize(true);

        RecyclerView.LayoutManager layoutManager;

        if (mIsOneLine)
            layoutManager = new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false);
        else
            layoutManager = new GridLayoutManager(context, mImagesPerRow);

        setLayoutManager(layoutManager);
    }

    private void init(Context context, AttributeSet attrs) {
        TypedArray styleable = context.obtainStyledAttributes(attrs, R.styleable.PhotoPicker, 0, 0);
        mImagesPerRowLandscape = styleable.getInt(R.styleable.PhotoPicker_photosPerRowLandscape, mImagesPerRowLandscape);
        mImagesPerRowPortrait = styleable.getInt(R.styleable.PhotoPicker_photosPerRowPortrait, mImagesPerRowPortrait);
        mIsOneLine = styleable.getBoolean(R.styleable.PhotoPicker_oneLineGallery, false);
        init(context);

        mMaxPhotos = styleable.getInt(R.styleable.PhotoPicker_maxPhotos, mMaxPhotos);
        mNewPhotosDir = styleable.getString(R.styleable.PhotoPicker_newPhotosDirectory);
        mNewPhotosDir = mNewPhotosDir == null ? Constants.NEW_PHOTOS_SAVE_DIR : mNewPhotosDir;
        mColorPrimary = styleable.getColor(R.styleable.PhotoPicker_primaryColor, mContext.getResources().getColor(R.color.primary));
        mColorAccent = styleable.getColor(R.styleable.PhotoPicker_accentColor, mContext.getResources().getColor(R.color.accent));
        styleable.recycle();
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        Bundle bundle = new Bundle();
        bundle.putParcelable("instanceState", super.onSaveInstanceState());
        bundle.putStringArrayList(Constants.BUNDLE_ATTACHED_IMAGES, mPhotoAdapter.getImagesPath());

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

            mPhotoAdapter.restoreImages(bundle.getStringArrayList(Constants.BUNDLE_ATTACHED_IMAGES));

            super.onRestoreInstanceState(bundle.getParcelable("instanceState"));
            return;
        }

        super.onRestoreInstanceState(state);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        super.onMeasure(widthSpec, heightSpec);

        mRowHeight = getMeasuredWidth() / mImagesPerRow;
        mPhotoAdapter.measureParent();
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        mPhotoAdapter.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void setAdapter(Adapter adapter) {
        if (!(adapter instanceof PhotoAdapter))
            throw new IllegalArgumentException("You should not pass adapter to PhotoPicker. It uses specific one.");

        mPhotoAdapter = (PhotoAdapter) adapter;
        super.setAdapter(adapter);
    }

    public void setMaxPhotos(int maxPhotos) {
        mMaxPhotos = maxPhotos;
    }

    public void setNewPhotosDirectory(String directoryName) {
        mNewPhotosDir = directoryName;
    }

    public ArrayList<String> getImagesPath() {
        return mPhotoAdapter.getImagesPath();
    }

    public static Bitmap getBitmap(String path, int requiredSize) throws OutOfMemoryError {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);
        int scale = 1;

        while (options.outWidth / scale > requiredSize && options.outHeight / scale > requiredSize)
            scale *= 2;

        options.inSampleSize = scale;
        options.inJustDecodeBounds = false;

        return BitmapFactory.decodeFile(path, options);
    }

    public class PhotoAdapter extends RecyclerView.Adapter<PhotoViewHolder> implements PhotoViewHolder.IViewHolderClick {
        private List<Bitmap> mImages;
        private List<String> mImagesPath;
        private Uri mPhotoUri;

        public PhotoAdapter() {
            mImages = new ArrayList<>();
            mImagesPath = new ArrayList<>();
            mImages.add(BitmapFactory.decodeResource(getResources(), R.drawable.ic_add_white_48dp));
            mImagesPath.add(null);
        }

        @Override
        public PhotoViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.photo_item, parent, false);
            view.setBackgroundColor(mColorAccent);
            return new PhotoViewHolder(view);
        }

        @Override
        public void onBindViewHolder(final PhotoViewHolder holder, final int position) {
            holder.setOnClickListener(this);
            holder.setPhoto(mImages.get(position));
            holder.adjustControl(mRowHeight, mColorPrimary, position == 0, mIsOneLine);
        }

        @Override
        public int getItemCount() {
            return mImages.size();
        }

        public ArrayList<String> getImagesPath() {
            ArrayList<String> images = new ArrayList<>();
            images.addAll(mImagesPath);
            images.remove(0);
            return images;
        }

        public void restoreImages(ArrayList<String> imagesPath) {
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
            int i = caller.getId();
            if (i == R.id.iv_photo) {
                if (position == 0) {
                    if (getItemCount() - 1 == mMaxPhotos) {
                        Toast.makeText(mContext, String.format(mContext.getString(R.string.max_photos), mMaxPhotos), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
                    builder.setTitle(R.string.photo_add);
                    builder.setItems(R.array.report_add_photos, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int item) {
                            Intent intent;
                            switch (item) {
                                case 0:
                                    intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                                    File photo = new File(Environment.getExternalStoragePublicDirectory(
                                            Environment.DIRECTORY_DCIM), mNewPhotosDir);

                                    if (!photo.mkdirs() && !photo.exists()) {
                                        Toast.makeText(mContext, R.string.hw_error, Toast.LENGTH_SHORT).show();
                                        return;
                                    }

                                    photo = new File(photo, System.currentTimeMillis() + ".jpg");
                                    mPhotoUri = Uri.fromFile(photo);
                                    intent.putExtra(MediaStore.EXTRA_OUTPUT, mPhotoUri);
                                    ((Activity) mContext).startActivityForResult(intent, Constants.CAMERA_REQUEST);
                                    break;
                                case 1:
                                    intent = new Intent(Intent.ACTION_GET_CONTENT);
                                    intent.setType("image/*");
                                    ((Activity) mContext).startActivityForResult(
                                            Intent.createChooser(intent, mContext.getString(R.string.photo_pick)), Constants.PICK_REQUEST);
                                    break;
                            }
                        }
                    });
                    builder.show();
                } else {
                    Intent preview = new Intent(getContext(), PreviewActivity.class);
                    preview.putExtra(Constants.BUNDLE_ATTACHED_IMAGES, getImagesPath());
                    preview.putExtra(Constants.BUNDLE_NEW_PHOTO_PATH, position - 1);
                    getContext().startActivity(preview);
                }
            } else if (i == R.id.ib_remove) {
                if (position == 0)
                    return;

                mImages.remove(position);
                mImagesPath.remove(position);
                notifyItemRemoved(position);
                measureParent();
            }
        }

        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            if (resultCode == Activity.RESULT_OK) {
                String selectedImagePath = null;

                switch (requestCode) {
                    case Constants.CAMERA_REQUEST:
                        selectedImagePath = mPhotoUri.getPath();
                        break;
                    case Constants.PICK_REQUEST:
                        selectedImagePath = FileUtil.getPath(mContext, data.getData());
                        break;
                }

                addImage(selectedImagePath);
            }
        }

        private boolean addImage(String imagePath) {
            Bitmap selectedImage = getBitmap(imagePath, Constants.REQUIRED_THUMBNAIL_SIZE);

            if (selectedImage == null) {
                Toast.makeText(mContext, mContext.getString(R.string.photo_fail_attach), Toast.LENGTH_SHORT).show();
                return false;
            }

            boolean result = mImages.add(selectedImage);
            result &= mImagesPath.add(imagePath);
            notifyItemInserted(mImages.size() - 1);
            measureParent();

            return result;
        }

        public void measureParent() {
            ViewGroup.LayoutParams params = getLayoutParams();
            int itemsCount = mIsOneLine ? 1 : mImages.size();
            params.height = (int) Math.ceil(1f * itemsCount / mImagesPerRow) * mRowHeight;
            setLayoutParams(params);
        }
    }
}
