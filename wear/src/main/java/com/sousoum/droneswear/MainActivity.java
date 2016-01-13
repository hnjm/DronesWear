package com.sousoum.droneswear;

import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.wearable.activity.ConfirmationActivity;
import android.support.wearable.activity.WearableActivity;
import android.support.wearable.view.BoxInsetLayout;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;
import com.sousoum.shared.AccelerometerData;
import com.sousoum.shared.Message;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends WearableActivity implements SensorEventListener, GoogleApiClient.ConnectionCallbacks, MessageApi.MessageListener, NodeApi.NodeListener
{

    private static final SimpleDateFormat AMBIENT_DATE_FORMAT =
            new SimpleDateFormat("HH:mm", Locale.US);
    private static final String TAG = "WearMainActivity";

    private BoxInsetLayout mContainerView;
    private TextView mClockView;
    private Button mActionBt;

    private Handler mHandler;
    private SensorManager mManager;

    private Object mAcceleroLock;
    private float mGyroData[]={0,0,0};
    private float mAccData[]={0,0,0};

    private GoogleApiClient mGoogleApiClient;

    private Object mNodeLock;
    private ArrayList<Node> mNodes;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setAmbientEnabled();

        mContainerView = (BoxInsetLayout) findViewById(R.id.container);
        mClockView = (TextView) findViewById(R.id.clock);
        mActionBt = (Button) findViewById(R.id.button);

        mNodes = new ArrayList<>();
        mAcceleroLock = new Object();
        mNodeLock = new Object();

        mManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        mGoogleApiClient = new GoogleApiClient.Builder(this).addConnectionCallbacks(this).addApi(Wearable.API).build();
        mHandler = new Handler();

        mHandler.postDelayed(new Runnable()
        {
            @Override
            public void run()
            {

                sendSensorValues();

                mHandler.postDelayed(this, 250);

            }
        }, 500);
    }

    @Override
    protected void onStop()
    {
        super.onStop();
        mManager.unregisterListener(this);
        mHandler.removeCallbacksAndMessages(null);
    }

    @Override
    protected void onPause(){
        super.onPause();
        if(mGoogleApiClient != null && mGoogleApiClient.isConnected()){
            mGoogleApiClient.disconnect();
        }
    }

    @Override
    protected void onResume(){
        super.onResume();

        mManager.registerListener(this, mManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_UI);
        mManager.registerListener(this, mManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE), SensorManager.SENSOR_DELAY_UI);

        mGoogleApiClient.connect();

    }

    @Override
    public void onEnterAmbient(Bundle ambientDetails)
    {
        super.onEnterAmbient(ambientDetails);
        updateDisplay();
    }

    @Override
    public void onUpdateAmbient()
    {
        super.onUpdateAmbient();
        updateDisplay();
    }

    @Override
    public void onExitAmbient()
    {
        updateDisplay();
        super.onExitAmbient();
    }

    private void updateDisplay()
    {
        if (isAmbient())
        {
            mContainerView.setBackgroundColor(getResources().getColor(android.R.color.black));
            mClockView.setVisibility(View.VISIBLE);

            mClockView.setText(AMBIENT_DATE_FORMAT.format(new Date()));
        }
        else
        {
            mContainerView.setBackgroundResource(R.color.accent);
            mClockView.setVisibility(View.GONE);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event)
    {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                synchronized (mAcceleroLock)
                {
                    mAccData[0] = event.values[0];
                    mAccData[1] = event.values[1];
                    mAccData[2] = event.values[2];
                }
                break;
            case Sensor.TYPE_GYROSCOPE:
                mGyroData[0] = event.values[0];
                mGyroData[1] = event.values[1];
                mGyroData[2] = event.values[2];
                break;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy)
    {

    }

    private void sendSensorValues()
    {
        synchronized (mNodeLock) {
            if (!mNodes.isEmpty()) {
                AccelerometerData accelerometerData;
                synchronized (mAcceleroLock)
                {
                    accelerometerData = new AccelerometerData(
                            mAccData[0], mAccData[1], mAccData[2]);
                }
                Message.sendAcceleroMessage(accelerometerData, mNodes, mGoogleApiClient);
            }
        }
    }

    @Override
    public void onConnected(Bundle bundle)
    {
        Wearable.NodeApi.addListener(mGoogleApiClient, this);
        Wearable.MessageApi.addListener(mGoogleApiClient, this);

        PendingResult<NodeApi.GetConnectedNodesResult> results = Wearable.NodeApi.getConnectedNodes(mGoogleApiClient);
        results.setResultCallback(new ResultCallback<NodeApi.GetConnectedNodesResult>()
        {
            @Override
            public void onResult(NodeApi.GetConnectedNodesResult getConnectedNodesResult)
            {
                if (getConnectedNodesResult.getStatus().isSuccess())
                {
                    synchronized (mNodeLock)
                    {
                        mNodes.addAll(getConnectedNodesResult.getNodes());
                    }
                }
            }
        });
    }

    @Override
    public void onConnectionSuspended(int i)
    {

    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent)
    {
        switch (Message.getMessageType(messageEvent)) {
            case ACTION_TYPE:
                int productAction = Message.decodeActionTypeMessage(messageEvent);
                onActionTypeChanged(productAction);
                break;
        }
    }

    private void onActionTypeChanged(int actionType)
    {
        switch (actionType) {
            case Message.ACTION_TYPE_NONE:
                mActionBt.setVisibility(View.GONE);
                break;
            case Message.ACTION_TYPE_JUMP:
                mActionBt.setText("JUMP");
                mActionBt.setVisibility(View.VISIBLE);
                break;
            case Message.ACTION_TYPE_TAKE_OFF:
                mActionBt.setText("TAKE OFF");
                mActionBt.setVisibility(View.VISIBLE);
                break;
            case Message.ACTION_TYPE_LAND:
                mActionBt.setText("LAND");
                mActionBt.setVisibility(View.VISIBLE);
                break;
        }
    }

    @Override
    public void onPeerConnected(Node node)
    {
        synchronized (mNodeLock)
        {
            Log.e(TAG, "Adding node = " + node);
            mNodes.add(node);
        }
    }

    @Override
    public void onPeerDisconnected(Node node)
    {
        synchronized (mNodeLock)
        {
            Log.e(TAG, "removing node = " + node);
            mNodes.remove(node);
        }
    }

    public void onButtonClicked(View view)
    {
        synchronized (mNodeLock) {
            if (!mNodes.isEmpty()) {
                Message.sendActionMessage(mNodes, mGoogleApiClient);

                Intent intent = new Intent(this, ConfirmationActivity.class);
                intent.putExtra(ConfirmationActivity.EXTRA_ANIMATION_TYPE,
                        ConfirmationActivity.SUCCESS_ANIMATION);
                intent.putExtra(ConfirmationActivity.EXTRA_MESSAGE,
                        "Action sent");
                startActivity(intent);
            }
        }
    }
}
