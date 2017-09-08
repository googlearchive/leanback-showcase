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
import android.support.annotation.Nullable;
import android.support.v17.leanback.app.BackgroundManager;
import android.support.v17.leanback.app.DetailsSupportFragment;
import android.support.v17.leanback.app.DetailsSupportFragmentBackgroundController;
import android.support.v17.leanback.media.MediaPlayerGlue;
import android.support.v17.leanback.supportleanbackshowcase.R;
import android.support.v17.leanback.supportleanbackshowcase.app.room.adapter.ListAdapter;
import android.support.v17.leanback.supportleanbackshowcase.app.room.config.AppConfiguration;
import android.support.v17.leanback.supportleanbackshowcase.app.room.db.entity.VideoEntity;
import android.support.v17.leanback.supportleanbackshowcase.app.room.network.NetworkLiveData;
import android.support.v17.leanback.supportleanbackshowcase.app.room.ui.DetailsDescriptionPresenter;
import android.support.v17.leanback.supportleanbackshowcase.app.room.ui.VideoCardPresenter;
import android.support.v17.leanback.supportleanbackshowcase.app.room.viewmodel.VideosViewModel;
import android.support.v17.leanback.widget.Action;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.ClassPresenterSelector;
import android.support.v17.leanback.widget.DetailsOverviewRow;
import android.support.v17.leanback.widget.FullWidthDetailsOverviewRowPresenter;
import android.support.v17.leanback.widget.FullWidthDetailsOverviewSharedElementHelper;
import android.support.v17.leanback.widget.HeaderItem;
import android.support.v17.leanback.widget.ImageCardView;
import android.support.v17.leanback.widget.ListRow;
import android.support.v17.leanback.widget.ListRowPresenter;
import android.support.v17.leanback.widget.OnActionClickedListener;
import android.support.v17.leanback.widget.OnItemViewClickedListener;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.Row;
import android.support.v17.leanback.widget.RowPresenter;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.res.ResourcesCompat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;

import java.util.Comparator;
import java.util.List;


public class LiveDataDetailViewWithVideoBackgroundFragment extends DetailsSupportFragment {

    // For debugging purpose.
    private static final Boolean DEBUG = true;
    private static final String TAG = "leanback.DetailsFrag";

    private static final int ACTION_PLAY = 1;
    private static final int ACTION_RENT = 2;
    private static final int ACTION_PREVIEW = 3;
    private static final int ACTION_LOADING = 4;

    // Resource category
    private static final String BACKGROUND = "background";
    private static final String CARD = "card";
    private static final String VIDEO = "video";
    private static final String TRAILER = "trailer";
    private static final String PRICE = "$4.54";
    private static final String RENT_ACTION = "Rent";
    private static final String LOADING_ACTION = "Loading";
    private static final String PREVIEW_ACTION = "Preview";
    private static final String PLAY_ACTION = "Play";
    private static final String RELATED_ROW = "Related Row";
    private static final String TRAILER_VIDEO = " (Trailer)";
    private static final String RENTED_VIDEO = " (Rented)";
    private static final String RENTED = "rented";

    private DetailsSupportFragmentBackgroundController mDetailsBackground;
    private Action mActionPlay;
    private Action mActionPreview;
    private Action mActionRent;
    private Action mActionLoading;
    private ArrayObjectAdapter mRowsAdapter;
    private VideoEntity mObservedVideo;

    // Default background, will be shown when there is no local content or the network is not
    // available
    private Drawable mDefaultBackground;
    private DetailsOverviewRow mDescriptionOverviewRow;
    private MediaPlayerGlue mVideoGlue;
    private ArrayObjectAdapter mActionAdapter;
    private long mSelectedVideoId;
    private BackgroundManager mBackgroundManager;
    private FullWidthDetailsOverviewSharedElementHelper mHelper;
    private DisplayMetrics mMetrics;
    private FullWidthDetailsOverviewRowPresenter mDorPresenter;
    private ListRow mRelatedRow;
    private HeaderItem mRelatedRowHeaderItem;
    private ListAdapter<VideoEntity> mRelatedRowAdapter;
    private RequestOptions mDefaultPlaceHolder;
    private FragmentActivity mFragmentActivity;
    private LifecycleOwner mLifecycleOwner;
    private VideosViewModel mViewModel;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mSelectedVideoId = getActivity().getIntent().getLongExtra(
                LiveDataDetailActivity.VIDEO_ID, -1L);

