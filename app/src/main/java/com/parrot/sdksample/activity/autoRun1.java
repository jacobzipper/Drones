package com.parrot.sdksample.activity;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_PICTUREEVENTCHANGED_ERROR_ENUM;
import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM;
import com.parrot.arsdk.arcontroller.ARCONTROLLER_DEVICE_STATE_ENUM;
import com.parrot.arsdk.arcontroller.ARControllerCodec;
import com.parrot.arsdk.arcontroller.ARFrame;
import com.parrot.arsdk.ardiscovery.ARDiscoveryDeviceService;
import com.parrot.sdksample.R;
import com.parrot.sdksample.classes.Wall;
import com.parrot.sdksample.drone.AutoDrone;
import com.parrot.sdksample.enums.Direction;
import com.parrot.sdksample.view.BebopVideoView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
/**
 * Icarus
 * Pine Crest School
 * Contact:
 *  Email: jacob.zipper@pinecrest.edu
 *  Phone: 954-740-1737
 */
public class autoRun1 extends AppCompatActivity  {
    private static final String TAG = "autoRun1";
    private AutoDrone mBebopDrone;
    private ProgressDialog mConnectionProgressDialog;
    private BebopVideoView mVideoView;
    private TextView mBatteryLabel;
    private Button mTakeOffLandBt;
    public Button emergencyButton;
    public TextView coords;

