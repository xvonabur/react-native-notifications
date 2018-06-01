package com.wix.reactnativenotifications.core.notifications;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import com.facebook.react.bridge.ReactContext;
import com.wix.reactnativenotifications.core.AppLaunchHelper;
import com.wix.reactnativenotifications.core.AppLifecycleFacade;
import com.wix.reactnativenotifications.core.AppLifecycleFacade.AppVisibilityListener;
import com.wix.reactnativenotifications.core.AppLifecycleFacadeHolder;
import com.wix.reactnativenotifications.core.BitmapLoader;
import com.wix.reactnativenotifications.core.InitialNotificationHolder;
import com.wix.reactnativenotifications.core.JsIOHelper;
import com.wix.reactnativenotifications.core.LocalNotificationService;

import static com.wix.reactnativenotifications.Defs.LOGTAG;
import static com.wix.reactnativenotifications.Defs.NOTIFICATION_OPENED_EVENT_NAME;

public class LocalNotification implements ILocalNotification {

    private final Context mContext;
    private final NotificationProps mNotificationProps;
    private final AppLifecycleFacade mAppLifecycleFacade;
    private final AppLaunchHelper mAppLaunchHelper;
    private final JsIOHelper mJsIOHelper;
    private final BitmapLoader mImageLoader;
    private final AppVisibilityListener mAppVisibilityListener = new AppVisibilityListener() {

        @Override
        public void onAppVisible() {
            mAppLifecycleFacade.removeVisibilityListener(this);
            dispatchImmediately();
        }

        @Override
        public void onAppNotVisible() {
        }
    };

    public static ILocalNotification get(Context context, NotificationProps localNotificationProps) {
        final AppLifecycleFacade appLifecycleFacade = AppLifecycleFacadeHolder.get();
        final AppLaunchHelper appLaunchHelper = new AppLaunchHelper();
        final Context appContext = context.getApplicationContext();

        if (appContext instanceof INotificationsApplication) {
            return ((INotificationsApplication) appContext).getLocalNotification(context, localNotificationProps, AppLifecycleFacadeHolder.get(), new AppLaunchHelper());
        }

        return new LocalNotification(context, localNotificationProps, appLifecycleFacade, appLaunchHelper);
    }

    protected LocalNotification(Context context, NotificationProps localNotificationProps, AppLifecycleFacade appLifecycleFacade, AppLaunchHelper appLaunchHelper, JsIOHelper jsIOHelper, BitmapLoader imageLoader) {
        mContext = context;
        mNotificationProps = localNotificationProps;
        mAppLifecycleFacade = appLifecycleFacade;
        mAppLaunchHelper = appLaunchHelper;
        mJsIOHelper = jsIOHelper;
        mImageLoader = imageLoader;
    }

    protected LocalNotification(Context context, NotificationProps localNotificationProps, AppLifecycleFacade appLifecycleFacade, AppLaunchHelper appLaunchHelper) {
        this(context, localNotificationProps, appLifecycleFacade, appLaunchHelper, new JsIOHelper(context), new BitmapLoader(context));
    }

    @Override
    public int post(Integer notificationId) {
        final int id = notificationId != null ? notificationId : createNotificationId();
        final PendingIntent pendingIntent = createOnOpenedIntent(id);
        setLargeIconThenPostNotification(id, getNotificationBuilder(pendingIntent));
        return id;
    }

    @Override
    public void onOpened() {
        digestNotification();
    }

    protected void digestNotification() {
        if (!mAppLifecycleFacade.isReactInitialized()) {
            setAsInitialNotification();
            launchOrResumeApp();
            return;
        }

        final ReactContext reactContext = mAppLifecycleFacade.getRunningReactContext();
        if (reactContext.getCurrentActivity() == null) {
            setAsInitialNotification();
        }

        if (mAppLifecycleFacade.isAppVisible()) {
            dispatchImmediately();
        } else {
            dispatchUponVisibility();
        }
    }