        // The observed video will always be updated to the video entity passed through
        // parcelable extra through the intent
        mObservedVideo = getActivity().getIntent().getParcelableExtra(
                LiveDataDetailActivity.CACHED_CONTENT);

        mActionPlay = new Action(ACTION_PLAY, PLAY_ACTION);
        mActionPreview = new Action(ACTION_PREVIEW, PREVIEW_ACTION);
        mActionLoading = new Action(ACTION_LOADING, LOADING_ACTION);
        mActionRent = new Action(ACTION_RENT, RENT_ACTION, PRICE, ResourcesCompat.getDrawable(
                getActivity().getResources(), R.drawable.ic_favorite_border_white_24dp,
                getActivity().getTheme()));
        mActionAdapter = new ArrayObjectAdapter();

        mVideoGlue = new MediaPlayerGlue(getActivity());

        // set up details background controller to attach video glue
        mDetailsBackground =
                new DetailsSupportFragmentBackgroundController(this);

        mDefaultBackground = getResources().getDrawable(R.drawable.no_cache_no_internet, null);
        mDefaultPlaceHolder = new RequestOptions().
                placeholder(mDefaultBackground);

        ClassPresenterSelector ps = new ClassPresenterSelector();
        mDorPresenter =
                new FullWidthDetailsOverviewRowPresenter(new DetailsDescriptionPresenter());
        mDorPresenter.setParticipatingEntranceTransition(true);
        ps.addClassPresenter(DetailsOverviewRow.class, mDorPresenter);
        ps.addClassPresenter(ListRow.class, new ListRowPresenter());

        mDescriptionOverviewRow = new DetailsOverviewRow(new Object());
        mDescriptionOverviewRow.setActionsAdapter(mActionAdapter);
        setSelectedPosition(0, false);

        mRelatedRowHeaderItem = new HeaderItem(RELATED_ROW);

        // simulate mDescriptionOverviewRow
        mRelatedRowAdapter = new ListAdapter<>(new VideoCardPresenter());
        mRelatedRow = new ListRow(mRelatedRowHeaderItem, mRelatedRowAdapter);

        mRowsAdapter = new ArrayObjectAdapter(ps);
        setAdapter(mRowsAdapter);
        mRowsAdapter.add(mDescriptionOverviewRow);
        mRowsAdapter.add(mRelatedRow);

        // required for setting background image
        mBackgroundManager = BackgroundManager.getInstance(getActivity());
        mBackgroundManager.attach(getActivity().getWindow());
        mMetrics = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(mMetrics);

        // Enable transition
        mHelper = new FullWidthDetailsOverviewSharedElementHelper();
        mHelper.setSharedElementEnterTransition(getActivity(),
                LiveDataDetailActivity.SHARED_ELEMENT_NAME);
        mDorPresenter.setListener(mHelper);
        mDorPresenter.setParticipatingEntranceTransition(false);

        mHelper.startPostponedEnterTransition();

        setOnItemViewClickedListener(new VideoItemViewClickedListener());
        mDorPresenter.setOnActionClickedListener(new ActionClickedListener());