    /**
     * Creates the activity and loads the layout.
     * Also does some drone initialization.
     * @param savedInstanceState
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_autonomous);

        emergencyButton = (Button) findViewById(R.id.emergency);


        Intent intent = getIntent();

        ARDiscoveryDeviceService service = intent.getParcelableExtra(DeviceListActivity.EXTRA_DEVICE_SERVICE);
        try {
            mBebopDrone = new AutoDrone(this, service);
        } catch (IOException e) {
            e.printStackTrace();
        }
        mBebopDrone.addListener(mBebopListener);
        coords = (TextView) findViewById(R.id.coordinates);
        initIHM();


    }

    @Override
    protected void onStart() {
        super.onStart();

        // show a loading view while the bebop drone is connecting
        if ((mBebopDrone != null) && !(ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING.equals(mBebopDrone.getConnectionState())))
        {
            mConnectionProgressDialog = new ProgressDialog(this, R.style.AppCompatAlertDialogStyle);
            mConnectionProgressDialog.setIndeterminate(true);
            mConnectionProgressDialog.setMessage("Connecting ...");
            mConnectionProgressDialog.setCancelable(false);
            mConnectionProgressDialog.show();

            // if the connection to the Bebop fails, finish the activity
            if (!mBebopDrone.connect()) {
                finish();
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (mBebopDrone != null)
        {
            mConnectionProgressDialog = new ProgressDialog(this, R.style.AppCompatAlertDialogStyle);
            mConnectionProgressDialog.setIndeterminate(true);
            mConnectionProgressDialog.setMessage("Disconnecting ...");
            mConnectionProgressDialog.setCancelable(false);
            mConnectionProgressDialog.show();

            if (!mBebopDrone.disconnect()) {
                finish();
            }
        }
    }
    public void updateCoords() {
        Direction d = mBebopDrone.curDirection;
        int[] c = mBebopDrone.currentCoordinates;
        String set = "Coordinates: (" + c[0] + ", " + c[1] + ")\nDirection: " + d.toString();
        coords.setText(set);
    }

    /**
     * Sets all the onClick listeners for buttons in the UI
     * and controls most of the other UI related stuff.
     */
    private void initIHM() {

        mVideoView = (BebopVideoView) findViewById(R.id.videoView);
        mBebopDrone.setApp(this);
        emergencyButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mBebopDrone.emergency();
            }
        });
        mBebopDrone.eButton = emergencyButton;

        mTakeOffLandBt = (Button) findViewById(R.id.takeOffOrLandBt);
        mTakeOffLandBt.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                switch (mBebopDrone.getFlyingState())
                {
                    case ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_LANDED:
                        mBebopDrone.takeOff();
                        break;
                    case ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_FLYING:
                    case ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_HOVERING:
                        mBebopDrone.land();
                        writeToFile(mBebopDrone.log);
                        break;
                    default:
                }
            }
        });

        findViewById(R.id.updateCoords).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                EditText c1 = (EditText) findViewById(R.id.coord1);
                EditText c2 = (EditText) findViewById(R.id.coord2);
                int cone = Integer.parseInt(c1.getText().toString());
                int ctwo = Integer.parseInt(c2.getText().toString());
                mBebopDrone.currentCoordinates = new int[]{cone, ctwo};
                mBebopDrone.log+="You are now in coordinates (x,y): ("+cone+","+ctwo+")\n";
            }
        });

        findViewById(R.id.sendWall).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String color = "";
                if(((CheckBox)findViewById(R.id.dkbButton)).isChecked()) {
                    color = "Dark Blue";
                }
                else if(((CheckBox)findViewById(R.id.ltbButton)).isChecked()) {
                    color = "Light Blue";
                }
                else if(((CheckBox)findViewById(R.id.ylwButton)).isChecked()) {
                    color = "Yellow";
                }
                else if(((CheckBox)findViewById(R.id.prpButton)).isChecked()) {
                    color = "Purple";
                }
                else if(((CheckBox)findViewById(R.id.blkButton)).isChecked()) {
                    color = "Black";
                }
                else if(((CheckBox)findViewById(R.id.redButton)).isChecked()) {
                    color = "Red";
                }
                else if(((CheckBox)findViewById(R.id.ongButton)).isChecked()) {
                    color = "Orange";
                }
                else if(((CheckBox)findViewById(R.id.grnButton)).isChecked()) {
                    color = "Green";
                }
                else {
                    color = "White";
                }
                mBebopDrone.walls.add(
                        new Wall(
                                ((CheckBox)findViewById(R.id.dotBox)).isChecked(),
                                color,
                                mBebopDrone.curDirection,
                                new int[] { mBebopDrone.currentCoordinates[0], mBebopDrone.currentCoordinates[1]}));
                if(!(((CheckBox)findViewById(R.id.dotBox)).isChecked())) {
                    mBebopDrone.log +=
                            "\n\nPerson found at [" + mBebopDrone.currentCoordinates[0] + ", " + mBebopDrone.currentCoordinates[1]
                            + "]\nThe Color of the wall is " + color
                                    + "\nIt is on the " + mBebopDrone.curDirection.toString() + " side of the wall\n"
                            + "Please follow all the previous directions to reach the person\n\n";
                }
                // Take picture after sending the wall coordinates
                mBebopDrone.takePicture();
            }
        });

        findViewById(R.id.forwardOne).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mBebopDrone.moveForwardOneSpace();
                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        updateCoords();
                    }
                });
            }
        });
        findViewById(R.id.autoButton).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mBebopDrone.autonomous();
            }
        });

        findViewById(R.id.turnLeft).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mBebopDrone.turnLeft();
                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        updateCoords();
                    }
                });
            }
        });

        findViewById(R.id.turnRight).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mBebopDrone.turnRight();
                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        updateCoords();
                    }
                });
            }
        });

        findViewById(R.id.takePictureBt).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mBebopDrone.takePicture();
            }
        });


        findViewById(R.id.downloadBt).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                //mBebopDrone.getFirstPicture();
            }
        });

        findViewById(R.id.gazUpBt).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        mBebopDrone.setGaz((byte) 50);
                        break;
                    case MotionEvent.ACTION_UP:
                        v.setPressed(false);
                        mBebopDrone.setGaz((byte) 0);
                        break;
                    default:
                        break;
                }
                return true;
            }
        });


        findViewById(R.id.gazDownBt).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        mBebopDrone.setGaz((byte) -50);
                        break;

                    case MotionEvent.ACTION_UP:
                        v.setPressed(false);
                        mBebopDrone.setGaz((byte) 0);
                        break;

                    default:

                        break;
                }

                return true;
            }
        });

        findViewById(R.id.yawLeftBt).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        mBebopDrone.setYaw((byte) -50);
                        break;

                    case MotionEvent.ACTION_UP:
                        v.setPressed(false);
                        mBebopDrone.setYaw((byte) 0);
                        break;
                    default:

                        break;
                }

                return true;
            }
        });

        findViewById(R.id.yawRightBt).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        mBebopDrone.setYaw((byte) 50);
                        break;

                    case MotionEvent.ACTION_UP:
                        v.setPressed(false);
                        mBebopDrone.setYaw((byte) 0);
                        break;

                    default:

                        break;
                }

                return true;
            }
        });

        findViewById(R.id.forwardBt).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        mBebopDrone.setPitch((byte) 50);
                        mBebopDrone.setFlag((byte) 1);
                        break;

                    case MotionEvent.ACTION_UP:
                        v.setPressed(false);
                        mBebopDrone.setPitch((byte) 0);
                        mBebopDrone.setFlag((byte) 0);
                        break;

                    default:

                        break;
                }

                return true;
            }
        });

        findViewById(R.id.backBt).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        mBebopDrone.setPitch((byte) -50);
                        mBebopDrone.setFlag((byte) 1);
                        break;

                    case MotionEvent.ACTION_UP:
                        v.setPressed(false);
                        mBebopDrone.setPitch((byte) 0);
                        mBebopDrone.setFlag((byte) 0);
                        break;

                    default:

                        break;
                }

                return true;
            }
        });

        findViewById(R.id.rollLeftBt).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        mBebopDrone.setRoll((byte) -50);
                        mBebopDrone.setFlag((byte) 1);
                        break;

                    case MotionEvent.ACTION_UP:
                        v.setPressed(false);
                        mBebopDrone.setRoll((byte) 0);
                        mBebopDrone.setFlag((byte) 0);
                        break;

                    default:

                        break;
                }

                return true;
            }
        });

        findViewById(R.id.rollRightBt).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        mBebopDrone.setRoll((byte) 50);
                        mBebopDrone.setFlag((byte) 1);
                        break;

                    case MotionEvent.ACTION_UP:
                        v.setPressed(false);
                        mBebopDrone.setRoll((byte) 0);
                        mBebopDrone.setFlag((byte) 0);
                        break;

                    default:

                        break;
                }

                return true;
            }
        });

        mBatteryLabel = (TextView) findViewById(R.id.batteryLabel);
    }
    private void writeToFile(String data) {
        try {
            File path = getExternalFilesDir(null);
            File file = new File(path, "flightlog.txt");
            FileOutputStream stream = new FileOutputStream(file);
            try {
                stream.write(mBebopDrone.log.getBytes());
            } finally {
                stream.close();
            }
        }
        catch (IOException e) {
            Log.e("Exception", "File write failed: " + e.toString());
        }
    }

    private final AutoDrone.Listener mBebopListener = new AutoDrone.Listener() {
        @Override
        public void onDroneConnectionChanged(ARCONTROLLER_DEVICE_STATE_ENUM state) {
            switch (state)
            {
                case ARCONTROLLER_DEVICE_STATE_RUNNING:
                    mConnectionProgressDialog.dismiss();
                    break;

                case ARCONTROLLER_DEVICE_STATE_STOPPED:
                    // if the deviceController is stopped, go back to the previous activity
                    mConnectionProgressDialog.dismiss();
                    finish();
                    break;

                default:
                    break;
            }
        }



        @Override
        public void onBatteryChargeChanged(int batteryPercentage) {
            mBatteryLabel.setText(String.format("%d%%", batteryPercentage));
        }

        @Override
        public void onPilotingStateChanged(ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM state) {
            switch (state) {
                case ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_LANDED:
                    mTakeOffLandBt.setText("Take off");
                    mTakeOffLandBt.setEnabled(true);
                    break;
                case ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_FLYING:
                case ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_HOVERING:
                    mTakeOffLandBt.setText("Land");
                    mTakeOffLandBt.setEnabled(true);
                    break;
            }
        }



        @Override
        public void onPictureTaken(ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_PICTUREEVENTCHANGED_ERROR_ENUM error) {
            Log.i(TAG, "Picture has been taken");
        }

        @Override
        public void configureDecoder(ARControllerCodec codec) {
            mVideoView.configureDecoder(codec);
        }

        @Override
        public void onFrameReceived(ARFrame frame) {
            mVideoView.displayFrame(frame);
        }

    };


}
