package ie.earboost3;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

/** User-initiated experimental effect; never silently starts boosting. */
public final class MainActivity extends Activity {
  private static final int ACCENT=0xffed3448;
  private TextView number,status;
  private SeekBar slider;
  private boolean listening=false;
  private final BroadcastReceiver updates=new BroadcastReceiver() {
    @Override public void onReceive(Context c,Intent i) {
      if(status!=null) status.setText(i.getStringExtra(BoosterService.EXTRA_STATUS));
    }
  };
  private int dp(int n) { return (int)(n*getResources().getDisplayMetrics().density); }
  private TextView text(String v,int sp,int color,boolean bold) {
    TextView t=new TextView(this);t.setText(v);t.setTextSize(sp);t.setTextColor(color);
    if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
    t.setPadding(0,dp(6),0,dp(6));return t;
  }
  private void gap(LinearLayout l,int h){l.addView(new View(this),new LinearLayout.LayoutParams(1,dp(h)));}
  @Override public void onCreate(Bundle b){
    super.onCreate(b);setVolumeControlStream(AudioManager.STREAM_MUSIC);
    getWindow().setStatusBarColor(Color.BLACK);getWindow().setNavigationBarColor(Color.BLACK);
    LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);
    l.setPadding(dp(22),dp(32),dp(22),dp(20));l.setBackgroundColor(0xff101014);
    setContentView(l);
    l.addView(text("earboost 3",32,Color.WHITE,true));
    l.addView(text("NOTHING EAR (3)  •  ANDROID 17 EXPERIMENT",11,0xffaaaaaa,false));
    gap(l,26);
    int previous=getSharedPreferences("settings",0).getInt("gain",0);
    number=text("+"+previous+"%",58,ACCENT,true);number.setGravity(Gravity.CENTER);l.addView(number);
    TextView desc=text("Requested extra digital signal amplitude",13,0xffbbbbbb,false);desc.setGravity(Gravity.CENTER);l.addView(desc);
    slider=new SeekBar(this);slider.setMax(50);slider.setProgress(previous);
    slider.setProgressTintList(ColorStateList.valueOf(ACCENT));
    slider.setThumbTintList(ColorStateList.valueOf(ACCENT));l.addView(slider);
    slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
      @Override public void onProgressChanged(SeekBar s,int v,boolean user){
        number.setText("+"+v+"%");
        if(user) getSharedPreferences("settings",0).edit().putInt("gain",v).apply();
      }
      @Override public void onStartTrackingTouch(SeekBar s){}
      @Override public void onStopTrackingTouch(SeekBar s){
        if(BoosterService.active)startService(new Intent(MainActivity.this,BoosterService.class)
          .setAction(BoosterService.SET).putExtra("gain",s.getProgress()));
      }
    });
    gap(l,20);
    Button start=new Button(this);start.setText("START AUDIO BOOST");
    start.setTextColor(Color.WHITE);start.setBackgroundTintList(ColorStateList.valueOf(ACCENT));
    l.addView(start);start.setOnClickListener(v->startBoost());
    Button stop=new Button(this);stop.setText("STOP & RESET");l.addView(stop);
    stop.setOnClickListener(v->{
      stopService(new Intent(this,BoosterService.class));
      status.setText("Stopped; audio effect released");
    });
    gap(l,24);
    status=text("Stopped — connect Ear (3), then tap START",15,Color.WHITE,false);l.addView(status);
    gap(l,18);
    l.addView(text("Experimental. Some firmware ignores session-0 global audio effects. "
       +"The maximum +50% means 1.5× PCM amplitude (+3.52 dB), not 50% louder hearing. "
       +"Start at 0% and increase slowly. Does not modify Nothing X, calls or firmware.",
       13,0xffb9b9b9,false));
  }
  private void startBoost(){
    if(Build.VERSION.SDK_INT>=31&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){
      requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},101);return;
    }
    if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
      requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},102);return;
    }
    Intent i=new Intent(this,BoosterService.class).setAction(BoosterService.START);
    try{startForegroundService(i);status.setText("Testing Bluetooth output and audio-effect support…");}
    catch(RuntimeException e){status.setText("Start failed: "+e.getClass().getSimpleName());}
  }
  @Override public void onRequestPermissionsResult(int code,String[] p,int[] r){
    super.onRequestPermissionsResult(code,p,r);
    if(r.length>0&&r[0]==PackageManager.PERMISSION_GRANTED)startBoost();
    else status.setText("Permission denied — cannot start");
  }
  @Override protected void onStart(){
    super.onStart();
    IntentFilter f=new IntentFilter(BoosterService.STATUS);
    if(Build.VERSION.SDK_INT>=33)registerReceiver(updates,f,Context.RECEIVER_NOT_EXPORTED);
    else registerReceiver(updates,f);
    listening=true;status.setText(BoosterService.lastStatus);
  }
  @Override protected void onStop(){
    if(listening){unregisterReceiver(updates);listening=false;}super.onStop();
  }
}
