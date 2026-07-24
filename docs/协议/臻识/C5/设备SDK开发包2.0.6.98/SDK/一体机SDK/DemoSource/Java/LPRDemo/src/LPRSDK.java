import java.util.Arrays;
import java.util.List;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.ByteByReference;
import com.sun.jna.Callback;

public interface LPRSDK extends Library {
	

	/** LPRSDK INSTANCE = (LPRSDK) Native.load("/home/sober/eclipse-workspace/LPRDemo/lib/libVzLPRSDK.so", LPRSDK.class);*/
	LPRSDK INSTANCE = (LPRSDK) Native.load("D:\\git_code\\JavaProject\\LPRDemo\\lib\\win64\\VzLPRSDK.dll", LPRSDK.class);
	
	public static class VZ_LPRC_IMAGE_INFO_Pointer extends Structure
	{
		public int uWidth;		
		public int uHeight;		
		public int uPitch;		
		public int uPixFmt;		
		public ByteByReference pBuffer;	
		
	    public static class ByReference extends VZ_LPRC_IMAGE_INFO_Pointer implements Structure.ByReference {}  
	    public static class ByValue extends VZ_LPRC_IMAGE_INFO_Pointer implements Structure.ByValue {}  
	  
	    @Override  
	    protected List<String> getFieldOrder() {  
	        return Arrays.asList(new String[]{"uWidth", "uHeight", "uPitch", "uPixFmt","pBuffer"});  
	    }  
	}
	
	public static class VZ_TM extends Structure
	{
		public short nYear;		
		public short nMonth;	
		public short nMDay;		
		public short nHour;		
		public short nMin;		
		public short nSec;		
		
	    public static class ByReference extends VZ_TM implements Structure.ByReference {}  
	    public static class ByValue extends VZ_TM implements Structure.ByValue {}  
	  
	    @Override  
	    protected List<String> getFieldOrder() {  
	        return Arrays.asList(new String[]{"nYear", "nMonth", "nMDay", "nHour", "nMin", "nSec"});  
	    }  
	}

	public static class VZ_DATE_TIME_INFO extends Structure
	{
		public int uYear;	
		public int nMonth;	
		public int nMDay;	
		public int nHour;	
		public int nMin;	
		public int nSec;	

		public static class ByReference extends VZ_DATE_TIME_INFO implements Structure.ByReference {}
		public static class ByValue extends VZ_DATE_TIME_INFO implements Structure.ByValue {}

		@Override
		protected List<String> getFieldOrder() {
			return Arrays.asList(new String[]{"nYear", "nMonth", "nMDay", "nHour", "nMin", "nSec"});
		}
	}
	
	public static class VZ_LPR_WLIST_VEHICLE extends Structure
	{
		public int	uVehicleID;
		public byte[] strPlateID = new byte[32];			
		public int	uCustomerID;							
		public int	bEnable;								
		public int	bEnableTMEnable;						
		public int	bEnableTMOverdule;	
		public VZ_TM.ByValue		struTMEnable;
		public VZ_TM.ByValue		struTMOverdule;			
		public int	bUsingTimeSeg;							
		public byte[]	byTimeSegOrRange = new byte[228];   
		public int	bAlarm;									
		public int	iColor;									
		public int	iPlateType;								
		public byte[]	strCode = new byte[32];				
		public byte[]	strComment = new byte[64];			
		
	    public static class ByReference extends VZ_LPR_WLIST_VEHICLE implements Structure.ByReference {}  
	    public static class ByValue extends VZ_LPR_WLIST_VEHICLE implements Structure.ByValue {}

		@Override
		protected List<String> getFieldOrder() {
			return Arrays.asList(new String[]{"uVehicleID", "strPlateID", "uCustomerID", "bEnable", "bEnableTMEnable", "bEnableTMOverdule", "struTMEnable", "struTMOverdule",
			"bUsingTimeSeg", "byTimeSegOrRange", "bAlarm", "iColor", "iPlateType", "strCode", "strComment"});
		}
	}
	
	public static class VZ_LPR_WLIST_ROW extends Structure
	{
		public Pointer pCustomer;							
		public VZ_LPR_WLIST_VEHICLE.ByReference pVehicle;	
		
	    public static class ByReference extends VZ_LPR_WLIST_ROW implements Structure.ByReference {}  
	    public static class ByValue extends VZ_LPR_WLIST_ROW implements Structure.ByValue {}

		protected List<String> getFieldOrder() {
			return Arrays.asList(new String[]{"pCustomer", "pVehicle"});
		}
	}

