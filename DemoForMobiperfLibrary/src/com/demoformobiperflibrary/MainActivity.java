package com.demoformobiperflibrary;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import android.os.Bundle;
import android.os.Parcelable;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.view.Menu;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;

import com.demoformobiperflibrary.R;


import com.mobiperf_library.MeasurementResult;
import com.mobiperf_library.MeasurementTask;
import com.mobiperf_library.UpdateIntent;

import com.mobiperf_library.api.API;
import com.mobiperf_library.exceptions.MeasurementError;

public class MainActivity extends Activity {
  private ListView consoleView;
  private ArrayAdapter<String> resultList;

  private API api;
  private BroadcastReceiver broadcastReceiver;
  private int counter = 0;
  private String clientKey;
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Logger.d("MainActivity-> onCreate called");
    setContentView(R.layout.activity_main);

    /**
     * Initialize API object with a unique string, called client key
     * It stands for you app, don't use string that may be  
     */
    this.clientKey = "mobiperf library demo";
    this.api = API.getAPI(this, clientKey);
    
    /**
     * Register an broadcast receiver in the activity which needs to process 
     * measurement results. There are two actions:
     * 1. api.userResultAction: results of USER_PRIORITY task.
     *      this string is not a constant, it will include the client key
     * 2. API.SERVER_RESULT_ACTION: results of other priority task
     */
    IntentFilter filter = new IntentFilter();
    filter.addAction(api.userResultAction);
    filter.addAction(API.SERVER_RESULT_ACTION);
    broadcastReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        /**
         * Timestamps for delay measurement
         */
        long lTsApiSend = 0l;
        long lTsSchedulerRecv = 0l;
        long lTsApiRecv = System.currentTimeMillis();
        long lTsSchedulerSend = intent.getLongExtra("ts_scheduler_send", 0l);
        
        /**
         * The result is delivered by Parcel. Need reconstructing
         * Notice that it is possible for one task to have multiple results
         * if it is an parallel or sequential task
         */
        Logger.e("Get result for task " +
            intent.getParcelableExtra(UpdateIntent.TASKID_PAYLOAD));
        Parcelable[] parcels =
            intent.getParcelableArrayExtra(UpdateIntent.RESULT_PAYLOAD);
        MeasurementResult[] results = null;
        if ( parcels != null ) {
          results = new MeasurementResult[parcels.length];
          for ( int i = 0; i < results.length; i++ ) {
            results[i] = (MeasurementResult) parcels[i];
          }
        }
        
        /**
         * Process measurement result: just print it on screen
         * Three delays are logged: API->Scheduler, Scheduler->API, Round trip
         */
        if ( results != null ) {
          for ( MeasurementResult r : results ) {
            resultList.insert(r.toString(), 0);
            String sTsApiSend = r.getParameter("ts_api_send");
            String sTsSchedulerRecv = r.getParameter("ts_scheduler_recv");
            if ( sTsApiSend != null ) {
              lTsApiSend = Long.parseLong(sTsApiSend);
            }
            if ( sTsSchedulerRecv != null ) {
              lTsSchedulerRecv = Long.parseLong(sTsSchedulerRecv);
            }
            long delayApiToScheduler = lTsSchedulerRecv - lTsApiSend;
            long delaySchedulerToApi = lTsApiRecv - lTsSchedulerSend;
            long delayEndToEnd = lTsApiRecv - lTsApiSend;
            Logger.e(delayApiToScheduler
              + " " + delaySchedulerToApi + " " + delayEndToEnd);
          }
        }
        else {
          resultList.insert("Task failed!", 0);
        }
        
