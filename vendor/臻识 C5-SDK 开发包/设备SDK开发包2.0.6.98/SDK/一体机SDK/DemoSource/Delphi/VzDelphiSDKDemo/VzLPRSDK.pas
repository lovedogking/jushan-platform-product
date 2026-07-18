unit VzLPRSDK;

interface
uses Windows;
const
  //车牌颜色
  LC_UNKNOWN = 0; //未知
  LC_BLUE = 1; //蓝色
  LC_YELLOW = 2; //黄色
  LC_WHITE = 3; //白色
  LC_BLACK = 4; //黑色
  LC_GREEN = 5; //绿色

//车牌类型
  LT_UNKNOWN = 0; //未知车牌
  LT_BLUE = 1; //蓝牌小汽车
  LT_BLACK = 2; //黑牌小汽车
  LT_YELLOW = 3; //单排黄牌
  LT_YELLOW2 = 4; //双排黄牌（大车尾牌，农用车）
  LT_POLICE = 5; //警车车牌
  LT_ARMPOL = 6; //武警车牌
  LT_INDIVI = 7; //个性化车牌
  LT_ARMY = 8; //单排军车牌
  LT_ARMY2 = 9; //双排军车牌
  LT_EMBASSY = 10; //使馆车牌
  LT_HONGKONG = 11; //香港进出中国大陆车牌
  LT_TRACTOR = 12; //农用车牌
  LT_COACH = 13; //教练车牌
  LT_MACAO = 14; //澳门进出中国大陆车牌
  LT_ARMPOL2 = 15; //双层武警车牌

//运动方向
  DIRECTION_LEFT = 1; //左
  DIRECTION_RIGHT = 2; //右
  DIRECTION_UP = 3; //上
  DIRECTION_DOWN = 4; //下

//图像格式（TH_SetImageFormat函数的cImageFormat参数）
  ImageFormatRGB = 0; //RGBRGBRGB...
  ImageFormatBGR = 1; //BGRBGRBGR...
  ImageFormatYUV422 = 2; //YYYY...UU...VV..	(YV16)
  ImageFormatYUV420COMPASS = 3; //YYYY...UV...		(NV12)
  ImageFormatYUV420 = 4; //YYYY...U...V...	(YU12)
  ImageFormatUYVY = 5; //UYVYUYVYUYVY...	(UYVY)
  ImageFormatNV21 = 6; //YYYY...VU...		(NV21)
  ImageFormatYV12 = 7; //YYYY...V...U		(YV12)
  ImageFormatYUYV = 8; //YUYVYUYVYUYV..	(YUYV)

//车辆颜色
  LGRAY_DARK = 0; //深
  LGRAY_LIGHT = 1; //浅

  LCOLOUR_WHITE = 0; //白
  LCOLOUR_SILVER = 1; //灰(银)
  LCOLOUR_YELLOW = 2; //黄
  LCOLOUR_PINK = 3; //粉
  LCOLOUR_RED = 4; //红
  LCOLOUR_GREEN = 5; //绿
  LCOLOUR_BLUE = 6; //蓝
  LCOLOUR_BROWN = 7; //棕
  LCOLOUR_BLACK = 8; //黑


//虚拟线圈名称
  VZ_LPRC_VIRTUAL_LOOP_NAME_LEN = 32;

//虚拟线圈顶点个数
  VZ_LPRC_VIRTUAL_LOOP_VERTEX_NUM = 4;

//可过滤的车牌识别触发类型
  VZ_LPRC_TRIG_ENABLE_STABLE = $1;
  VZ_LPRC_TRIG_ENABLE_VLOOP  = $2;
  VZ_LPRC_TRIG_ENABLE_IO_IN1 = $10;
  VZ_LPRC_TRIG_ENABLE_IO_IN2 = $20;
  VZ_LPRC_TRIG_ENABLE_IO_IN3 = $40;

  //可配置的识别类型
  VZ_LPRC_REC_BLUE  = (1 shl (LT_BLUE));
  VZ_LPRC_REC_YELLOW  = (1 shl (LT_YELLOW) or 1 shl(LT_YELLOW2));
  VZ_LPRC_REC_BLACK   = (1 shl (LT_BLACK));
  VZ_LPRC_REC_COACH   = (1 shl (LT_COACH));
  VZ_LPRC_REC_POLICE  = (1 shl (LT_POLICE));
  VZ_LPRC_REC_AMPOL   = (1 shl (LT_ARMPOL));
  VZ_LPRC_REC_ARMY    = (1 shl (LT_ARMY)  or 1 shl (LT_ARMY2));
  VZ_LPRC_REC_GANGAO  = (1 shl (LT_HONGKONG) or 1 shl (LT_MACAO));
  VZ_LPRC_REC_EMBASSY = (1 shl (LT_EMBASSY));

type
  VzLPRClientHandle = Integer;

//设置回调函数时需要指定的类型
  VZ_LPRC_CALLBACK_TYPE =
    (
    VZ_LPRC_CALLBACK_COMMON_NOTIFY = 0, //SDK通用信息反馈
    VZ_LPRC_CALLBACK_PLATE_STR = 1, //车牌号码字符
    VZ_LRPC_CALLBACK_FULL_IMAGE = 2, //完整图像
    VZ_LPRC_CALLBACK_CLIP_IMAGE = 3, //截取图像
    VZ_LPRC_CALLBACK_PLATE_RESULT = 4, //实时识别结果
    VZ_LPRC_CALLBACK_PLATE_RESULT_STABLE = 5, //稳定识别结果
    VZ_LPRC_CALLBACK_PLATE_RESULT_TRIGGER = 6, //触发的识别结果，包括API（软件）和IO（硬件）方式的
    VZ_LPRC_CALLBACK_VIDEO = 7 //视频帧回调
    );

//通用信息反馈类型
  VZ_LPRC_COMMON_NOTIFY =
    (
    VZ_LPRC_NO_ERR = 0,
    VZ_LPRC_ACCESS_DENIED = 1, //用户名密码错误
    VZ_LPRC_NETWORK_ERR = 2 //网络连接故障
    );


