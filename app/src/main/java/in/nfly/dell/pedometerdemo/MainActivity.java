package in.nfly.dell.pedometerdemo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import java.text.NumberFormat;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private SensorManager sensorManager;
    private TextView infoTextView,stepsTextView;
    public final static String ACTION_PAUSE_STATE_CHANGED = "PAUSE_CHANGED";
    private boolean running=false;

    private int todayOffset, total_start, goal, since_boot, total_days;
    public final static NumberFormat formatter = NumberFormat.getInstance(Locale.getDefault());
    private boolean showSteps = true;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        infoTextView=findViewById(R.id.infoTextView);
        stepsTextView=findViewById(R.id.stepsTextView);

        sensorManager= (SensorManager) getSystemService(Context.SENSOR_SERVICE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Database db = Database.getInstance(MainActivity.this);

        if (BuildConfig.DEBUG) db.logState();
        // read todays offset
        todayOffset = db.getSteps(Util.getToday());

        SharedPreferences prefs =
                MainActivity.this.getSharedPreferences("pedometer", Context.MODE_PRIVATE);

        goal = prefs.getInt("goal", 1000);
        since_boot = db.getCurrentSteps();
        int pauseDifference = since_boot - prefs.getInt("pauseCount", since_boot);

        // register a sensorlistener to live update the UI if a step is taken
        SensorManager sm = (SensorManager) MainActivity.this.getSystemService(Context.SENSOR_SERVICE);
        Sensor sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        if (sensor == null) {
            new AlertDialog.Builder(MainActivity.this).setTitle("Sensor Issue")
                    .setMessage("Sensor Not Found")
                    .setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(final DialogInterface dialogInterface) {
                            MainActivity.this.finish();
                        }
                    }).setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(final DialogInterface dialogInterface, int i) {
                    dialogInterface.dismiss();
                }
            }).create().show();
        } else {
            sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI, 0);
        }

        since_boot -= pauseDifference;

        total_start = db.getTotalWithoutToday();
        total_days = db.getDays();

        db.close();

        //stepsDistanceChanged();

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_PAUSE_STATE_CHANGED);
        MainActivity.this.registerReceiver(pauseReceiver, filter);
        /*
        running=true;
        Sensor countSensor=sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        if(countSensor!=null){
            sensorManager.registerListener(this,countSensor,SensorManager.SENSOR_DELAY_UI);
        }
        else{
            Toast.makeText(this, "Sensor Not Found", Toast.LENGTH_SHORT).show();
        }*/
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            SensorManager sm =
                    (SensorManager) MainActivity.this.getSystemService(Context.SENSOR_SERVICE);
            sm.unregisterListener(this);
        } catch (Exception e) {
            if (BuildConfig.DEBUG){}
        }
        Database db = Database.getInstance(MainActivity.this);
        db.saveCurrentSteps(since_boot);
        db.close();
        try {
            MainActivity.this.unregisterReceiver(pauseReceiver);
        } catch (Exception e) {
            if (BuildConfig.DEBUG){}
        }
        //running=false;
        //sensorManager.unregisterListener(this);
    }

    private final BroadcastReceiver pauseReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            todayOffset -= intent.getIntExtra("stepsDuringPause", 0);
            MainActivity.this.invalidateOptionsMenu();
        }
    };
    @Override
    public void onSensorChanged(SensorEvent event) {
        /*if(running){
            stepsTextView.setText(String.valueOf(event.values[0]));
        }*/
        if (event.values[0] > Integer.MAX_VALUE || event.values[0] == 0 || false) {
            return;
        }
        if (todayOffset == Integer.MIN_VALUE) {
            // no values for today
            // we dont know when the reboot was, so set todays steps to 0 by
            // initializing them with -STEPS_SINCE_BOOT
            todayOffset = -(int) event.values[0];
            Database db = Database.getInstance(MainActivity.this);
            db.insertNewDay(Util.getToday(), (int) event.values[0]);
            db.close();
        }
        since_boot = (int) event.values[0];

        int steps_today = Math.max(todayOffset + since_boot, 0);
        stepsTextView.setText(formatter.format(steps_today));
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}
