package com.example.matthew.calhacks;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.widget.TextView;
import android.support.v7.app.ActionBarActivity;
import com.google.android.gms.common.*;
import com.google.android.gms.common.api.*;
import com.google.android.gms.fitness.*;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.fitness.ListSubscriptionsResult;
import com.google.android.gms.fitness.Subscription;

public class StepCounter extends ActionBarActivity implements ConnectionCallbacks, OnConnectionFailedListener{

    private TextView mTextView;
    private GoogleApiClient mClient = null;
    public static final int REQUEST_OAUTH = 1; //possibly 0010? or 1000?
    public static final String STEPS_KEY = "steps"; //change later
    @Override
    protected void onStart() {
        super.onStart();
        if (mClient == null || !mClient.isConnected()) {
            connectFitness();
        }
    }

    private void connectFitness() {
        System.out.println("Connecting...");

        mClient = new GoogleApiClient.Builder(this)
                .addApi(Fitness.API)
                .addScope(FitnessScopes.SCOPE_ACTIVITY_READ_WRITE)
                .addScope(FitnessScopes.SCOPE_BODY_READ_WRITE)
                .addScope(FitnessScopes.SCOPE_LOCATION_READ_WRITE)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        mClient.connect();
    }

    // Manage OAuth authentication
    @Override
    public void onConnectionFailed(ConnectionResult result) {

        // Error while connecting. Try to resolve using the pending intent returned.
        if (result.getErrorCode() == ConnectionResult.SIGN_IN_REQUIRED ||
                result.getErrorCode() == FitnessStatusCodes.NEEDS_OAUTH_PERMISSIONS) {
            try {
                // Request authentication
                result.startResolutionForResult(this, REQUEST_OAUTH);
            } catch (IntentSender.SendIntentException e) {
                System.out.println("Exception connecting to the fitness service" + e);
            }
        } else {
            System.out.println("Unknown connection issue. Code = " + result.getErrorCode());
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_OAUTH) {
            if (resultCode == RESULT_OK) {
                // If the user authenticated, try to connect again
                mClient.connect();
            }
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        // If your connection gets lost at some point,
        // you'll be able to determine the reason and react to it here.
        if (i == ConnectionCallbacks.CAUSE_NETWORK_LOST) {
            System.out.println("Connection lost.  Cause: Network Lost.");
        } else if (i == ConnectionCallbacks.CAUSE_SERVICE_DISCONNECTED) {
            System.out.println("Connection lost.  Reason: Service Disconnected");
        }
    }

    @Override
    public void onConnected(Bundle bundle) {
        System.out.println("Connected!");

        // Now you can make calls to the Fitness APIs.
        invokeFitnessAPIs();
    }

    // Since there are multiple things you can do with a list of subscriptions (dump to log, mine
    // for data types, unsubscribe from everything) it's easiest to abstract out the part that
    // wants the list, and leave it to the calling method to decide what to do with the result.
    private PendingResult<ListSubscriptionsResult> getSubscriptionsList() {
        // Invoke a Subscriptions list request with the Recording API
        return Fitness.RecordingApi.listSubscriptions(mClient, DataTypes.ACTIVITY_SAMPLE);
    }

    public void invokeFitnessAPIs() {

        // 1. Invoke the Recording API with:
        // - The Google API client object
        // - The data type to subscribe to
        //PendingResult<Status> pendingResultOverTime =
        //       Fitness.RecordingApi.subscribe(mClient, DataTypes.STEP_COUNT_CUMULATIVE); //returns a cumulative total of steps

        new Thread() {
            public void run() {
                ListSubscriptionsResult subResults = getSubscriptionsList().await();
                boolean activitySubActive = false;
                for (Subscription sc : subResults.getSubscriptions()) {
                    if (sc.getDataType().equals(DataTypes.STEP_COUNT_CUMULATIVE)) {
                        activitySubActive = true;
                        break;
                    }
                }

                if (activitySubActive) {
                    System.out.println("Existing subscription for activity detection detected.");
                    return;
                }
                PendingResult<Status> pendingResultOverTime =
                    Fitness.RecordingApi.subscribe(mClient, DataTypes.STEP_COUNT_CUMULATIVE);
                Status st = pendingResultOverTime.await();
                if (st.isSuccess()) {
                    System.out.println("Successfully subscribed!");
                } else {
                    System.out.println("There was a problem subscribing.");
                }
            }
        }.start();
        // 2. Retrieve the result synchronously
        // (For the subscribe method, this call returns immediately)
        /**Status st = pendingResultOverTime.await();
        if (st.isSuccess()) {
            System.out.println("Successfully subscribed!");
        } else {
            System.out.println("There was a problem subscribing.");
        }**/

        //Charles: when STEP_COUNT_CUMULATIVE reaches certain levels, you want to give user congratulations notifications

        // 1. Specify what data sources to return
        DataSourcesRequest req = new DataSourcesRequest.Builder()
                .setDataSourceTypes(DataSource.TYPE_RAW)
                .setDataTypes(DataTypes.STEP_COUNT_DELTA)
                .build();

        // 2. Invoke the Sensors API with:
        // - The Google API client object
        // - The data sources request object
        PendingResult<DataSourcesResult> pendingResult =
                Fitness.SensorsApi.findDataSources(mClient, req);

        // 3. Obtain the list of data sources asynchronously
        pendingResult.setResultCallback(new ResultCallback<DataSourcesResult>() {
            @Override
            public void onResult(DataSourcesResult dataSourcesResult) {
                for (DataSource ds : dataSourcesResult.getDataSources()) {
                    String dsName = ds.getName();
                    Device device = ds.getDevice();
                    int steps = 0;
                    //if (device.describeContents() < 100)
                    //{
                        //sending data to phone that says that the person has not been active enough
                        PutDataMapRequest dataMap = PutDataMapRequest.create("/steps");
                        dataMap.getDataMap().putInt(STEPS_KEY, steps++);
                        PutDataRequest request = dataMap.asPutDataRequest();
                        PendingResult<DataApi.DataItemResult> pendingResultSend = Wearable.DataApi.putDataItem(mClient, request);
                    //}
                }
            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //setContentView(R.layout.activity_step_counter);
        setContentView(R.layout.notification_activity);
        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                mTextView = (TextView) stub.findViewById(R.id.text);
            }
        });
    }

}