//识别结果的类型
  VZ_LPRC_RESULT_TYPE =
    (
    VZ_LPRC_RESULT_REALTIME = 0, //实时识别结果
    VZ_LPRC_RESULT_STABLE = 1, //稳定识别结果
    VZ_LPRC_RESULT_FORCE_TRIGGER = 2, //调用“VzLPRClient_ForceTrigger”触发的识别结果
    VZ_LPRC_RESULT_IO_TRIGGER = 3, //外部IO信号触发的识别结果
    VZ_LPRC_RESULT_VLOOP_TRIGGER = 4 //虚拟线圈触发的识别结果
    );

//触发出入的类型
   VZ_InputType  =
   (
     nWhiteList = 0, //通过
     nNotWhiteList = 1,  //不通过
     nNoLicence = 2,     //无车牌
     nBlackList = 3,     //黑名单
     nExtIoctl1 = 4,     //开关量/电平输入1
     nExtIoctl2 = 5,     //开关量/电平输入2
     nExtIoctl3 = 6     //开关量/电平输入3
   );

//顶点定义
//  *X_1000和Y_1000的取值范围为[0, 1000]；
//  *即位置信息为实际图像位置在整体图像位置的相对尺寸；
//  *例如X_1000 = x*1000/win_width，其中x为点在图像中的水平像素位置，win_width为图像宽度

  VZ_LPRC_VERTEX = record

    X_1000: LongWord; //水平方向相对坐标
    Y_1000: LongWord; //竖直方向相对坐标
  end;


// 图像信息
  VZ_LPRC_IMAGE_INFO = record
    uWidth: Word; //宽度 * /
    uHeigh: Word; // 高度 * /
    uPitch: Word; //图像宽度的一行像素所占内存字节数 * /
    uPixFmt: Word; //图像像素格式，参考枚举定义图像格式（ImageFormatXXX） * /
    pBuffer: PByte; // 图像内存的首地址 * /
  end;

type
PVZ_LPRC_IMAGE_INFO = ^VZ_LPRC_IMAGE_INFO;

VZ_TIMEVA = record
	uTVSec :Integer;
	uTVUSec:Integer;
end;


VzBDTime = record
    bdt_sec:Byte;
    bdt_min:Byte;
    bdt_hour:Byte;
    bdt_mday:Byte;
    bdt_mon:Byte;
    res1: array[0..2] of char;
    bdt_year: Integer;
    res2: array[0..3] of char;   
end;


  TH_RECT = record
    left: Integer;
    top: Integer;
    right: Integer;
    bottom: Integer;
  end;
  TH_PlateResult = record
    license: array[0..15] of char; // 车牌号码
    color: array[0..7] of char; // 车牌颜色
    nColor: Integer; // 车牌颜色序号
    nType: Integer; // 车牌类型
    nConfidence: Integer; // 车牌可信度
    nBright: Integer; // 亮度评价
    nDirection: Integer; // 运动方向，0 unknown, 1 left, 2 right, 3 up , 4 down
    rcLocation: TH_RECT; //车牌位置
    nTime: Integer; //识别所用时间
    tvPTS: VZ_TIMEVA; // /**<识别时间点*/
    uBitsTrigType:Integer; // <强制触发结果的类型,见TH_TRIGGER_TYPE_BIT
    nCarBright: Byte; //车的亮度
    nCarColor: Byte; //车的颜色
    reserved0: array[0..1] of char;
    uId:Integer;
    struBDTime:VzBDTime;
    reserved: array[0..67] of char; // 保留
  end;
type
   PTH_PlateResult = ^TH_PlateResult;

  TH_PlateResultMsg  = record
    sLicense : string ;   //车牌号
    sImgPath : string ;   //识别图片路径
  end;
type
PTH_PlateResultMsg = ^TH_PlateResultMsg;

  VZ_DEV_SERIAL_NUM = record
	  uHi: dword;
	  uLo: dword;
  end;
type
PVZ_DEV_SERIAL_NUM = ^VZ_DEV_SERIAL_NUM;

VZ_LPRC_PROVINCE_INFO = record
  strProvinces: array[0..127] of char;
	nCurrIndex:Integer;
end;
type
PVZ_LPRC_PROVINCE_INFO = ^VZ_LPRC_PROVINCE_INFO;

// 串口参数
VZ_SERIAL_PARAMETER = record
    uBaudRate: Integer; // <波特率
    uParity: Integer;   // <校验位 其值为0-2=no,odd,even
    uDataBits: Integer; // <数据位 其值为4-8=4,5,6,7,8 位数据位*/
    uStopBit: Integer;  // <停止位 其值为1,2 = 1, 2 位停止位*/
  end;
type
PVZ_SERIAL_PARAMETER = ^VZ_SERIAL_PARAMETER;

//OSD信息
VZ_LPRC_OSD_PARAM = record
   dstampenable   : Byte;         // 0 off 1 on
   dateFormat     : Integer;      // 0:YYYY/MM/DD;1:MM/DD/YYYY;2:DD/MM/YYYY
   datePosX       : Integer;
   datePosY       : Integer;
   tstampenable   : Byte;         // 0 off 1 on
   timeFormat     : Integer;      // 0:12Hrs;1:24Hrs
   timePosX       : Integer;
   timePosY       : Integer;
   nLogoEnable    : Byte;         // 0 off 1 on
   nLogoPositionX : Integer;
   nLogoPositionY : Integer;
   nTextEnable    : Byte;         // 0 off 1 on
   nTextPositionX : Integer;
   nTextPositionY : Integer;
   overlaytext    : array[0..16] of Char;     ///user define text
end;
type
  PVZ_LPRC_OSD_PARAM = ^VZ_LPRC_OSD_PARAM ;

//日期、时间
VZ_DATE_TIME_INFO = record
    uYear  : LongWord;
    uMonth : LongWord;
    uDay   : LongWord;
    uHour  : LongWord;
    uMin   : LongWord;
    uSec   : LongWord;
end;
type
  PVZ_DATE_TIME_INFO = ^VZ_DATE_TIME_INFO;

//智能视频
VZ_LPRC_DRAWMODE = record
    byDspAddTarget : Byte;
    byDspAddRule : Byte;
    byDspAddTrajectory : Byte;
    dwRes : array[0..2] of Char;
