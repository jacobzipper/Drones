package com.parrot.sdksample.drone;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_ANIMATIONS_FLIP_DIRECTION_ENUM;
import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_PICTUREEVENTCHANGED_ERROR_ENUM;
import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM;
import com.parrot.arsdk.arcontroller.ARCONTROLLER_DEVICE_STATE_ENUM;
import com.parrot.arsdk.arcontroller.ARCONTROLLER_DICTIONARY_KEY_ENUM;
import com.parrot.arsdk.arcontroller.ARCONTROLLER_ERROR_ENUM;
import com.parrot.arsdk.arcontroller.ARControllerArgumentDictionary;
import com.parrot.arsdk.arcontroller.ARControllerCodec;
import com.parrot.arsdk.arcontroller.ARControllerDictionary;
import com.parrot.arsdk.arcontroller.ARControllerException;
import com.parrot.arsdk.arcontroller.ARDeviceController;
import com.parrot.arsdk.arcontroller.ARDeviceControllerListener;
import com.parrot.arsdk.arcontroller.ARDeviceControllerStreamListener;
import com.parrot.arsdk.arcontroller.ARFeatureARDrone3;
import com.parrot.arsdk.arcontroller.ARFeatureCommon;
import com.parrot.arsdk.arcontroller.ARFrame;
import com.parrot.arsdk.ardiscovery.ARDISCOVERY_PRODUCT_ENUM;
import com.parrot.arsdk.ardiscovery.ARDISCOVERY_PRODUCT_FAMILY_ENUM;
import com.parrot.arsdk.ardiscovery.ARDiscoveryDevice;
import com.parrot.arsdk.ardiscovery.ARDiscoveryDeviceNetService;
import com.parrot.arsdk.ardiscovery.ARDiscoveryDeviceService;
import com.parrot.arsdk.ardiscovery.ARDiscoveryException;
import com.parrot.arsdk.ardiscovery.ARDiscoveryService;
import com.parrot.arsdk.arutils.ARUtilsException;
import com.parrot.arsdk.arutils.ARUtilsFtpConnection;
import com.parrot.arsdk.arutils.ARUtilsManager;
import com.parrot.sdksample.activity.autoRun1;
import com.parrot.sdksample.enums.Direction;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

public class AutoDrone {
    private static final String TAG = "AutoDrone";

    private static final int DEVICE_PORT = 21;

    public interface Listener {
        /**
         * Called when the connection to the drone changes
         * Called in the main thread
         *
         * @param state the state of the drone
         */
        void onDroneConnectionChanged(ARCONTROLLER_DEVICE_STATE_ENUM state);

        /**
         * Called when the battery charge changes
         * Called in the main thread
         *
         * @param batteryPercentage the battery remaining (in percent)
         */
        void onBatteryChargeChanged(int batteryPercentage);

        /**
         * Called when the piloting state changes
         * Called in the main thread
         *
         * @param state the piloting state of the drone
         */
        void onPilotingStateChanged(ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM state);

        /**
         * Called when a picture is taken
         * Called on a separate thread
         *
         * @param error ERROR_OK if picture has been taken, otherwise describe the error
         */
        void onPictureTaken(ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_PICTUREEVENTCHANGED_ERROR_ENUM error);

        /**
         * Called when the video decoder should be configured
         * Called on a separate thread
         *
         * @param codec the codec to configure the decoder with
         */
        void configureDecoder(ARControllerCodec codec);

        /**
         * Called when a video frame has been received
         * Called on a separate thread
         *
         * @param frame the video frame
         */
        void onFrameReceived(ARFrame frame);
    }

    private List<Listener> mListeners;

    private Handler mHandler;
    private ARDiscoveryDevice discoveryDevice;
    private ARDeviceController mDeviceController;
    private ARCONTROLLER_DEVICE_STATE_ENUM mState;
    private ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM mFlyingState;
    public autoRun1 app;
    public AutoDrone drone = this;
    public Button eButton;
    public Direction curDirection;
    public Direction[][] coordinateSystem = new Direction[3][3];
    public int[] currentCoordinates = {2,2};
    public Bitmap curPhoto;
    public FTPClient ftpClient;

