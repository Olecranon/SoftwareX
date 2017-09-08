package util;

import jwave.Transform;
import jwave.transforms.FastWaveletTransform;
import jwave.transforms.wavelets.haar.Haar1;

public class IWZsensorClass {
	
	// for initialization
	private int initIndex;
	private boolean initFlag;

	// for sliding window
	private double[] speedArray;
	private double[]  occuArray;
	private String[]  timeArray;
	private String detectorID;

	//class static parameters
	private static final int N = 64;	
	private static final double THREHOLDLEVEL_OCCU = 4.0;
	private static final double THREHOLDLEVEL_SPEED = 5.5;
	private static final Transform WT = new Transform(new FastWaveletTransform(new Haar1()));
	
	
	//DMS sending info
	private boolean DMSFlag;
	private int DMSIndex;
	
	/**
	 * constructor to initialize the IWZsensor class
	 * @param sensorID
	 */
	public IWZsensorClass(String sensorID){
		initIndex = 0;
		initFlag = false;
		
		detectorID = sensorID;
		speedArray = new double[N];
		occuArray  = new double[N];
		timeArray  = new String[N];	
		
		
		DMSFlag = false;
		DMSIndex = 0;
	}
	
	/**
	 * @return time array
	 */
	public String[] getTimeArr(){
		return timeArray;
	}
	
	/**
	 * @return speed array
	 */
	public double[] getSpeedArr(){
		return speedArray;
	}
	
	/**
	 * @return occupancy array
	 */
	public double[] getOccuArr(){
		return occuArray;
	}
	
	/**
	 * @return detector ID
	 */
	public String getDetectorID(){
		return detectorID;
	}

	/**
	 * @return status to see if initialization is done
	 */
	public boolean getInitStatus(){
		return initFlag;
	}
	
	
	/**
	 * setDMS index to control it's display time, +1 means wait for 20 seconds
	 */
	public void setDMSIndex(){
		DMSIndex++;
	}
	
	/**
	 * get DMS index
	 */
	public int getDMSIndex(){
		return DMSIndex;
	}
	
	
	/**
	 * reset DMS index = 0 and DMS display flag to false
	 */
	public void reSetDMSIndex(){
		DMSIndex = 0;
		DMSFlag = false;
	}

	public void setDMSFlagOn(){
		DMSFlag = true;
	}
	
	public void setDMSFlagOff(){
		DMSFlag = false;
	}
	
	public boolean getDMSflag(){
		return DMSFlag;
	}
	
	
	
	
	/**
	 * update arrays as data streamed in
	 * @param speed
	 * @param occu
	 * @param time
	 */
	public void fillArrays(String speed, String occu, String time){		
		// first fill up the window
		// then update the window
		if (!initFlag){
			speedArray[initIndex] = Double.parseDouble(speed);
			occuArray[initIndex]  = Double.parseDouble(occu);
			timeArray[initIndex]  = time;
			
			initIndex++;
			if(initIndex == N){
				initFlag = true;
			}
		} else {			
			// update
			initFlag = true;
			for (int j = 0; j < N - 1; j++) {
				speedArray[j] = speedArray[j + 1];
				 occuArray[j] =  occuArray[j + 1];
				 timeArray[j] =  timeArray[j + 1];		
			}
			speedArray[N - 1] = Double.parseDouble(speed);
			 occuArray[N - 1] = Double.parseDouble(occu);
			 timeArray[N - 1] = time;			
		}		
	}
	
	
	/**
	 * @return smoothed Speed
	 */
	public double getSmoothSpeed(){
		double smootedSpeed = getSmoothData(speedArray, THREHOLDLEVEL_SPEED);
		return smootedSpeed;
	}
	
	
	/**
	 * @return smoothed Occupancy
	 */
	public double getSmoothOccupancy(){
		double smootedOccu = getSmoothData(occuArray, THREHOLDLEVEL_OCCU);
		return smootedOccu;
	}
	
	
	/**
	 * private UDF to find the smoothed data
	 * @param arr
	 * @param level
	 * @return
	 */
	private static double getSmoothData(double[] arr, double level){
		double threshold = findThreshold(arr) * level;
		double[] arrTransformed = WT.forward(arr);
		
		for (int i = 0; i < arr.length; i++) {
			boolean flag = Math.abs(arrTransformed[i]) <=  threshold;
			if(flag){
				arrTransformed[i] = 0;
			}
		}	
		
		//return the last item of the filtered value
		double[] filtered_Transform_reverse = WT.reverse(arrTransformed);
		
		int returnIndex = arr.length - 1;
		double ans = filtered_Transform_reverse[returnIndex];
		if(ans >= 0){
			return ans;
		} else {
			return 0;
		}
	}
	
	
		
	/**
	 * find the Transform threshold. 
	 * @param arr
	 * @return
	 */
	private static double findThreshold(double[] arr){
		double[] arrTransformed = WT.forward(arr);
		double sum = 0;
		for (int i = 1; i < arr.length - 1; i++) {
			
			double y_i_b = Math.abs(arrTransformed[i-1]);
			double y_i = Math.abs(arrTransformed[i]);
			double y_i_a = Math.abs(arrTransformed[i+1]);
			
			double temp = 0.5 * y_i_b - y_i + 0.5 * y_i_a;			
			sum = sum + temp*temp;
		}		
		return Math.sqrt(2.0 * sum / (3*(arr.length - 2)));
	}
		
}