end;
type
  PVZ_LPRC_DRAWMODE = ^VZ_LPRC_DRAWMODE;

//输出配置
VZ_LPRC_OutputConfig = record
    switchout1 : Integer;
    switchout2 : Integer;
    switchout3 : Integer;
    switchout4 : Integer;
    levelout1  : Integer;
    levelout2  : Integer;
    rs485out1  : Integer;
    rs485out2  : Integer;
    eInputType : VZ_InputType;
end;

//输出配置信息
VZ_OutputConfigInfo = record
  oConfigInfo : array[0..6] of VZ_LPRC_OutputConfig;  //多个输出配置输出的消息
end;
type  PVZ_OutputConfigInfo = ^VZ_OutputConfigInfo;

const
  VZ_LPR_WLIST_INVAILID_ID = $FFFFFFFF;
  VZ_LPR_WLIST_VEHICLE_CODE_LEN	 = 32;
  VZ_LPR_WLIST_MAX_ENUM_VALUE_LEN	=	32;
  VZ_LPR_WLIST_LP_MAX_LEN	=	32;
  VZ_LPR_WLIST_TIME_SEG_MAX_NUM	= 8;

type
  //**日期时间描述*/
  VZ_TM = record
  	nYear:Word;	//**<年*/
  	nMonth:Word;//**<月*/
  	nMDay:Word;	//**<日*/
  	nHour:Word;	//**<时*/
  	nMin:Word;	//**<分*/
  	nSec:Word;	//**<秒*/
  end;
  LPVZ_TM = ^VZ_TM;

    //**一周七天的选择掩膜*/
  VZ_TM_WEEK_DAY = record
  	bSun:Byte;		//**<周日*/
  	bMon:Byte;		//**<周一*/
  	bTue:Byte;		//**<周二*/
  	bWed:Byte;		//**<周三*/
  	bThur:Byte;		//**<周四*/
  	bFri:Byte;		//**<周五*/
  	bSat:Byte;		//**<周六*/
  	reserved:Byte;
  end;

    //**一天中的时间表达*/
  VZ_TM_DAY = record
  	nHour:Word;
  	nMin:Word;
  	nSec:Word;
  	reserved:Word;
  end;

    //**时间段信息*/
  VZ_TM_WEEK_SEGMENT = record
  	uEnable:LongWord;
  	struDaySelect:VZ_TM_WEEK_DAY;		//**<一周内的天数选择*/
  	struDayTimeStart:VZ_TM_DAY;   	//**每天的时间起点*/
  	struDayTimeEnd:VZ_TM_DAY;		    //**每天的时间终点*/
  end;

  VZ_TM_RANGE = record
  	struTimeStart:VZ_TM;		//**每天的时间起点*/
  	struTimeEnd:VZ_TM;		  //**每天的时间终点*/
  end;

  VZ_TM_PERIOD = record
  	uEnable:LongWord;
  	struWeekSeg:array[0..7] of VZ_TM_WEEK_SEGMENT;
  end;

type
  //**黑白名单中的车辆记录*/
  VZ_LPR_WLIST_VEHICLE = record
  	uVehicleID:LongWord;							//**<车辆在数据库的ID*/
  	strPlateID:array[0..31] of char;	//**<车牌字符串*/
  	uCustomerID:LongWord;							//**<客户在数据库的ID，与VZ_LPR_WLIST_CUSTOMER::uCustomerID对应*/
  	bEnable:LongWord;								  //**<该记录有效标记*/
    bEnableTMEnable:LongWord;		      //**<是否开启生效时间*/
	  bEnableTMOverdule:LongWord;			  //**<是否开启过期时间*/
    struTMEnable:VZ_TM;								//**<该记录生效时间*/
	  struTMOverdule:VZ_TM;							//**<该记录过期时间*/
  	bUsingTimeSeg:LongWord;						//**<是否使用时间段*/
  	struTimeSegOrRange:VZ_TM_PERIOD;			//**<时间段信息*/
  	bAlarm:LongWord;									//**<是否触发报警（黑名单记录）*/
	  iColor:Integer;									  //**<车辆颜色*/
	  iPlateType:Integer;								//**<车牌类型*/
	  strCode:array[0..31] of char;	    //**<车辆编码*/
    strComment:array[0..63] of char;	// **<车辆编码*/
  end;
  LPVZ_LPR_WLIST_VEHICLE = ^VZ_LPR_WLIST_VEHICLE;

  const
  VZ_LPR_WLIST_CUSTOMER_NAME_LEN = 32;
  VZ_LPR_WLIST_CUSTOMER_CODE_LEN = 32;

  VZ_LPR_WLIST_MAX_KEY_LEN = 32;
	VZ_LPR_WLIST_MAX_SEARCH_STRING_LEN = 128;

