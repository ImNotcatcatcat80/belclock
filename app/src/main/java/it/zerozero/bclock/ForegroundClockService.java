package it.zerozero.bclock;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import java.util.Calendar;
import java.util.Locale;

public class ForegroundClockService extends Service implements SensorEventListener {

    private Handler bgSvcHandler;
    private Runnable bgSvcRunnable;
    private SensorManager sensorManager;
    private Sensor accel;
    private SharedPreferences mShPref;
    private NotificationManager notificationManager;
    private static final String BELCLOCK_SERVICE_NOTIF_CHANN = "BelClock_3300_notifChannel";
    private static long bgCtr;
    private boolean isControlFlashlight = false;
    private boolean isCreateNotification = false;
    private boolean previousOverThrPositive = true;
    private long previousOverThrMs = 0;
    private int numOverThr = 0;
    private long previousToggleMs = 0;
    private boolean flashOn = false;

    public ForegroundClockService() {

    }

    @Override
    public void onCreate() {
        super.onCreate();
        bgCtr = 0;
        bgSvcRunnable = new BgSvcRunnable();
        bgSvcHandler = new Handler();
        sensorManager = (SensorManager) getApplicationContext().getSystemService(Context.SENSOR_SERVICE);
        accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_UI);
        bgSvcHandler.post(bgSvcRunnable);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(10236, createNotification());
        return Service.START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.e("BgSvcRunnable", "service destroyed.");
        notificationManager.cancel(10236);
        bgSvcHandler.removeCallbacks(bgSvcRunnable);
    }

    public Notification createNotification() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.notif_channel_service_name);
            String description = getString(R.string.notif_channel_service_description);
            int importance = NotificationManager.IMPORTANCE_MIN;
            NotificationChannel channel = new NotificationChannel(BELCLOCK_SERVICE_NOTIF_CHANN, name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder notBuilder = new NotificationCompat.Builder(this, BELCLOCK_SERVICE_NOTIF_CHANN)
                .setSmallIcon(R.drawable.ic_developer_mode_black_24px)
                .setContentTitle("BelClock - foreground service")
                .setContentText("...")
                .setAutoCancel(true)
                .setLights(0x80ffffff, 500, 2500);
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(10236, notBuilder.build());  // "10236" è un id arbitrario per aggiornare poi la notifica

        return notBuilder.build();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (Math.abs(event.values[0]) > 19.62) {
            if (((event.values[0] > 0) != previousOverThrPositive) && isControlFlashlight) {
                boolean isOverThrFast = (System.currentTimeMillis() - previousOverThrMs < 1250);
                previousOverThrMs = System.currentTimeMillis();
                if(isOverThrFast) {
                    numOverThr++;
                    previousOverThrPositive = event.values[0] > 0;
                    Log.i("BgSvcRunnable ", "accel X = " + String.valueOf(event.values[0]));
                }
                else {
                    numOverThr = 0;
                }

                if(numOverThr > 7) {
                    Log.i("BgSvcRunnable ", "********************");
                    if (System.currentTimeMillis() - previousToggleMs >= 1500) {
                        toggleFlashSDK23();
                    }
                    numOverThr = 0;
                    previousToggleMs = System.currentTimeMillis();
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    private void toggleFlashSDK23() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            String cameraId = null; // Usually back camera is at 0 position.
            try {
                cameraId = cameraManager.getCameraIdList()[0];
            }
            catch (Exception e) {
                e.printStackTrace();
            }
            try {
                if (!flashOn) {
                    cameraManager.setTorchMode(cameraId, true); //Turn ON
                    flashOn = true;
                }
                else {
                    cameraManager.setTorchMode(cameraId, false); //Turn OFF
                    flashOn = false;
                }
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }

    public class BgSvcRunnable implements Runnable {

        @Override
        public void run() {
            mShPref = getSharedPreferences("BelClock", MODE_PRIVATE);
            isControlFlashlight = mShPref.getBoolean("ControlFlashlight", false);
            isCreateNotification = mShPref.getBoolean("CreateNotif", true);
            StringBuilder notifText = new StringBuilder(10);
            if(flashOn) {
                notifText.append("Flashlight *ON*");
                notifText.append("\n\n");
            }
            if(!isCreateNotification) {
                notifText.append("At ");
                notifText.append(Calendar.getInstance().getTime().toString());
                notifText.append("\n");
            }
            notifText.append(String.format(Locale.ITALIAN, "10-seconds Runnable counter: %d", bgCtr));
            NotificationCompat.Builder notBuilder = new NotificationCompat.Builder(getApplicationContext(), BELCLOCK_SERVICE_NOTIF_CHANN)
                    .setGroup(MainActivity.BCLOCK_NOTIF_GROUP)
                    .setSmallIcon(R.drawable.ic_developer_mode_black_24px)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(notifText))
                    .setContentTitle("BClock - foreground service")
                    .setContentText(notifText)
                    .setAutoCancel(true)
                    .setLights(0x80ffffff, 500, 2500);
            notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.notify(10236, notBuilder.build());

            if(!isControlFlashlight) {
                stopSelf();
            }
            bgCtr++;
            bgSvcHandler.postDelayed(this, 10000);
        }
    }

}
