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
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v17.leanback.app.PlaybackOverlayFragment;
import android.support.v17.leanback.supportleanbackshowcase.R;
import android.support.v17.leanback.widget.Action;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.ControlButtonPresenterSelector;
import android.support.v17.leanback.widget.OnItemViewSelectedListener;
import android.support.v17.leanback.widget.PlaybackControlsRow;
import android.support.v17.leanback.widget.PlaybackControlsRowPresenter;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.Row;
import android.support.v17.leanback.widget.RowPresenter;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**<p>>
 * This glue extends the {@link MediaPlayerGlue} and handles all the heavy-lifting of the
 * interactions between the fragment, playback controls, and the music service. It starts and
 * connects to a music service which will be running in the background. The music service notifies
 * the listeners set in this glue upon any playback status changes, and this glue will in turn
 * notify listeners passed from the fragment.
 * <p/>
 */
public abstract class MusicMediaPlayerGlue extends MediaPlayerGlue implements
        MusicPlaybackService.ServiceCallback {

    public static final int FAST_FORWARD_REWIND_STEP = 10 * 1000; // in milliseconds
    public static final int FAST_FORWARD_REWIND_REPEAT_DELAY = 200; // in milliseconds
    private static final String TAG = "MusicMediaPlayerGlue";
    protected final PlaybackControlsRow.ThumbsDownAction mThumbsDownAction;
    protected final PlaybackControlsRow.ThumbsUpAction mThumbsUpAction;
    private final Context mContext;
    private final PlaybackControlsRow.RepeatAction mRepeatAction;
    private final PlaybackControlsRow.ShuffleAction mShuffleAction;
    private PlaybackControlsRow mControlsRow;
    private static final int REFRESH_PROGRESS = 1;
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case REFRESH_PROGRESS:
                    updateProgress();
                    Log.d(TAG, "enableProgressUpdating(boolean)");
                    queueNextRefresh();
            }
        }
    };

    private boolean mInitialized = false; // true when the MediaPlayer is prepared/initialized
    private Action mSelectedAction; // the action which is currently selected by the user
    private long mLastKeyDownEvent = 0L; // timestamp when the last DPAD_CENTER KEY_DOWN occurred
    private List<MediaMetaData> mMediaMetaDataList = new ArrayList<>();
    boolean mMediaMetaDataListChanged = false; // flag indicating that mMediaMetaDataList is changed and
                                            // the media item list in the service needs to be updated
                                            // next time one of its APIs is used

    private MusicPlaybackService mPlaybackService;
    private boolean mServiceCallbackRegistered = false;
    private boolean mOnBindServiceHasBeenCalled = false;
    private boolean mStartPlayingAfterConnect = true;

    private ServiceConnection mPlaybackServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            MusicPlaybackService.LocalBinder binder = (MusicPlaybackService.LocalBinder) iBinder;
            mPlaybackService = binder.getService();

            if (mPlaybackService.getCurrentMediaItem() == null) {
                if (mStartPlayingAfterConnect && mMediaMetaData != null) {
                    prepareAndPlay(mMediaMetaData);
                }
            }

            mPlaybackService.registerServiceCallback(MusicMediaPlayerGlue.this);
            mServiceCallbackRegistered = true;

            Log.d("MusicPlaybackService", "mPlaybackServiceConnection connected!");
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.d("MusicPlaybackService", "mPlaybackServiceConnection disconnected!");
            mOnBindServiceHasBeenCalled = false;
            mPlaybackService = null;
            // update UI before the service disconnects. This should presumably happen after the
            // activity has called onStop. If the playback service finishes and stops, and when the user
            // returns to the playback from home screen, the play status and progress bar UIs could
            // be outdated and the activity may not connect to the service for a while. So we update
            // UI here for the playback state to be up-to-date once the user returns to the activity.
            onMediaStateChangedByPlaybackService(null, -1);
        }
    };

    public MusicMediaPlayerGlue(Context context, PlaybackOverlayFragment fragment) {
        super(context, fragment);
        mContext = context;

        // Instantiate secondary actions
        mShuffleAction = new PlaybackControlsRow.ShuffleAction(mContext);
        mRepeatAction = new PlaybackControlsRow.RepeatAction(mContext);
        mThumbsDownAction = new PlaybackControlsRow.ThumbsDownAction(mContext);
        mThumbsUpAction = new PlaybackControlsRow.ThumbsUpAction(mContext);
        mThumbsDownAction.setIndex(PlaybackControlsRow.ThumbsAction.OUTLINE);
        mThumbsUpAction.setIndex(PlaybackControlsRow.ThumbsAction.OUTLINE);

        startAndBindToServiceIfNeeded();

        // Register selected listener such that we know what action the user currently has focused.
        fragment.setOnItemViewSelectedListener(this);
    }

    public boolean isPlaybackServiceConnected() {
        return (mPlaybackService != null) ? true : false;
    }

    private void startAndBindToServiceIfNeeded() {
        if (mOnBindServiceHasBeenCalled) {
            return;
        }
        // setting this flag to true so that media item list is repopulated once this activity
        // connects to a fresh copy of the playback service
        mMediaMetaDataListChanged = true;
        // Bind to MusicPlaybackService
        Intent serviceIntent = new Intent(mContext, MusicPlaybackService.class);
        mContext.startService(serviceIntent);
        mContext.bindService(serviceIntent, mPlaybackServiceConnection, 0);
        mOnBindServiceHasBeenCalled = true;
    }

    /**
     * Called when the playback state changes for a media item. This is either called by
     * {@link MusicPlaybackService} when the media status changes or when the service disconnects
     * (service is stopped) in order to restore the latest UI to the playback fragment.
     * @param currentMediaItem The media item whose status got changes
     * @param currentMediaState The current state of the media item (For different playback states,
     *                          refer to {@link MediaUtils})
     */
    @Override
    public void onMediaStateChangedByPlaybackService(MediaMetaData currentMediaItem, int currentMediaState) {

        if (mPlaybackService == null) {
            // onMetadataChanged updates both the metadata info on the player as well as the progress bar
            onMetadataChanged();
            // onStateChanged updates the play/pause action buttons and the fading status
            onStateChanged();
            return;
        }
        if (currentMediaItem == null) {
            throw new IllegalArgumentException("PlaybackService passed a null media item!");
        }

        if (mMediaFileStateChangeListener != null) {
            mMediaFileStateChangeListener.onMediaStateChanged(
                    currentMediaItem, currentMediaState);
        }
        switch (currentMediaState) {
            case MediaUtils.MEDIA_STATE_PREPARING:
                break;
            case MediaUtils.MEDIA_STATE_MEDIALIST_COMPLETED:
                updateProgress();
                enableProgressUpdating(false);
                break;
            case MediaUtils.MEDIA_STATE_PREPARED:
                mInitialized = true;
                onMetadataChanged();
                updateProgress();
                break;
        }
        onStateChanged();

        if (mPlaybackService != null) {
            int repeatState = mPlaybackService.getRepeatState();
            // if the activity's current repeat state differs from the service's, update it with the
            // repeatState of the service
            if (getRepeatState() != repeatState) {
                switch (repeatState) {
                    case MusicPlaybackService.MEDIA_ACTION_NO_REPEAT:
                        mRepeatAction.setIndex(PlaybackControlsRow.RepeatAction.NONE);
                        break;
                    case MusicPlaybackService.MEDIA_ACTION_REPEAT_ONE:
                        mRepeatAction.setIndex(PlaybackControlsRow.RepeatAction.ONE);
                        break;
                    case MusicPlaybackService.MEDIA_ACTION_REPEAT_ALL:
                        mRepeatAction.setIndex(PlaybackControlsRow.RepeatAction.ALL);
                        break;
                }
            }
        }
    }

    public void openServiceCallback() {
        if (mPlaybackService != null && !mServiceCallbackRegistered) {
            mPlaybackService.registerServiceCallback(this);
            mServiceCallbackRegistered = true;
        }
    }

    public void releaseServiceCallback() {
        if (mPlaybackService != null && mServiceCallbackRegistered) {
            mPlaybackService.unregisterAll();
            mServiceCallbackRegistered = false;
        }
    }
    /**
     * Unbinds glue from the playback service. Called when the fragment is destroyed (pressing back)
     */
    public void close() {
        Log.d("MusicPlaybackService", "MusicMediaPlayerGlue closed!");
        mContext.unbindService(mPlaybackServiceConnection);
    }

    /**
     * Will reset the {@link MediaPlayer} and the glue such that a new file can be played. You are
     * not required to call this method before playing the first file. However you have to call it
     * before playing a second one.
     */
    void reset() {
        mInitialized = false;
        if (mPlaybackService != null) {
            mPlaybackService.reset();
        }
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
            if (repeatOne()) {
                setRepeatState(MusicPlaybackService.MEDIA_ACTION_REPEAT_ONE);
            } else if (repeatAll()) {
                setRepeatState(MusicPlaybackService.MEDIA_ACTION_REPEAT_ALL);
            } else {
                setRepeatState(MusicPlaybackService.MEDIA_ACTION_NO_REPEAT);
            }
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

    @Override public boolean onKey(View v, int keyCode, KeyEvent event) {
        // This method is overridden in order to make implement fast forwarding and rewinding when
        // the user keeps the corresponding action pressed.
        // We only consume DPAD_CENTER Action_DOWN events on the Fast-Forward and Rewind action and
        // only if it has not been pressed in the last X milliseconds.
        boolean consume = mSelectedAction instanceof PlaybackControlsRow.RewindAction;
        consume = consume || mSelectedAction instanceof PlaybackControlsRow.FastForwardAction;
        consume = consume && mInitialized;
        consume = consume && event.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER;
        consume = consume && event.getAction() == KeyEvent.ACTION_DOWN;
        consume = consume && System
                .currentTimeMillis() - mLastKeyDownEvent > FAST_FORWARD_REWIND_REPEAT_DELAY;
        if (consume) {
            mLastKeyDownEvent = System.currentTimeMillis();
            int newPosition = getCurrentPosition() + FAST_FORWARD_REWIND_STEP;
            if (mSelectedAction instanceof PlaybackControlsRow.RewindAction) {
                newPosition = getCurrentPosition() - FAST_FORWARD_REWIND_STEP;
            }
            // Make sure the new calculated duration is in the range 0 >= X >= MediaDuration
            if (newPosition < 0) newPosition = 0;
            if (newPosition > getMediaDuration()) newPosition = getMediaDuration();
            seekTo(newPosition);
            return true;
        }
        return super.onKey(v, keyCode, event);
    }


    @Override public boolean isMediaPlaying() {
        return (mPlaybackService != null) && mPlaybackService.isPlaying();
    }

    @Override public int getMediaDuration() {
        return (mPlaybackService != null) ? mPlaybackService.getDuration() : 0;
    }

    @Override public int getCurrentPosition() {
        return (mPlaybackService != null) ? mPlaybackService.getCurrentPosition() : 0;
    }

    @Override protected void startPlayback(int speed) throws IllegalStateException {
        prepareAndPlay(mMediaMetaData);
    }

    @Override protected void pausePlayback() {
        if (mPlaybackService != null) {
            mPlaybackService.pause();
        }
    }

    /**
     * Called whenever the user presses fast-forward/rewind or when the user keeps the corresponding
     * action pressed.
     *
     * @param newPosition The new position of the media track in milliseconds.
     */
    protected void seekTo(int newPosition) {
        if (mPlaybackService != null) {
            mPlaybackService.seekTo(newPosition);
        }
    }

    private void setRepeatState(int repeatState) {
        if (mPlaybackService != null) {
            mPlaybackService.setRepeatState(repeatState);
        }
    }

    public void setMediaMetaDataList(List<MediaMetaData> mediaMetaDataList) {
        mMediaMetaDataList.clear();
        mMediaMetaDataList.addAll(mediaMetaDataList);
        mMediaMetaDataListChanged = true;
        if (mPlaybackService != null) {
            mPlaybackService.setMediaItemList(mMediaMetaDataList, false);
            mMediaMetaDataListChanged = false;
        }
    }


    public void prepareAndPlay(MediaMetaData mediaMetaData) {
        if (mediaMetaData == null) {
            throw new RuntimeException("Provided uri is null!");
        }
        startAndBindToServiceIfNeeded();
        mMediaMetaData = mediaMetaData;
        if (mPlaybackService == null) {
            // This media item is saved (mMediaMetaData) and later played when the
            // connection channel is established.
            mStartPlayingAfterConnect = true;
            return;
        }
        if (mMediaMetaDataListChanged) {
            mPlaybackService.setMediaItemList(mMediaMetaDataList, false);
            mMediaMetaDataListChanged = false;
        }
        mPlaybackService.playMediaItem(mediaMetaData);
    }

    /**
     * Call to <code>startPlayback(1)</code>.
     *
     * @throws IllegalStateException See {@link MediaPlayer} for further information about it's
     * different states when setting a data source and preparing it to be played.
     */
    public void startPlayback() throws IllegalStateException {
        startPlayback(1);
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

    public int getRepeatState() {
        if (repeatOne()) {
            return MusicPlaybackService.MEDIA_ACTION_REPEAT_ONE;
        } else if (repeatAll()) {
            return MusicPlaybackService.MEDIA_ACTION_REPEAT_ALL;
        } else {
            return MusicPlaybackService.MEDIA_ACTION_NO_REPEAT;
        }
    }

    /**
     * This is a listener implementation for the {@link OnItemViewSelectedListener} of the {@link
     * PlaybackOverlayFragment}. This implementation is required in order to detect KEY_DOWN events
     * on the {@link android.support.v17.leanback.widget.PlaybackControlsRow.FastForwardAction} and
     * {@link android.support.v17.leanback.widget.PlaybackControlsRow.RewindAction}. Thus you should
     * <u>NOT</u> set another {@link OnItemViewSelectedListener} on your {@link
     * PlaybackOverlayFragment}. Instead, override this method and call its super (this)
     * implementation.
     *
     * @see OnItemViewSelectedListener#onItemSelected(Presenter.ViewHolder, Object,
     * RowPresenter.ViewHolder, Row)
     */
    @Override
    public void onItemSelected(Presenter.ViewHolder itemViewHolder, Object item,
            RowPresenter.ViewHolder rowViewHolder, Row row) {
        if (item instanceof Action) {
            mSelectedAction = (Action) item;
        } else {
            mSelectedAction = null;
        }
    }
}