	public static class VZ_LPR_WLIST_CUSTOMER extends Structure
	{
		public int	uCustomerID;
		public byte[] strName = new byte[32];
		public byte[] strCode = new byte[32];
		public byte[] reserved = new byte[256];

		public static class ByReference extends VZ_LPR_WLIST_CUSTOMER implements Structure.ByReference {}
		public static class ByValue extends VZ_LPR_WLIST_CUSTOMER implements Structure.ByValue {}

		@Override
		protected List<String> getFieldOrder() {
			return Arrays.asList(new String[]{"uCustomerID", "strName", "strCode", "reserved"});
		}
	}
	

	public static class VZ_LPR_WLIST_IMPORT_RESULT extends Structure
	{
		public int ret;
		public int error_code;
		
	    public static class ByReference extends VZ_LPR_WLIST_IMPORT_RESULT implements Structure.ByReference {}  
	    public static class ByValue extends VZ_LPR_WLIST_IMPORT_RESULT implements Structure.ByValue {}

		protected List<String> getFieldOrder() {
			return Arrays.asList(new String[]{"ret", "error_code"});
		}
	}

	public enum VZ_LPR_WLIST_LIMIT_TYPE
	{
		VZ_LPR_WLIST_LIMIT_TYPE_ONE,   
		VZ_LPR_WLIST_LIMIT_TYPE_ALL,   
		VZ_LPR_WLIST_LIMIT_TYPE_RANGE, 
	}


	public static class VZ_LPR_WLIST_RANGE_LIMIT extends Structure
	{
		public int startIndex;  
		public int count;      
		public static class ByReference extends VZ_LPR_WLIST_RANGE_LIMIT implements Structure.ByReference {}
		public static class ByValue extends VZ_LPR_WLIST_RANGE_LIMIT implements Structure.ByValue {}

		protected List<String> getFieldOrder() {
			return Arrays.asList(new String[]{"startIndex", "count"});
		}
	}

	public static class VZ_LPR_WLIST_LIMIT extends Structure
	{
		public int limitType; 						
		public VZ_LPR_WLIST_RANGE_LIMIT.ByReference pRangeLimit;       

		public static class ByReference extends VZ_LPR_WLIST_LIMIT implements Structure.ByReference {}
		public static class ByValue extends VZ_LPR_WLIST_LIMIT implements Structure.ByValue {}

		protected List<String> getFieldOrder() {
			return Arrays.asList(new String[]{"limitType", "pRangeLimit"});
		}
	}

	public enum VZ_LPR_WLIST_SEARCH_TYPE
	{
		VZ_LPR_WLIST_SEARCH_TYPE_LIKE,
		VZ_LPR_WLIST_SEARCH_TYPE_EQUAL
	}

	public static class VZ_LPR_WLIST_SEARCH_CONSTRAINT extends Structure
	{
		public byte[] key = new byte[32];
		public byte[] search_string = new byte[128];

		public static class ByReference extends VZ_LPR_WLIST_SEARCH_CONSTRAINT implements Structure.ByReference {}
		public static class ByValue extends VZ_LPR_WLIST_SEARCH_CONSTRAINT implements Structure.ByValue {}

		protected List<String> getFieldOrder() {
			return Arrays.asList(new String[]{"key", "search_string"});
		}
	}

	public static class VZ_LPR_WLIST_SEARCH_WHERE extends Structure
	{
		public int searchType; 
		public int searchConstraintCount;          
		public VZ_LPR_WLIST_SEARCH_CONSTRAINT.ByReference pSearchConstraints;   

		public static class ByReference extends VZ_LPR_WLIST_SEARCH_WHERE implements Structure.ByReference {}
		public static class ByValue extends VZ_LPR_WLIST_SEARCH_WHERE implements Structure.ByValue {}

		protected List<String> getFieldOrder() {
			return Arrays.asList(new String[]{"searchType", "searchConstraintCount","pSearchConstraints"});
		}
	}

	public static class VZ_LPR_WLIST_LOAD_CONDITIONS extends Structure
	{
		public VZ_LPR_WLIST_SEARCH_WHERE.ByReference pSearchWhere;
		public VZ_LPR_WLIST_LIMIT.ByReference pLimit;      
		public Pointer pSortType;    

		public static class ByReference extends VZ_LPR_WLIST_LOAD_CONDITIONS implements Structure.ByReference {}
		public static class ByValue extends VZ_LPR_WLIST_LOAD_CONDITIONS implements Structure.ByValue {}

		protected List<String> getFieldOrder() {
			return Arrays.asList(new String[]{"pSearchWhere", "pLimit","pSortType"});
		}
	}
	

