import java.util.Arrays;
import java.util.List;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.ByteByReference;
import com.sun.jna.Callback;

public interface LPRTcpSDK extends Library {
	
	//加载TcpSDK动态库
	LPRTcpSDK INSTANCE = (LPRTcpSDK) Native.loadLibrary("/usr/local/java/src/TcpSDKDemo/liblprtcpsdk.so", LPRTcpSDK.class);
	
	/**图像信息*/
	public static class VZ_LPRC_IMAGE_INFO_Pointer extends Structure
	{
		public int uWidth;				/**<宽度*/
		public int uHeight;				/**<高度*/
		public int uPitch;				/**<图像宽度的一行像素所占内存字节数*/
		public int uPixFmt;				/**<图像像素格式，参考枚举定义图像格式（ImageFormatXXX）*/
		public ByteByReference pBuffer;	/**<图像内存的首地址*/
		
	    public static class ByReference extends VZ_LPRC_IMAGE_INFO_Pointer implements Structure.ByReference {}  
	    
	    public static class ByValue extends VZ_LPRC_IMAGE_INFO_Pointer implements Structure.ByValue {}  
	  
	    @Override  
	    protected List<String> getFieldOrder() {  
	        return Arrays.asList(new String[]{"uWidth", "uHeight", "uPitch", "uPixFmt","pBuffer"});  
	    }  
	}
	
	/** 车牌坐标 */
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
	
	/** 识别时间 */
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
	
	/**分解时间*/
	public static class VzBDTime_Pointer extends Structure
	{
	    public byte bdt_sec;    /**<秒，取值范围[0,59]*/
	    public byte bdt_min;    /**<分，取值范围[0,59]*/
	    public byte bdt_hour;   /**<时，取值范围[0,23]*/
	    public byte bdt_mday;   /**<一个月中的日期，取值范围[1,31]*/
	    public byte bdt_mon;    /**<月份，取值范围[1,12]*/
	    public byte[] res1 = new byte[3];    /**<预留*/
	    public int bdt_year;   /**<年份*/
	    public byte[] res2 = new byte[4];    /**<预留*/
	    
	    public static class ByReference extends VzBDTime_Pointer implements Structure.ByReference {}  
	    public static class ByValue extends VzBDTime_Pointer implements Structure.ByValue {}  
	  
	    @Override  
	    protected List<String> getFieldOrder() {  
	        return Arrays.asList(new String[]{"bdt_sec", "bdt_min","bdt_hour","bdt_mday","bdt_mon","res1","bdt_year","res2"});  
	    } 
	}
	
	/** 识别结果结构体 */
	public static class TH_PlateResult_Pointer extends Structure {
		public byte[] license = new byte[16]; 	// 车牌号码
		public byte[] color = new byte[8]; 		// 车牌颜色
		public int nColor; 						// 车牌颜色序
		public int nType; 						// 车牌类型
		public int nConfidence; 				// 车牌可信度
		public int nBright; 					// 亮度评价
		public int nDirection; 					// 运动方向，0 unknown, 1 left, 2 right, 3 up , 4 down
		public TH_RECT_Pointer.ByValue rcLocation; // 车牌位置  error
		public int nTime; 						// 识别所用时间
		public VZ_TIMEVAL_Pointer.ByValue tvPTS;
		public int uBitsTrigType;
		public byte nCarBright; 				// 车的亮度
		public byte nCarColor; 					// 车的颜色
		public byte[] reserved0 = new byte[2];
		public int uId;
		public VzBDTime_Pointer.ByValue struBDTime;
		public byte[] reserved = new byte[68]; // 保留字
		
	    public static class ByReference extends TH_PlateResult_Pointer implements Structure.ByReference {}  
	    public static class ByValue extends TH_PlateResult_Pointer implements Structure.ByValue {}  
	  
	    @Override  
	    protected List<String> getFieldOrder() {  
	        return Arrays.asList(new String[]{"license", "color", "nColor", "nType", "nConfidence", "nBright", "nDirection", "rcLocation", "nTime", "nCarBright", "nCarColor", "reserved"});  
	    }  
	}
	
	/**识别结果的类型*/
	public enum VZ_LPRC_RESULT_TYPE
	{
		VZ_LPRC_RESULT_REALTIME,		/**<实时识别结果*/
		VZ_LPRC_RESULT_STABLE,			/**<稳定识别结果*/
		VZ_LPRC_RESULT_FORCE_TRIGGER,	/**<调用“VzLPRClient_ForceTrigger”触发的识别结果*/
		VZ_LPRC_RESULT_IO_TRIGGER,		/**<外部IO信号触发的识别结果*/
		VZ_LPRC_RESULT_VLOOP_TRIGGER,	/**<虚拟线圈触发的识别结果*/
		VZ_LPRC_RESULT_MULTI_TRIGGER,	/**<由_FORCE_\_IO_\_VLOOP_中的一种或多种同时触发，具体需要根据每个识别结果的TH_PlateResult::uBitsTrigType来判断*/
		VZ_LPRC_RESULT_TYPE_NUM			/**<结果种类个数*/
	}
	
	//全局初始化
	int VzLPRTcp_Setup();
	//打开一个设备
	int VzLPRTcp_Open(String pStrIP, int wPort, String pStrUserName, String pStrPassword);
	//强制触发
	int VzLPRTcp_ForceTrigger(int handle);
	//全局释放
	int VzLPRTcp_Cleanup();
	//设置识别结果的回调函数	
	int VzLPRTcp_SetPlateInfoCallBack(int handle, VZLPRC_TCP_PLATE_INFO_CALLBACK func, Pointer pUserData, int bEnableImage);
	//通过该回调函数获得车牌识别信息
	public static interface VZLPRC_TCP_PLATE_INFO_CALLBACK extends Callback{
	    public void invoke(int handle
	    				, Pointer pUserData
	    				, LPRTcpSDK.TH_PlateResult_Pointer.ByReference pResult
	    				, int uNumPlates
	    				, int eResultType
	    				, String sImgFull
	    				, int nFullSize
	    				, String sImgPlateClip
	    				, int nClipSize);
	}
	//开启透明通道
	int VzLPRTcp_SerialStart(int handle, int nSerialPort, VZDEV_TCP_SERIAL_RECV_DATA_CALLBACK func, Pointer pUserData);
	//通过该回调函数获得透明通道接收的数据
	public static interface VZDEV_TCP_SERIAL_RECV_DATA_CALLBACK extends Callback {
	    public void invoke(int handle
	    				, Pointer pRecvData	    				
	    				, int nRecvSize
	    				, Pointer pUserData);
	}
}