    /**
     * Constructor for the Drone. Initializes the FTP client,
     * the coordinate system, and necessary drone listeners.
     **/

    public AutoDrone(Context context, @NonNull ARDiscoveryDeviceService deviceService) throws IOException {
        for(int i = 0; i < coordinateSystem.length; i++) {
            for(int j = 0; j < coordinateSystem[i].length; j++) {
                coordinateSystem[i][j] = null;
            }
        }
        ftpClient = new FTPClient();
        curDirection = Direction.NORTH;
        coordinateSystem[2][2] = Direction.NORTH;
        mListeners = new ArrayList<>();

        // needed because some callbacks will be called on the main thread
        mHandler = new Handler(context.getMainLooper());

        mState = ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_STOPPED;

        // if the product type of the deviceService match with the types supported
        ARDISCOVERY_PRODUCT_ENUM productType = ARDiscoveryService.getProductFromProductID(deviceService.getProductID());
        ARDISCOVERY_PRODUCT_FAMILY_ENUM family = ARDiscoveryService.getProductFamily(productType);
        if (ARDISCOVERY_PRODUCT_FAMILY_ENUM.ARDISCOVERY_PRODUCT_FAMILY_ARDRONE.equals(family)) {



            discoveryDevice = createDiscoveryDevice(deviceService, productType);
            if (discoveryDevice != null) {
                mDeviceController = createDeviceController(discoveryDevice);
            }


        } else {
            Log.e(TAG, "DeviceService type is not supported by AutoDrone");
        }
    }

    //region Listener functions
    public void addListener(Listener listener) {
        mListeners.add(listener);
    }

    public void removeListener(Listener listener) {
        mListeners.remove(listener);
    }
    //endregion Listener

    /**
     * Connect to the drone
     *
     * @return true if operation was successful.
     * Returning true doesn't mean that device is connected.
     * You can be informed of the actual connection through {@link Listener#onDroneConnectionChanged}
     */
    public boolean connect() {
        boolean success = false;
        if ((mDeviceController != null) && (ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_STOPPED.equals(mState))) {
            ARCONTROLLER_ERROR_ENUM error = mDeviceController.start();
            if (error == ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK) {
                success = true;
            }
        }
        return success;
    }

    /**
     * Disconnect from the drone
     *
     * @return true if operation was successful.
     * Returning true doesn't mean that device is disconnected.
     * You can be informed of the actual disconnection through {@link Listener#onDroneConnectionChanged}
     */


    public boolean disconnect() {
        boolean success = false;
        if ((mDeviceController != null) && (ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING.equals(mState))) {
            ARCONTROLLER_ERROR_ENUM error = mDeviceController.stop();
            if (error == ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK) {
                success = true;
            }
        }
        return success;
    }

    /**
     *
     * @param a sets the instance of the activity to the field app.
     */
    public void setApp(autoRun1 a) {
        app = a;

    }
    /**
     * Get the current connection state
     *
     * @return the connection state of the drone
     */
    public ARCONTROLLER_DEVICE_STATE_ENUM getConnectionState() {
        return mState;
    }

    /**
     * Get the current flying state
     *
     * @return the flying state
     */
    public ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM getFlyingState() {
        return mFlyingState;
    }