	public enum VZ_LPRC_RESULT_TYPE
	{
		VZ_LPRC_RESULT_REALTIME,		
		VZ_LPRC_RESULT_STABLE,			
		VZ_LPRC_RESULT_FORCE_TRIGGER,	
		VZ_LPRC_RESULT_IO_TRIGGER,		
		VZ_LPRC_RESULT_VLOOP_TRIGGER,	
		VZ_LPRC_RESULT_MULTI_TRIGGER,	
		VZ_LPRC_RESULT_TYPE_NUM			
	}

	public static class VZ_LPRC_VERTEX extends Structure{
		public int X_1000;
		public int Y_1000;

		public static class ByReference extends VZ_LPRC_VERTEX implements Structure.ByReference {}
		public static class ByValue extends VZ_LPRC_VERTEX implements Structure.ByValue {}

		protected List<String> getFieldOrder() {
			return Arrays.asList(new String[]{"X_1000", "Y_1000"});
		}
	}

	public static class VZ_LPRC_VIRTUAL_LOOP_EX extends Structure
	{
		public byte			byID;		
		public byte			byEnable;	
		public byte			byDraw;		
		public byte		    byRes;	    
		public byte[] strName = new byte[32];
		public int uNumVertex;
		public VZ_LPRC_VERTEX[]	struVertex = new VZ_LPRC_VERTEX[12];	
		public int eCrossDir;	        
		public int uTriggerTimeGap;	    
		public int uMaxLPWidth;		    
		public int uMinLPWidth;		    

		public static class ByReference extends VZ_LPRC_VIRTUAL_LOOP_EX implements Structure.ByReference {}
		public static class ByValue extends VZ_LPRC_VIRTUAL_LOOP_EX implements Structure.ByValue {}

		protected List<String> getFieldOrder() {
			return Arrays.asList(new String[]{"byID", "byEnable", "byDraw", "byRes", "strName", "uNumVertex", "struVertex", "eCrossDir", "uTriggerTimeGap", "uMaxLPWidth", "uMinLPWidth"});
		}
	}


	public static class VZ_LPRC_VIRTUAL_LOOPS_EX extends Structure
	{
		public int				uNumVirtualLoop;
		public VZ_LPRC_VIRTUAL_LOOP_EX[] 	struLoop = new VZ_LPRC_VIRTUAL_LOOP_EX[8];

		public static class ByReference extends VZ_LPRC_VIRTUAL_LOOPS_EX implements Structure.ByReference {}
		public static class ByValue extends VZ_LPRC_VIRTUAL_LOOPS_EX implements Structure.ByValue {}

		protected List<String> getFieldOrder() {
			return Arrays.asList(new String[]{"uNumVirtualLoop", "struLoop"});
		}
	}


	public static class CarBrand_Pointer extends Structure {
		public byte brand;
		public byte type;
		public short year;

		public static class ByReference extends CarBrand_Pointer implements Structure.ByReference {}
		public static class ByValue extends CarBrand_Pointer implements Structure.ByValue {}

		@Override
		protected List<String> getFieldOrder() {
			return Arrays.asList(new String[]{"brand", "type", "year"});
		}
	}

	public static class TH_RECT_Pointer extends Structure {
		public int left;
		public int top;
		public int right;
		public int bottom;

		public static class ByReference extends TH_RECT_Pointer implements Structure.ByReference {}
		public static class ByValue extends TH_RECT_Pointer implements Structure.ByValue {}

		@Override
		protected List<String> getFieldOrder() {
			return Arrays.asList(new String[]{"left", "top", "right", "bottom"});
		}
	}

	public static class VZ_TIMEVAL_Pointer extends Structure
	{
		public int uTVSec;
		public int uTVUSec;

		public static class ByReference extends VZ_TIMEVAL_Pointer implements Structure.ByReference {}
		public static class ByValue extends VZ_TIMEVAL_Pointer implements Structure.ByValue {}

		@Override
		protected List<String> getFieldOrder() {
			return Arrays.asList(new String[]{"uTVSec", "uTVUSec"});
		}
	}


	public static class VzBDTime_Pointer extends Structure
	{
		public byte bdt_sec;    
		public byte bdt_min;    
		public byte bdt_hour;   
		public byte bdt_mday;   
		public byte bdt_mon;    
		public byte[] res1 = new byte[3];   
		public int bdt_year;   				
		public byte[] res2 = new byte[4];   

		public static class ByReference extends VzBDTime_Pointer implements Structure.ByReference {}
		public static class ByValue extends VzBDTime_Pointer implements Structure.ByValue {}

