/*
 * Copyright (C) 2016 The Android Open Source Project
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
 *
 */

package android.support.v17.leanback.supportleanbackshowcase.app.media;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Message;
import android.support.v17.leanback.app.PlaybackControlSupportGlue;
import android.support.v17.leanback.app.PlaybackOverlaySupportFragment;
import android.support.v17.leanback.supportleanbackshowcase.R;
import android.support.v17.leanback.widget.Action;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.ControlButtonPresenterSelector;
import android.support.v17.leanback.widget.PlaybackControlsRow;
import android.support.v17.leanback.widget.PlaybackControlsRowPresenter;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.support.v4.app.FragmentActivity;

/**
 * This glue extends the {@link PlaybackControlGlue} with a {@link MediaPlayer} synchronization.
 * This glue layer provides common functionality for both music and video players (refer to
 * {@link MusicMediaPlayerGlue} and {@link VideoMediaPlayerGlue}).
 * It supports 7 actions: <ul> <li>{@link android.support.v17.leanback.widget.PlaybackControlsRow.FastForwardAction}</li>
 * <li>{@link android.support.v17.leanback.widget.PlaybackControlsRow.RewindAction}</li> <li>{@link
 * android.support.v17.leanback.widget.PlaybackControlsRow.PlayPauseAction}</li> <li>{@link
 * android.support.v17.leanback.widget.PlaybackControlsRow.ShuffleAction}</li> <li>{@link
 * android.support.v17.leanback.widget.PlaybackControlsRow.RepeatAction}</li> <li>{@link
 * android.support.v17.leanback.widget.PlaybackControlsRow.ThumbsDownAction}</li> <li>{@link
 * android.support.v17.leanback.widget.PlaybackControlsRow.ThumbsUpAction}</li> </ul>
 * <p/>
 */
public abstract class MediaPlayerGlue extends PlaybackControlSupportGlue {

