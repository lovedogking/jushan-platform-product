import java.io.UnsupportedEncodingException;
import java.util.Base64;
import java.util.Scanner;
import com.sun.jna.Pointer;

public class LPRDemo {

	static LPRSDK lpr = LPRSDK.INSTANCE;
	VZLPRC_PLATE_INFO_CALLBACK PlateCallBack = new VZLPRC_PLATE_INFO_CALLBACK();
	VZDEV_SERIAL_RECV_DATA_CALLBACK SerialCallBack = new VZDEV_SERIAL_RECV_DATA_CALLBACK();
    VZLPRC_FIND_DEVICE_CALLBACK_EX findCallBack = new VZLPRC_FIND_DEVICE_CALLBACK_EX();
	VZLPRC_WLIST_QUERY_CALLBACK wlistCallback = new VZLPRC_WLIST_QUERY_CALLBACK();
	
	private void InitClient(){
		try{
			lpr.VzLPRClient_Setup();

            // lpr.VZLPRClient_StartFindDeviceEx(findCallBack, Pointer.NULL);

			long handle = lpr.VzLPRClient_Open("192.168.13.112", 80, "admin", "admin");

			// long handle = lpr.VzLPRClient_OpenV2("118.31.4.231", 8000, "admin", "admin",8557, 1, "28fd49c3-83d38341");

			System.out.println("handle:" + handle);

			int res = lpr.VzLPRClient_WhiteListSetQueryCallBack( handle ,wlistCallback , Pointer.NULL);

			// AddWlistPlate(handle, "12345");
			// AddWlistPlate(handle, "宸滲12346");
			// DeleteWlistByPlate(handle, "宸滱12345");
			// QueryWlistByPlate(handle, "宸滲12346");
			// BatchAddWlistPlates(handle);
			// QueryWlistByPage(handle,0);
			Record(handle);
			
			int serial_handle = lpr.VzLPRClient_SerialStart(handle, 0, SerialCallBack, Pointer.NULL);
			System.out.println("serial_handle:" + serial_handle);

			if (serial_handle != 0)
			{
				SendSerialData(serial_handle);

				lpr.VzLPRClient_SerialStop(serial_handle);
			}

			SnapImg(handle);

			if(handle == 0 || handle == -1) {
				System.out.println("open failed");
			}
			else {
				System.out.println("open success");
			}

			int callbackindex = lpr.VzLPRClient_SetPlateInfoCallBack(handle, PlateCallBack, Pointer.NULL, 1);
			System.out.println(callbackindex);
			
			Scanner input = new Scanner(System.in);
			
			int end = input.nextInt();
			System.out.println(end);
			
			lpr.VzLPRClient_Close(handle);
		}
		catch(Exception e)
		{
			System.out.println("Error:"+e);
		}
	}

	private boolean LoadImg(long handle, int id)
	{
		boolean ret = false;

		int max_size = 1920 * 1080;
		int[] size = new int[1];

		size[0] = max_size;
		byte[] img_data = new byte[max_size];

		int ret_load = lpr.VzLPRClient_LoadImageById(handle, id, img_data, size);
		if (ret_load == 0){
			ret = true;
		}

		return ret;
	}

