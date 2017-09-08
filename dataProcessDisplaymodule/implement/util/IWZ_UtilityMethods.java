package util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Scanner;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import weka.classifiers.Classifier;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instances;



public class IWZ_UtilityMethods {

	/**
	 * download xml file from the link
	 */
	public static void downloadXML() throws Exception{
		String link = "http://xxxxx/xx.xx.xxx.xxx.xxx/"
				+ "xxxx.xxx/xxx?"
				+ "xx=stringHTTP/1.1";
		URL url = new URL(link);
		
		File folderDir = new File("./tempXML");
		
		if(!folderDir.exists()){
			folderDir.mkdirs();
		}
		
		String fileName = "WavetronixTemp.xml";
		
		BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));
		File f = new File("./tempXML" + File.separator + fileName);
		FileWriter fw = new FileWriter(f);
		String inputLine;
		while ((inputLine = in.readLine()) != null) {
			fw.write(inputLine);
			fw.write("\n");
		}
		in.close();
		fw.close();		
	}
	
	
	
	
	/**
	 * Parse xml information
	 * @param detectorID, desired detector ID
	 * @return date, startTime, endTime, timeStartSec, timeEndSec, Vol, Occu, Speed;
	 */
	public static String parseXML(String detectorID, String path) throws Exception{
		Document doc = normizedDoc(path);
		
		//find the date of time
		NodeList dateList = doc.getElementsByTagName("local-date");
		String date = dateList.item(0).getTextContent();		
		
		//find the start-time
		NodeList startList = doc.getElementsByTagName("start-time");		
		String startTime = startList.item(0).getTextContent();
		
		//find the end-time
		NodeList endList = doc.getElementsByTagName("end-time");		
		String endTime = endList.item(0).getTextContent();

		int timeStartSec = convertToSec(startTime);
		int timeEndSec = convertToSec(endTime);
				
		//find the desired node using XPath
		XPath xpath = XPathFactory.newInstance().newXPath();
		String expString = "//detector-report[detector-id = \"" + detectorID + "\"]";	
		
		String trafficInfo = "";
		// Sometimes, the data does not have any sensor Info(can not find sensor Node), so just assign "-1,-1,-1"
		try{
			Node node = (Node) xpath.compile(expString).evaluate(doc, XPathConstants.NODE);
			trafficInfo = findTrafficInfo(node);
		} catch (Exception e){
			trafficInfo = "-1,-1,-1";	
		}
		
		String ans = date + "," + startTime + "," + endTime + "," + timeStartSec + "," + timeEndSec + "," + trafficInfo;		
		return ans;
	}
	
	

	/**
	 * Normal procedure to setup parsing xml files. return a root Dom tree Document obj
	 * @param path
	 * @return
	 * @throws Exception
	 */
	public static Document normizedDoc(String path) throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder = factory.newDocumentBuilder();
		Document doc = builder.parse(path);
		
		doc.getDocumentElement().normalize();
		return doc;
	}
	
	
	

	/**
	 * find&average the traffic information for each detector vol/occu/speed
	 * if the detector is not "operational" or no cars passing by, return "-1, -1, -1"
	 * @param detectorNode
	 * @return
	 */
	public static String findTrafficInfo(Node detectorNode){
		
//		NodeList statusNode = ((Element)detectorNode).getElementsByTagName("status");		
//		String status = statusNode.item(0).getTextContent();
				
		NodeList lanes = ((Element)detectorNode).getElementsByTagName("lane");
		int numLanes = lanes.getLength();
		
		int vol = 0;
		double occu = 0;
		double speed = 0;
		
		
		for (int i = 0; i < numLanes; i++){
			Element lane = (Element)(lanes.item(i));
			
			
			//some data may not even contains these tag, which can raise the exception, if so, continue.
			try{
				lane.getElementsByTagName("volume").item(0).getTextContent().trim();
				lane.getElementsByTagName("occupancy").item(0).getTextContent().trim();
				lane.getElementsByTagName("speed").item(0).getTextContent().trim();
			} catch(Exception e){
				continue;
			}
			
			
			String laneVol = lane.getElementsByTagName("volume").item(0).getTextContent().trim();
			String laneOccu = lane.getElementsByTagName("occupancy").item(0).getTextContent().trim();
			String laneSpeed = lane.getElementsByTagName("speed").item(0).getTextContent().trim();
			
			if(laneVol.length()>0 && laneOccu.length()>0 && laneSpeed.length()> 0) {
				vol = vol + Integer.parseInt(laneVol);
				occu = occu + Double.parseDouble(laneOccu);
				
				//speed is weighted by the volume
				speed = speed + Double.parseDouble(laneSpeed) * Integer.parseInt(laneVol);				
			}			
		}
		
		
		String info = "";
//		 && status.equals("operational")
		if(vol > 0 && numLanes > 0){			
			info = vol + "," + occu/numLanes + "," + speed/vol*0.621371;
		} else {
			info = "-1,-1,-1";			
		}
				
		return info;
	}
	

	
	/**
	 * find the detector information map<id, xmlInfo>
	 * @param sensorIDs
	 * @param xmlPath
	 * @return
	 */
	public static HashMap<String, String> getDetectorsInfo(String[] sensorIDs, String xmlPath) throws Exception{
		HashMap<String, String> detectorInfoMap = new HashMap<String, String>();		
		for (String id:sensorIDs){
			String info = parseXML(id, xmlPath);
			detectorInfoMap.put(id, info);
		}
		return detectorInfoMap;
	}


	/**
	 * generate weka dataset for prediction
	 * @param speedS
	 * @param occuS
	 * @return Weka Instances dataset
	 */
	public static Instances buildDataSet(double speedS, double occuS){		
		// build the weka-formated data first
		Attribute speed = new Attribute("speed");
		Attribute occu  = new Attribute("occu");
		
		ArrayList<String> labels = new ArrayList<>();
		labels.add("35");
		labels.add("45");
		labels.add("55");
		labels.add("70");
		Attribute vsl = new Attribute("label", labels);
		
		ArrayList<Attribute> attrs = new ArrayList<>();
		attrs.add(speed);
		attrs.add(occu);
		attrs.add(vsl);
		Instances datasets = new Instances("prediction", attrs, 1);	
				
		// feed in the new values
		double[] attValues = new double[datasets.numAttributes()];
		attValues[0] = speedS;
		attValues[1] = occuS;
		attValues[2] = weka.core.Utils.missingValue();
		datasets.add(new DenseInstance(1.0, attValues));
		datasets.setClassIndex(2);
		
		return datasets;
	}

	
	/**
	 * revised Decision Tree boundaries, have some cutoff values for normal (65), slow (60), and stop (37.5)
	 * @param newData
	 * @param cls
	 * @return Capped prediction result
	 * @throws Exception
	 */
	public static String revisedDT(Instances newData, Classifier cls) throws Exception{
		double valIndex = cls.classifyInstance(newData.firstInstance());
		String predictedMsg = newData.classAttribute().value((int)valIndex);
		
		double speed = newData.firstInstance().value(0);
		
		if (speed >= 65) {
			predictedMsg = "70";
		}
		
		if ((speed >= 60) && predictedMsg.equals("45")){
			predictedMsg = "55";
		}
		
		if ((speed >= 37.5) && predictedMsg.equals("35")){
			predictedMsg = "45";
		}
		
		return predictedMsg;		
	}

	
	
	/**
	 * Convert time string to desired format
	 * 224740 -> 22*3600 + 47*60 + 40
	 */	
	private static int convertToSec(String str){
		String hh = str.substring(0,2);
		String mm = str.substring(2,4);
		String ss = str.substring(4);
		
		int strInSec = Integer.parseInt(hh) * 3600 + Integer.parseInt(mm) * 60 + Integer.parseInt(ss);
		return strInSec;		
	}


	
	/**
	 * get the sensor list with its associated workzone IDs
	 * @param path of IWZSensorList.csv
	 * @return 
	 * @throws Exception
	 */
	public static HashMap<String, String> getSensorIWZMaps(String path) throws Exception{
		Scanner scan = new Scanner(new File(path)); 
		HashMap<String, String> SensorIWZMaps = new HashMap<>();
		
		while(scan.hasNextLine()){
			String info = scan.nextLine();
			String sensorID = info.split(",")[0];
			String workZoneID = info.split(",")[1];
			SensorIWZMaps.put(sensorID, workZoneID);			
		}
		scan.close();
		return SensorIWZMaps;
	}
	
	
	/**
	*  Convenience method to add a specified number of minutes to a Date object
	*  From: http://stackoverflow.com/questions/9043981/how-to-add-minutes-to-my-date
	*  @param  minutes  The number of minutes to add
	*  @param  beforeTime  The time that will have minutes added to it
	*  @return  A date object(in string) with the specified number of minutes added to it 
	*/	
	public static String addingTimeString(int addingMinutes, String currentTimeString) throws ParseException{
		final long ONE_MINUTE_IN_MILLIS = 60000;//millisecs
		
		SimpleDateFormat df = new SimpleDateFormat("HHmmss");
		Date currentTime = df.parse(currentTimeString);
		long curTimeInMs = currentTime.getTime();
		Date afterAddingMins = new Date(curTimeInMs + (addingMinutes * ONE_MINUTE_IN_MILLIS));		
		
		return df.format(afterAddingMins);
	}
	
	
	
	/**
	 * @param InternalID 	-> 1, 2, 3, 4
	 * @param Msg 			-> just simple speed limit number
	 * @param localDate 	-> 20161014
	 * @param startTime 	-> 142000
	 * @param endTime		-> 144000
	 * @return				-> control XML string
	 */
	public static String findControlMessage(String InternalID, String Msg, String localDate, String startTime, String endTime){
		
		String part1 = "<dMSControlRequest><organization-information><organization-id>xxxxx</organization-id><organization-name>Ames</organization-name><center-id>1</center-id></organization-information><device-id>"
					+ InternalID
					+ "</device-id><request-id>intrans</request-id><operator-id>intrans</operator-id><user-id>xxxxx\\xxxxx\\xxxxx</user-id><password>xxxxx</password><dms-beacon-control>0</dms-beacon-control>";

		String part2 = "<dms-message>" + Msg + "</dms-message>";
		
		String part3 = "<command-request-priority>1</command-request-priority><request-date-time><local-date>"
					+ localDate + "</local-date>" 
					+ "<local-time>" + startTime + "</local-time><utc-offset>-0500</utc-offset></request-date-time>"
					+ "<command-end-time>" + endTime + "</command-end-time></dMSControlRequest>";

		return part1 + part2 + part3;
	}

	
	
}