type
  //**黑白名单记录的客户信息*/
  VZ_LPR_WLIST_CUSTOMER = record
  	uCustomerID:LongWord;			//**<客户在数据库的ID，用于修改、删除操作（主键）*/
  	strName:array[0..31] of char;	//**<客户姓名*/
  	strCode:array[0..31] of char;	//**<客户编码*/
  	reserved:array[0..255] of char;
  end;
  LPVZ_LPR_WLIST_CUSTOMER = ^VZ_LPR_WLIST_CUSTOMER;

  //**客户和其车辆中的一辆合成的一行数据*/
  VZ_LPR_WLIST_ROW = record
  	pCustomer:LPVZ_LPR_WLIST_CUSTOMER;			//**<客户*/
  	pVehicle:LPVZ_LPR_WLIST_VEHICLE;				//**<车辆，可以为空*/
  end;
  LPVZ_LPR_WLIST_ROW = ^VZ_LPR_WLIST_ROW;

  VZ_LPR_WLIST_IMPORT_RESULT = record
  	ret:Integer;
  	error_code:Integer;
  end;
  LPVZ_LPR_WLIST_IMPORT_RESULT = ^VZ_LPR_WLIST_IMPORT_RESULT;

    //**查找数据条件*/
  VZ_LPR_WLIST_SEARCH_CONSTRAINT = record
  	key:array[0..VZ_LPR_WLIST_MAX_KEY_LEN-1] of AnsiChar;							//**<查找的字段*/
  	search_string:array[0..VZ_LPR_WLIST_MAX_SEARCH_STRING_LEN-1] of AnsiChar;		//**<查找的字符串*/
  end;
  LPVZ_LPR_WLIST_SEARCH_CONSTRAINT = ^VZ_LPR_WLIST_SEARCH_CONSTRAINT;

  VZ_LPR_WLIST_LIMIT_TYPE = (
  	VZ_LPR_WLIST_LIMIT_TYPE_ONE,						//**<查找一条*/
  	VZ_LPR_WLIST_LIMIT_TYPE_ALL,						//**<查找所有*/
  	VZ_LPR_WLIST_LIMIT_TYPE_RANGE						//**<查找一段*/
  );

  VZ_LPR_WLIST_RANGE_LIMIT = record
  	startIndex:Integer;
  	count:Integer;
  end;
  LPVZ_LPR_WLIST_RANGE_LIMIT = ^VZ_LPR_WLIST_RANGE_LIMIT;

  VZ_LPR_MSG_PLATE_INFO = record
    plate : array[0..31] of Char;
    img_path : array[0..128] of Char;
  end;
  LPVZ_LPR_MSG_PLATE_INFO = ^VZ_LPR_MSG_PLATE_INFO;

  VZ_LPR_WLIST_LIMIT = record
  	limitType:VZ_LPR_WLIST_LIMIT_TYPE;				   	//**<查找条数限制*/
  	pRangeLimit:LPVZ_LPR_WLIST_RANGE_LIMIT;				//**<查找哪一段数据，当limitType为VZ_LPR_WLIST_LIMIT_TYPE_RANGE时起作用*/
  end;
  LPVZ_LPR_WLIST_LIMIT = ^VZ_LPR_WLIST_LIMIT;

  VZ_LPR_WLIST_SORT_DIRECTION = (
  	VZ_LPR_WLIST_SORT_DIRECTION_DESC=0,
  	VZ_LPR_WLIST_SORT_DIRECTION_ASC=1
  );

  //**查找数据方式*/
  VZ_LPR_WLIST_SEARCH_TYPE = (
  	VZ_LPR_WLIST_SEARCH_TYPE_LIKE = 0,				//**<包含字符*/
  	VZ_LPR_WLIST_SEARCH_TYPE_EQUAL = 1				//**<完全匹配*/
  );

  type
    //**结果的排列方式*/
  VZ_LPR_WLIST_SORT_TYPE = record
  	key:array[0..VZ_LPR_WLIST_MAX_KEY_LEN-1] of AnsiChar;
  	direction:VZ_LPR_WLIST_SORT_DIRECTION;
  end;
  LPVZ_LPR_WLIST_SORT_TYPE = ^VZ_LPR_WLIST_SORT_TYPE;

  type
  VZ_LPR_WLIST_SEARCH_WHERE = record
     searchType : LongWord;
  	//searchType : VZ_LPR_WLIST_SEARCH_TYPE;					//**<查找的方式，如果是完全匹配，每个条件之间为与，是包含时，每个条件之间为或*/
  	searchConstraintCount : LongWord;	            	//**<查找条件个数，为0表示没有搜索条件*/
  	pSearchConstraints : LPVZ_LPR_WLIST_SEARCH_CONSTRAINT;			//**<查找条件数组指针*/
  end;
  LPVZ_LPR_WLIST_SEARCH_WHERE = ^VZ_LPR_WLIST_SEARCH_WHERE;

  VZ_LPR_WLIST_LOAD_CONDITIONS = record
  	pSearchWhere:LPVZ_LPR_WLIST_SEARCH_WHERE;				//**<查找条件*/
  	pLimit:LPVZ_LPR_WLIST_LIMIT;			          		//**<查找条数限制*/
  	pSortType:LPVZ_LPR_WLIST_SORT_TYPE;					    //**<结果的排序方式，为空表示按默认排序*/
  end;
  LPVZ_LPR_WLIST_LOAD_CONDITIONS = ^VZ_LPR_WLIST_LOAD_CONDITIONS;

  VZLPRC_WLIST_CB_TYPE = (
  	VZLPRC_WLIST_CB_TYPE_ROW = 0,
  	VZLPRC_WLIST_CB_TYPE_CUSTOMER,
  	VZLPRC_WLIST_CB_TYPE_VEHICLE
  );

  const
  ENCRYPT_NAME_LENGTH = 32;
  type
  //**加密类型*/
  VZ_EMS_INFO = record
    uId : LongWord;
    sName : array[0..ENCRYPT_NAME_LENGTH-1] of Char;
  end;

  const
  ENCRYPT_LENGTH = 16;
  SIGNATURE_LENGTH = 32;
  type
  //**当前识别结果加密类型**/
  VZ_LPRC_ACTIVE_ENCRYPT = record
  	uActiveID:LongWord;
    oEncrypt:array[0..ENCRYPT_LENGTH-1] of VZ_EMS_INFO ;
    uSize : LongWord;
    signature : array[0..SIGNATURE_LENGTH-1] of Char;
  end;
  LPVZ_LPRC_ACTIVE_ENCRYPT = ^VZ_LPRC_ACTIVE_ENCRYPT;

type VZLPRC_PLATE_INFO_CALLBACK = function(mhandle: VzLPRClientHandle;
    pUserData: Pointer;
    pResult: PTH_PlateResult;
    uNumPlates: Word;
    eResultType: VZ_LPRC_RESULT_TYPE;
    var pImgFull: VZ_LPRC_IMAGE_INFO;
    var pImgPlateClip: VZ_LPRC_IMAGE_INFO): Integer; stdcall;

type VZLPRC_PLATE_QUERY_CALLBACK  = function(mhandle: VzLPRClientHandle;
    pUserData: Pointer;
    pResult: PTH_PlateResult;
    uNumPlates: Word;
    eResultType: VZ_LPRC_RESULT_TYPE;
    var pImgFull: VZ_LPRC_IMAGE_INFO;
    var pImgPlateClip: VZ_LPRC_IMAGE_INFO): Integer; stdcall;



