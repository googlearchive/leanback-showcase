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

import android.arch.lifecycle.LifecycleRegistry;
import android.arch.lifecycle.LifecycleRegistryOwner;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v17.leanback.supportleanbackshowcase.R;
import android.support.v17.leanback.supportleanbackshowcase.app.room.adapter.ListAdapter;
import android.support.v17.leanback.supportleanbackshowcase.app.room.db.entity.VideoEntity;
import android.support.v17.leanback.supportleanbackshowcase.app.room.network.NetworkLiveData;
import android.support.v17.leanback.supportleanbackshowcase.app.room.ui.VideoCardPresenter;
import android.support.v17.leanback.supportleanbackshowcase.app.room.viewmodel.VideosViewModel;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.HeaderItem;
import android.support.v17.leanback.widget.ImageCardView;
import android.support.v17.leanback.widget.ListRow;
import android.support.v17.leanback.widget.ListRowPresenter;
import android.support.v17.leanback.widget.ObjectAdapter;
import android.support.v17.leanback.widget.OnItemViewClickedListener;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.Row;
import android.support.v17.leanback.widget.RowPresenter;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.content.res.ResourcesCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import java.util.Comparator;
import java.util.List;

public class SearchFragment extends android.support.v17.leanback.app.SearchSupportFragment
        implements android.support.v17.leanback.app.SearchSupportFragment.SearchResultProvider,
        LifecycleRegistryOwner {

    // For debugging
    private static final String TAG = "leanback.SearchFragment";
    private static final Boolean DEBUG = true;

    private ArrayObjectAdapter mRowsAdapter;

    // set up lifecycle registry
    private LifecycleRegistry lifecycleRegistry = new LifecycleRegistry(this);

    private ListAdapter<VideoEntity> mRelatedAdapter;
    private VideosViewModel mViewModel;

    @Override
    public LifecycleRegistry getLifecycle() {
        return lifecycleRegistry;
    }

    private final class ItemViewClickedListener implements OnItemViewClickedListener {
        @Override
        public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item,
                                  RowPresenter.ViewHolder rowViewHolder, Row row) {
            Intent intent;
            Long videoItemId = ((VideoEntity) item).getId();
            intent = new Intent(getActivity(), LiveDataDetailActivity.class);
            intent.putExtra(LiveDataDetailActivity.VIDEO_ID, videoItemId);

            /**
             * Put existing information into a bundle and passed it to next activity
             */
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

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Context context = getActivity();

        setBadgeDrawable(ResourcesCompat.getDrawable(context.getResources(),
                R.drawable.ic_add_row_circle_black_24dp, context.getTheme()));

        setTitle("Search Using Live Data");

        setSearchResultProvider(this);

        setOnItemViewClickedListener(new ItemViewClickedListener());

        /**
         * Set up the adapter which contains all the rows in current fragment page
         */
        mRowsAdapter = new ArrayObjectAdapter(new ListRowPresenter());
        mRowsAdapter.clear();
        mRelatedAdapter = new ListAdapter<>(new VideoCardPresenter());
        HeaderItem header = new HeaderItem(0, "results");
        mRowsAdapter.add(new ListRow(header, mRelatedAdapter));
    }


    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mViewModel = ViewModelProviders.of(this).get(VideosViewModel.class);
        subscribeUi(mViewModel);

        NetworkLiveData.sync(this.getActivity()).observe(this, new Observer<Boolean>() {
            @Override
            public void onChanged(@Nullable Boolean isNetworkAvailable) {
                if (isNetworkAvailable) {
                    getActivity().findViewById(R.id.no_internet_search).setVisibility(View.GONE);
                } else {
                    getActivity().findViewById(R.id.no_internet_search).setVisibility(View.VISIBLE);
                }
            }
        });
    }

    @Override
    public ObjectAdapter getResultsAdapter() {
        return mRowsAdapter;
    }

    @Override
    public boolean onQueryTextChange(String newQuery) {

        if (DEBUG) {
            Log.i(TAG, String.format("Search Query Text Change %s", newQuery));
        }

        if (!TextUtils.isEmpty(newQuery) && !newQuery.equals("nil")) {
            getActivity().findViewById(R.id.search_progressbar).setVisibility(View.VISIBLE);
            newQuery = "%" + newQuery + "%";
            mViewModel.setQueryMessage(newQuery);
        }
        return true;
    }

    @Override
    public boolean onQueryTextSubmit(String query) {

        if (DEBUG) {
            Log.i(TAG, String.format("Search Query Text Submit %s", query));
        }

        if (!TextUtils.isEmpty(query) && !query.equals("nil")) {
            getActivity().findViewById(R.id.search_progressbar).setVisibility(View.VISIBLE);
            query = "%" + query + "%";
            mViewModel.setQueryMessage(query);
        }
        return true;
    }

    private void subscribeUi(VideosViewModel viewModel) {
        viewModel.getSearchResult().observe(this, new Observer<List<VideoEntity>>() {
            @Override
            public void onChanged(@Nullable List<VideoEntity> videoEntities) {

                if (DEBUG) {
                    Log.e(TAG, "onChanged: " + videoEntities );
                }

                if (videoEntities != null && !videoEntities.isEmpty()) {
                    getActivity().findViewById(R.id.no_search_result).setVisibility(View.GONE);
                    getActivity().findViewById(R.id.search_progressbar).setVisibility(View.GONE);
                    mRelatedAdapter.setItems(videoEntities, new Comparator<VideoEntity>() {
                        @Override
                        public int compare(VideoEntity o1, VideoEntity o2) {
                            return o1.getId() == o2.getId() ? 0 : -1;
                        }
                    }, new Comparator<VideoEntity>() {
                        @Override
                        public int compare(VideoEntity o1, VideoEntity o2) {
                            return o1.equals(o2)? 0:-1;
                        }
                    });
                } else {
                    // When the search result is null (when data base has not been created) or
                    // empty, the text view field will be visible and telling user that no search
                    // result is available
                    getActivity().findViewById(R.id.no_search_result).setVisibility(View.VISIBLE);
                }
            }
        });
    }
}