        runOnUiThread(new Runnable() {
          public void run() { resultList.notifyDataSetChanged(); }
        });

      }
      
    };
    this.registerReceiver(broadcastReceiver, filter);

    this.consoleView = (ListView) this.findViewById(R.id.resultConsole);
    this.resultList = new ArrayAdapter<String>(getApplicationContext(), R.layout.list_item);
    this.consoleView.setAdapter(this.resultList);
    this.findViewById(R.id.resultConsole);
    /**
     * When the button is pressed, a measurement task will be submitted to be
     * executed. 
     * 1. DNS Lookup to www.google.com with server priority
     * 2. HTTP request to www.google.com with user priority
     * 3. Sequentially run a)HTTP b)DNS Lookup to www.google.com
     *      with user priority
     * 4. Run both a) Traceroute and b) PING to www.google.com parallelly 
     *      with user priority
     */
    Button button = (Button)this.findViewById(R.id.start_measurement);  
    button.setText("Start Measurement");  
    button.setOnClickListener(new View.OnClickListener()   
    {
      public void onClick(View view) 
      {
        Map<String, String> params = new HashMap<String, String>();
        int priority = MeasurementTask.USER_PRIORITY;
        Date endTime = null;
        int contextIntervalSec = 1;
        MeasurementTask task = null;
        ArrayList<MeasurementTask> taskList = new ArrayList<MeasurementTask>();
        try {
          switch (counter % 4) {   
            case 0:
              // Single task with server level priority
              params.put("target","www.google.com");
              priority = MeasurementTask.INVALID_PRIORITY;
              task = api.createTask(API.TaskType.DNSLOOKUP
                , Calendar.getInstance().getTime(), endTime, 120, 1
                , priority, contextIntervalSec, params);
              break;
            case 1:
              // Single task with user level priority
              params.put("url","www.google.com");
              task = api.createTask(API.TaskType.HTTP
                , Calendar.getInstance().getTime(), endTime, 120, 1
                , priority, contextIntervalSec, params);
              break;
            case 2:
              // add sequential task
              params.put("url","www.google.com");
              task = api.createTask(API.TaskType.HTTP
                , Calendar.getInstance().getTime(), endTime, 120, 1
                , priority, contextIntervalSec, params);
              taskList.add(task);

              params.put("target","www.google.com");
              task = api.createTask(API.TaskType.DNSLOOKUP
                , Calendar.getInstance().getTime(), endTime, 120, 1
                , priority, contextIntervalSec, params);
              taskList.add(task);

              task = api.composeTasks(API.TaskType.SEQUENTIAL
                , Calendar.getInstance().getTime(), endTime, 120, 1
                , priority, contextIntervalSec, params, taskList);
              break;  
            case 3:
              // add parallel task
              params.put("target","www.google.com");
              task = api.createTask(API.TaskType.TRACEROUTE
                , Calendar.getInstance().getTime(), endTime, 120, 1
                , priority, contextIntervalSec,  params);
              taskList.add(task);

              params.put("target","www.google.com");
              endTime = new Date(System.currentTimeMillis() + 5000L);
              task = api.createTask(API.TaskType.PING
                , Calendar.getInstance().getTime(), endTime, 120, 1
                , priority, contextIntervalSec,  params);
              taskList.add(task);

              task = api.composeTasks(API.TaskType.PARALLEL
                , Calendar.getInstance().getTime(), endTime, 120, 1
                , priority, contextIntervalSec,  params, taskList);
              break;   
          }
        } catch (MeasurementError e) {
          Logger.e(e.getMessage());
          return;
        }
        counter++;
        /**
         * Submit this task to the scheduler
         */
        try {
          api.submitTask(task);
        } catch (MeasurementError e) {
          Logger.e(e.getMessage());
        }
      }
    });
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    // Inflate the menu; this adds items to the action bar if it is present.
    getMenuInflater().inflate(R.menu.main, menu);
    return true;
  }

  @Override
  protected void onDestroy() {
    Logger.d("MainActivity -> onDestroy called");
    this.unregisterReceiver(broadcastReceiver);
    /**
     * If you wanna keep running server scheduled tasks after exiting the app,
     * don't add this line. Otherwise you should add this line in activity's
     * onDestroy() call back. 
     */
    api.unbind();
    super.onDestroy();
  }
}