        // Lifecycle related variables
        mFragmentActivity = this.getActivity();
        mLifecycleOwner = (LifecycleOwner) mFragmentActivity;
        mViewModel = ViewModelProviders.of(mFragmentActivity).get(VideosViewModel.class);
    }


    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mDetailsBackground.enableParallax();

        // First loading "cached" data to finish transition animation
        mActionAdapter.clear();
        mActionAdapter.add(mActionLoading);

        mDescriptionOverviewRow.setItem(mObservedVideo);
        loadAndSetBackgroundImage();
        loadAndSetVideoCardImage();

        mViewModel = ViewModelProviders.of(mFragmentActivity).get(VideosViewModel.class);
        subscribeToModel(mViewModel);
        subscribeToNetworkLiveData();
    }

    private void subscribeToModel(final VideosViewModel model) {

        // reactively fetching data through updating the mutable video id
        model.setVideoId(mSelectedVideoId);

        model.getVideoById().observe(mLifecycleOwner, new Observer<VideoEntity>() {
            @Override
            public void onChanged(@Nullable VideoEntity videoEntity) {
                if (videoEntity != null) {

                    mObservedVideo = videoEntity;
                    mDetailsBackground.setupVideoPlayback(mVideoGlue);

                    // different loading strategy based on whether the video is rented or not
                    if (!videoEntity.isRented()) {
                        mActionAdapter.clear();
                        mActionAdapter.add(mActionRent);
                        mActionAdapter.add(mActionPreview);

                        mVideoGlue.setTitle(mObservedVideo.getTitle().concat(TRAILER_VIDEO));
                        mVideoGlue.setVideoUrl(findLocalContentUriOrNetworkUrl(TRAILER));
                    } else {

                        // Once the video is rented, always remove the spinner and text view in
                        // center screen
                        getActivity().findViewById(R.id.renting_progressbar).setVisibility(View.GONE);
                        getActivity().findViewById(R.id.loading_renting).setVisibility(View.GONE);

                        mActionAdapter.clear();
                        mActionAdapter.add(mActionPlay);

                        mVideoGlue.setTitle(mObservedVideo.getTitle().concat(RENTED_VIDEO));
                        mVideoGlue.setVideoUrl(findLocalContentUriOrNetworkUrl(VIDEO));
                    }

                    mDescriptionOverviewRow.setItem(mObservedVideo);
                    loadAndSetVideoCardImage();

                    if (DEBUG) {
                        Log.e(TAG, "Tracing Function: " + "setCategory" );
                    }
                    model.setCategory(mObservedVideo.getCategory());
                }
            }
        });

        /**
         * begin to load videos in same category
         */
        model.getVideosInSameCategory().observe(mLifecycleOwner, new Observer<List<VideoEntity>>() {
            @Override
            public void onChanged(@Nullable List<VideoEntity> videoEntities) {
                if (videoEntities != null) {

                    if (DEBUG) {
                        Log.e(TAG, "Tracing Function: " + "Related Row Data Source Change" );
                    }

                    /**
                     * The diff util will compare two lists and dispatch the difference to correct
                     * position
                     */
                    mRelatedRowAdapter.setItems(videoEntities, new Comparator<VideoEntity>() {
                        @Override
                        public int compare(VideoEntity o1, VideoEntity o2) {
                            return o1.getId() == o2.getId() ? 0 : -1;
                        }
                    }, new Comparator<VideoEntity>() {
                        @Override
                        public int compare(VideoEntity o1, VideoEntity o2) {
                            return o1.equals(o2) ? 0 : -1;
                        }
                    });
                }
            }
        });
    }

    /**
     * Subscribe network live data and react with the change of network's status
     */
    private void subscribeToNetworkLiveData() {
        NetworkLiveData.sync(mFragmentActivity).observe(mLifecycleOwner, new Observer<Boolean>() {
            @Override
            public void onChanged(@Nullable Boolean aBoolean) {
                if (aBoolean) {
                    getActivity().findViewById(R.id.no_internet_detail).setVisibility(View.GONE);
                } else {
                    getActivity().findViewById(R.id.no_internet_detail).setVisibility(View.VISIBLE);
                }
            }
        });
    }

    private void loadAndSetBackgroundImage() {
        String loadedUri = findLocalContentUriOrNetworkUrl(BACKGROUND);
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
    }

    // Action Click Listener
    private final class ActionClickedListener implements OnActionClickedListener {
        @Override
        public void onActionClicked(Action action) {
            if (action.getId() == ACTION_RENT) {

                new AsyncTask<Void, Void,Void>() {
                    @Override
                    protected Void doInBackground(Void... voids) {
                        if (AppConfiguration.IS_RENTING_OPERATION_DELAY_ENABLED) {
                            addDelay(2000L);
                        }

                        // update the database with rented field
                        mViewModel.updateDatabase(mObservedVideo, RENTED, "");
                        return null;
                    }
                }.execute();

                // when user click rent action, there will be a spinner show up indicating the
                // transaction is being processed.
                // Also there will be a text view in the center of the screen indicating the status

                // The processing logic for renting is, it will update the database immediately
                // for isRented field, then the UI need 2 seconds (programmatically added latency
                // in mediator LiveData) to fetch the updated results (which contain the isRented)
                // information
                getActivity().findViewById(R.id.renting_progressbar).setVisibility(View.VISIBLE);
                getActivity().findViewById(R.id.loading_renting).setVisibility(View.VISIBLE);
            } else if (action.getId() == ACTION_PLAY) {
                mDetailsBackground.switchToVideo();
            } else if (action.getId() == ACTION_PREVIEW) {
                mDetailsBackground.switchToVideo();
            } else if (action.getId() == ACTION_LOADING) {
                Toast.makeText(getActivity(), "Loading...", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Click listener when the item in the related row is clicked
     */
    private final class VideoItemViewClickedListener implements OnItemViewClickedListener {
        @Override
        public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item,
                                  RowPresenter.ViewHolder rowViewHolder, Row row) {

            if (item instanceof VideoEntity) {

                Intent intent;
                Long videoItemId = ((VideoEntity) item).getId();
                intent = new Intent(getActivity(), LiveDataDetailActivity.class);
                intent.putExtra(LiveDataDetailActivity.VIDEO_ID, videoItemId);

                VideoEntity cacheBundle = (VideoEntity) item;
                cacheBundle.setBgImageUrl(cacheBundle.getCardImageUrl());
                cacheBundle.setVideoBgImageLocalStorageUrl(
                        cacheBundle.getVideoCardImageLocalStorageUrl());

                intent.putExtra(LiveDataDetailActivity.CACHED_CONTENT, cacheBundle);
                Bundle bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(
                        getActivity(),
                        ((ImageCardView) itemViewHolder.view).getMainImageView(),
                        LiveDataDetailActivity.SHARED_ELEMENT_NAME).toBundle();
                getActivity().startActivity(intent, bundle);
            }
        }
    }


    /**
     * Similar to loadAndSetBackgroundImage() function, to make sure the background image
     * can always be loaded correctly, this function is registered in onStart() phase.
     */
    private void loadAndSetVideoCardImage() {
        String loadedUri = findLocalContentUriOrNetworkUrl(CARD);
        Glide.with(this)
                .asBitmap()
                .load(loadedUri)
                .apply(mDefaultPlaceHolder)
                .into(new SimpleTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(final Bitmap resource,
                                                Transition<? super Bitmap> glideAnimation) {
                        mDescriptionOverviewRow.setImageBitmap(getActivity(), resource);
                    }
                });
        setSelectedPosition(0, false);
    }

    /**
     * When image's local content is existed, this function will return the uri of the local
     * content, or it will return the url of the according resource.
     */
    private String findLocalContentUriOrNetworkUrl(String type) {
        String loadedUri;
        switch (type) {
            case BACKGROUND:
                if (!mObservedVideo.getVideoBgImageLocalStorageUrl().isEmpty()) {
                    loadedUri = mObservedVideo.getVideoBgImageLocalStorageUrl();
                } else {
                    loadedUri = mObservedVideo.getBgImageUrl();
                }
                break;
            case CARD:
                if (!mObservedVideo.getVideoCardImageLocalStorageUrl().isEmpty()) {
                    loadedUri = mObservedVideo.getVideoCardImageLocalStorageUrl();
                } else {
                    loadedUri = mObservedVideo.getCardImageUrl();
                }
                break;
            case VIDEO:
                if (!mObservedVideo.getVideoLocalStorageUrl().isEmpty()) {
                    loadedUri = mObservedVideo.getVideoLocalStorageUrl();
                } else {
                    loadedUri = mObservedVideo.getVideoUrl();
                }
                break;
            case TRAILER:
                loadedUri = mObservedVideo.getTrailerVideoUrl();
                break;
            default:
                if (DEBUG) {
                    Log.d(TAG, "Not valid image resource type");
                }
                return "";
        }
        return loadedUri;
    }

    private void addDelay(long ms) {
        try {
            Thread.sleep(ms);
        } catch(InterruptedException e) {
            if (DEBUG) {
                Log.e(TAG, "addDelay: ",e );
            }
        }
    }
}
