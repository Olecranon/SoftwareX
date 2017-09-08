# Data Preprocessing and DMS Display Module

## Code Structure
- Under the src folder:
  1. jwave\\* is the package for Wavelet data smoothing, which can be downloaded from https://github.com/cscheiblich/JWave
  2. com\transcore\webservices\\* contains a series of TransSuite® web service API. Due to the security reason, these files are not included.

- Under the implement folder:
  1. util folder has the utility methods and work zone sensor class so that this project is scalable to multiple sensors.
    - IWZ_UtilityMethods.java has common static methods to download sensor xml feeds, to parse xml file, to read sensor list, and set up Weka dataset for message classification.
    - IWZsensorClass.java is the class customized for each work zone sensor, mainly for data smoothing purpose.  

  2. online folder has the Java file that smooths the data, classifies messages, and sends messages to the field DMS via TransSuite® web service API.

- DecisionTree.model is the trained model using Weka
- IWZSensorList.csv is the static file that has Wavetronix ID and associated workzone ID.

## Code Dependencies
- To read Weka trained decision tree model file (DecisionTree.model) and classify data using trained model.
  - weka3-8.jar
- To send http POST requests to TextAlert Module (traffic information includes: classified messages, smoothed speed, Wavetronix sensor IDs, and work zone IDs)
  - httpclient-4.5.3.jar
  - httpcore-4.4.6.jar
  - commons-logging-1.2.jar