		@Override
		protected List<String> getFieldOrder() {
			return Arrays.asList(new String[]{"bdt_sec", "bdt_min","bdt_hour","bdt_mday","bdt_mon","res1","bdt_year","res2"});
		}
	}
	public static class VZ_LPRC_OSD_Param extends Structure
	{
		public byte	dstampenable;					/** 0 off 1 on */
		public int	dateFormat;						/** 0:YYYY/MM/DD;1:MM/DD/YYYY;2:DD/MM/YYYY*/
		public int  datePosX;
		public int	datePosY;
		public byte	tstampenable;   				/**0 off 1 on*/
		public int	timeFormat;						/**0:12Hrs;1:24Hrs*/
		public int  timePosX;
		public int	timePosY;
		public byte	nLogoEnable;					/**0 off 1 on*/
		public int	nLogoPositionX;   				/**<  logo position*/
		public int	nLogoPositionY;   				/**<  logo position*/
		public byte	nTextEnable;					/**0 off 1 on*/
		public int  nTextPositionX;   				/**<  text position*/
		public int	nTextPositionY;   				/**<  text position*/
		public byte[] overlaytext = new byte[16];   /**user define text           	//user define text*/

		public static class ByReference extends VZ_LPRC_OSD_Param implements Structure.ByReference {}
		public static class ByValue extends VZ_LPRC_OSD_Param implements Structure.ByValue {}

		@Override
		protected List<String> getFieldOrder() {
			return Arrays.asList(new String[]{"dstampenable", "dateFormat","datePosX","datePosY","tstampenable","timeFormat","timePosX","timePosY","nLogoEnable","nLogoPositionX","nLogoPositionY",
					"nTextEnable","nTextPositionX","nTextPositionY","overlaytext"});
		}
	}


	public static class TH_PlateResult_Pointer extends Structure {
		public byte[] license = new byte[16]; 	
		public byte[] color = new byte[8]; 		
		public int nColor; 						
		public int nType; 						
		public int nConfidence; 				
		public int nBright; 					
		public int nDirection; 					
		public TH_RECT_Pointer.ByValue rcLocation; 
		public int nTime; 						
		public VZ_TIMEVAL_Pointer.ByValue tvPTS;
		public int uBitsTrigType;
		public byte nCarBright; 				
		public byte nCarColor; 					
		public byte[] reserved0 = new byte[2];
		public int uId;
		public VzBDTime_Pointer.ByValue struBDTime;
		public byte nIsEncrypt;          		
		public byte nPlateTrueWidth;     		
		public byte nPlateDistance;      		
		public byte nIsFakePlate;        		
		public TH_RECT_Pointer.ByValue     car_location;        
		public CarBrand_Pointer.ByValue    car_brand;           
		public byte[] featureCode = new byte[20];      			
		public byte[] reserved = new byte[24];


		public static class ByReference extends TH_PlateResult_Pointer implements Structure.ByReference {}
		public static class ByValue extends TH_PlateResult_Pointer implements Structure.ByValue {}

		@Override
		protected List<String> getFieldOrder() {
			return Arrays.asList(new String[]{"license", "color", "nColor", "nType", "nConfidence", "nBright", "nDirection", "rcLocation", "nTime", "tvPTS", "uBitsTrigType", "nCarBright", "nCarColor",
					"reserved0", "uId", "struBDTime", "nIsEncrypt", "nPlateTrueWidth", "nPlateDistance", "nIsFakePlate", "car_location","car_brand","featureCode","reserved"});
		}
	}


	int VzLPRClient_Setup();


	void VzLPRClient_Cleanup();
	

	long VzLPRClient_Open(String pStrIP, int wPort, String pStrUserName, String pStrPassword);
	

	long VzLPRClient_OpenV2(String pStrIP, int wPort, String pStrUserName, String pStrPassword,int wRtspPort, int network_type, String sn);


	int VzLPRClient_Close(long handle);
	

	int VzLPRClient_IsConnected(long handle, byte[] pStatus);
	

	int VzLPRClient_SerialStart(long handle, int nSerialPort, VZDEV_SERIAL_RECV_DATA_CALLBACK func, Pointer pUserData);
	

	int VzLPRClient_SerialSend(int nSerialHandle, byte[] pData, int uSizeData);


	int VzLPRClient_SerialStop(int nSerialHandle);
		

	int VzLPRClient_SetIOOutputAuto(long handle, int uChnId, int nDuration);


	int VzLPRClient_WhiteListImportRows(long handle, int rowcount, VZ_LPR_WLIST_ROW.ByReference pRowDatas, VZ_LPR_WLIST_IMPORT_RESULT.ByReference pResults);	
	

