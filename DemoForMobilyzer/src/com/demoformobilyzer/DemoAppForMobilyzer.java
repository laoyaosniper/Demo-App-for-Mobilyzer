package com.demoformobilyzer;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
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
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.ToggleButton;
import android.widget.CompoundButton.OnCheckedChangeListener;

import com.mobilyzer.MeasurementResult;
import com.mobilyzer.MeasurementScheduler.DataUsageProfile;
import com.mobilyzer.MeasurementScheduler.TaskStatus;
import com.mobilyzer.MeasurementTask;
import com.mobilyzer.UpdateIntent;

import com.mobilyzer.api.API;
import com.mobilyzer.exceptions.MeasurementError;

public class DemoAppForMobilyzer extends Activity {
  private Button button;
  private ToggleButton showUserResultButton;
  private ToggleButton showSystemResultButton;
  private boolean userResultsActive;
  private ListView consoleView;
  private ArrayAdapter<String> resultList;
  private ArrayList<String> userResults;
  private ArrayList<String> serverResults;

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
     * Now we can immediately use Mobilyzer API after created.
     */
    MeasurementTask task = null;
    HashMap<String, String> params = new HashMap<String, String>();
    params.put("target", "www.google.com");
    try {
      task = api.createTask(API.TaskType.DNSLOOKUP
        , Calendar.getInstance().getTime(), null, 120, 1
        , MeasurementTask.USER_PRIORITY, 1, params);
      api.submitTask(task);
    } catch (MeasurementError e) {
      Logger.e("Expected Error", e);
    }
    
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
    filter.addAction(api.batteryThresholdAction);
    filter.addAction(api.checkinIntervalAction);
    filter.addAction(api.taskStatusAction);
    filter.addAction(api.dataUsageAction);
    
    broadcastReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        String text = intent.getAction();
        if ( intent.getAction().equals(api.batteryThresholdAction) ) {
          text = "Battery Threshold is "
              + intent.getIntExtra(UpdateIntent.BATTERY_THRESHOLD_PAYLOAD, -1);
          Logger.e(text);
          resultList.insert(text, 0);
          return;
        }
        else if ( intent.getAction().equals(api.checkinIntervalAction)) {
          text = "Checkin interval is "
              + intent.getLongExtra(UpdateIntent.CHECKIN_INTERVAL_PAYLOAD, -1);
          Logger.e(text);
          resultList.insert(text, 0);
          return;
        }
        else if ( intent.getAction().equals(api.taskStatusAction)) {
          text = "Task status for task "
              + intent.getStringExtra(UpdateIntent.TASKID_PAYLOAD) + " is "
              + (TaskStatus)intent.getSerializableExtra(UpdateIntent.TASK_STATUS_PAYLOAD);
          Logger.e(text);
          resultList.insert(text, 0);
          return;
        }
        else if ( intent.getAction().equals(api.dataUsageAction)) {
          text = "Data usage profile is "
              + (DataUsageProfile)intent.getSerializableExtra(UpdateIntent.DATA_USAGE_PAYLOAD);
          Logger.e(text);
          resultList.insert(text, 0);
          return;
        }
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
            intent.getStringExtra(UpdateIntent.TASKID_PAYLOAD));
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
        boolean isUIChange = false;
        if ( results != null ) {
          for ( MeasurementResult r : results ) {
            String resultLog = r.toString();
            if ( intent.getAction().equals(api.userResultAction)) {
              userResults.add(resultLog);
              if ( userResultsActive ) {
                resultList.insert(resultLog, 0);
                isUIChange = true;
              }
            }
            else {
              serverResults.add(r.toString());
              if ( !userResultsActive ) {
                resultList.insert(resultLog, 0);
                isUIChange = true;
              }
            }
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

        if ( isUIChange ) {
          runOnUiThread(new Runnable() {
            public void run() { resultList.notifyDataSetChanged(); }
          });
        }

      }
      
    };
    this.registerReceiver(broadcastReceiver, filter);

    showUserResultButton = (ToggleButton) findViewById(R.id.showUserResults);
    showSystemResultButton = (ToggleButton) findViewById(R.id.showSystemResults);
    userResults = new ArrayList<String>();
    serverResults = new ArrayList<String>();
    showUserResultButton.setChecked(true);
    showSystemResultButton.setChecked(false);
    userResultsActive = true;
    
    // We enforce a either-or behavior between the two ToggleButtons
    OnCheckedChangeListener buttonClickListener = new OnCheckedChangeListener() {
      @Override
      public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        Logger.d("onCheckedChanged");
        userResultsActive = (buttonView == showUserResultButton ? isChecked : !isChecked);

        resultList.clear();
        final List<String> scheduler_results =
            (userResultsActive ? userResults : serverResults);
        for (String result : scheduler_results) {
          resultList.insert(result, 0);
        }
        
        showUserResultButton.setChecked(userResultsActive);
        showSystemResultButton.setChecked(!userResultsActive);
        Logger.d("switchBetweenResults: showing " + resultList.getCount() + " " +
                 (userResultsActive ? "user" : "system") + " results");
        runOnUiThread(new Runnable() {
          public void run() { resultList.notifyDataSetChanged(); }
        });
      }
    };
    showUserResultButton.setOnCheckedChangeListener(buttonClickListener);
    showSystemResultButton.setOnCheckedChangeListener(buttonClickListener);
    
    this.consoleView = (ListView) this.findViewById(R.id.resultConsole);
    this.resultList = new ArrayAdapter<String>(getApplicationContext(),
        R.layout.list_item);
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
    button = (Button)this.findViewById(R.id.start_measurement);  
    button.setText("Submit a measurement task");  
    button.setOnClickListener(new View.OnClickListener()   
    {
      private MeasurementTask prevTask = null;
      public void onClick(View view) 
      {
        Map<String, String> params = new HashMap<String, String>();
        int priority = MeasurementTask.USER_PRIORITY;
        Date endTime = null;
        int contextIntervalSec = 1;
        MeasurementTask task = null;
        ArrayList<MeasurementTask> taskList = new ArrayList<MeasurementTask>();
        try {
          switch (counter % 10) {   
            case 0:
              /**
               * Parallel Task
               */
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
            case 1:
              /**
               * Sequential Task
               */
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
            case 2:
              /** 
               * TCP Throughput task (Downlink)
               * Optional parameter:
               *    dir_up: set to "Up" or "true" for uplink test. By default it
               *       will run downlink test .
               *    data_limit_mb_up: Data limitation for uplink
               *    data_limit_mb_down: Data limitation for downlink
               *    duration_period_sec: Upperbound for entire measurement period 
               *    pkt_size_up_bytes: Packet size for uplink test  
               *    sample_period_sec: The length of sample window which is used
               *            to calculate TCP throughput
               *    slow_start_period_sec: Time to skip slow start period
               *    target: Currently only support MLab
               *    tcp_timeout_sec: Timeout value for establishing
               *                     TCP connection
               * 
               */
              task = api.createTask(API.TaskType.TCPTHROUGHPUT
                , Calendar.getInstance().getTime(), endTime, 120, 1
                , priority, contextIntervalSec, params);
              break;
            case 3: 
              /** 
               * TCP Throughput task (Uplink)
               */
              params.put("dir_up", "true");
              task = api.createTask(API.TaskType.TCPTHROUGHPUT
                , Calendar.getInstance().getTime(), endTime, 120, 1
                , priority, contextIntervalSec, params);
              break;
            case 4:
              /** 
               * Ping task
               * Required parameter: 
               *   target - the host name or IP address of the server to ping
               * Optional parameters:
               *   packet_size_byte - the packet per ICMP ping in the
               *     unit of bytes. Default to 56.
               *   ping_timeout_sec - the number of seconds we wait
               *     for a ping response. Default to 0.5.
               */
              params.put("target", "www.google.com");
              task = api.createTask(API.TaskType.PING
                , Calendar.getInstance().getTime(), endTime, 120, 1
                , priority, contextIntervalSec, params);
              break;
            case 5:
              /** 
               * Traceroute task
               * Required parameter:
               *   target - the hostname or IP address to use as the
               *     target of the traceroute.
               * Optional parameters:
               *   packet_size_byte - the packet per ICMP ping in the
               *     unit of bytes. Default to 56.
               *   ping_timeout_sec - the number of seconds we wait
               *     for a ping response. Default to 2.
               *   ping_interval_sec - the interval between successive
               *     pings in seconds. Default to 0.5.
               *   pings_per_hop - the number of pings we use for
               *     each ttl value. Default to 3.
               *   max_hop_count - the total number of hops we ping
               *     before we declare the traceroute fails. Default to 10.
               */
              params.put("target", "www.google.com");
              task = api.createTask(API.TaskType.TRACEROUTE
                , Calendar.getInstance().getTime(), endTime, 120, 1
                , priority, contextIntervalSec, params);
              break;
            case 6:
              /** 
               * UDP Burst task (Downlink)
               */
              task = api.createTask(API.TaskType.UDPBURST
                , Calendar.getInstance().getTime(), endTime, 120, 1
                , priority, contextIntervalSec, params);
              break;
            case 7:
              /** 
               * UDP Burst task (Downlink)
               */
              params.put("direction", "Up");
              task = api.createTask(API.TaskType.UDPBURST
                , Calendar.getInstance().getTime(), endTime, 120, 1
                , priority, contextIntervalSec, params);
              break;
            
            case 8:  
                /** 
                 * DNS lookup task
                 * Required parameter:
                 *   target - Hostname of the target to resolve
                 * Optional parameter:
                 *   server - IP address of a DNS server to use as the resolver.
                 *     If not present, the device's default resolver is used.
                 */
                params.put("target", "www.google.com");
                task = api.createTask(API.TaskType.DNSLOOKUP
                  , Calendar.getInstance().getTime(), endTime, 120, 1
                  , priority, contextIntervalSec, params);
                break;
            case 9:
                /** 
                 * HTTP Get task
                 * Required parameter:
                 *   url - URL to request
                 * Optional parameters:
                 *   method - HTTP method to use. Defaults to "GET"
                 *   headers - String (possibly containing newlines) with
                 *     additional headers to send with the request. Each header
                 *     and value pair is in the form of "headerParam:value", with
                 *     different pairs separated by "\r\n".
                 *   body - String with the request body to send (if method is "POST")
                 */
                params.put("url", "www.google.com");
                task = api.createTask(API.TaskType.HTTP
                  , Calendar.getInstance().getTime(), endTime, 120, 1
                  , priority, contextIntervalSec, params);
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
          /**
           * How to cancel a task
           */
//          if ( counter > 1 ) api.cancelTask(prevTask.getTaskId());
          api.submitTask(task);
          
          /**
           * How to set and get batteryThreshold (by default threshold is 60)
           * When the battery is below threshold, checkin will never be performed
           * Currently this rule will take effect even the device is on charge  
           */
//          api.setBatteryThreshold(62);
//          api.getBatteryThreshold();
          /**
           * How to set and get checkin interval (by default 3600sec = 1h)
           * the scheduler will checkin to openmobiledata.appspot.com
           * every checkin_interval
           */
//          api.setCheckinInterval(18000);
//          api.getCheckinInterval();
          /**
           * How to get the task running status for submitted task
           */
//          api.getTaskStatus(task.getTaskId());
          /**
           * How to set and get current data limitation profile
           * We put several data limitation type here to prevent cause huge data
           * consumption under cellular network. Later we will put detailed 
           * explanation here. 
           */
//          api.setDataUsage(DataUsageProfile.PROFILE2);
//          api.getDataUsage();
        } catch (MeasurementError e) {
          Logger.e("Error occured: " + e.getMessage());
        }
        prevTask = task;
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