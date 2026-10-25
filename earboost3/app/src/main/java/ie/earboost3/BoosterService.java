package ie.earboost3;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.content.pm.ServiceInfo;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.audiofx.LoudnessEnhancer;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import java.util.Locale;

public final class BoosterService extends Service{
  public static final String START="ie.earboost3.START",SET="ie.earboost3.SET",STOP="ie.earboost3.STOP";
  public static final String STATUS="ie.earboost3.STATUS",EXTRA_STATUS="status";
  public static volatile boolean active=false;
  public static volatile String lastStatus="Stopped";
  private static final String CHANNEL="earboost_active";
  private static final int NOTIFICATION=3103;
  private AudioManager audio;
  private LoudnessEnhancer effect;
  private AudioDeviceCallback callback;
  private BroadcastReceiver noisy;
  private boolean foreground=false;
  private int gain;
  @Override public void onCreate(){
    super.onCreate();
    audio=(AudioManager)getSystemService(Context.AUDIO_SERVICE);
    gain=getSharedPreferences("settings",0).getInt("gain",0);
    NotificationManager n=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
    n.createNotificationChannel(new NotificationChannel(CHANNEL,"EarBoost processing",NotificationManager.IMPORTANCE_LOW));
    callback=new AudioDeviceCallback(){
      @Override public void onAudioDevicesAdded(AudioDeviceInfo[] d){refresh();}
      @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] d){refresh();}
    };
    audio.registerAudioDeviceCallback(callback,new Handler(Looper.getMainLooper()));
    noisy=new BroadcastReceiver(){
      @Override public void onReceive(Context c,Intent i){
        releaseEffect();publish("Audio route changed — boost paused");
      }
    };
    IntentFilter f=new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
    if(Build.VERSION.SDK_INT>=33)registerReceiver(noisy,f,Context.RECEIVER_NOT_EXPORTED);
    else registerReceiver(noisy,f);
  }
  @Override public int onStartCommand(Intent i,int flags,int startId){
    if(i!=null&&STOP.equals(i.getAction())){stopSelf();return START_NOT_STICKY;}
    if(i!=null&&SET.equals(i.getAction())){
      gain=Math.max(0,Math.min(50,i.getIntExtra("gain",gain)));
      getSharedPreferences("settings",0).edit().putInt("gain",gain).apply();
    }
    if(!foreground){
      try{
        if(Build.VERSION.SDK_INT>=34)startForeground(NOTIFICATION,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(NOTIFICATION,notification());
        foreground=true;active=true;
      }catch(RuntimeException e){
        publish("Service rejected: "+e.getClass().getSimpleName());stopSelf();return START_NOT_STICKY;
      }
    }
    refresh();return START_NOT_STICKY;
  }
  private Notification notification(){
    PendingIntent open=PendingIntent.getActivity(this,1,new Intent(this,MainActivity.class),
      PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    PendingIntent stop=PendingIntent.getService(this,2,new Intent(this,BoosterService.class).setAction(STOP),
      PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    return new Notification.Builder(this,CHANNEL)
      .setSmallIcon(android.R.drawable.ic_media_play)
      .setContentTitle("EarBoost 3 · experimental")
      .setContentText(lastStatus).setContentIntent(open).setOngoing(true)
      .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_pause,"Stop boost",stop).build()).build();
  }
  private boolean ear3Available(){
    try{
      for(AudioDeviceInfo d:audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)){
        int t=d.getType();
        if(t!=AudioDeviceInfo.TYPE_BLUETOOTH_A2DP&&t!=AudioDeviceInfo.TYPE_BLE_HEADSET&&t!=AudioDeviceInfo.TYPE_BLE_SPEAKER)continue;
        CharSequence p=d.getProductName();
        String n=p==null?"":p.toString().toLowerCase(Locale.ROOT);
        if(n.contains("ear (3)")||n.contains("ear(3)")||n.contains("ear 3")||n.contains("ear（3）"))return true;
      }
    }catch(RuntimeException e){publish("Audio device query failed: "+e.getClass().getSimpleName());}
    return false;
  }
  private void refresh(){
    if(!foreground||audio==null)return;
    if(!ear3Available()){releaseEffect();publish("No matching Ear (3) output — boost paused");return;}
    if(audio.getMode()!=AudioManager.MODE_NORMAL){releaseEffect();publish("Call mode — boost paused");return;}
    if(gain==0){releaseEffect();publish("Ear (3) connected — boost at 0%");return;}
    try{
      if(effect==null)effect=new LoudnessEnhancer(0);
      int mb=(int)Math.round(2000.0*Math.log10(1.0+gain/100.0));
      effect.setTargetGain(mb);effect.setEnabled(true);
      if(!effect.getEnabled()){publish("Global effect refused — unsupported");return;}
      publish("Requested +"+gain+"% gain ("+mb+" mB); audible result unverified");
    }catch(RuntimeException e){releaseEffect();publish("Global effect unavailable: "+e.getClass().getSimpleName());}
  }
  private void releaseEffect(){
    if(effect!=null){
      try{effect.setEnabled(false);}catch(RuntimeException ignored){}
      try{effect.release();}catch(RuntimeException ignored){}
      effect=null;
    }
  }
  private void publish(String s){
    lastStatus=s;
    if(foreground)((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(NOTIFICATION,notification());
    Intent i=new Intent(STATUS).setPackage(getPackageName());i.putExtra(EXTRA_STATUS,s);sendBroadcast(i);
  }
  @Override public void onDestroy(){
    releaseEffect();
    if(audio!=null&&callback!=null)audio.unregisterAudioDeviceCallback(callback);
    if(noisy!=null)unregisterReceiver(noisy);
    foreground=false;active=false;lastStatus="Stopped";
    super.onDestroy();
  }
  @Override public IBinder onBind(Intent i){return null;}
}
