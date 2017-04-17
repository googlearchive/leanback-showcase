/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package android.support.v17.leanback.supportleanbackshowcase.app.media;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.v17.leanback.app.PlaybackOverlayFragment;
import android.support.v17.leanback.supportleanbackshowcase.utils.Constants;
import android.support.v17.leanback.supportleanbackshowcase.R;
import android.support.v17.leanback.supportleanbackshowcase.utils.Utils;
import android.support.v17.leanback.supportleanbackshowcase.models.Song;
import android.support.v17.leanback.supportleanbackshowcase.models.SongList;
import android.support.v17.leanback.widget.*;
import android.support.v17.leanback.widget.AbstractMediaItemPresenter;
import android.util.Log;
import android.widget.TextView;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;

/**
 * This example shows how to play music files and build a simple track list.
 */
public class MusicConsumptionExampleFragment extends PlaybackOverlayFragment implements
        BaseOnItemViewClickedListener, MediaPlayerGlue.OnMediaStateChangeListener {

    private static final String TAG = "MusicConsumptionExampleFragment";
    private static final int PLAYLIST_ACTION_ID = 0;
    private static final int FAVORITE_ACTION_ID = 1;
    private ArrayObjectAdapter mRowsAdapter;
    private MusicMediaPlayerGlue mGlue;
    private int mCurrentSongIndex = 0;
    private Uri mCurrentMediaUri;
    private List<Song> mSongList;
    private boolean mAdapterNotified = false;
    private static TextView firstRowView;
    boolean onStopCalled = false;
    int stopCount = 1;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Constants.LOCAL_LOGD) Log.d(TAG, "onCreate");

        mGlue = new MusicMediaPlayerGlue(getActivity(), this) {

            @Override protected void onRowChanged(PlaybackControlsRow row) {
                if (mRowsAdapter == null || mAdapterNotified) return;
                //mAdapterNotified = true;
                mRowsAdapter.notifyArrayItemRangeChanged(0, 1);
            }
        };
        mGlue.setOnMediaFileFinishedPlayingListener(this);

        String json = Utils.inputStreamToString(
                getResources().openRawResource(R.raw.music_consumption_example));


        mSongList = new Gson().fromJson(json, SongList.class).getSongs();

        Resources res = getActivity().getResources();

        // For each song add a playlist and favorite actions.
        for(Song song : mSongList) {
            MultiActionsProvider.MultiAction[] mediaRowActions = new
                    MultiActionsProvider.MultiAction[2];
            MultiActionsProvider.MultiAction playlistAction = new
                    MultiActionsProvider.MultiAction(PLAYLIST_ACTION_ID);
            Drawable[] playlistActionDrawables = new Drawable[] {
                    res.getDrawable(R.drawable.ic_playlist_add_white_24dp,
                            getActivity().getTheme()),
                    res.getDrawable(R.drawable.ic_playlist_add_filled_24dp,
                            getActivity().getTheme())};
            playlistAction.setDrawables(playlistActionDrawables);
            mediaRowActions[0] = playlistAction;

            MultiActionsProvider.MultiAction favoriteAction = new
                    MultiActionsProvider.MultiAction(FAVORITE_ACTION_ID);
            Drawable[] favoriteActionDrawables = new Drawable[] {
                    res.getDrawable(R.drawable.ic_favorite_border_white_24dp,
                            getActivity().getTheme()),
                    res.getDrawable(R.drawable.ic_favorite_filled_24dp,
                            getActivity().getTheme())};
            favoriteAction.setDrawables(favoriteActionDrawables);
            mediaRowActions[1] = favoriteAction;
            song.setMediaRowActions(mediaRowActions);
        }

        List<MediaMetaData> songMetaDataList = new ArrayList<>();
        List<Uri> songUriList = new ArrayList<>();
        for (Song song : mSongList) {
            MediaMetaData metaData = createMetaDataFromSong(song);
            songMetaDataList.add(metaData);
        }
        mGlue.setMediaMetaData(songMetaDataList.get(0));
        mGlue.setMediaMetaDataList(songMetaDataList);
        addPlaybackControlsRow();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mGlue.close();
    }

    public void onPause() {
        super.onPause();
        Log.d("MusicService", "onPause called.");

    }

    @Override
    public void onStart() {
        super.onStart();
        Log.d("MusicService", "onStart called.");
        mGlue.openServiceCallback();
    }

    @Override
    public void onStop() {
        Log.d("MusicService", "onStop called.");
        super.onStop();
        mGlue.enableProgressUpdating(false);
        mGlue.releaseServiceCallback();
    }

    static class SongPresenter extends AbstractMediaItemPresenter {

        SongPresenter() {
            super();
        }

        SongPresenter(Context context, int themeResId) {
            super(themeResId);
            setHasMediaRowSeparator(true);
        }

        @Override
        protected void onBindMediaDetails(ViewHolder vh, Object item) {

            int favoriteTextColor =  vh.view.getContext().getResources().getColor(
                    R.color.song_row_favorite_color);
            Song song = (Song) item;
            if (song.getNumber() == 1 && firstRowView == null) {
                firstRowView = vh.getMediaItemNameView();
            }
            vh.getMediaItemNumberView().setText("" + song.getNumber());

            String songTitle = song.getTitle() + " / " + song.getDescription();
            vh.getMediaItemNameView().setText(songTitle);

            vh.getMediaItemDurationView().setText("" + song.getDuration());

            if (song.isFavorite()) {
                vh.getMediaItemNumberView().setTextColor(favoriteTextColor);
                vh.getMediaItemNameView().setTextColor(favoriteTextColor);
                vh.getMediaItemDurationView().setTextColor(favoriteTextColor);
            } else {
                Context context = vh.getMediaItemNumberView().getContext();
                vh.getMediaItemNumberView().setTextAppearance(context,
                        R.style.TextAppearance_Leanback_PlaybackMediaItemNumber);
                vh.getMediaItemNameView().setTextAppearance(context,
                        R.style.TextAppearance_Leanback_PlaybackMediaItemName);
                vh.getMediaItemDurationView().setTextAppearance(context,
                        R.style.TextAppearance_Leanback_PlaybackMediaItemDuration);
            }
        }
    };

    static class SongPresenterSelector extends PresenterSelector {
        Presenter mRegularPresenter;
        Presenter mFavoritePresenter;

        /**
         * Adds a presenter to be used for the given class.
         */
        public SongPresenterSelector setSongPresenterRegular(Presenter presenter) {
            mRegularPresenter = presenter;
            return this;
        }

        /**
         * Adds a presenter to be used for the given class.
         */
        public SongPresenterSelector setSongPresenterFavorite(Presenter presenter) {
            mFavoritePresenter = presenter;
            return this;
        }

        @Override
        public Presenter[] getPresenters() {
            return new Presenter[]{mRegularPresenter, mFavoritePresenter};
        }

        @Override
        public Presenter getPresenter(Object item) {
            return ( (Song) item).isFavorite() ? mFavoritePresenter : mRegularPresenter;
        }

    }

    static class TrackListHeaderPresenter extends AbstractMediaListHeaderPresenter {

        TrackListHeaderPresenter() {
            super();
        }

        @Override
        protected void onBindMediaListHeaderViewHolder(ViewHolder vh, Object item) {
            vh.getHeaderView().setText("Tracklist");
        }
    };

    private void addPlaybackControlsRow() {
        mRowsAdapter = new ArrayObjectAdapter(new ClassPresenterSelector()
                .addClassPresenterSelector(Song.class, new SongPresenterSelector()
                        .setSongPresenterRegular(new SongPresenter(getActivity(),
                                R.style.Theme_Example_LeanbackMusic_RegularSongNumbers))
                        .setSongPresenterFavorite(new SongPresenter(getActivity(),
                                R.style.Theme_Example_LeanbackMusic_FavoriteSongNumbers)))
                .addClassPresenter(TrackListHeader.class, new TrackListHeaderPresenter())
                .addClassPresenter(PlaybackControlsRow.class,
                        mGlue.createControlsRowAndPresenter()));
        mRowsAdapter.add(mGlue.getControlsRow());
        mRowsAdapter.add(new TrackListHeader());
        mRowsAdapter.addAll(2, mSongList);
        setAdapter(mRowsAdapter);
        setOnItemViewClickedListener(this);
    }

    public MusicConsumptionExampleFragment() {
        super();
    }



    @Override public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item,
                                        RowPresenter.ViewHolder rowViewHolder, Object row) {

        if (item instanceof  Action) {
            // if the clicked item is a primary or secondary action in the playback controller
            mGlue.onActionClicked((Action) item);
        } else if (row instanceof  Song) {
            // if a media item row is clicked
            Song clickedSong = (Song) row;
            AbstractMediaItemPresenter.ViewHolder songRowVh =
                    (AbstractMediaItemPresenter.ViewHolder) rowViewHolder;

            // if an action within a media item row is clicked
            if (item instanceof MultiActionsProvider.MultiAction) {
                if ( ((MultiActionsProvider.MultiAction) item).getId() == FAVORITE_ACTION_ID) {
                    MultiActionsProvider.MultiAction favoriteAction =
                            (MultiActionsProvider.MultiAction) item;
                    MultiActionsProvider.MultiAction playlistAction =
                            songRowVh.getMediaItemRowActions()[0];
                    favoriteAction.incrementIndex();
                    playlistAction.incrementIndex();;

                    clickedSong.setFavorite(!clickedSong.isFavorite());
                    songRowVh.notifyDetailsChanged();
                    songRowVh.notifyActionChanged(playlistAction);
                    songRowVh.notifyActionChanged(favoriteAction);
                }
            } else if (item == null){
                // if a media item details is clicked, start playing that media item
                onSongDetailsClicked(clickedSong);
            }

        }
    }

    public void onSongDetailsClicked(Song song) {
        int nextSongIndex = mSongList.indexOf(song);
        mCurrentSongIndex = nextSongIndex;
        startPlayback();
    }

    @Override
    public void onMediaStateChanged(MediaMetaData currentMediaMetaData, int currentMediaState) {
        Uri currentMediaUri = currentMediaMetaData.getMediaSourceUri();
        if (mCurrentMediaUri == null || !mCurrentMediaUri.equals(currentMediaUri)) {
            mCurrentSongIndex = findSongIndex(currentMediaUri);
            mCurrentMediaUri = currentMediaUri;
            if (mCurrentSongIndex == -1) {
                throw new IllegalArgumentException("currentMediaUri not found in the song list!");
            }
            Song song = mSongList.get(mCurrentSongIndex);
            MediaMetaData metaData = createMetaDataFromSong(song);
            mGlue.setMediaMetaData(metaData);
        }
    }

    private int findSongIndex(Uri currentMediaUri) {
        for(int i = 0; i < mSongList.size(); i++) {
            Uri uri = Utils.getResourceUri(getActivity(),
                    mSongList.get(i).getFileResource(getActivity()));
            if (uri.equals(currentMediaUri)) {
                return i;
            }
        }
        return -1;
    }

    private void startPlayback() {
        Song song = mSongList.get(mCurrentSongIndex);
        MediaMetaData mediaMetaData = createMetaDataFromSong(song);
        mGlue.prepareAndPlay(mediaMetaData);
    }

    private MediaMetaData createMetaDataFromSong(Song song) {
        MediaMetaData mediaMetaData = new MediaMetaData();
        mediaMetaData.setMediaTitle(song.getTitle());
        mediaMetaData.setMediaArtistName(song.getDescription());
        Uri uri = Utils.getResourceUri(getActivity(), song.getFileResource(getActivity()));
        mediaMetaData.setMediaSourceUri(uri);
        mediaMetaData.setMediaAlbumArtResId(song.getImageResource(getActivity()));
        return mediaMetaData;
    }
}
