package com.gathin.tvhome;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/**
 * Notification listener that exists only to be allowed to read the TV's media sessions.
 *
 * <p>Android gates {@code MediaSessionManager.getActiveSessions()} behind notification-listener access, so the
 * launcher ships this tiny service and the install script grants it with
 * {@code cmd notification allow_listener}. It posts no notifications of its own.
 */
public class NowPlayingService extends NotificationListenerService {

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        NowPlaying.refresh(this);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        NowPlaying.refresh(this);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        NowPlaying.refresh(this);
    }
}