	int VzLPRClient_WhiteListInsertVehicle(long handle, VZ_LPR_WLIST_VEHICLE.ByReference pVehicle);


	int VzLPRClient_WhiteListClearCustomersAndVehicles(long handle);


	int VzLPRClient_SetPlateInfoCallBack(long handle, VZLPRC_PLATE_INFO_CALLBACK func, Pointer pUserData, int bEnableImage);
	

	int VzLPRClient_ImageSaveToJpeg(VZ_LPRC_IMAGE_INFO_Pointer.ByReference pImgInfo, String pFullPathName, int nQuality);


	int VzLPRClient_SetDateTime(long handle, VZ_DATE_TIME_INFO.ByReference pDTInfo);


	 int VzLPRClient_LoadImageById(long handle, int id, byte[] pdata, int[] size);


	 int VzLPRClient_WhiteListDeleteVehicle(long handle, byte[] strPlateID);

	
	int  VzLPRClient_SetVirtualLoopEx(long handle, VZ_LPRC_VIRTUAL_LOOPS_EX.ByReference pVirtualLoops);


	int VzLPRClient_GetVirtualLoopEx(long handle,  VZ_LPRC_VIRTUAL_LOOPS_EX.ByReference pVirtualLoops);


	int VzLPRClient_SetOsdParam(long handle, VZ_LPRC_OSD_Param.ByReference pParam);


	int VzLPRClient_GetOsdParam(long handle, VZ_LPRC_OSD_Param.ByReference pParam);


	int VzLPRClient_ForceTrigger(long handle);


	int VzLPRClient_GetDeviceIP( long handle, byte[] ip, int max_count );


	int  VzLPRClient_SaveSnapImageToJpeg(long handle, String pFullPathName);


	int VzLPRClient_GetSnapImage(long handle, byte[] pDstBuf, int  uSizeBuf);
	
	
	int VzLPRClient_TcpTransSend(long handle, String cmd_type, String cmd, byte[] response, int max_response_len);
	
	int VzLPRClient_SaveRealData(long handle, String sFileName);
	
	int VzLPRClient_StopSaveRealData(long handle);

	public static interface VZLPRC_PLATE_INFO_CALLBACK extends Callback{
	    public void invoke(long handle,Pointer pUserData,LPRSDK.TH_PlateResult_Pointer.ByReference pResult,int uNumPlates,
	    		int eResultType,LPRSDK.VZ_LPRC_IMAGE_INFO_Pointer.ByReference pImgFull,LPRSDK.VZ_LPRC_IMAGE_INFO_Pointer.ByReference pImgPlateClip);
	}


	public static interface VZDEV_SERIAL_RECV_DATA_CALLBACK extends Callback{
		public void invoke(long handle,Pointer pRecvData , int uRecvSize, Pointer pUserData );
	}
	
	public static interface VZLPRC_COMMON_NOTIFY_CALLBACK extends Callback{
		public void invoke(long handle,Pointer pUserData, int eNotify, String pStrDetail );
	}
	
	int VZLPRClient_SetCommonNotifyCallBack(VZLPRC_COMMON_NOTIFY_CALLBACK func, Pointer pUserData);


    public static interface VZLPRC_FIND_DEVICE_CALLBACK_EX extends Callback{
        public void invoke(String pStrDevName, String pStrIPAddr, short usPort1, short usType, long SL, long SH, String netmask, String gateway, Pointer pUserData);
    }

 
    int VZLPRClient_StartFindDeviceEx(VZLPRC_FIND_DEVICE_CALLBACK_EX func, Pointer pUserData);


    void VZLPRClient_StopFindDevice();

	public static interface VZLPRC_WLIST_QUERY_CALLBACK extends Callback{
		public void invoke( int cbtype,VZ_LPR_WLIST_VEHICLE.ByReference vehicle,VZ_LPR_WLIST_CUSTOMER.ByReference pCustomer,Pointer UserData);
	}

	int  VzLPRClient_WhiteListSetQueryCallBack(  long handle ,VZLPRC_WLIST_QUERY_CALLBACK callback, Pointer pUserData);

	int VzLPRClient_WhiteListQueryVehicleByPlate(long handle,byte[] strPlateID);


	int VzLPRClient_WhiteListGetVehicleCount(long handle, int count[], VZ_LPR_WLIST_SEARCH_WHERE.ByReference pSearchWhere);

	int VzLPRClient_WhiteListLoadVehicle(long handle,  VZ_LPR_WLIST_LOAD_CONDITIONS.ByReference pLoadCondition);

}