    protected void setAsInitialNotification() {
        InitialNotificationHolder.getInstance().set(mNotificationProps);
    }

    protected void dispatchImmediately() {
        sendOpenedEvent();
    }

    protected void dispatchUponVisibility() {
        mAppLifecycleFacade.addVisibilityListener(getIntermediateAppVisibilityListener());

        // Make the app visible so that we'll dispatch the notification opening when visibility changes to 'true' (see
        // above listener registration).
        launchOrResumeApp();
    }

    protected AppVisibilityListener getIntermediateAppVisibilityListener() {
        return mAppVisibilityListener;
    }

    protected PendingIntent createOnOpenedIntent(int id) {
        final Intent serviceIntent = new Intent(mContext, LocalNotificationService.class);
        serviceIntent.putExtra(LocalNotificationService.EXTRA_NOTIFICATION, mNotificationProps.asBundle());
        return PendingIntent.getService(mContext, id, serviceIntent, PendingIntent.FLAG_ONE_SHOT);
    }

    protected Notification.Builder getNotificationBuilder(PendingIntent intent) {
        final Integer icon = mNotificationProps.getIcon();

        final Notification.Builder builder = new Notification.Builder(mContext)
                .setContentTitle(mNotificationProps.getTitle())
                .setContentText(mNotificationProps.getBody())
                .setSmallIcon(icon != null ? icon : mContext.getApplicationContext().getApplicationInfo().icon)
                .setSound(mNotificationProps.getSound())
                .setContentIntent(intent)
                .setDefaults(Notification.DEFAULT_ALL)
                .setAutoCancel(true);

        final Integer color = mNotificationProps.getColor();

        if (color != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setColor(color);
        }

        final Integer lightsColor = mNotificationProps.getLightsColor();
        final Integer lightsOnMs = mNotificationProps.getLightsOnMs();
        final Integer lightsOffMs = mNotificationProps.getLightsOffMs();

        if (lightsColor != null && lightsOnMs != null && lightsOffMs != null) {
            builder.setLights(lightsColor, lightsOnMs, lightsOffMs);
        }

        return builder;
    }

    protected void setLargeIconThenPostNotification(final int notificationId, final Notification.Builder notificationBuilder) {
        final String icon = mNotificationProps.getLargeIcon();

        if (icon != null && (icon.startsWith("http://") || icon.startsWith("https://") || icon.startsWith("file://"))) {
            mImageLoader.loadUri(Uri.parse(icon), new BitmapLoader.OnBitmapLoadedCallback() {
                @Override
                public void onBitmapLoaded(Bitmap bitmap) {
                    notificationBuilder.setLargeIcon(bitmap);
                    postNotification(notificationId, notificationBuilder.build());
                }
            });
        } else {
            if (icon != null) {
                final int id = mContext.getResources().getIdentifier(icon, "drawable", mContext.getPackageName());
                final Bitmap bitmap = id != 0 ? BitmapFactory.decodeResource(mContext.getResources(), id) : null;

                if (bitmap != null) {
                    notificationBuilder.setLargeIcon(bitmap);
                } else {
                    Log.e(LOGTAG, icon + " does not correspond to a known bitmap drawable");
                }
            }

            postNotification(notificationId, notificationBuilder.build());
        }
    }

    protected void postNotification(int id, Notification notification) {
        final NotificationManager notificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(mNotificationProps.getTag(), id, notification);
    }

    protected int createNotificationId() {
        return mNotificationProps.getTag() != null ? 0 : (int) System.nanoTime();
    }

    protected void launchOrResumeApp() {
        final Intent intent = mAppLaunchHelper.getLaunchIntent(mContext);
        mContext.startActivity(intent);
    }

    private void sendOpenedEvent() {
        mJsIOHelper.sendEventToJS(NOTIFICATION_OPENED_EVENT_NAME, mNotificationProps.asBundle());
    }
}
