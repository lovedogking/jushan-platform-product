import com.sun.jna.Pointer;

public class DemoMain {
	
	//一体机TcpSDK实例
	static LPRTcpSDK lpr = LPRTcpSDK.INSTANCE;
	
	//车牌识别结果信息回调函数
	public static class PLATE_INFO_CALLBACK implements LPRTcpSDK.VZLPRC_TCP_PLATE_INFO_CALLBACK
    {
        public void invoke(int handle
        				  , Pointer pUserData
        				  , LPRTcpSDK.TH_PlateResult_Pointer.ByReference pResult
        				  , int uNumPlates
        				  , int eResultType
        				  , String sImgFull
  	    				  , int nFullSize
  	    				  , String sImgPlateClip
  	    				  , int nClipSize)
    	{
        	System.out.println("车牌识别结果!");
			//实现实际业务需求
			//????????????????
        }
    }
	
	//透明通道接受数据回调函数
	public static class VZDEV_TCP_SERIAL_RECV_DATA_CALLBACK implements LPRTcpSDK.VZDEV_TCP_SERIAL_RECV_DATA_CALLBACK
    {
		public void invoke(int handle
				, Pointer pRecvData	    				
				, int nRecvSize
				, Pointer pUserData)
    	{
        	System.out.println("Serial Recv!");
			//实现具体的业务需求
			//??????????????????
        }
    }
	
	public static void main(String[] args) throws Exception 
	{
		//初始化SDK环境
		int nRet = lpr.VzLPRTcp_Setup();
		if (nRet != 0)
		{
			System.out.println("初始化环境失败");
			return;
		}	
		
		//打开设备
		String ip = "192.168.4.172";
		String admin = "admin";
		String password = "admin";
		int handle = lpr.VzLPRTcp_Open(ip, 80, admin, password);
		
		if(handle == 0)
		{
			System.out.println("打开设备失败");
			return;
		}	
		else
		{
			System.out.println("打开设备成功");
			
			//设置车牌识别结果回调
			PLATE_INFO_CALLBACK PlateCallBack = new PLATE_INFO_CALLBACK();
			lpr.VzLPRTcp_SetPlateInfoCallBack(handle, PlateCallBack, Pointer.NULL, 0);
			
			//设置透明通道接受数据回调
			VZDEV_TCP_SERIAL_RECV_DATA_CALLBACK SerialRecv = new VZDEV_TCP_SERIAL_RECV_DATA_CALLBACK(); 
			lpr.VzLPRTcp_SerialStart(handle, 0, SerialRecv, Pointer.NULL);
			
			//强制触发
			lpr.VzLPRTcp_ForceTrigger(handle);
			
			//释放SDK资源
			lpr.VzLPRTcp_Cleanup();
		}
	}
}