    /**
     * Drone takes off, the FTP client connects/moves to the picture directory,
     * and sets the current coordinates to the correct spot.
     */
    public void takeOff() {
        if ((mDeviceController != null) && (mState.equals(ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING))) {
            mDeviceController.getFeatureARDrone3().sendPilotingTakeOff();
        }
        currentCoordinates[0] = 2;
        currentCoordinates[1] = 2;

        try {
            ftpClient.connect(InetAddress.getByName("192.168.42.1"),21);
            ftpClient.login("anonymous", "");
            ftpClient.changeWorkingDirectory("/internal_000/Bebop_2/media");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Lands the drone and disconnects the FTP client.
     */
    public void land() {
        if ((mDeviceController != null) && (mState.equals(ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING))) {
            mDeviceController.getFeatureARDrone3().sendPilotingLanding();
        }
        try {
            ftpClient.disconnect();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * ONLY TO BE CALLED IN CASE OF AN EMERGENCY.
     *
     * DRONE IMMEDIATELY STOPS AND DOES A HARD LANDING.
     */
    public void emergency() {
        if ((mDeviceController != null) && (mState.equals(ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING))) {
            mDeviceController.getFeatureARDrone3().sendPilotingEmergency();
        }
    }

    /**
     * Takes a picture and puts it in media directory.
     */
    public void takePicture() {
        mDeviceController.getFeatureARDrone3().sendMediaRecordPictureV2();
    }

    /**
     * Set the forward/backward angle of the drone
     * Note that {@link AutoDrone#setFlag(byte)} should be set to 1 in order to take in account the pitch value
     *
     * @param pitch value in percentage from -100 to 100
     */
    public void setPitch(byte pitch) {
        if ((mDeviceController != null) && (mState.equals(ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING))) {
            mDeviceController.getFeatureARDrone3().setPilotingPCMDPitch(pitch);
        }
    }

    /**
     * Set the side angle of the drone
     * Note that {@link AutoDrone#setFlag(byte)} should be set to 1 in order to take in account the roll value
     *
     * @param roll value in percentage from -100 to 100
     */
    public void setRoll(byte roll) {
        if ((mDeviceController != null) && (mState.equals(ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING))) {
            mDeviceController.getFeatureARDrone3().setPilotingPCMDRoll(roll);
        }
    }

    /**
     * Yaknow, does a flip. Pretty cool to see, but you need A LOT of space.
     *
     * @param direction direction of flip
     */
    public void flip(ARCOMMANDS_ARDRONE3_ANIMATIONS_FLIP_DIRECTION_ENUM direction) {
        mDeviceController.getFeatureARDrone3().sendAnimationsFlip(direction);

    }

    /**
     * Parameters greater than 0 turn it to the right and vice versa
     *
     * @param yaw byte from -100 to 100
     */
    public void setYaw(byte yaw) {
        if ((mDeviceController != null) && (mState.equals(ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING))) {
            mDeviceController.getFeatureARDrone3().setPilotingPCMDYaw(yaw);
        }
    }

    /**
     * Greater than 0 is up, less is down.
     *
     * @param gaz byte from -100 to 100
     */
    public void setGaz(byte gaz) {
        if ((mDeviceController != null) && (mState.equals(ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING))) {
            mDeviceController.getFeatureARDrone3().setPilotingPCMDGaz(gaz);
        }
    }

    /**
     * EXPERIMENTAL METHOD. USE AT YOUR OWN RISK.
     *
     * Basically this takes a picture, the FTP client then receives it.
     * After some bitwise shifting to receive the individual rgb values
     * of the middle pixel, it tries to determine if a wall is in front of it.
     *
     * @return returns a boolean if the condition of it seeing a wall is met.
     */
    public boolean analyzePicture() {
        drone.takePicture();
        drone.getFirstPicture();
        // Manipulating bitmap to get values that are easy to work with
        curPhoto.setHasAlpha(true);
        int[][] argb = getPixels2D(curPhoto);
        int curPixel = argb[curPhoto.getHeight()][curPhoto.getWidth()];
        int blue = curPixel & 0x000000FF;
        int green = curPixel >> 8 & 0x000000FF;
        int red = curPixel >> 16 & 0x000000FF;
        // Try to determine if wall is in front. This is a color blind test.
        if(blue  > 210 || green > 210 || red > 210) {
            return true;
        }
        return false;
    }

    /**
     * Sets curPhoto equal to the first photo in the working directory.
     */
    public void getFirstPicture() {
        try {
            if (FTPReply.isPositiveCompletion(ftpClient.getReplyCode())) {
                ftpClient.enterLocalPassiveMode();
                ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
                FTPFile[] files = ftpClient.listFiles("/internal_000/Bebop_2/media");
                boolean firstFileFound = false;
                for (FTPFile file : files) {
                    if (!firstFileFound) {
                        String fileName = file.getName();
                        if (fileName.endsWith(".jpg")
                                || fileName.endsWith(".png")) {
                            String fileWithPath = "/internal_000/Bebop_2/media" + "/" + fileName;
                            drone.curPhoto = null;
                            InputStream inStream = ftpClient.retrieveFileStream(fileWithPath);
                            // Sets the current photo to the first photo in the media directory (most recent)
                            drone.curPhoto = BitmapFactory.decodeStream(inStream);
                            inStream.close();
                            break;
                        }
                    }
                }
            }
        }catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Since the Bitmap class only has the getPixel and the getPixels (returns a 1D array) methods
     * I wrote this to retrieve a matrix of the pixel values, to make the whole process of image
     * manipulation easier.
     *
     * @param bmp the current photo
     * @return returns a 2D array of pixels from the bitmap parameter.
     */
    public int[][] getPixels2D(Bitmap bmp) {
        int[][] ret = new int[bmp.getHeight()][bmp.getWidth()];
        for(int i = 0; i < bmp.getHeight(); i++) {
            for(int j = 0; j < bmp.getWidth(); j++) {
                ret[i][j] = bmp.getPixel(i,j);
            }
        }
        return ret;
    }

    /**
     * VERY IMPORTANT METHOD. THIS IS HOW YOU MAKE THE DRONE DO COMPLEX MOVEMENTS FOR A DESIGNATED AMOUNT OF TIME.
     *
     * @param flag byte 1 or 0. 1 if you want it to go, 0 if not.
     * @param x same as setRoll
     * @param y same as setPitch
     * @param z same as setYaw
     * @param g same as setGaz
     * @param time milliseconds for command to run for.
     */
    public void setpositionHere(final byte flag, final byte x, final byte y, final byte z, final byte g, final int time) {
        // Based on the values sent, this changes the coordinates and direction of the drone.
        if ((mDeviceController != null) && (mState.equals(ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING)) && mFlyingState.equals(ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM.ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_HOVERING)) {
            if(z < 0) {
                if(curDirection != Direction.NORTH) curDirection = Direction.values()[(curDirection.ordinal()-1)];
                else curDirection = Direction.WEST;
                coordinateSystem[currentCoordinates[0]][currentCoordinates[1]] = curDirection;
            }
            else if(z > 0) {
                if(curDirection != Direction.WEST) curDirection = Direction.values()[(curDirection.ordinal()+1)];
                else curDirection = Direction.NORTH;
                coordinateSystem[currentCoordinates[0]][currentCoordinates[1]] = curDirection;
            }
            if(y > 0) {
                if(curDirection == Direction.NORTH) {
                    coordinateSystem[currentCoordinates[0]][currentCoordinates[1]] = null;
                    currentCoordinates[0]--;
                    coordinateSystem[currentCoordinates[0]][currentCoordinates[1]] = curDirection;
                }
                else if(curDirection == Direction.EAST) {
                    coordinateSystem[currentCoordinates[0]][currentCoordinates[1]] = null;
                    currentCoordinates[1]++;
                    coordinateSystem[currentCoordinates[0]][currentCoordinates[1]] = curDirection;
                }
                else if(curDirection == Direction.WEST) {
                    coordinateSystem[currentCoordinates[0]][currentCoordinates[1]] = null;
                    currentCoordinates[1]--;
                    coordinateSystem[currentCoordinates[0]][currentCoordinates[1]] = curDirection;
                }
                else if(curDirection == Direction.SOUTH) {
                    coordinateSystem[currentCoordinates[0]][currentCoordinates[1]] = null;
                    currentCoordinates[0]++;
                    coordinateSystem[currentCoordinates[0]][currentCoordinates[1]] = curDirection;
                }
            }

            /**
             * Okay, here's where it gets weird.
             * We need to multithread to keep certain elements of the UI (like the emergency button)
             * available in case we would like to run said commands.
             * By multithreading, we can run the app and the drone command simultaneously with no problem.
             */

            Thread r = new Thread() {
                @Override
                public void run() {
                    mDeviceController.getFeatureARDrone3().setPilotingPCMD(flag, x, y, z, g, time);
                    try {
                        this.sleep(time);
                    } catch (Exception e) {
                    }
                    mDeviceController.getFeatureARDrone3().setPilotingPCMDFlag((byte) 0);
                }

            };
            Thread t = new Thread() {
                @Override
                public void run() {
                    eButton.setOnClickListener(new View.OnClickListener() {
                        public void onClick(View v) {
                            mDeviceController.getFeatureARDrone3().sendPilotingEmergency();
                        }
                    });
                }
            };
            t.start();
            r.start();

        }
    }

    /**
     * Command that attempts to move the drone forward one space in the maze.
     *
     * Almost works perfectly, but still in testing.
     */
    public void moveForwardOneSpace() {
        drone.setpositionHere((byte) 1, (byte) 0, (byte) 10, (byte) 0, (byte) 0, 2500);
        try {
            Thread.sleep(3500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * EXPERIMENTAL
     * Command that attempts to turn the drone 90 degrees to the left.
     */
    public void turnLeft() {
        drone.setpositionHere((byte) 1, (byte) 0, (byte) 0, (byte) -25, (byte) 0,2500);
        try {
            Thread.sleep(4250);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * EXPERIMENTAL
     * Command that attempts to turn the drone 90 degrees to the right.
     */

    public void turnRight() {
        drone.setpositionHere((byte) 1, (byte) 0, (byte) 0, (byte) 25, (byte) 0,2950);
        try {
            Thread.sleep(4250);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Determines whether to run the command or not
     * @param flag byte 1 or 0
     */
    public void setFlag(byte flag) {
        if ((mDeviceController != null) && (mState.equals(ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING))) {
            mDeviceController.getFeatureARDrone3().setPilotingPCMDFlag(flag);
        }
    }

    private ARDiscoveryDevice createDiscoveryDevice(@NonNull ARDiscoveryDeviceService service, ARDISCOVERY_PRODUCT_ENUM productType) {
        ARDiscoveryDevice device = null;
        try {
            device = new ARDiscoveryDevice();

            ARDiscoveryDeviceNetService netDeviceService = (ARDiscoveryDeviceNetService) service.getDevice();
            device.initWifi(productType, netDeviceService.getName(), netDeviceService.getIp(), netDeviceService.getPort());

        } catch (ARDiscoveryException e) {
            Log.e(TAG, "Exception", e);
            Log.e(TAG, "Error: " + e.getError());
        }

        return device;
    }

    private ARDeviceController createDeviceController(@NonNull ARDiscoveryDevice discoveryDevice) {
        ARDeviceController deviceController = null;
        try {
            deviceController = new ARDeviceController(discoveryDevice);

            deviceController.addListener(mDeviceControllerListener);
            deviceController.addStreamListener(mStreamListener);
        } catch (ARControllerException e) {
            Log.e(TAG, "Exception", e);
        }

        return deviceController;
    }

    //region notify listener block
    private void notifyConnectionChanged(ARCONTROLLER_DEVICE_STATE_ENUM state) {
        List<Listener> listenersCpy = new ArrayList<>(mListeners);
        for (Listener listener : listenersCpy) {
            listener.onDroneConnectionChanged(state);
        }
    }

    private void notifyBatteryChanged(int battery) {
        List<Listener> listenersCpy = new ArrayList<>(mListeners);
        for (Listener listener : listenersCpy) {
            listener.onBatteryChargeChanged(battery);
        }
    }

    private void notifyPilotingStateChanged(ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM state) {
        List<Listener> listenersCpy = new ArrayList<>(mListeners);
        for (Listener listener : listenersCpy) {
            listener.onPilotingStateChanged(state);
        }
    }

    private void notifyPictureTaken(ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_PICTUREEVENTCHANGED_ERROR_ENUM error) {
        List<Listener> listenersCpy = new ArrayList<>(mListeners);
        for (Listener listener : listenersCpy) {
            listener.onPictureTaken(error);
        }
    }

    private void notifyConfigureDecoder(ARControllerCodec codec) {
        List<Listener> listenersCpy = new ArrayList<>(mListeners);
        for (Listener listener : listenersCpy) {
            listener.configureDecoder(codec);
        }
    }

    private void notifyFrameReceived(ARFrame frame) {
        List<Listener> listenersCpy = new ArrayList<>(mListeners);
        for (Listener listener : listenersCpy) {
            listener.onFrameReceived(frame);
        }
    }



    public final ARDeviceControllerListener mDeviceControllerListener = new ARDeviceControllerListener() {
        @Override
        public void onStateChanged(ARDeviceController deviceController, ARCONTROLLER_DEVICE_STATE_ENUM newState, ARCONTROLLER_ERROR_ENUM error) {
            mState = newState;
            if (ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING.equals(mState)) {
                mDeviceController.getFeatureARDrone3().sendMediaStreamingVideoEnable((byte) 1);
            } else if (ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_STOPPED.equals(mState)) {

            }
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    notifyConnectionChanged(mState);
                }
            });
        }

        @Override
        public void onExtensionStateChanged(ARDeviceController deviceController, ARCONTROLLER_DEVICE_STATE_ENUM newState, ARDISCOVERY_PRODUCT_ENUM product, String name, ARCONTROLLER_ERROR_ENUM error) {
        }

        @Override
        public void onCommandReceived(ARDeviceController deviceController, ARCONTROLLER_DICTIONARY_KEY_ENUM commandKey, ARControllerDictionary elementDictionary) {
            // if event received is the battery update
            boolean cont = true;
            int count = 0;
            if ((commandKey == ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_COMMON_COMMONSTATE_BATTERYSTATECHANGED) && (elementDictionary != null)) {
                ARControllerArgumentDictionary<Object> args = elementDictionary.get(ARControllerDictionary.ARCONTROLLER_DICTIONARY_SINGLE_KEY);
                if (args != null) {
                    final int battery = (Integer) args.get(ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_COMMONSTATE_BATTERYSTATECHANGED_PERCENT);
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            notifyBatteryChanged(battery);
                        }
                    });
                }
            }
            // if event received is the flying state update

            else if ((commandKey == ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED) && (elementDictionary != null)) {
                ARControllerArgumentDictionary<Object> args = elementDictionary.get(ARControllerDictionary.ARCONTROLLER_DICTIONARY_SINGLE_KEY);
                if (args != null) {
                    final ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM state = ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM.getFromValue((Integer) args.get(ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE));

                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mFlyingState = state;
                            notifyPilotingStateChanged(state);
                        }
                    });
                    switch (state) {
                        case ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_LANDED:

                            break;
                        case ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_FLYING:

                            break;
                        case ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_HOVERING:
                            break;
                        default:
                            break;
                    }

                }
            }
            // if event received is the picture notification
            else if ((commandKey == ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSTATE_POSITIONCHANGED) && (elementDictionary != null)) {
                ARControllerArgumentDictionary<Object> args = elementDictionary.get(ARControllerDictionary.ARCONTROLLER_DICTIONARY_SINGLE_KEY);
                if (args != null) {

                }
            } else if ((commandKey == ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_MEDIARECORDEVENT_PICTUREEVENTCHANGED) && (elementDictionary != null)) {
                ARControllerArgumentDictionary<Object> args = elementDictionary.get(ARControllerDictionary.ARCONTROLLER_DICTIONARY_SINGLE_KEY);
                if (args != null) {
                    final ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_PICTUREEVENTCHANGED_ERROR_ENUM error = ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_PICTUREEVENTCHANGED_ERROR_ENUM.getFromValue((Integer) args.get(ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_MEDIARECORDEVENT_PICTUREEVENTCHANGED_ERROR));
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            notifyPictureTaken(error);
                        }
                    });
                }
            }
            // if event received is the run id
            else if ((commandKey == ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_COMMON_RUNSTATE_RUNIDCHANGED) && (elementDictionary != null)) {
                ARControllerArgumentDictionary<Object> args = elementDictionary.get(ARControllerDictionary.ARCONTROLLER_DICTIONARY_SINGLE_KEY);
                if (args != null) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                        }
                    });
                }
            }
        }
    };

    private final ARDeviceControllerStreamListener mStreamListener = new ARDeviceControllerStreamListener() {
        @Override
        public ARCONTROLLER_ERROR_ENUM configureDecoder(ARDeviceController deviceController, final ARControllerCodec codec) {
            notifyConfigureDecoder(codec);
            return ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK;
        }

        @Override
        public ARCONTROLLER_ERROR_ENUM onFrameReceived(ARDeviceController deviceController, final ARFrame frame) {
            notifyFrameReceived(frame);
            return ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK;
        }

        @Override
        public void onFrameTimeout(ARDeviceController deviceController) {
        }

    };
}
