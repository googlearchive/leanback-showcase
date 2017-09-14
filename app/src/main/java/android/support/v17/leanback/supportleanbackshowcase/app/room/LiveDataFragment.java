/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.support.v17.leanback.supportleanbackshowcase.app.room;

import android.arch.lifecycle.LifecycleOwner;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v17.leanback.app.BackgroundManager;
import android.support.v17.leanback.app.BrowseSupportFragment;
import android.support.v17.leanback.supportleanbackshowcase.R;
import android.support.v17.leanback.supportleanbackshowcase.app.room.adapter.ListAdapter;
import android.support.v17.leanback.supportleanbackshowcase.app.room.config.AppConfiguration;
import android.support.v17.leanback.supportleanbackshowcase.app.room.db.entity.CategoryEntity;
import android.support.v17.leanback.supportleanbackshowcase.app.room.db.entity.VideoEntity;
import android.support.v17.leanback.supportleanbackshowcase.app.room.network.DownloadCompleteBroadcastReceiver;
import android.support.v17.leanback.supportleanbackshowcase.app.room.network.DownloadingTaskDescription;
import android.support.v17.leanback.supportleanbackshowcase.app.room.network.NetworkLiveData;
import android.support.v17.leanback.supportleanbackshowcase.app.room.ui.LiveDataRowPresenter;
import android.support.v17.leanback.supportleanbackshowcase.app.room.ui.VideoCardPresenter;
import android.support.v17.leanback.supportleanbackshowcase.app.room.viewmodel.VideosInSameCategoryViewModel;
import android.support.v17.leanback.supportleanbackshowcase.app.room.viewmodel.VideosViewModel;
import android.support.v17.leanback.widget.HeaderItem;
import android.support.v17.leanback.widget.ImageCardView;
import android.support.v17.leanback.widget.ListRow;
import android.support.v17.leanback.widget.ListRowPresenter;
import android.support.v17.leanback.widget.OnItemViewClickedListener;
import android.support.v17.leanback.widget.OnItemViewSelectedListener;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.Row;
import android.support.v17.leanback.widget.RowPresenter;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.app.FragmentActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class LiveDataFragment extends BrowseSupportFragment
        implements DownloadCompleteBroadcastReceiver.DownloadCompleteListener,
        LiveDataRowPresenter.DataLoadedListener {

    // For debugging purpose
    private static final Boolean DEBUG = false;
    private static final String TAG = "LiveDataFragment";

    // Resource category
    private static final String BACKGROUND = "background";
    private static final String CARD = "card";
    private static final String VIDEO = "video";
    private static final int BACKGROUND_UPDATE_DELAY = 300;

    // handler to load background image using specified delay
    private final Handler mHandler = new Handler();

    private FragmentActivity mFragmentActivity;
    private LifecycleOwner mLifecycleOwner;
    private Drawable mDefaultBackground;
    private BackgroundManager mBackgroundManager;
    private DisplayMetrics mMetrics;
    private RequestOptions mDefaultPlaceHolder;
    private Runnable mBackgroundRunnable;
    private ListAdapter<ListRow> mRowsAdapter;
    private VideoEntity mSelectedVideo;
    private VideosViewModel mViewModel;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // presenter for each row
        LiveDataRowPresenter rowPresenter = new LiveDataRowPresenter();

        // register the listener for start entrance transition notification
        rowPresenter.registerDataLoadedListener(this);


        // the adapter which contains all the rows
        mRowsAdapter = new ListAdapter<>(rowPresenter);
        setAdapter(mRowsAdapter);

        mBackgroundManager = BackgroundManager.getInstance(getActivity());

        // this is necessary
        mBackgroundManager.attach(getActivity().getWindow());

        mBackgroundRunnable = new Runnable() {
            @Override
            public void run() {
                loadAndSetBackgroundImage();
            }
        };
        mDefaultBackground = getResources().getDrawable(R.drawable.no_cache_no_internet, null);
        mDefaultPlaceHolder = new RequestOptions().
                placeholder(mDefaultBackground);
        mMetrics = new DisplayMetrics();

        // measure background to show background image
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(mMetrics);

        // set up listener
        setOnItemViewClickedListener(new VideoEntityClickedListener());
        setOnItemViewSelectedListener(new VideoEntitySelectedListener());
        setOnSearchClickedListener(new VideoItemSearchListener());


        // create lifecycle component
        mFragmentActivity = LiveDataFragment.this.getActivity();
        mLifecycleOwner = (LifecycleOwner) mFragmentActivity;

        // create the view model based on lifecycle event owner (attached activity)
        mViewModel = ViewModelProviders.of(getActivity()).get(VideosViewModel.class);

        // register broadcast receiver
        DownloadCompleteBroadcastReceiver.getInstance().registerListener(this);

        // tweak the ui
        setHeadersState(HEADERS_ENABLED);
        setHeadersTransitionOnBackEnabled(true);
        setTitle(getString(R.string.livedata));


        // enable transition
        prepareEntranceTransition();
    }


    /**
     * In this life cycle phase, we will subscribe the live data and update the ui according to
     * the change of live data
     *
     * @param savedInstanceState saved instance state
     */
    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        subscribeNetworkInfo();
        subscribeUi(mViewModel);
    }

    /**
     * Perform StartEntranceTransition when the item (row) is bound to the view holder
     */
    @Override
    public void onDataLoaded() {
        startEntranceTransition();
    }

    /**
     * Implement downloading completion listener
     */
    @Override
    public void onDownloadingCompleted(final DownloadingTaskDescription desc) {
        final VideoEntity videoEntity = desc.getVideo();
        switch (desc.getCategory()) {

            // based on the resource category, create different toast and update local storage
            // uri
            case VIDEO:
                new AsyncTask<Void, Void, Void>() {
                    @Override
                    protected Void doInBackground(Void... voids) {

                        if (AppConfiguration.IS_NETWORK_LATENCY_ENABLED) {
                            addLatency(3000L);
                        }
                        mViewModel.updateDatabase(videoEntity, VIDEO, desc.getStoragePath());
                        return null;
                    }

                    @Override
                    protected void onPostExecute(Void aVoid) {
                        super.onPostExecute(aVoid);
                        Toast.makeText(
                                getActivity().getApplicationContext(), "video " + videoEntity.getId() + " " +
                                        "downloaded",
                                Toast.LENGTH_SHORT).show();
                    }
                }.execute();
                break;

            case BACKGROUND:
                new AsyncTask<Void, Void, Void>() {
                    @Override
                    protected Void doInBackground(Void... voids) {

                        if (AppConfiguration.IS_NETWORK_LATENCY_ENABLED) {
                            addLatency(2000L);
                        }
                        mViewModel.updateDatabase(videoEntity, BACKGROUND, desc.getStoragePath());
                        return null;
                    }

                    @Override
                    protected void onPostExecute(Void aVoid) {
                        super.onPostExecute(aVoid);
                        Toast.makeText(
                                getActivity().getApplicationContext(), "background" + videoEntity.getId() + " " +
                                        "downloaded",
                                Toast.LENGTH_SHORT).show();
                    }
                }.execute();
                break;

            case CARD:
                new AsyncTask<Void, Void, Void>() {
                    @Override
                    protected Void doInBackground(Void... voids) {

                        if (AppConfiguration.IS_NETWORK_LATENCY_ENABLED) {
                            addLatency(1000L);
                        }
                        mViewModel.updateDatabase(videoEntity, CARD, desc.getStoragePath());
                        return null;
                    }

                    @Override
                    protected void onPostExecute(Void aVoid) {
                        super.onPostExecute(aVoid);
                        Toast.makeText(
                                getActivity().getApplicationContext(), "card " + videoEntity.getId() + " downloaded",
                                Toast.LENGTH_SHORT).show();
                    }
                }.execute();
                break;
        }
    }


    /**
     * Helper function to observe network status
     */
    private void subscribeNetworkInfo() {
        NetworkLiveData.sync(mFragmentActivity).observe(mLifecycleOwner, new Observer<Boolean>() {
            @Override
            public void onChanged(@Nullable Boolean aBoolean) {
                if (aBoolean) {
                    getActivity().findViewById(R.id.no_internet).setVisibility(View.GONE);

                    // TODO: an appropriate method to re-create the database
                } else {
                    getActivity().findViewById(R.id.no_internet).setVisibility(View.VISIBLE);
                }
            }
        });
    }

    /**
     * Helper function to categories
     *
     * @param viewModel view model
     */
    private void subscribeUi(final VideosViewModel viewModel) {
        viewModel.getAllCategories().observe(mLifecycleOwner,
                new Observer<List<CategoryEntity>>() {
                    @Override
                    public void onChanged(@Nullable List<CategoryEntity> categoryEntities) {
                        if (categoryEntities != null) {
                            List<ListRow> rows = new ArrayList<>();

                            // Prepare all the rows in current fragment
                            for (CategoryEntity categoryEntity : categoryEntities) {

                                // create current category row
                                ListRow row = new ListRow(new HeaderItem(categoryEntity.getCategoryName()),
                                        new ListAdapter<>(new VideoCardPresenter()));
                                rows.add(row);
                            }

                            // first comparator: same item comparator
                            // second comparator: same content comparator

                            // After the execution of setItems methods, live data presenter will
                            // bind the list row and create the live data accordingly
                            mRowsAdapter.setItems(rows, new Comparator<ListRow>() {
                                @Override
                                public int compare(ListRow o1, ListRow o2) {
                                    return o1.getId() == o2.getId() ? 0 : -1;
                                }
                            }, new Comparator<ListRow>() {
                                @Override
                                public int compare(ListRow o1, ListRow o2) {
                                    return o1.getHeaderItem().getName()
                                            .equals(o2.getHeaderItem().getName()) ? 0 : -1;
                                }
                            });
                        }
                    }
                });
    }

    private void startBackgroundTimer() {
        mHandler.removeCallbacks(mBackgroundRunnable);
        mHandler.postDelayed(mBackgroundRunnable, BACKGROUND_UPDATE_DELAY);
    }

    private void loadAndSetBackgroundImage() {
        if (mSelectedVideo == null) {
            return;
        }
        String url1 = mSelectedVideo.getVideoBgImageLocalStorageUrl();
        String url2 = mSelectedVideo.getBgImageUrl();
        String loadedUri;
        if (url1.isEmpty()) {
            loadedUri = url2;
        } else {
            loadedUri = url1;
        }

        // glide on error
        Glide.with(this)
                .asBitmap()
                .load(loadedUri)
                .apply(mDefaultPlaceHolder)
                .into(new SimpleTarget<Bitmap>(mMetrics.widthPixels, mMetrics.heightPixels) {
                    @Override
                    public void onResourceReady(Bitmap resource,
                                                Transition<? super Bitmap> glideAnimation) {
                        mBackgroundManager.setDrawable(
                                new BitmapDrawable(getResources(), resource));
                    }
                });

        mHandler.removeCallbacks(mBackgroundRunnable);
    }


    /**
     * Click listener
     */
    private final class VideoEntityClickedListener implements OnItemViewClickedListener {
        @Override
        public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item,
                                  RowPresenter.ViewHolder rowViewHolder, Row row) {
            Intent intent;
            Long videoItemId = ((VideoEntity) item).getId();
            intent = new Intent(getActivity(), LiveDataDetailActivity.class);
            intent.putExtra(LiveDataDetailActivity.VIDEO_ID, videoItemId);

            VideoEntity cachedBundle = (VideoEntity) item;

            intent.putExtra(LiveDataDetailActivity.CACHED_CONTENT, cachedBundle);

            // enable the scene transition animation when the detail's activity is launched
            Bundle bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(
                    getActivity(),
                    ((ImageCardView) itemViewHolder.view).getMainImageView(),
                    LiveDataDetailActivity.SHARED_ELEMENT_NAME).toBundle();
            getActivity().startActivity(intent, bundle);
        }
    }

    /**
     * Selected listener
     */
    private final class VideoEntitySelectedListener implements OnItemViewSelectedListener {
        @Override
        public void onItemSelected(Presenter.ViewHolder itemViewHolder, Object item,
                                   RowPresenter.ViewHolder rowViewHolder, Row row) {
            mSelectedVideo = (VideoEntity) item;
            startBackgroundTimer();
        }
    }

    /**
     * search listener
     */
    private final class VideoItemSearchListener implements View.OnClickListener {
        @Override
        public void onClick(View view) {
            Intent intent = new Intent(getActivity(), SearchActivity.class);
            startActivity(intent);
        }
    }

    private void addLatency(Long ms) {
        try {
            // add 1s latency for video downloading, when network latency option
            // is enabled.
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            if (DEBUG) {
                Log.e(TAG, "doInBackground: ", e);
            }
        }
    }
}