	private int AddWlistPlate( long handle, String plate)
	{
		int ret = 0;

		try
		{
			LPRSDK.VZ_LPR_WLIST_VEHICLE.ByReference wlistVehicle = new LPRSDK.VZ_LPR_WLIST_VEHICLE.ByReference();

			byte[] plate_data = plate.getBytes("GB2312");
			for (int i = 0; i < plate_data.length; i++) {
				wlistVehicle.strPlateID[i] = plate_data[i];
				wlistVehicle.strCode[i] = plate_data[i];
			}

			wlistVehicle.uCustomerID	= 0;
			wlistVehicle.bEnable		= 1;

			LPRSDK.VZ_TM.ByValue struTMOverdule = new LPRSDK.VZ_TM.ByValue();
			struTMOverdule.nYear	= 2020;
			struTMOverdule.nMonth	= 12;
			struTMOverdule.nMDay	= 30;
			struTMOverdule.nHour	= 12;
			struTMOverdule.nMin		= 40;
			struTMOverdule.nSec		= 50;

			wlistVehicle.struTMOverdule	= struTMOverdule;
			wlistVehicle.bUsingTimeSeg	= 0;
			wlistVehicle.bAlarm			= 0;
			wlistVehicle.bEnableTMOverdule = 1;

			LPRSDK.VZ_LPR_WLIST_ROW.ByReference wlistRow	= new LPRSDK.VZ_LPR_WLIST_ROW.ByReference();
			wlistRow.pVehicle = wlistVehicle;
			wlistRow.pCustomer = null;
			LPRSDK.VZ_LPR_WLIST_IMPORT_RESULT.ByReference importResult = new LPRSDK.VZ_LPR_WLIST_IMPORT_RESULT.ByReference();
			ret = lpr.VzLPRClient_WhiteListImportRows(handle, 1, wlistRow, importResult);
		}
		catch(Exception e)
		{
			System.out.println("error:" + e.getMessage());
		}

		return ret;
	}
	
	private int BatchAddWlistPlates( long handle )
	{
		int ret = 0;
		
		String cmd_type = "white_list_operator";
		String cmd = "{\"cmd\":\"white_list_operator\",\"dldb_rec\":[{\"context\":\"\",\"enable\":1,\"enable_time\":\"2021-01-28 00:00:00\",\"need_alarm\":0,\"overdue_time\":\"2021-01-28 23:59:59\",\"plate\":\"川A12345\",\"seg_time_end\":\"\",\"seg_time_start\":\"\"},{\"context\":\"\",\"enable\":1,\"enable_time\":\"2021-01-28 00:00:00\",\"need_alarm\":0,\"overdue_time\":\"2021-01-28 23:59:59\",\"plate\":\"川A12346\",\"seg_time_end\":\"\",\"seg_time_start\":\"\"}],\"id\":\"132156\",\"operator_type\":\"update_or_add\"}";
		
		byte[] response = new byte[1024]; 
		lpr.VzLPRClient_TcpTransSend(handle, cmd_type, cmd, response, 1024);
		
		/*
		
		try
		{	
			int count = 2;

			LPRSDK.VZ_LPR_WLIST_ROW[] rows = new LPRSDK.VZ_LPR_WLIST_ROW[count];
			LPRSDK.VZ_LPR_WLIST_IMPORT_RESULT[] importResult = new LPRSDK.VZ_LPR_WLIST_IMPORT_RESULT[count];
			
		
				// LPRSDK.VZ_LPR_WLIST_VEHICLE[] wlistVehicle = new LPRSDK.VZ_LPR_WLIST_VEHICLE[count];
				
				for (int i = 0; i < count; i++) {
					rows[i].pVehicle  = new LPRSDK.VZ_LPR_WLIST_VEHICLE.ByReference();
					rows[i].pVehicle.strPlateID[0] = '1';
					rows[i].pVehicle.strPlateID[1] = '2';
					rows[i].pVehicle.strPlateID[2] = '3';
					rows[i].pVehicle.strPlateID[3] = '4';
					if (i == 0) {
						rows[i].pVehicle.strPlateID[4] = '5';
					}
					else {
						rows[i].pVehicle.strPlateID[4] = '6';
					}
					
					
					rows[i].pVehicle.uCustomerID	= 0;
					rows[i].pVehicle.bEnable		= 1;
					

					LPRSDK.VZ_TM.ByValue struTMOverdule = new LPRSDK.VZ_TM.ByValue();
					struTMOverdule.nYear	= 2016;
					struTMOverdule.nMonth	= 12;
					struTMOverdule.nMDay	= 30;
					struTMOverdule.nHour	= 12;
					struTMOverdule.nMin		= 40;
					struTMOverdule.nSec		= 50;

					rows[i].pVehicle.struTMOverdule	= struTMOverdule;
					rows[i].pVehicle.bUsingTimeSeg	= 0;
					rows[i].pVehicle.bAlarm			= 1;
					rows[i].pVehicle.bEnableTMOverdule = 1;
					
					rows[i].pVehicle.strCode[0] = '1';
					rows[i].pVehicle.strCode[1] = '2';
					rows[i].pVehicle.strCode[2] = '3';
					rows[i].pVehicle.strCode[3] = '4';
					if (i == 0) {
						rows[i].pVehicle.strCode[4] = '5';
					}
					else {
						rows[i].pVehicle.strCode[4] = '6';
					}
					
					rows[i].pCustomer = null;
				}
				
			
			ret = lpr.VzLPRClient_WhiteListImportRows(handle, count, rows, importResult);
		}
		catch(Exception e)
		{
			System.out.println("Error:"+e);
		}
		*/
		
		return ret;
	}

