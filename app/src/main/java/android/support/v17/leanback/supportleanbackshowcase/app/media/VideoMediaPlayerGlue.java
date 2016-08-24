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

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.support.v17.leanback.app.PlaybackOverlayFragment;
import android.support.v17.leanback.supportleanbackshowcase.R;
import android.support.v17.leanback.widget.Action;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.PlaybackControlsRow;
import android.support.v17.leanback.widget.PlaybackControlsRowPresenter;
import android.util.Log;
import android.view.KeyEvent;
import android.view.SurfaceHolder;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.SimpleTarget;

import java.io.IOException;
import java.util.List;

/**<p>>
 * This glue extends the {@link MediaPlayerGlue} and handles all the
 * interactions between the fragment, playback controls, and the video player implemented as an
 * Android media player. It makes asynchronous calls to the media player and notifies the fragment
 * for playback status changes.
 * <p/>
 */
public abstract class VideoMediaPlayerGlue extends MediaPlayerGlue implements
        AudioManager.OnAudioFocusChangeListener{

    private final PlaybackControlsRow.ClosedCaptioningAction mClosedCaptioningAction;
    private final PlaybackControlsRow.PictureInPictureAction mPipAction;
    private MediaPlayer mPlayer;
    // MediaSession required for receiving input focus while in PIP mode or homescreen.
    // The framework also needs it for displaying NowPlayingCard on the homescreen
    private MediaSession mVideoSession;
    private AudioManager mAudioManager;
    int mAudioFocus = AudioManager.AUDIOFOCUS_LOSS;
    private static final String TAG = "VideoMediaPlayerGlue";

    public VideoMediaPlayerGlue(Context context, PlaybackOverlayFragment fragment) {
        super(context, fragment);

        createMediaPlayerIfNeeded();
        mAudioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        // Instantiate secondary actions
        mClosedCaptioningAction = new PlaybackControlsRow.ClosedCaptioningAction(context);
        mPipAction = new PlaybackControlsRow.PictureInPictureAction(context);
        setFadingEnabled(true);
    }

    @Override protected void addSecondaryActions(ArrayObjectAdapter secondaryActionsAdapter) {
        secondaryActionsAdapter.add(mClosedCaptioningAction);
        secondaryActionsAdapter.add(mThumbsDownAction);
        secondaryActionsAdapter.add(mThumbsUpAction);
        if (VideoExampleActivity.supportsPictureInPicture(getContext())) {
            secondaryActionsAdapter.add(mPipAction);
        }
    }

    @Override public void onActionClicked(Action action) {
        super.onActionClicked(action);
        if (action == mClosedCaptioningAction) {
            mClosedCaptioningAction.nextIndex();
        } else if (action == mPipAction) {
            ((Activity) getContext()).enterPictureInPictureMode();
        }
    }

    public void setupControlsRowPresenter(PlaybackControlsRowPresenter presenter) {
        // TODO: hahnr@ move into resources
        presenter.setProgressColor(Color.parseColor("#EEFF41"));
        presenter.setBackgroundColor(Color.parseColor("#007236"));
    }

    public boolean requestAudioFocus() {
        return mAudioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    public boolean abandonAudioFocus() {
        return mAudioManager.abandonAudioFocus(this) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    private void updateVideoSessionMetaData() {
        if (mMediaMetaData == null) {
            throw new IllegalArgumentException(
                    "mCurrentMediaItem is null in updateMediaSessionMetaData!");
        }
        final MediaMetadata.Builder metaDataBuilder = new MediaMetadata.Builder();
        if (mMediaMetaData.getMediaTitle() != null) {
            metaDataBuilder.putString(MediaMetadata.METADATA_KEY_TITLE,
                    mMediaMetaData.getMediaTitle());
        }
        if (mMediaMetaData.getMediaAlbumName() != null) {
            metaDataBuilder.putString(MediaMetadata.METADATA_KEY_ALBUM,
                    mMediaMetaData.getMediaAlbumName());
        }
        if (mMediaMetaData.getMediaArtistName() != null) {
            metaDataBuilder.putString(MediaMetadata.METADATA_KEY_ARTIST,
                   mMediaMetaData.getMediaArtistName());
        }
        Resources res = getContext().getResources();
        int cardWidth = res.getDimensionPixelSize(R.dimen.playback_now_playing_card_width);
        int cardHeight = res.getDimensionPixelSize(R.dimen.playback_now_playing_card_height);
        if (mMediaMetaData.getMediaAlbumArtUrl() != null) {
            Glide.with(getContext())
                    .load(mMediaMetaData.getMediaAlbumArtUrl())
                    .asBitmap()
                    .into(new SimpleTarget<Bitmap>(cardWidth, cardHeight) {
                              @Override
                              public void onResourceReady(
                                      Bitmap resource,
                                      GlideAnimation<? super Bitmap> glideAnimation) {
                                  metaDataBuilder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART,
                                          resource);
                                  mVideoSession.setMetadata(metaDataBuilder.build());
                              }
                          }
                    );
        }
    }

    /**
     * Updates the playback state of the media session. This is used in both the NowPlayingCard and
     * PIP control buttons.
     * @param playbackState The current playback state for the media session
     */
    private void updateVideoSessionPlayState(int playbackState) {
        if (mVideoSession == null) {
            // MediaSession has already been released, no need to update PlaybackState
            return;
        }
        PlaybackState.Builder playbackStateBuilder = new PlaybackState.Builder();
        long currentPosition = getCurrentPosition();
        playbackStateBuilder.setState(playbackState, currentPosition, (float) 1.0).setActions(
                getPlaybackStateActions()
        );
        mVideoSession.setPlaybackState(playbackStateBuilder.build());
    }

    /**
     * Sets the media session's activity launched when clicking on NowPlayingCard. This returns to
     * the media screen that is playing or paused; the launched activity corresponds to the
     * currently shown media session in the NowPlayingCard on TV launcher.
     */
    private void updateMediaSessionIntent() {
        if (mVideoSession == null) {
            return;
        }
        Intent nowPlayIntent = new Intent(getContext(), VideoExampleActivity.class);
        nowPlayIntent.putExtra(VideoExampleActivity.TAG, mMediaMetaData);
        nowPlayIntent.setData(Uri.parse(mMediaMetaData.getMediaSourcePath()));
        PendingIntent pi = PendingIntent.getActivity(getContext(), 0, nowPlayIntent,
                PendingIntent.FLAG_CANCEL_CURRENT);
        mVideoSession.setSessionActivity(pi);
    }

    /**
     * @return The available set of actions for the media session. These actions should be provided
     * for the PlaybackState of the MediaSession in order for the play/pause control buttons to
     * be displayed in the list of PIP controls.
     */
    private long getPlaybackStateActions() {
        return PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE |
                PlaybackState.ACTION_FAST_FORWARD | PlaybackState.ACTION_REWIND |
                PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS;
    }

    /**
     * @see MediaPlayer#setDisplay(SurfaceHolder)
     */
    public void setDisplay(SurfaceHolder surfaceHolder) {
        mPlayer.setDisplay(surfaceHolder);
    }

    @Override public boolean isMediaPlaying() {
        return mPlayer.isPlaying();
    }

    @Override public int getMediaDuration() {
        return mInitialized ? mPlayer.getDuration() : 0;
    }

    @Override public int getCurrentPosition() {
        return mInitialized ? mPlayer.getCurrentPosition() : 0;
    }

    @Override protected void startPlayback(int speed) throws IllegalStateException {
        if (requestAudioFocus()) {
            mAudioFocus = AudioManager.AUDIOFOCUS_GAIN;
        } else {
            Log.e(TAG, "Video player could not obtain audio focus in startPlayback");
            return;
        }
        prepareIfNeededAndPlay(mMediaMetaData);
    }

    @Override protected void pausePlayback() {
        if (isMediaPlaying()) {
            mPlayer.pause();
            updateVideoSessionPlayState(PlaybackState.STATE_PAUSED);
        }
    }

    /**
     * Called whenever the user presses fast-forward/rewind or when the user keeps the corresponding
     * action pressed.
     *
     * @param newPosition The new position of the media track in milliseconds.
     */
    @Override
    protected void seekTo(int newPosition) {
        if (mInitialized) {
            mPlayer.seekTo(newPosition);
        }
    }

    public void prepareIfNeededAndPlay(MediaMetaData mediaMetaData) {
        if (mediaMetaData == null) {
            throw new RuntimeException("Provided metadata is null!");
        }
        if (requestAudioFocus()) {
            mAudioFocus = AudioManager.AUDIOFOCUS_GAIN;
        } else {
            Log.e(TAG, "Video player could not obtain audio focus in prepareIfNeededAndPlay");
            return;
        }
        createMediaPlayerIfNeeded();
        createMediaSessionIfNeeded();
        if (mInitialized && isMediaItemsTheSame(mMediaMetaData, mediaMetaData)) {
            if (!isMediaPlaying()) {
                // This media item had been already playing but is being paused. Will resume the player.
                // No need to reset the player.
                Log.d(TAG, "mPlayer is started (meta data is the same)");
                mPlayer.start();
                updateVideoSessionPlayState(PlaybackState.STATE_PLAYING);
                onStateChanged();
            }
        } else {
            prepareNewMedia(mediaMetaData);
        }
    }

    public void saveUIState() {
        onMetadataChanged();
        onStateChanged();
    }

    private boolean isMediaItemsTheSame(MediaMetaData currentMediaMetaData,
                                        MediaMetaData newMediaMetaData) {
        if (currentMediaMetaData == newMediaMetaData) {
            return true;
        }
        if (currentMediaMetaData == null || newMediaMetaData == null) {
            return false;
        }
        if (newMediaMetaData.getMediaSourceUri() != null) {
            return currentMediaMetaData.getMediaSourceUri().equals(
                    newMediaMetaData.getMediaSourceUri()
            );
        }
        if (newMediaMetaData.getMediaSourcePath() != null) {
            return currentMediaMetaData.getMediaSourcePath().equals(
                    newMediaMetaData.getMediaSourcePath()
            );
        }
        return false;
    }

    private void prepareNewMedia(final MediaMetaData mediaMetaData) {
        resetPlayer();
        // mMediaMetaData must be set in the beginning before onStateChanged() call so that
        // hasValidMedia returns true. Otherwise, updatePlaybackState is not called and the
        // primary controls bar is not displayed at the start when media is prepared.
        mMediaMetaData = mediaMetaData;
        updateMediaSessionIntent();
        try {
            if (mediaMetaData.getMediaSourceUri() != null) {
                mPlayer.setDataSource(getContext(), mediaMetaData.getMediaSourceUri());
            }
            else mPlayer.setDataSource(mediaMetaData.getMediaSourcePath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override public void onPrepared(MediaPlayer mp) {
                mInitialized = true;
                mPlayer.start();
                updateVideoSessionPlayState(PlaybackState.STATE_PLAYING);
                updateVideoSessionMetaData();
                onMetadataChanged();
                onStateChanged();
                updateProgress();
            }
        });
        mPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override public void onCompletion(MediaPlayer mp) {
                if (mInitialized && mMediaFileStateChangeListener != null)
                    mMediaFileStateChangeListener.onMediaStateChanged(mediaMetaData,
                            MediaUtils.MEDIA_STATE_COMPLETED);
            }
        });
        mPlayer.setOnBufferingUpdateListener(new MediaPlayer.OnBufferingUpdateListener() {
            @Override public void onBufferingUpdate(MediaPlayer mp, int percent) {
                if (mInitialized) {
                    mControlsRow.setBufferedProgress((int) (mp.getDuration() * (percent / 100f)));
                }
            }
        });
        mPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mediaPlayer, int what, int extra) {
                Log.e(TAG, "MediaPlayer had error " + what  + " extra " + extra);
                return true;
            }
        });
        mPlayer.prepareAsync();
        updateVideoSessionPlayState(PlaybackState.STATE_BUFFERING);
        onStateChanged();
    }

    /**
     * Resets the {@link MediaPlayer} such that a new media file can be played.
     */
    public void resetPlayer() {
        mInitialized = false;
        if (mPlayer != null) {
            mPlayer.reset();
            updateVideoSessionPlayState(PlaybackState.STATE_PAUSED);
        }
    }

    public void createMediaPlayerIfNeeded() {
        if (mPlayer == null) {
            mPlayer = new MediaPlayer();
        }
    }

    /**
     * Releases the {@link MediaPlayer}. The media player is released when the fragment exits
     * (onDestroy() is called on the playback fragment).
     */
    public void releaseMediaPlayer() {
        resetPlayer();
        if (mPlayer != null) {
            mPlayer.release();
            mPlayer = null;
        }
        if (!abandonAudioFocus() ) {
            Log.e(TAG, "Video player could not abandon audio focus in releaseResources");
        }
    }

    public void createMediaSessionIfNeeded() {
        if (mVideoSession == null) {
            mVideoSession = new MediaSession(this.getContext(), "VideoPlayer Session");
            mVideoSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS |
                    MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
            mVideoSession.setCallback(new VideoSessionCallback());
            mVideoSession.setActive(true);
            updateVideoSessionPlayState(PlaybackState.STATE_NONE);
            getFragment().getActivity().setMediaController(
                    new MediaController(getContext(), mVideoSession.getSessionToken()));
        }
    }

    /**
     * Releases the {@link MediaSession}. The media session is released when the playback fragment
     * is no longer visible. This can happen in onStop() or surfaceDestroyed() (The ordering of these
     * two is not deterministic, and that's why it's important to release the session in both callbacks).
     * Since the media session can be released without the fragment being destroyed,
     * {@link #createMediaSessionIfNeeded()} should be called when the playback becomes active again.
     */
    public void releaseMediaSession() {
        Log.d(TAG, "Media session being released!");
        if (mVideoSession != null){
            mVideoSession.release();
            mVideoSession = null;
        }
        if (!abandonAudioFocus() ) {
            Log.e(TAG, "Video player could not abandon audio focus in releaseResources");
        }
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                Log.d(TAG, "AudioFocus loss transient.");
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                Log.d(TAG, "AudioFocus loss transient can duck.");
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
                abandonAudioFocus();
                Log.d(TAG, "AudioFocus loss");
                break;
            case AudioManager.AUDIOFOCUS_GAIN:
                Log.d(TAG, "AudioFocus Gained");
                break;
        }
    }
    private class VideoSessionCallback extends MediaSession.Callback {

        @Override
        public boolean onMediaButtonEvent(Intent mediaButtonIntent) {
            KeyEvent keyEvent = mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
            Log.d(TAG, "onMediaButtonEvent in VideoSessionCallback called with event: " + keyEvent);
            // No need to handle KEYCODE_MEDIA_PLAY_PAUSE separately, as the super class delegates
            // it to the correct onPlay() or onPause() methods.
            return super.onMediaButtonEvent(mediaButtonIntent);
        }

        @Override
        public void onPlay() {
            Log.d(TAG, "onPlay in VideoSessionCallback called");
            startPlayback();
        }

        @Override
        public void onPause() {
            Log.d(TAG, "onPause in VideoSessionCallback called");
            pausePlayback();
        }

        @Override
        public void onSkipToNext() {
            super.onSkipToNext();
        }

        @Override
        public void onSkipToPrevious() {
            super.onSkipToPrevious();
        }

        @Override
        public void onFastForward() {
            super.onFastForward();
        }

        @Override
        public void onRewind() {
            super.onRewind();
        }

        @Override
        public void onStop() {
            super.onStop();
        }

        @Override
        public void onSeekTo(long pos) {
            super.onSeekTo(pos);
        }
    }
}
