/*
 *           Copyright © 2015 Stanislav Petriakov
 *  Distributed under the Boost Software License, Version 1.0.
 *     (See accompanying file LICENSE_1_0.txt or copy at
 *           http://www.boost.org/LICENSE_1_0.txt)
 */

package com.keenfin.easypicker;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.util.ArrayList;
import java.util.List;

public class PreviewActivity extends AppCompatActivity {
    private List<String> mImages;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preview);

        Bundle bundle = getIntent().getExtras();

        int position = 0;
        if (bundle != null && bundle.containsKey(Constants.BUNDLE_ATTACHED_IMAGES)) {
            mImages = bundle.getStringArrayList(Constants.BUNDLE_ATTACHED_IMAGES);
            position = bundle.getInt(Constants.BUNDLE_NEW_PHOTO_PATH, 0);
        }

        if (mImages == null)
            mImages = new ArrayList<>();

        PreviewAdapter adapter = new PreviewAdapter(getSupportFragmentManager(), mImages);
        ViewPager pager = (ViewPager) findViewById(R.id.vp_photos);
        pager.setAdapter(adapter);
        pager.setCurrentItem(position);
    }

    private static class PreviewAdapter extends FragmentPagerAdapter {
        private List<String> mImages;

        public PreviewAdapter(FragmentManager fm, List<String> images) {
            super(fm);
            mImages = images;
        }

        @Override
        public int getCount() {
            return mImages.size();
        }

        @Override
        public PreviewFragment getItem(int position) {
            PreviewFragment fragment = new PreviewFragment();
            Bundle bundle = new Bundle();
            bundle.putString(Constants.BUNDLE_ATTACHED_IMAGES, mImages.get(position));
            fragment.setArguments(bundle);
            return fragment;
        }
    }

    public static class PreviewFragment extends Fragment {
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            ImageView view = (ImageView) inflater.inflate(R.layout.preview_item, container, false);
            int maxSide = Math.max(getActivity().getWindowManager().getDefaultDisplay().getWidth(),
                    getActivity().getWindowManager().getDefaultDisplay().getHeight());
            String imagePath = null;

            if (getArguments() != null)
                imagePath = getArguments().getString(Constants.BUNDLE_ATTACHED_IMAGES);

            view.setImageBitmap(PhotoPicker.getBitmap(imagePath, maxSide));

            return view;
        }
    }
}