	private boolean DeleteWlistByPlate(long handle, String plate)
	{
		boolean ret = false;
		try {
			byte[] plate_data = plate.getBytes("GB2312");

			byte[] value2 = new byte[plate_data.length + 1];
			for (int i = 0; i < plate_data.length; i++) {
				value2[i] = plate_data[i];
			}
			int ret_delete = lpr.VzLPRClient_WhiteListDeleteVehicle(handle, value2);
			if (ret_delete == 0) {
				ret = true;
			}
		}
		catch(UnsupportedEncodingException e)
		{
			System.out.println("exception msg:" + e.getMessage());
		}

		return ret;
	}

	private boolean QueryWlistByPlate(long handle, String plate)
	{
		boolean ret = false;
		try {
			byte[] plate_data = plate.getBytes("GB2312");

			byte[] value2 = new byte[plate_data.length + 1];
			for (int i = 0; i < plate_data.length; i++) {
				value2[i] = plate_data[i];
			}
			int res = lpr.VzLPRClient_WhiteListQueryVehicleByPlate(handle,value2);
			if (res == 0) {
				ret = true;
			}
		}
		catch(UnsupportedEncodingException e)
		{
			System.out.println("exception msg:" + e.getMessage());
		}

		return ret;
	}

	private boolean QueryWlistByPage(long handle, int nPageIndex)
	{
		boolean ret = false;

		LPRSDK.VZ_LPR_WLIST_LIMIT.ByReference limit = new LPRSDK.VZ_LPR_WLIST_LIMIT.ByReference();
		limit.limitType = 2; // LPRSDK.VZ_LPR_WLIST_LIMIT_TYPE.VZ_LPR_WLIST_LIMIT_TYPE_RANGE;

		LPRSDK.VZ_LPR_WLIST_RANGE_LIMIT.ByReference rangLimit = new LPRSDK.VZ_LPR_WLIST_RANGE_LIMIT.ByReference();
		rangLimit.startIndex = nPageIndex * 10;
		rangLimit.count = 10;
		limit.pRangeLimit = rangLimit;

		LPRSDK.VZ_LPR_WLIST_SEARCH_CONSTRAINT.ByReference searchConstraint = new LPRSDK.VZ_LPR_WLIST_SEARCH_CONSTRAINT.ByReference();
		// strcpy(searchConstraint.key, "PlateID");
		// strcpy_s(searchConstraint.search_string, sizeof(searchConstraint.search_string), m_sPlate.c_str());
		searchConstraint.key[0]= 'P';
		searchConstraint.key[1]= 'l';
		searchConstraint.key[2]= 'a';
		searchConstraint.key[3]= 't';
		searchConstraint.key[4]= 'e';
		searchConstraint.key[5]= 'I';
		searchConstraint.key[6]= 'D';

		LPRSDK.VZ_LPR_WLIST_SEARCH_WHERE.ByReference searchWhere = new LPRSDK.VZ_LPR_WLIST_SEARCH_WHERE.ByReference();
		searchWhere.pSearchConstraints = searchConstraint;
		searchWhere.searchConstraintCount = 1;
		searchWhere.searchType = 0; // LPRSDK.VZ_LPR_WLIST_SEARCH_TYPE.VZ_LPR_WLIST_SEARCH_TYPE_LIKE;

		LPRSDK.VZ_LPR_WLIST_LOAD_CONDITIONS.ByReference conditions = new LPRSDK.VZ_LPR_WLIST_LOAD_CONDITIONS.ByReference();
		conditions.pSearchWhere = searchWhere;
		conditions.pLimit = limit;

		int[] count = new int[1];
		lpr.VzLPRClient_WhiteListGetVehicleCount(handle, count, searchWhere);
		System.out.println("count:" + count[0]);

		int res = lpr.VzLPRClient_WhiteListLoadVehicle(handle, conditions);
		if (res == 0) {
			ret = true;
		}

		return ret;
	}