type VZLPRC_FIND_DEVICE_CALLBACK = function(pStrDevName: PChar;
     pStrIPAddr: PChar;
     usPort1: Word;
     usPort2: Word;
     SL : LongWord;
     SH : LongWord;
     pUserData: Pointer): Integer; stdcall;

type VZDEV_SERIAL_RECV_DATA_CALLBACK = function(nSerialHandle:Integer;
  pRecvData:Pointer;
  uRecvSize:Integer;
  pUserData:Pointer): Integer; stdcall;


  VZLPRC_WLIST_QUERY_CALLBACK = procedure(aType:VZLPRC_WLIST_CB_TYPE; const pLP:LPVZ_LPR_WLIST_VEHICLE; const pCustomer:LPVZ_LPR_WLIST_CUSTOMER; pUserData:Pointer); stdcall;

var
  BusDll: THandle;
  VzLPRClient_Setup: function(): Integer; stdcall;
  VzLPRClient_Cleanup: procedure(); stdcall;
  VZLPRClient_StartFindDevice: function(func: VZLPRC_FIND_DEVICE_CALLBACK; pUserData: Pointer ): Integer; stdcall;
  VZLPRClient_StopFindDevice: function(): Integer; stdcall;
  VzLPRClient_Open: function(pStrIP: PChar; wPort: Word; pStrUserName: PChar; pStrPassword: PChar): VzLPRClientHandle; stdcall;
  VzLPRClient_Close: function(mhandle: VzLPRClientHandle): Integer; stdcall;
  VzLPRClient_StartRealPlay: function(mhandle: VzLPRClientHandle; hWnd: HWND): Integer; stdcall;
  VzLPRClient_StopRealPlay: function(nPlayHandle: Integer): Integer; stdcall;
  VzLPRClient_SetPlateInfoCallBack: function(mhandle: VzLPRClientHandle; mfunc: VZLPRC_PLATE_INFO_CALLBACK; pUserData: Pointer; bEnableImage: Integer): Integer; stdcall;
  VzLPRClient_SetIOOutput: function(mhandle: VzLPRClientHandle; uChnId: Word; nOutput: Integer): Integer; stdcall;
  VzLPRClient_GetSnapShootToJpeg2: function(mhandle: VzLPRClientHandle; pFullPathName: PChar; nQuality: Integer): Integer; stdcall;
  VzLPRClient_ImageSaveToJpeg: function(pImgFull: PVZ_LPRC_IMAGE_INFO; pFullPathName: PChar; nQuality: Integer): Integer; stdcall;
  VzLPRClient_SerialStart: function(mhandle: VzLPRClientHandle; nSerialPort:Integer; mfunc: VZDEV_SERIAL_RECV_DATA_CALLBACK; pUserData:Pointer) : Integer; stdcall;
  VzLPRClient_SerialSend: function(nSerialHandle:Integer; pData:Pointer; uSizeData:Integer) : Integer; stdcall;
  VzLPRClient_SerialStop: function(nSerialHandle:Integer) :  Integer; stdcall;
  VzLPRClient_GetSerialNumber:function(mhandle: VzLPRClientHandle; pSerialNum:PVZ_DEV_SERIAL_NUM) : Integer; stdcall;
  VzLPRClient_ReadUserData:function(mhandle: VzLPRClientHandle; pBuffer:Pointer; uSizeBuf:Integer ) : Integer; stdcall;
  VzLPRClient_WriteUserData:function(mhandle: VzLPRClientHandle; pBuffer:Pointer; uSizeBuf:Integer ) : Integer; stdcall;
  VzLPRClient_SetOfflineCheck:function(mhandle: VzLPRClientHandle) : Integer; stdcall;
  VzLPRClient_CancelOfflineCheck:function(mhandle: VzLPRClientHandle) : Integer; stdcall;
  VzLPRClient_GetSupportedProvinces:function(mhandle: VzLPRClientHandle; pProvInfo:PVZ_LPRC_PROVINCE_INFO) : Integer; stdcall;
  VzLPRClient_PresetProvinceIndex:function(mhandle: VzLPRClientHandle; nIndex:Integer) : Integer; stdcall;
  VzLPRClient_WhiteListImportRows:function(mhandle:Integer; rowcount:LongWord; pRowDatas:LPVZ_LPR_WLIST_ROW; pResults:LPVZ_LPR_WLIST_IMPORT_RESULT):Integer; stdcall;
  VzLPRClient_WhiteListLoadVehicle:function(mhandle:Integer; pLoadCondition:LPVZ_LPR_WLIST_LOAD_CONDITIONS):Integer; stdcall;
  VzLPRClient_WhiteListSetQueryCallBack:function(mhandle:Integer; func:VZLPRC_WLIST_QUERY_CALLBACK; pUserData:Pointer):Integer; stdcall;
  VzLPRClient_WhiteListUpdateVehicleByID:function(mhandle:Integer; pWlistVehicle:LPVZ_LPR_WLIST_VEHICLE):Integer; stdcall;
  VzLPRClient_WhiteListDeleteVehicle:function(mhandle:Integer; pPlateID:PChar):Integer; stdcall;
  VzLPRClient_WhiteListClearCustomersAndVehicles:function(mhandle:Integer):Integer; stdcall;
  VzLPRClient_ForceTrigger:function(mhandle:Integer):Integer; stdcall;
  VzLPRClient_SaveRealData:function(mhandle:Integer; pFullPathName: PChar):Integer; stdcall;
  VzLPRClient_StopSaveRealData:function(mhandle:Integer):Integer; stdcall;
  VzLPRClient_IsConnected:function(mhandle:Integer; var pStatus :Byte):Integer; stdcall;
  VzLPRClient_GetGPIOValue:function(mhandle:Integer; gpioIn:Integer; var value:Integer):Integer; stdcall;
  VzLPRClient_GetOsdParam:function(mhandle:Integer; pOSDParam:PVZ_LPRC_OSD_PARAM):Integer; stdcall;
  VzLPRClient_SetOsdParam:function(mhandle:Integer; pOSDParam:PVZ_LPRC_OSD_PARAM):Integer; stdcall;
  VzLPRClient_SetDateTime:function(mhandle:Integer; pDTinfo:PVZ_DATE_TIME_INFO):Integer; stdcall;
  VzLPRClient_GetDateTime:function(mhandle:Integer; pDTinfo:PVZ_DATE_TIME_INFO):Integer; stdcall;
  VzLPRClient_GetPlateRecType:function(mhandle:Integer; var pBitsRecType:Integer):Integer; stdcall;
  VzLPRClient_SetPlateRecType:function(mhandle:Integer; uBitsRecType:Integer):Integer; stdcall;
  VzLPRClient_GetDrawMode:function(mhandle:Integer;  pDrawMode:PVZ_LPRC_DRAWMODE):Integer; stdcall;
  VzLPRClient_SetDrawMode:function(mhandle:Integer;  pDrawMode:PVZ_LPRC_DRAWMODE):Integer; stdcall;
  VzLPRClient_GetPlateTrigType:function(mhandle:Integer;  var pBitsTrigType:Integer):Integer; stdcall;
  VzLPRClient_SetPlateTrigType:function(mhandle:Integer;  pBitsTrigType:Integer):Integer; stdcall;
  VzLPRClient_GetVideoFrameSizeIndex:function(mhandle:Integer; var sizeval:Integer):Integer; stdcall;
  VzLPRClient_SetVideoFrameSizeIndex:function(mhandle:Integer; sizeval:Integer):Integer; stdcall;
  VzLPRClient_GetVideoFrameRate:function(mhandle:Integer; var Rateval:Integer):Integer; stdcall;
  VzLPRClient_SetVideoFrameRate:function(mhandle:Integer; Rateval:Integer):Integer; stdcall;
  VzLPRClient_GetVideoEncodeType:function(mhandle:Integer; var pEncType:Integer):Integer; stdcall;
  VzLPRClient_SetVideoEncodeType:function(mhandle:Integer; pEncType:Integer):Integer; stdcall;
  VzLPRClient_GetVideoCompressMode:function(mhandle:Integer; var modeval:Integer):Integer; stdcall;
  VzLPRClient_SetVideoCompressMode:function(mhandle:Integer; modeval:Integer):Integer; stdcall;
  VzLPRClient_GetVideoCBR:function(mhandle:Integer; var rateval:Integer; var ratelist:Integer):Integer; stdcall;
  VzLPRClient_SetVideoCBR:function(mhandle:Integer; rateval:Integer):Integer; stdcall;
  VzLPRClient_GetVideoVBR:function(mhandle:Integer; var levelval:Integer):Integer; stdcall;
  VzLPRClient_SetVideoVBR:function(mhandle:Integer; levelval:Integer):Integer; stdcall;
  VzLPRClient_GetVideoPara:function(mhandle:Integer; var brt:Integer; var cst:Integer; var sat:Integer; var hue:Integer):Integer; stdcall;
  VzLPRClient_SetVideoPara:function(mhandle:Integer; brt:Integer; cst:Integer; sat:Integer; hue:Integer):Integer; stdcall;
  VzLPRClient_GetFrequency:function(mhandle:Integer; var frequency:Integer):Integer; stdcall;
  VzLPRClient_SetFrequency:function(mhandle:Integer; frequency:Integer):Integer; stdcall;
  VzLPRClient_GetShutter:function(mhandle:Integer; var shutter:Integer):Integer; stdcall;
  VzLPRClient_SetShutter:function(mhandle:Integer; shutter:Integer):Integer; stdcall;
  VzLPRClient_GetFlip:function(mhandle:Integer; var flip:Integer):Integer; stdcall;
  VzLPRClient_SetFlip:function(mhandle:Integer; flip:Integer):Integer; stdcall;
  VzLPRClient_GetTriggerDelay:function(mhandle:Integer; var nDelay:Integer):Integer; stdcall;
  VzLPRClient_SetTriggerDelay:function(mhandle:Integer; nDelay:Integer):Integer; stdcall;
  VzLPRClient_GetWLCheckMethod:function(mhandle:Integer; var nType:Integer):Integer; stdcall;
  VzLPRClient_SetWLCheckMethod:function(mhandle:Integer; nType:Integer):Integer; stdcall;
  VzLPRClient_GetWLFuzzy:function(mhandle:Integer; var nFuzzyType:Integer; var nFuzzyLen:Integer; var bFuzzyCC:Boolean):Integer; stdcall;
  VzLPRClient_SetWLFuzzy:function(mhandle:Integer; nFuzzyType:Integer; nFuzzyLen:Integer; bFuzzyCC:Boolean):Integer; stdcall;
  VzLPRClient_GetSerialParameter:function(mhandle:Integer; nSerialPort:Integer; pParameter:PVZ_SERIAL_PARAMETER):Integer; stdcall;
  VzLPRClient_SetSerialParameter:function(mhandle:Integer; nSerialPort:Integer; pParameter:PVZ_SERIAL_PARAMETER):Integer; stdcall;
  VzLPRClient_GetOutputConfig:function(mhandle:Integer;  pOutputConfigInfo:PVZ_OutputConfigInfo):Integer; stdcall;
  VzLPRClient_SetOutputConfig:function(mhandle:Integer;  pOutputConfigInfo:PVZ_OutputConfigInfo):Integer; stdcall;
  VzLPRClient_QueryCountByTimeAndPlate:function(mhandle:Integer; pStartTime:PChar; pEndTime:PChar; keyword:PChar):Integer; stdcall;
  VzLPRClient_QueryPageRecordByTimeAndPlate:function(mhandle:Integer; pStartTime:PChar; pEndTime:PChar; keyword:PChar; nStart:Integer; nEnd:Integer):Integer; stdcall;
  VzLPRClient_SetQueryPlateCallBack:function(mhandle:Integer; mfunc: VZLPRC_PLATE_QUERY_CALLBACK; pUserData: Pointer):Integer; stdcall;
  VzLPRClient_UpdateNetworkParam:function(SH:LongWord; SL:LongWord; strNewIP:PChar; strGateway:PChar; strNetmask:PChar):Integer; stdcall;
  VzLPRClient_LoadImageById:function(mhandle:Integer; id : Integer; pData:PByte; pSize:PInteger):Integer; stdcall;
  VzLPRClient_GetEMS:function(mhandle:Integer; pData:LPVZ_LPRC_ACTIVE_ENCRYPT):Integer; stdcall;
  VzLPRClient_StartLogin:function(mhandle:Integer; pData:LPVZ_LPRC_ACTIVE_ENCRYPT):Integer; stdcall;
  VzLPRClient_LoginAuth:function(mhandle:Integer; key:PChar):Integer; stdcall;
  VzLPRClient_AesCtrDecrypt:function(hertextb:PChar; nTextBits:Integer; password:PChar; decryptText:PChar; decryptLen:Integer):Integer; stdcall;
  VzLPRClient_PlayVoice:function(mhandle:VzLPRClientHandle;  voice:PChar; interval,  volume,  male:integer):Integer; stdcall;
