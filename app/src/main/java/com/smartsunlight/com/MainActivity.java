package com.smartsunlight.com;

import android.Manifest;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Message;
import android.provider.SyncStateContract;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.util.EntityUtils;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class MainActivity extends Activity
{
    private static final String TAG = "MainActivity";
	String gHttpAuthToken;


	//-------------- RAK811  config--------------

	int qos[] = {2};

	private static final String DeviceEUI = "1122334455667788";

	static final String ApplicationID = "1";

	//application/1/device/60c5a8fffe777c2a/rx
	String subscribeTopic[] = {"application/" + ApplicationID + "/device/" + DeviceEUI + "/rx"};

	 private static final String publishTopic = "application/" + ApplicationID + "/device/" + DeviceEUI + "/tx";
	 //private static final String messageStr = "{\"confirmed\":false,\"fPort\":10,\"data\":\"MDEyMzQ1Njc4OQ==\"}";
     
    //private static final String publishTopic = "device/rx/" + DeviceEUI;
    private static final String messageStr = "{\"confirmed\":false,\"devEUI\":\"" + DeviceEUI + "\",\"fPort\":1,\"data\":\"SSBMb3ZlIENoaW5hIQ==\"}";


    Button gStartPublishButton;
    Button gStopPublishButton;
    Button gClearStatus;
    Button gOnlyLoopPublishButtion;


    private int gTickCnt = 0;
    private int gTickTaskCnt = 0;

    private static int sendCnt = 0;
    private static int recvCnt = 0;

    private static boolean startFlag = false;

    private static boolean sendingflag = false;

    private static boolean pingPongModeFlag = false;
    private static boolean loopPublishModeFlag = false;

    final int TickInterval = 200;

    final int ReceivedTimeoutTick = 100;

    //final int LoopPublishTick = 50;
    final int LoopPublishTick = 5;

    final int SendInterval = 1000; /*TickInterval*/

    TextView titleText;
    TextView modeText;

    TextView subscribeText;

    TextView publishText;
    TextView messageText;


    TextView sendCntText;
    TextView recvCntText;

    Timer gTimer = null;
    TimerTask gTimerTask = null;

    Handler gPostHandler = null;
    Runnable gPosRunnable = null;


    static final int MY_R_STORAGE_PERMISSIONS_REQUEST_CODE = 1;
    static final int MY_W_STORAGE_PERMISSIONS_REQUEST_CODE = 2;
    static final int MY_R_PHONE_PERMISSIONS_REQUEST_CODE = 3;
    static final int MY_A_INTERNET_PERMISSIONS_REQUEST_CODE = 4;
    static final int MY_R_NETS_PERMISSIONS_REQUEST_CODE = 5;
    static final int MY_PERMISSIONS_REQUEST_READ_SDCARD = 6;


    static final int UPDATA_RECV_CNT = 1;
	static final int UPDATA_SEND_CNT = 2;
	
    static final int SEND_RUNABLE = 3;
    static final int UPDATA_RECV_CNT_RUNABLE = 8;
	
    private Lock gRunableTaskLock = new ReentrantLock();// 锁对象
    private Lock gMqttTimerLock = new ReentrantLock();// 锁对象
    private Lock gTickTimerLock = new ReentrantLock();// 锁对象
    private Lock gMsgLock = new ReentrantLock();

	
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        EventBus.getDefault().register(this);


        initView();

        startRequestPermissions();

        startService(new Intent(this, MQTTService.class));
		
        //一般订阅的内容都是从服务器获取
        //这里写获取服务器订阅主题的代码

		Log.w(MainActivity.TAG, "[M]onCreate!!!");
    }

    Handler gMsgHandler = new Handler()
    {
    
        @Override
        public void handleMessage(Message msg)
        {
            gMsgLock.lock();
            
            Log.d(MainActivity.TAG, "[M]Handle Message:" + msg.what);

            switch (msg.what)
            {
                case UPDATA_RECV_CNT:
                    recvCnt++;
                    recvCntText.setText("[Recv]" + recvCnt);
                    break;
					
				case UPDATA_SEND_CNT:
                    sendCnt++;
                    sendCntText.setText("[Send]" + sendCnt);
					break;

                case SEND_RUNABLE:
                    
                    sendCnt++;
                    sendCntText.setText("[Send]" + sendCnt);
                    
                    if((gPostHandler != null)&&(gPosRunnable != null))
                    {
                        gPostHandler.postDelayed(gPosRunnable, SendInterval);
                    }
                    break;

                case UPDATA_RECV_CNT_RUNABLE:/* UPDATA_RECV_CNT + SEND_RUNABLE*/
                    
                    recvCnt++;
                    recvCntText.setText("[Recv]" + recvCnt);
                    
                    sendCnt++;
                    sendCntText.setText("[Send]" + sendCnt);

					
                    if((gPostHandler != null)&&(gPosRunnable != null))
                    {
                        gPostHandler.postDelayed(gPosRunnable, SendInterval);
                    }
  
                    sendingflag = false;
                    break;
            }
            
            gMsgLock.unlock();
        }
    };



    private void initView()
    {

        titleText = (TextView) findViewById(R.id.Title);
        modeText = (TextView) findViewById(R.id.Mode);

        subscribeText = (TextView) findViewById(R.id.Subscribe);
        publishText = (TextView) findViewById(R.id.Publish);
        messageText = (TextView) findViewById(R.id.Message);


        sendCntText = (TextView) findViewById(R.id.SendCnt);
        recvCntText = (TextView) findViewById(R.id.RecvCnt);


        gTimer = new Timer();
        gTimerTask = new TimerTask()
        {
            @Override
            public void run()
            {
                gTickTimerLock.lock();

                try
                {
                    gTickCnt++;

                    //MainActivity.publish(messageStr, publishTopic); //Arrays.toString(topic)
                    //holder.post(url,"",200);

                    if(loopPublishModeFlag)
                    {
                        gTickTaskCnt++;

                        if(gTickTaskCnt%10 == 0)
                            Log.d(MainActivity.TAG, "[M]Task!" + gTickTaskCnt);

                        if(gTickTaskCnt > LoopPublishTick)/*every LoopPublishTick*TickInterval try start onece*/
                        {
                            gTickTaskCnt = 0;

                            Message message = new Message();
                            message.what = SEND_RUNABLE;
                            gMsgHandler.sendMessage(message);

                        }
                    }



                    if(pingPongModeFlag)
                    {
                    
                        if(sendingflag)
                        {
                            gTickTaskCnt++;

                            if(gTickTaskCnt%10 == 0)
                                Log.d(MainActivity.TAG, "[M]Task!" + gTickTaskCnt);

                            if(gTickTaskCnt > ReceivedTimeoutTick)/*every ReceivedTimeoutTick*TickInterval try start onece*/
                            {
                                gTickTaskCnt = 0;
                                sendingflag = false;

                                Message message = new Message();
                                message.what = SEND_RUNABLE;
                                gMsgHandler.sendMessage(message);
                            } 
                        }
                        else
                        {
                            gTickTaskCnt = 0;
                        }
                    }
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }

                gTickTimerLock.unlock();
            }
        };


        /*Start Timer*/
        gTimer.schedule(gTimerTask,TickInterval,TickInterval);


        gPostHandler = new Handler();
        gPosRunnable = new Runnable()
        {
            @Override
            public void run()
            {
                gRunableTaskLock.lock();// 得到锁

                try
                {
                    Log.d(MainActivity.TAG, "[M]At Runnable Publish Topic:" + messageStr);
                    Log.d(MainActivity.TAG, "[M]Topic: " + publishTopic);
                    Log.d(MainActivity.TAG, "[M]Msg: " + messageStr);

                    MQTTService.publish(messageStr, publishTopic);
					
					sendingflag = true;

                    Log.d(MainActivity.TAG, "[M]At Runnable Exit!");
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
                
                gRunableTaskLock.unlock();// 释放锁
            }
        };


        /*Only Loop Pulish Button*/
        gOnlyLoopPublishButtion = (Button) findViewById(R.id.LoopPublish);
        gOnlyLoopPublishButtion.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                if(!startFlag)
                    startFlag = true;
                else
                    return;

                loopPublishModeFlag = true;

                gTickTaskCnt = 0;

                modeText.setText("Start Pulish, Loop Mode");

                Message message = new Message();
                message.what = SEND_RUNABLE;
                gMsgHandler.sendMessage(message);

            }
        });


        /*Ping-Pong Pulish Button*/
        gStartPublishButton = (Button) findViewById(R.id.PingPongPublish);
        gStartPublishButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                if(!startFlag)
                    startFlag = true;
                else
                    return;

                pingPongModeFlag = true;

                gTickTaskCnt = 0;

                modeText.setText("Start Pulish, Ping-Pong Mode");

                Message message = new Message();
                message.what = SEND_RUNABLE;
                gMsgHandler.sendMessage(message);
            }

        });


        /*Stop Running*/
        gStopPublishButton = (Button) findViewById(R.id.StopPublish);
        gStopPublishButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick (View view)
            {
                gPostHandler.removeCallbacks(gPosRunnable);

                //gTimerTask.cancel();

                sendingflag = false;

                loopPublishModeFlag = false;

                pingPongModeFlag = false;

                startFlag = false;

                modeText.setText("Stop");
            }
        });


        /*Clear Status*/
        gClearStatus = (Button) findViewById(R.id.ClearStatus);
        gClearStatus.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick (View view)
            {
                sendCnt = 0;
                recvCnt = 0;

                gTickTaskCnt = 0;

                sendCntText.setText("");
                recvCntText.setText("");
            }
        });

    }


    @Subscribe(threadMode = ThreadMode.MAIN)
    public void getMqttMessage(MQTTMessage mqttMessage)
    {
        gMqttTimerLock.lock();// 得到锁

        Log.e(MQTTService.TAG, "get message:" + mqttMessage.getMessage());
        //Toast.makeText(this, mqttMessage.getMessage(), Toast.LENGTH_SHORT).show();


        if(sendingflag
           && pingPongModeFlag
           &&(!loopPublishModeFlag))

        {
            Message message = new Message();
            message.what = UPDATA_RECV_CNT_RUNABLE;
            gMsgHandler.sendMessage(message);

        }
        else
        {
            Message message = new Message();
            message.what = UPDATA_RECV_CNT;
            gMsgHandler.sendMessage(message);
        }

        gMqttTimerLock.unlock();// 释放锁

    }



    @Subscribe(threadMode = ThreadMode.MAIN)
    public void getMqttConMessage(MQTTConMessage isConnection)
    {
        //连接成功发送过来的通知，连接成功才能去订阅消息。
        if(isConnection.getMessage().equals("connect"))
        {
            titleText.setText("Join LoRa Gateway Success!");

            subscribeText.setText("Subscribe Topic:  " + subscribeTopic[0]);

            publishText.setText("Publish Topic:  " + publishTopic);

            messageText.setText("Message:  " + messageStr);

            //订阅消息
            //注意：订阅单个主题我不多说，但是如果需要一次订阅多个主题，需要用for循环去订阅多个，这可能是MQTT的bug。
            MQTTService.subscribe(subscribeTopic, qos);

        }
        else
        {
        	;
        }

    }

    @Override
    protected void onDestroy()
    {
        EventBus.getDefault().unregister(this);
        super.onDestroy();

        if (ContextCompat.checkSelfPermission(this,
                                              Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED)
        {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE))
            {

                // Show an expanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.

            }
            else
            {

                // No explanation needed, we can request the permission.

                ActivityCompat.requestPermissions(this,
                                                  new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE},
                                                  MY_PERMISSIONS_REQUEST_READ_SDCARD);

                // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                // app-defined int constant. The callback method gets the
                // result of the request.
            }
        }
    }


    private void MQTTPostGetToken() {


		
        try {
            HttpClient httpClient = new DefaultHttpClient();

            String url = "http://lora.smartkit.io:8001/api/v1/user/login";

            HttpPost httpPost = new HttpPost(url);

            httpPost.setHeader("Content-Type", "application/json");

            JSONObject obj = new JSONObject();

            obj.put("password", "123456");
            obj.put("username", "15920174554");

            Log.e(MQTTService.TAG, obj.toString());
			
            httpPost.setEntity(new StringEntity(obj.toString()));
			
            HttpResponse httpResponse;

            httpClient.getParams().setParameter(CoreConnectionPNames.
                    CONNECTION_TIMEOUT, 10000);
            httpClient.getParams().setParameter(CoreConnectionPNames.
                    SO_TIMEOUT, 10000);

            httpResponse = httpClient.execute(httpPost);
            int code = httpResponse.getStatusLine().getStatusCode();
			

			try
            {
                String httpRev = "";

                if ( code == 200 ){
                    httpRev = EntityUtils.toString(httpResponse.getEntity());
                    Log.e(MQTTService.TAG, "CODE-RESULT:"+httpRev);
                }else{
                    Log.e(MQTTService.TAG, "CODE:"+code);
                }
				JSONObject root = new JSONObject(httpRev);
				
				gHttpAuthToken = root.getString("jwt");
				
                Log.e(MQTTService.TAG, gHttpAuthToken);
				

			} catch (JSONException e) {
				e.printStackTrace();
			}



			/*
			try {
				JSONObject root = new JSONObject(sBuilder.toString());
				Log.d("Tag", "cat=" + root.getString("cat")); 
				JSONArray array = root.getJSONArray("languages");
				for (int i = 0; i < array.length(); i++) {
					JSONObject lan = array.getJSONObject(i);
					Log.d("Tag", "---------------------------------");
					Log.d("Tag", "id=" + lan.getInt("id")); //注意get方法要与json中实际数据类型一致
					Log.d("Tag", "ide=" + lan.getString("ide"));
					Log.d("Tag", "name=" + lan.getString("name"));
				}
			} catch (JSONException e) {
				e.printStackTrace();
			}
			*/
			

        } catch (Exception e) {
            Log.e(MQTTService.TAG, "Exception:"+e.toString());
        }

    }

    private void MQTTPostHTTPData() {
        try {
            HttpClient httpClient = new DefaultHttpClient();

            String url = "http://lora.smartkit.io:8001/api/v1/nodes/ffffff1000013da1/queue";

            HttpPost httpPost = new HttpPost(url);

            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setHeader("Authorization", "bearer " + gHttpAuthToken);


            JSONObject obj = new JSONObject();

            obj.put("confirmed", "false");
            obj.put("data", "MDEyMzQ1Njc4OQ==");
            obj.put("devEUI", "ffffff1000013da1");
            obj.put("fPort", "10");
            obj.put("reference", "string");



            Log.e(MQTTService.TAG, obj.toString());
			
            //httpPost.setEntity(new StringEntity(obj.toString()));

			httpPost.setEntity(new StringEntity("{\"confirmed\":false,\"data\":\"MDEyMzQ1Njc4OQ==\",\"devEUI\":\"ffffff1000013da1\",\"fPort\":10,\"reference\":\"string\"}"));

			
            HttpResponse httpResponse;

            httpClient.getParams().setParameter(CoreConnectionPNames.
                    CONNECTION_TIMEOUT, 10000);
            httpClient.getParams().setParameter(CoreConnectionPNames.
                    SO_TIMEOUT, 10000);

            httpResponse = httpClient.execute(httpPost);
            int code = httpResponse.getStatusLine().getStatusCode();
            if ( code == 200 ){
                String rev = EntityUtils.toString(httpResponse.getEntity());
                Log.e(MQTTService.TAG, "CODE-RESULT:"+rev);
            }else{
                Log.e(MQTTService.TAG, "CODE:"+code);
            }


        } catch (Exception e) {

            Log.e(MQTTService.TAG, "Exception:"+e.toString());
        }

    }



    public void startRequestPermissions()
    {
        if (ContextCompat.checkSelfPermission(this,
                                              Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED)
        {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.READ_EXTERNAL_STORAGE))
            {

                Log.i(TAG, "onRequestPermissionsResult granted");

                // Show an expanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.

            }
            else
            {

                // No explanation needed, we can request the permission.

                ActivityCompat.requestPermissions(this,
                                                  new String[] {Manifest.permission.READ_EXTERNAL_STORAGE},
                                                  MY_R_STORAGE_PERMISSIONS_REQUEST_CODE);

                // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                // app-defined int constant. The callback method gets the
                // result of the request.
            }
        }


        /*
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.READ_PHONE_STATE)) {

                // Show an expanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.

            } else {

                // No explanation needed, we can request the permission.

                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_PHONE_STATE},
                        MY_R_PHONE_PERMISSIONS_REQUEST_CODE);

                // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                // app-defined int constant. The callback method gets the
                // result of the request.
            }
        }
        */

        if (ContextCompat.checkSelfPermission(this,
                                              Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED)
        {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE))
            {

                // Show an expanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.

            }
            else
            {

                // No explanation needed, we can request the permission.

                ActivityCompat.requestPermissions(this,
                                                  new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE},
                                                  MY_W_STORAGE_PERMISSIONS_REQUEST_CODE);

                // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                // app-defined int constant. The callback method gets the
                // result of the request.
            }
        }


        if (ContextCompat.checkSelfPermission(this,
                                              Manifest.permission.ACCESS_NETWORK_STATE)
            != PackageManager.PERMISSION_GRANTED)
        {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_NETWORK_STATE))
            {

                // Show an expanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.

            }
            else
            {

                // No explanation needed, we can request the permission.

                ActivityCompat.requestPermissions(this,
                                                  new String[] {Manifest.permission.ACCESS_NETWORK_STATE},
                                                  MY_R_NETS_PERMISSIONS_REQUEST_CODE);

                // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                // app-defined int constant. The callback method gets the
                // result of the request.
            }
        }


        if (ContextCompat.checkSelfPermission(this,
                                              Manifest.permission.INTERNET)
            != PackageManager.PERMISSION_GRANTED)
        {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.INTERNET))
            {

                // Show an expanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.

            }
            else
            {

                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(this,
                                                  new String[] {Manifest.permission.INTERNET},
                                                  MY_A_INTERNET_PERMISSIONS_REQUEST_CODE);

                // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                // app-defined int constant. The callback method gets the
                // result of the request.
            }
        }
    }


    private void showWaringDialog()
    {
        AlertDialog dialog = new AlertDialog.Builder(this)
        .setTitle("Permission Refuse")
        .setMessage("Setting/Permissions/Enable")
        .setPositiveButton("Sure", new DialogInterface.OnClickListener()
        {

            @Override
            public void onClick(DialogInterface dialog, int which)
            {
                //Exit!!!
                //finish();
            }
        }).show();
    }




    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults)
    {
        switch (requestCode)
        {

            case MY_R_STORAGE_PERMISSIONS_REQUEST_CODE:
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                {

                    Log.i(TAG, "Read Storage Granted");
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.

                }
                else
                {
                    Log.e(TAG, "Read Storage  Denied");
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    showWaringDialog();
                }
                break;

            case MY_W_STORAGE_PERMISSIONS_REQUEST_CODE:
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                {
                    Log.i(TAG, "Write Storage Granted");
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.

                }
                else
                {
                    Log.e(TAG, "Write Storage Denied");
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    showWaringDialog();
                }
                break;

            case MY_R_PHONE_PERMISSIONS_REQUEST_CODE:
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                {
                    Log.i(TAG, "Read Phone Granted");
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.

                }
                else
                {
                    Log.e(TAG, "Read Phone Denied");
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    showWaringDialog();
                }
                break;

            case MY_A_INTERNET_PERMISSIONS_REQUEST_CODE:
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                {
                    Log.i(TAG, "Acess Internet Granted");
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.

                }
                else
                {
                    Log.e(TAG, "Acess Internet  Denied");
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    showWaringDialog();
                }
                break;

            case MY_R_NETS_PERMISSIONS_REQUEST_CODE:
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                {
                    Log.i(TAG, "Read Net Status Granted");
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.

                }
                else
                {
                    Log.e(TAG, "Read Net Status Denied");
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.

                    showWaringDialog();
                }

                break;

        }

        // other 'case' lines to check for other
        // permissions this app might request
    }

}