	private void TestLoopParam(long handle)
	{
		LPRSDK.VZ_LPRC_VIRTUAL_LOOPS_EX.ByReference pLoopInfo = new LPRSDK.VZ_LPRC_VIRTUAL_LOOPS_EX.ByReference();
		lpr.VzLPRClient_GetVirtualLoopEx(handle, pLoopInfo);
		pLoopInfo.struLoop[0].eCrossDir = 1;
		lpr.VzLPRClient_SetVirtualLoopEx(handle, pLoopInfo);
	}

	private int SetDateTime(long handle)
	{
		LPRSDK.VZ_DATE_TIME_INFO.ByReference pDTInfo = new LPRSDK.VZ_DATE_TIME_INFO.ByReference();
		pDTInfo.uYear = 2019;
		pDTInfo.nMonth = 5;
		pDTInfo.nMDay = 6;
		pDTInfo.nHour = 12;
		pDTInfo.nMin  = 30;
		pDTInfo.nSec  = 40;

		int ret = lpr.VzLPRClient_SetDateTime(handle, pDTInfo);
		return ret;
	}

	private int SetOSDParam(long handle)
	{
		LPRSDK.VZ_LPRC_OSD_Param.ByReference osd_param = new LPRSDK.VZ_LPRC_OSD_Param.ByReference();
		lpr.VzLPRClient_GetOsdParam(handle, osd_param);

		osd_param.overlaytext[0] = '1';
		osd_param.overlaytext[1] = '2';
		osd_param.overlaytext[2] = '3';
		osd_param.overlaytext[3] = '4';
		osd_param.overlaytext[4] = 0;
		int ret = lpr.VzLPRClient_SetOsdParam(handle, osd_param);
		return ret;
	}

	private int GetIsConnect(long handle)
	{
		byte[] state = new byte[1];
		int ret = lpr.VzLPRClient_IsConnected(handle, state);
		System.out.println("state:" + state[0]);

		return ret;
	}

	private int SendSerialData(long serial_handle)
	{
		byte data[] = new byte[]{0x01, 0x02, 0x05, (byte)0xAA, (byte)0xDD};
		int ret = lpr.VzLPRClient_SerialSend(serial_handle, data, data.length);

		System.out.println("SendSerialData ret:" + ret);
		return ret;
	}

	private int SnapImg(long handle)
	{
		int img_len = 1280 * 720;
		byte[] img_data = new byte[img_len];
		int real_len = lpr.VzLPRClient_GetSnapImage(handle, img_data, img_len);

		System.out.println("img data len:" + real_len);

		return real_len;
	}
	
	private int Record(long handle) {
		lpr.VzLPRClient_SaveRealData(handle, "d:\\test.avi");
		
		// 
		// lpr.VzLPRClient_StopSaveRealData(handle);
		return 0;
	}
	
