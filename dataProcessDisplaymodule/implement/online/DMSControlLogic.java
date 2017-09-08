package online;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import util.IWZ_UtilityMethods;
import util.IWZsensorClass;


import weka.classifiers.Classifier;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instances;


import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

public class DMSControlLogic {
	private final static int UPDATE_RATE = 20 * 1000; // update every 20 seconds
	private static String FOLDERPATH = "./"; // log the result to wherever one want
	private static final int INTERVAL = 6; // each message will be displayed at least 20*6 seconds.
	
	public static void main(String[] args) throws Exception {
		// mappers for the prediction
		HashMap<String, String> MsgMap = new HashMap<String, String>();
		MsgMap.put("35", "STOP");
		MsgMap.put("45", "SLOW");
		MsgMap.put("55", "DELAY");
		MsgMap.put("70", "BLANK");
		
		
		//Note that, these sensor IDs and TransSuite internal IDs are made up, 
		//build the proxy object using TransCore webservice API
		SVC_C2CSoapProxy proxy = new SVC_C2CSoapProxy();
		
		// field available DMS to post messages to
		ArrayList<String> fieldDMSIDs = new ArrayList<>();
		fieldDMSIDs.add("Field DMS 1");
		fieldDMSIDs.add("Field DMS 2");
		fieldDMSIDs.add("Field DMS 3");
		fieldDMSIDs.add("Field DMS 4");

		// sensor IDs and its TransSuite internal ID
		HashMap<String, String> sensorTransSuitMaps = new HashMap<>();
		sensorTransSuitMaps.put("Field DMS 1", "1"); 			
		sensorTransSuitMaps.put("Field DMS 2", "2");		
		sensorTransSuitMaps.put("Field DMS 3", "3");		
		sensorTransSuitMaps.put("Field DMS 4", "4");		
		
		
		// API initialization
		// //Create HTTP Client
		HttpClient client = HttpClients.createDefault();
		//Initialize HTTP Post Request (The link is made up)
		HttpPost post = new HttpPost("http://xxx.xxx.com:8080/workzone/feeds/consume");
		post.addHeader("Content-Type", "application/json");
		
		// load the Weka Algorithm
		Classifier cls = (Classifier) weka.core.SerializationHelper.read("./DecisionTree.model");
		
		
		// load the sensorList and its associated work zone IDs:
		HashMap<String, String> sensorIWZMaps = IWZ_UtilityMethods.getSensorIWZMaps("IWZSensorList.csv");
		
		// sensorIDs
		String[] sensorIDs = sensorIWZMaps.keySet().toArray(new String[sensorIWZMaps.keySet().size()]);
		
		
		// build the Map and initialize a IWZ class for each sensorID
		HashMap<String, IWZsensorClass> IWZCLassMap = new HashMap<>();		
		for (String id : sensorIDs){
			IWZsensorClass temp = new IWZsensorClass(id);
			IWZCLassMap.put(id, temp);
		}
		
		// forever loop to run this application 24/7
		while (true){
			try{
				long startTime = System.currentTimeMillis();
				String today = new SimpleDateFormat("yyyyMMdd").format(new java.util.Date());
				String filePath = today + "_API.csv";
				FileWriter fw = new FileWriter(FOLDERPATH + filePath, true);
				
				String filePath_DMS = today + "_DMS.csv";
				FileWriter fw_DMS = new FileWriter(FOLDERPATH + filePath_DMS, true);
				
				//download the xml first
				IWZ_UtilityMethods.downloadXML();
				
				String xmlPath = "./tempXML/WavetronixTemp.xml";
				HashMap<String, String> XML_info = IWZ_UtilityMethods.getDetectorsInfo(sensorIDs, xmlPath);
				
				
				for (String id: XML_info.keySet()){
					
					String idInfo = XML_info.get(id);
					if(idInfo.contains("-1")){
						continue;
					}
					
					String infoDate = idInfo.split(",")[0];
					if(!today.equals(infoDate)){
						continue;
					}
					
					// get the sensor class
					IWZsensorClass iwzSensor = IWZCLassMap.get(id);
					
					//extract the sensor information
					//date, startTime, endTime, timeStartSec, timeEndSec, vol, occu, speed;
					String time  = idInfo.split(",")[3]; //start time in seconds
					String speed = idInfo.split(",")[7]; //speed
					String occu  = idInfo.split(",")[6]; //occu					
					String infoStartTime = idInfo.split(",")[1]; //start time in HHmmss
					
					// update the sensor class
					iwzSensor.fillArrays(speed, occu, time);
					
					// check window initialization status
					boolean initStatus = iwzSensor.getInitStatus();
					
					if(!initStatus){
						System.out.println(id + ": Initializing, " + idInfo);
					}
					
					
					if (initStatus) {
						double smootedSpeed = iwzSensor.getSmoothSpeed();
						double smootedOccu  = iwzSensor.getSmoothOccupancy();
						
						Instances newData = IWZ_UtilityMethods.buildDataSet(smootedSpeed, smootedOccu);
						String predMsg = IWZ_UtilityMethods.revisedDT(newData, cls);
																		
						//send HTTP post
						String Msg = MsgMap.get(predMsg);
						
						String jsonString1 = getJsonString(sensorIWZMaps.get(id), id, smootedSpeed, Msg);
					    HttpEntity entity = new ByteArrayEntity(jsonString1.getBytes("UTF-8"));
					    post.setEntity(entity);
					    
					    //Execute the request. 
					    HttpResponse response = client.execute(post);	
					    
					    String result = EntityUtils.toString(response.getEntity());
					    System.out.println(result);
					    
						//writre the prediction info to the file
						String log = id + ", " + sensorIWZMaps.get(id) + ", " + idInfo + ", " + smootedOccu + ", " + smootedSpeed + ", " + predMsg;
						System.out.println(log);
						fw.write(log + "\n");					
						fw.flush();	
						
						/////////////////////
						// DMS Display Module
						/////////////////////
						int iwzIndex = iwzSensor.getDMSIndex();
						if (fieldDMSIDs.contains(id) && (iwzIndex == INTERVAL)){
							//send to TransSuite to display first
//							String Msg = MsgMap.get(predMsg);
							String sensorTransSuitlID = sensorTransSuitMaps.get(id);
							
							//write the prediction to the local DMS
							String controlStrStartTime = IWZ_UtilityMethods.addingTimeString(-5, infoStartTime);
							String controlStrEndTime = IWZ_UtilityMethods.addingTimeString(30, infoStartTime);
							String controlMessageString = IWZ_UtilityMethods.findControlMessage(sensorTransSuitlID, 
																			Msg, 
																			today, 
																			controlStrStartTime, 
																			controlStrEndTime);
							
							proxy.OP_ShareDMSControl(controlMessageString);	
							
							String DMSStr = id + ", " +  sensorTransSuitlID + "," + idInfo + ", " + smootedOccu + ", " + smootedSpeed + ", " + predMsg + ", " + Msg;
							System.out.println(DMSStr);
							
							fw_DMS.write(DMSStr + "\n");
							fw_DMS.flush();	
							
							//reset DMS display index
							iwzSensor.reSetDMSIndex();
						}
					}										
				}
				
				System.out.println("---------------------------------------------------------------------");
				fw.close();
				//wait for every 20 seconds.			
				wait4it(System.currentTimeMillis(), startTime);	
			} catch (Exception e){
				Thread.sleep(UPDATE_RATE);
				continue;				
			}
		}
		
		
	}

	

	/**
	 * wait function to wait till 20 seconds.
	 * @param curT
	 * @param startT
	 */
	//wait function to wait till 20 seconds.
	private static void wait4it(long curT, long startT) throws Exception{
		long duration = curT - startT;		
		if (duration < UPDATE_RATE) {
			Thread.sleep(UPDATE_RATE - duration);
		}		
	}

	
	
	/**
	 * Json String function to send out msg to API
	 * @param workzoneName
	 * @param sensorID
	 * @param aveSpeed
	 * @param Msg
	 * @return
	 */
	private static String getJsonString(String workzoneName, String sensorID, double aveSpeed, String Msg){
		String jsonString = "{\"workzone\":\"" + workzoneName + "\",\"device\":\"" + 
					  sensorID + "\",\"avgSpeed\":" + aveSpeed + ",\"alert\":\"" + Msg + "\"}";
		return jsonString;
	}
	
}
