import {NativeModules, DeviceEventEmitter} from "react-native";
import NotificationAndroid from "./notification";

const RNNotifications = NativeModules.WixRNNotifications;

let notificationReceivedListener;
let notificationOpenedListener;
let registrationTokenUpdateListener;

export class NotificationsAndroid {
  static setNotificationOpenedListener(listener) {
    notificationOpenedListener = DeviceEventEmitter.addListener("notificationOpened", (notification) => listener(new NotificationAndroid(notification)));
  }

  static clearNotificationOpenedListener() {
    if (notificationOpenedListener) {
      notificationOpenedListener.remove();
      notificationOpenedListener = null;
    }
  }

  static setNotificationReceivedListener(listener) {
    notificationReceivedListener = DeviceEventEmitter.addListener("notificationReceived", (notification) => listener(new NotificationAndroid(notification)));
  }

  static clearNotificationReceivedListener() {
    if (notificationReceivedListener) {
      notificationReceivedListener.remove();
      notificationReceivedListener = null;
    }
  }

  static setRegistrationTokenUpdateListener(listener) {
    registrationTokenUpdateListener = DeviceEventEmitter.addListener("remoteNotificationsRegistered", listener);
  }

  static clearRegistrationTokenUpdateListener() {
    if (registrationTokenUpdateListener) {
      registrationTokenUpdateListener.remove();
      registrationTokenUpdateListener = null;
    }
  }

  static refreshToken() {
    RNNotifications.refreshToken();
  }

  static localNotification(notification, id) {
    const notificationProperties = notification instanceof NotificationAndroid ? notification.properties : notification;

    if (!id && id !== 0) {
      id = notificationProperties.tag ? 0 : Math.random() * 100000000 | 0; // Bitwise-OR forces value onto a 32bit limit
    }

    RNNotifications.postLocalNotification(notificationProperties, id);
    return id;
  }

  static cancelLocalNotification(id, tag) {
    RNNotifications.cancelLocalNotification(id, tag);
  }

  static cancelAllLocalNotifications() {
    RNNotifications.cancelAllLocalNotifications();
  }
}

export class PendingNotifications {
  static getInitialNotification() {
    return RNNotifications.getInitialNotification()
      .then((rawNotification) => {
        return rawNotification ? new NotificationAndroid(rawNotification) : undefined;
      });
  }
}