function LoadBusModule(): Boolean;
procedure UnLoadBusModule();

implementation

function LoadBusModule(): Boolean;
begin
  BusDll := LoadLibrary('VzLPRSDK.dll');
  Result := BusDll <> 0;
  if Result then
  begin
    VzLPRClient_Setup := GetProcAddress(BusDll, 'VzLPRClient_Setup');
    VzLPRClient_Cleanup := GetProcAddress(BusDll, 'VzLPRClient_Cleanup');
    VZLPRClient_StartFindDevice := GetProcAddress(BusDll, 'VZLPRClient_StartFindDevice');
    VZLPRClient_StopFindDevice := GetProcAddress(BusDll, 'VZLPRClient_StopFindDevice');
    VzLPRClient_Open := GetProcAddress(BusDll, 'VzLPRClient_Open');
    VzLPRClient_Close := GetProcAddress(BusDll, 'VzLPRClient_Close');
    VzLPRClient_StartRealPlay := GetProcAddress(BusDll, 'VzLPRClient_StartRealPlay');
    VzLPRClient_StopRealPlay := GetProcAddress(BusDll, 'VzLPRClient_StopRealPlay');
    VzLPRClient_SetPlateInfoCallBack := GetProcAddress(BusDll, 'VzLPRClient_SetPlateInfoCallBack');
    VzLPRClient_SetIOOutput := GetProcAddress(BusDll, 'VzLPRClient_SetIOOutput');
    VzLPRClient_GetSnapShootToJpeg2 := GetProcAddress(BusDll, 'VzLPRClient_GetSnapShootToJpeg2');
    VzLPRClient_ImageSaveToJpeg := GetProcAddress(BusDll, 'VzLPRClient_ImageSaveToJpeg');
    VzLPRClient_SerialStart := GetProcAddress(BusDll, 'VzLPRClient_SerialStart');
    VzLPRClient_SerialSend  := GetProcAddress(BusDll, 'VzLPRClient_SerialSend');
    VzLPRClient_SerialStop  := GetProcAddress(BusDll, 'VzLPRClient_SerialStop');
    VzLPRClient_SetSerialParameter := GetProcAddress(BusDll, 'VzLPRClient_SetSerialParameter');
    VzLPRClient_GetSerialNumber := GetProcAddress(BusDll, 'VzLPRClient_GetSerialNumber');
    VzLPRClient_ReadUserData := GetProcAddress(BusDll, 'VzLPRClient_ReadUserData');
    VzLPRClient_WriteUserData := GetProcAddress(BusDll, 'VzLPRClient_WriteUserData');
    VzLPRClient_SetOfflineCheck := GetProcAddress(BusDll, 'VzLPRClient_SetOfflineCheck');
    VzLPRClient_CancelOfflineCheck := GetProcAddress(BusDll, 'VzLPRClient_CancelOfflineCheck');
    VzLPRClient_GetSupportedProvinces := GetProcAddress(BusDll, 'VzLPRClient_GetSupportedProvinces');
    VzLPRClient_PresetProvinceIndex := GetProcAddress(BusDll, 'VzLPRClient_PresetProvinceIndex');
    VzLPRClient_WhiteListImportRows := GetProcAddress(BusDll, 'VzLPRClient_WhiteListImportRows');
    VzLPRClient_WhiteListLoadVehicle := GetProcAddress(BusDll, 'VzLPRClient_WhiteListLoadVehicle');
    VzLPRClient_WhiteListSetQueryCallBack := GetProcAddress(BusDll, 'VzLPRClient_WhiteListSetQueryCallBack');
    VzLPRClient_WhiteListUpdateVehicleByID := GetProcAddress(BusDll, 'VzLPRClient_WhiteListUpdateVehicleByID');
    VzLPRClient_WhiteListDeleteVehicle := GetProcAddress(BusDll, 'VzLPRClient_WhiteListDeleteVehicle');
    VzLPRClient_WhiteListClearCustomersAndVehicles := GetProcAddress(BusDll, 'VzLPRClient_WhiteListClearCustomersAndVehicles');
    VzLPRClient_ForceTrigger   := GetProcAddress(BusDll, 'VzLPRClient_ForceTrigger');
    VzLPRClient_SaveRealData   := GetProcAddress(BusDll, 'VzLPRClient_SaveRealData');
    VzLPRClient_StopSaveRealData   := GetProcAddress(BusDll, 'VzLPRClient_StopSaveRealData');
    VzLPRClient_IsConnected   := GetProcAddress(BusDll, 'VzLPRClient_IsConnected');
    VzLPRClient_GetGPIOValue   := GetProcAddress(BusDll, 'VzLPRClient_GetGPIOValue');
    VzLPRClient_GetOsdParam   := GetProcAddress(BusDll, 'VzLPRClient_GetOsdParam');
    VzLPRClient_SetOsdParam   := GetProcAddress(BusDll, 'VzLPRClient_SetOsdParam');
    VzLPRClient_SetDateTime   := GetProcAddress(BusDll, 'VzLPRClient_SetDateTime');
    VzLPRClient_GetDateTime   := GetProcAddress(BusDll, 'VzLPRClient_GetDateTime');
    VzLPRClient_GetPlateRecType :=  GetProcAddress(BusDll, 'VzLPRClient_GetPlateRecType');
    VzLPRClient_SetPlateRecType :=  GetProcAddress(BusDll, 'VzLPRClient_SetPlateRecType');
    VzLPRClient_GetDrawMode  :=  GetProcAddress(BusDll, 'VzLPRClient_GetDrawMode');
    VzLPRClient_SetDrawMode  :=  GetProcAddress(BusDll, 'VzLPRClient_SetDrawMode');
    VzLPRClient_GetPlateTrigType :=  GetProcAddress(BusDll, 'VzLPRClient_GetPlateTrigType');
    VzLPRClient_SetPlateTrigType :=  GetProcAddress(BusDll, 'VzLPRClient_SetPlateTrigType');
    VzLPRClient_GetVideoFrameSizeIndex := GetProcAddress(BusDll, 'VzLPRClient_GetVideoFrameSizeIndex');
    VzLPRClient_SetVideoFrameSizeIndex := GetProcAddress(BusDll, 'VzLPRClient_SetVideoFrameSizeIndex');
    VzLPRClient_GetVideoFrameRate := GetProcAddress(BusDll, 'VzLPRClient_GetVideoFrameRate');
    VzLPRClient_SetVideoFrameRate := GetProcAddress(BusDll, 'VzLPRClient_SetVideoFrameRate');
    VzLPRClient_GetVideoEncodeType := GetProcAddress(BusDll, 'VzLPRClient_GetVideoEncodeType');
    VzLPRClient_SetVideoEncodeType := GetProcAddress(BusDll, 'VzLPRClient_SetVideoEncodeType');
    VzLPRClient_GetVideoCompressMode := GetProcAddress(BusDll, 'VzLPRClient_GetVideoCompressMode');
    VzLPRClient_SetVideoCompressMode := GetProcAddress(BusDll, 'VzLPRClient_SetVideoCompressMode');
    VzLPRClient_GetVideoCBR := GetProcAddress(BusDll, 'VzLPRClient_GetVideoCBR');
    VzLPRClient_SetVideoCBR := GetProcAddress(BusDll, 'VzLPRClient_SetVideoCBR');
    VzLPRClient_GetVideoVBR := GetProcAddress(BusDll, 'VzLPRClient_GetVideoVBR');
    VzLPRClient_SetVideoVBR := GetProcAddress(BusDll, 'VzLPRClient_SetVideoVBR');
    VzLPRClient_GetVideoPara := GetProcAddress(BusDll, 'VzLPRClient_GetVideoPara');
    VzLPRClient_SetVideoPara := GetProcAddress(BusDll, 'VzLPRClient_SetVideoPara');
    VzLPRClient_GetFrequency := GetProcAddress(BusDll, 'VzLPRClient_GetFrequency');
    VzLPRClient_SetFrequency := GetProcAddress(BusDll, 'VzLPRClient_SetFrequency');
    VzLPRClient_GetShutter := GetProcAddress(BusDll, 'VzLPRClient_GetShutter');
    VzLPRClient_SetShutter := GetProcAddress(BusDll, 'VzLPRClient_SetShutter');
    VzLPRClient_GetFlip := GetProcAddress(BusDll, 'VzLPRClient_GetFlip');
    VzLPRClient_SetFlip := GetProcAddress(BusDll, 'VzLPRClient_SetFlip');
    VzLPRClient_GetTriggerDelay := GetProcAddress(BusDll, 'VzLPRClient_GetTriggerDelay');
    VzLPRClient_SetTriggerDelay := GetProcAddress(BusDll, 'VzLPRClient_SetTriggerDelay');
    VzLPRClient_GetWLCheckMethod := GetProcAddress(BusDll, 'VzLPRClient_GetWLCheckMethod');
    VzLPRClient_SetWLCheckMethod := GetProcAddress(BusDll, 'VzLPRClient_SetWLCheckMethod');
    VzLPRClient_GetWLFuzzy := GetProcAddress(BusDll, 'VzLPRClient_GetWLFuzzy');
    VzLPRClient_SetWLFuzzy := GetProcAddress(BusDll, 'VzLPRClient_SetWLFuzzy');
    VzLPRClient_SetSerialParameter := GetProcAddress(BusDll, 'VzLPRClient_SetSerialParameter');
    VzLPRClient_GetSerialParameter := GetProcAddress(BusDll, 'VzLPRClient_GetSerialParameter');
    VzLPRClient_GetOutputConfig := GetProcAddress(BusDll, 'VzLPRClient_GetOutputConfig');
    VzLPRClient_SetOutputConfig := GetProcAddress(BusDll, 'VzLPRClient_SetOutputConfig');
    VzLPRClient_QueryCountByTimeAndPlate := GetProcAddress(BusDll, 'VzLPRClient_QueryCountByTimeAndPlate');
    VzLPRClient_QueryPageRecordByTimeAndPlate := GetProcAddress(BusDll, 'VzLPRClient_QueryPageRecordByTimeAndPlate');
    VzLPRClient_SetQueryPlateCallBack := GetProcAddress(BusDll, 'VzLPRClient_SetQueryPlateCallBack');
    VzLPRClient_UpdateNetworkParam := GetProcAddress(BusDll, 'VzLPRClient_UpdateNetworkParam');
    VzLPRClient_LoadImageById := GetProcAddress(BusDll, 'VzLPRClient_LoadImageById');
    VzLPRClient_GetEMS := GetProcAddress(BusDll, 'VzLPRClient_GetEMS');
    VzLPRClient_StartLogin := GetProcAddress(BusDll, 'VzLPRClient_StartLogin');
    VzLPRClient_LoginAuth := GetProcAddress(BusDll, 'VzLPRClient_LoginAuth');
    VzLPRClient_AesCtrDecrypt := GetProcAddress(BusDll, 'VzLPRClient_AesCtrDecrypt');
    VzLPRClient_PlayVoice := GetProcAddress(BusDll, 'VzLPRClient_PlayVoice');
    //VzLPRClient_Cleanup        := GetProcAddress(BusDll, 'VzLPRClient_Cleanup');
  end;
end;

procedure UnLoadBusModule();
begin
  if BusDll <> 0 then
    FreeLibrary(BusDll);
end;

end.