    private static final String TAG = "MusicMediaPlayerGlue";
    protected final PlaybackControlsRow.ThumbsDownAction mThumbsDownAction;
    protected final PlaybackControlsRow.ThumbsUpAction mThumbsUpAction;
    private final Context mContext;
    protected FragmentActivity mActivity;
    private final PlaybackControlsRow.RepeatAction mRepeatAction;
    private final PlaybackControlsRow.ShuffleAction mShuffleAction;
    protected PlaybackControlsRow mControlsRow;
    private static final int REFRESH_PROGRESS = 1;
    private int mSpeed = PLAYBACK_SPEED_PAUSED;
    private long mLastTimeUpdate = 0;
    private int mLastPosition = 0;
    private static final int[] sFastForwardSpeeds = {5, 10, 20};
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case REFRESH_PROGRESS:
                    updateProgress();
                    updateCurrentPosition();
                    queueNextRefresh();
            }
        }
    };

    protected boolean mInitialized = false; // true when the MediaPlayer is prepared/initialized
    protected OnMediaStateChangeListener mMediaFileStateChangeListener;
    private long mLastKeyDownEvent = 0L; // timestamp when the last DPAD_CENTER KEY_DOWN occurred

    protected MediaMetaData mMediaMetaData = null;

    public MediaPlayerGlue(Context context, PlaybackOverlaySupportFragment fragment,
                           FragmentActivity activity) {
        super(context, fragment, sFastForwardSpeeds);
        mContext = context;
        mActivity = activity;
        // Instantiate secondary actions
        mShuffleAction = new PlaybackControlsRow.ShuffleAction(mContext);
        mRepeatAction = new PlaybackControlsRow.RepeatAction(mContext);
        mThumbsDownAction = new PlaybackControlsRow.ThumbsDownAction(mContext);
        mThumbsUpAction = new PlaybackControlsRow.ThumbsUpAction(mContext);
        mThumbsDownAction.setIndex(PlaybackControlsRow.ThumbsAction.OUTLINE);
        mThumbsUpAction.setIndex(PlaybackControlsRow.ThumbsAction.OUTLINE);
    }



    public void setOnMediaFileFinishedPlayingListener(OnMediaStateChangeListener listener) {
        mMediaFileStateChangeListener = listener;
    }

    /**
     * Override this method in case you need to add different secondary actions.
     *
     * @param secondaryActionsAdapter The adapter you need to add the {@link Action}s to.
     */
    protected void addSecondaryActions(ArrayObjectAdapter secondaryActionsAdapter) {
        secondaryActionsAdapter.add(mShuffleAction);
        secondaryActionsAdapter.add(mRepeatAction);
        secondaryActionsAdapter.add(mThumbsDownAction);
        secondaryActionsAdapter.add(mThumbsUpAction);
    }

    /**
     * Use this method to setup the {@link PlaybackControlsRowPresenter}. It'll be called
     * <u>after</u> the {@link PlaybackControlsRowPresenter} has been created and the primary and
     * secondary actions have been added.
     *
     * @param presenter The PlaybackControlsRowPresenter used to display the controls.
     */
    public void setupControlsRowPresenter(PlaybackControlsRowPresenter presenter) {
        // TODO: hahnr@ move into resources
        presenter.setProgressColor(getContext().getResources().getColor(
                R.color.player_progress_color));
        presenter.setBackgroundColor(getContext().getResources().getColor(
                R.color.player_background_color));
    }

    @Override public PlaybackControlsRowPresenter createControlsRowAndPresenter() {
        PlaybackControlsRowPresenter presenter = super.createControlsRowAndPresenter();
        mControlsRow = getControlsRow();

        // Add secondary actions and change the control row color.
        ArrayObjectAdapter secondaryActions = new ArrayObjectAdapter(
                new ControlButtonPresenterSelector());
        mControlsRow.setSecondaryActionsAdapter(secondaryActions);
        addSecondaryActions(secondaryActions);
        setupControlsRowPresenter(presenter);
        return presenter;
    }

    @Override public void enableProgressUpdating(final boolean enabled) {
        Log.d(TAG, "enableProgressUpdating: " + enabled);
        if (!enabled) {
            mHandler.removeMessages(REFRESH_PROGRESS);
            return;
        }
        queueNextRefresh();
    }

    private void queueNextRefresh() {
        Message refreshMsg = mHandler.obtainMessage(REFRESH_PROGRESS);
        mHandler.removeMessages(REFRESH_PROGRESS);
        mHandler.sendMessageDelayed(refreshMsg, getUpdatePeriod());
    }

    @Override public void onActionClicked(Action action) {
        // If either 'Shuffle' or 'Repeat' has been clicked we need to make sure the acitons index
        // is incremented and the UI updated such that we can display the new state.
        super.onActionClicked(action);
        if (action instanceof PlaybackControlsRow.ShuffleAction) {
            mShuffleAction.nextIndex();
        } else if (action instanceof PlaybackControlsRow.RepeatAction) {
            mRepeatAction.nextIndex();
        } else if (action instanceof PlaybackControlsRow.ThumbsUpAction) {
            if (mThumbsUpAction.getIndex() == PlaybackControlsRow.ThumbsAction.SOLID) {
                mThumbsUpAction.setIndex(PlaybackControlsRow.ThumbsAction.OUTLINE);
            } else {
                mThumbsUpAction.setIndex(PlaybackControlsRow.ThumbsAction.SOLID);
                mThumbsDownAction.setIndex(PlaybackControlsRow.ThumbsAction.OUTLINE);
            }
        } else if (action instanceof PlaybackControlsRow.ThumbsDownAction) {
            if (mThumbsDownAction.getIndex() == PlaybackControlsRow.ThumbsAction.SOLID) {
                mThumbsDownAction.setIndex(PlaybackControlsRow.ThumbsAction.OUTLINE);
            } else {
                mThumbsDownAction.setIndex(PlaybackControlsRow.ThumbsAction.SOLID);
                mThumbsUpAction.setIndex(PlaybackControlsRow.ThumbsAction.OUTLINE);
            }
        }
        onMetadataChanged();
    }

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        return super.onKey(v, keyCode, event);
    }

    public int updateCurrentPosition() {
        long currentTimeMillis = System.currentTimeMillis();
        int newPosition = getCurrentPosition();
        if (mSpeed >= PLAYBACK_SPEED_FAST_L0 || mSpeed <= -PLAYBACK_SPEED_FAST_L0) {
            // only update the current position if we are in fast-forward or rewind mode
            int speed = 0;
            if (mSpeed >= PLAYBACK_SPEED_FAST_L0) {
                speed = getFastForwardSpeeds()[mSpeed - PLAYBACK_SPEED_FAST_L0];
            } else if (mSpeed <= -PLAYBACK_SPEED_FAST_L0) {
                speed = -getRewindSpeeds()[-mSpeed - PLAYBACK_SPEED_FAST_L0];
            }
            newPosition = mLastPosition + (int) (currentTimeMillis - mLastTimeUpdate) * speed;
            if (newPosition < 0) {
                newPosition = 0;
            }
            if (newPosition > getMediaDuration()) {
                newPosition = getMediaDuration();
            }
            seekTo(newPosition);
            // We need to pause the playback to avoid the constant media attempt to play the video
            // while doing fast-forward or rewind. At the same time, we want to let the
            // PlaybackControlGlue know that the media is still playing and the progress bar needs
            // to be updated.
            int oldSpeed = mSpeed;
            pausePlayback();
            mSpeed = oldSpeed;
        }
        mLastTimeUpdate = currentTimeMillis;
        mLastPosition = newPosition;
        return 0;
    }

    @Override public boolean hasValidMedia() {
        return mMediaMetaData != null;
    }


    @Override public CharSequence getMediaTitle() {
        return hasValidMedia() ? mMediaMetaData.getMediaTitle() : "N/a";
    }

    @Override public CharSequence getMediaSubtitle() {
        return hasValidMedia() ? mMediaMetaData.getMediaArtistName() : "N/a";
    }

    @Override public Drawable getMediaArt() {
        return (hasValidMedia() && mMediaMetaData.getMediaAlbumArtResId() != 0) ?
                getContext().getResources().getDrawable(mMediaMetaData.getMediaAlbumArtResId())
                : null;
    }

    @Override public long getSupportedActions() {
        return PlaybackControlSupportGlue.ACTION_PLAY_PAUSE | PlaybackControlSupportGlue.ACTION_FAST_FORWARD
                | PlaybackControlSupportGlue.ACTION_REWIND;
    }

    @Override public int getCurrentSpeedId() {
        return mSpeed;
    }

    @Override protected void skipToNext() {
        // Not supported.
    }

    @Override protected void skipToPrevious() {
        // Not supported.
    }

    /**
     * Callback called after the current media item finishes playing.
     */
    void onMediaItemComplete() {
        // Depending on the app's need we can either reset or not reset the playing speed when
        // the current song finishes playing.
        mSpeed = PLAYBACK_SPEED_NORMAL;
        mLastPosition = 0;
        mLastTimeUpdate = System.currentTimeMillis();
    }

    /**
     * Callback called after the entire medialist finishes playing.
     */
    void onMediaListComplete() {
        onMediaItemComplete();
        mSpeed = PLAYBACK_SPEED_PAUSED;
    }

    protected abstract void seekTo(int newPosition);


    /**
     * Call to <code>startPlayback(1)</code>.
     *
     * @throws IllegalStateException See {@link MediaPlayer} for further information about it's
     * different states when setting a data source and preparing it to be played.
     */
    public void startPlayback() throws IllegalStateException {
        mSpeed = PLAYBACK_SPEED_NORMAL;
        startPlayback(1);
    }

    @Override
    protected void startPlayback(int speed) {
        mSpeed = speed;
        mLastPosition = getCurrentPosition();
        mLastTimeUpdate = System.currentTimeMillis();
    }

    @Override
    protected void pausePlayback() {
        mSpeed = PLAYBACK_SPEED_PAUSED;
    }

    /**
     * @return Returns <code>true</code> iff 'Shuffle' is <code>ON</code>.
     */
    public boolean useShuffle() {
        return mShuffleAction.getIndex() == PlaybackControlsRow.ShuffleAction.ON;
    }

    /**
     * @return Returns <code>true</code> iff 'Repeat-One' is <code>ON</code>.
     */
    public boolean repeatOne() {
        return mRepeatAction.getIndex() == PlaybackControlsRow.RepeatAction.ONE;
    }

    /**
     * @return Returns <code>true</code> iff 'Repeat-All' is <code>ON</code>.
     */
    public boolean repeatAll() {
        return mRepeatAction.getIndex() == PlaybackControlsRow.RepeatAction.ALL;
    }

    public MediaMetaData getMediaMetaData() {
        return mMediaMetaData;
    }

    public void setMediaMetaData(MediaMetaData mediaMetaData) {
        mMediaMetaData = mediaMetaData;
        onMetadataChanged();
    }

    @Override
    public int getUpdatePeriod() {
        int totalTime = getControlsRow().getTotalTime();
        Fragment fragment = getFragment();
        if (fragment.getView() == null || fragment.getView().getWidth() == 0 || totalTime <= 0) {
            return 1000;
        }
        return Math.max(16, totalTime / fragment.getView().getWidth());
    }

    /**
     * A listener which will be called whenever a media item's playback status changes.
     */
    public interface OnMediaStateChangeListener {
        /**
         * Called when a media item's playback state changes.
         * @param currentMediaMetaData {@link MediaMetaData} of the current media item
         * @param currentMediaState The current play state of the media item. For
         * {@link MusicMediaPlayerGlue}'s different play states, refer to {@link MediaUtils}
         */
        void onMediaStateChanged(MediaMetaData currentMediaMetaData, int currentMediaState);;
    }
}