	public class VZLPRC_PLATE_INFO_CALLBACK implements LPRSDK.VZLPRC_PLATE_INFO_CALLBACK
    {
        public void invoke(long handle,Pointer pUserData,LPRSDK.TH_PlateResult_Pointer.ByReference pResult,int uNumPlates,
        		int eResultType,LPRSDK.VZ_LPRC_IMAGE_INFO_Pointer.ByReference pImgFull,LPRSDK.VZ_LPRC_IMAGE_INFO_Pointer.ByReference pImgPlateClip)
    	{
        	int type = LPRSDK.VZ_LPRC_RESULT_TYPE.VZ_LPRC_RESULT_REALTIME.ordinal();
        	if(eResultType != type)
        	{
				byte[] device_ip = new byte[32];
				lpr.VzLPRClient_GetDeviceIP(handle, device_ip, 32);

				String ip = new String(device_ip);
				System.out.println("device ip:" + ip);

        		try {
					String license = new String(pResult.license, "gb2312");
					int index = license.indexOf("\0");
					if (index > 0) {
						license = license.substring(0, index);
					}
					
					System.out.println(license);

					System.out.println("nIsFakePlate:" + pResult.nIsFakePlate);

					String path = "./" + pResult.struBDTime.bdt_year + pResult.struBDTime.bdt_mon
							+ pResult.struBDTime.bdt_mday + pResult.struBDTime.bdt_hour
							+ pResult.struBDTime.bdt_min + pResult.struBDTime.bdt_sec + ".jpg";
					lpr.VzLPRClient_ImageSaveToJpeg(pImgFull, path, 100);
				}
        		catch(UnsupportedEncodingException e)
				{
					System.out.println("exception msg:" + e.getMessage());
				}
        	}
        }
    }

	public class VZDEV_SERIAL_RECV_DATA_CALLBACK implements LPRSDK.VZDEV_SERIAL_RECV_DATA_CALLBACK
	{
		public void invoke(long handle,Pointer pRecvData , int uRecvSize, Pointer pUserData )
		{
			byte[] serial_data = pRecvData.getByteArray(0, uRecvSize);

			System.out.println("data size:" + uRecvSize);
		}
	}

    public class VZLPRC_FIND_DEVICE_CALLBACK_EX implements LPRSDK.VZLPRC_FIND_DEVICE_CALLBACK_EX
    {
        public void invoke(String pStrDevName, String pStrIPAddr, short usPort1, short usType, long SL, long SH, String netmask, String gateway, Pointer pUserData)
        {
            System.out.println("find ip:" + pStrIPAddr);
        }
    }

	public class VZLPRC_WLIST_QUERY_CALLBACK implements LPRSDK.VZLPRC_WLIST_QUERY_CALLBACK
	{
		public void invoke( int cbtype,LPRSDK.VZ_LPR_WLIST_VEHICLE.ByReference vehicle,LPRSDK.VZ_LPR_WLIST_CUSTOMER.ByReference pCustomer,Pointer UserData)
		{
			System.out.println("VZLPRC_WLIST_QUERY_CALLBACK");

			try {
				String plate = new String(vehicle.strPlateID, "gb2312");
				System.out.println(plate);
			}
			catch(UnsupportedEncodingException e)
			{
				System.out.println("exception msg:" + e.getMessage());
			}
		}
	}
	
	public static void main(String[] args) {
		// TODO Auto-generated method stub

		// byte data[] = new byte[]{0x01, 0x02, 0x05, (byte)0xAA, (byte)0xDD};
		// byte base64_data[] = Base64.getEncoder().encode(data);
		// String base64_str = new String(base64_data);
		// System.out.println("base64 str:" + base64_str);

		LPRDemo lprtest = new LPRDemo();
		lprtest.InitClient();
	}

}
