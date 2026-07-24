// VzLPRSDKDemoDlg.cpp : 实现文件
//

#include "stdafx.h"
#include "VzLPRSDKDemo.h"
#include "VzLPRSDKDemoDlg.h"
#include "DlgConfigRules.h"
#include "DlgWhiteList.h"
#include "DlgConfigExt.h"
#include "DlgOSDSet.h"
#include "DlgPlateQuery.h"
#include "DlgMaintain.h"
#include "DlgNetwork.h"
#include "DlgVoice.h"
#include "DlgModifyIP.h"
#include "DlgEncrypt.h"
#include "DlgGroupCfg.h"
#include "DlgVideoCfg.h"
#include "DlgStore.h"
#include "DlgDeviceInfo.h"
#include <algorithm>
#include "OutAndIn.h"
#include "VzDlgSerialParam.h"
#include "VzString.h"
#include <time.h>
#include "DlgPlayVoice.h"
#include "DlgActiveStatus.h"
#include "DlgSerialData.h"
#include "DlgTcpTran.h"
#include "DlgUserOSD.h"
#include <sys\types.h>
#include "DlgRVideoCfg.h"
#include "DlgTalkClient.h"
#include "DlgPdnsLoginInfo.h"
#include "DlgStreamCloud.h"
#include "DlgDevMate.h"
#include "Base64.h"
#include "json/json.h"
#include "mmsystem.h"


#define MAX_BRAND_COUNT 121
static TCHAR *s_car_make_lut[MAX_BRAND_COUNT] = {
	_T("比亚迪"),
	_T("一汽"),
	_T("宝马迷你"),
	_T("荣威"),
	_T("吉普"),
	_T("丰田"),
	_T("铃木"),
	_T("长安"),
	_T("雪铁龙"),
	_T("雪佛兰"),
	_T("长城"),
	_T("凯迪拉克"),
	_T("本田"),
	_T("五菱"),
	_T("雷克萨斯"),
	_T("福特"),
	_T("东风风神"),
	_T("沃尔沃"),
	_T("大众"),
	_T("福田"),
	_T("北汽"),
	_T("吉利"),
	_T("奇瑞"),
	_T("中华"),
	_T("斯柯达"),
	_T("斯巴鲁"),
	_T("广汽传祺"),
	_T("三菱"),
	_T("海马"),
	_T("现代"),
	_T("名爵"),
	_T("长安商用"),
	_T("奔驰"),
	_T("宝骏"),
	_T("路虎"),
	_T("别克"),
	_T("哈弗"),
	_T("起亚"),
	_T("尼桑"),
	_T("江铃"),
	_T("马自达"),
	_T("江淮"),
	_T("奥迪"),
	_T("东南"),
	_T("标致"),
	_T("金杯"),
	_T("宝马"),
	_T("保时捷"),
	_T("风驰"),
	_T("双环"),
	_T("中兴"),
	_T("菲亚特"),
	_T("大运"),
	_T("华泰"),
	_T("北京"),
	_T("猎豹汽车"),
	_T("北汽制造"),
	_T("东风风行"),
	_T("福田时代"),
	_T("凯马"),
	_T("中顺"),
	_T("宾利"),
	_T("昌河"),
	_T("克莱斯勒"),
	_T("上汽大通"),
	_T("纳智捷"),
	_T("华普"),
	_T("广汽日野"),
	_T("北汽绅宝"),
	_T("三一重工"),
	_T("道奇"),
	_T("讴歌"),
	_T("开瑞"),
	_T("众泰"),
	_T("吉利汽车"),
	_T("雷诺"),
	_T("东风风度"),
	_T("陕汽"),
	_T("中国重汽"),
	_T("安凯"),
	_T("欧宝"),
	_T("捷豹"),
	_T("陆风"),
	_T("五十铃"),
	_T("黄海"),
	_T("依维柯"),
	_T("解放"),
	_T("华菱"),
	_T("唐骏"),
	_T("中通"),
	_T("福田欧曼"),
	_T("金龙"),
	_T("莲花汽车"),
	_T("启辰"),
	_T("恒通"),
	_T("理念"),
	_T("北奔"),
	_T("东风多利卡"),
	_T("北方"),
	_T("跃进"),
	_T("力帆"),
	_T("北汽威旺"),
	_T("福田奥铃"),
	_T("飞碟"),
	_T("东风柳汽"),
	_T("哈飞"),
	_T("金旅"),
	_T("宇通"),
	_T("英菲尼迪"),
	_T("林肯"),
	_T("凯迪拉克ATS"),
	_T("东风风光"),
	_T("玛莎拉蒂"),
	_T("特斯拉"),
	_T("法拉利"),
	_T("兰博基尼"),
	_T("劳斯莱斯"),
	_T("福迪"),
	_T("东风小康"),
	_T("吉奥"),
	_T("客车")
};


#define MAX_STYLE_COUNT 13
static TCHAR *s_car_style_name[MAX_BRAND_COUNT] = {
	_T("轿车"),
	_T("SUV"),
	_T("MPV"),
	_T("面包车"),
	_T("Jeep"),
	_T("皮卡"),
	_T("小型客车"),
	_T("中型客车"),
	_T("大型客车"),
	_T("小型货车"),
	_T("中型货车"),
	_T("重型货车"),
	_T("跑车")
};

#ifdef _DEBUG
#define new DEBUG_NEW
//#define SAVE_LP2FILE	//定义该宏，输出测试所需的车牌识别结果
#endif

// #define TEST_OUTPUT_PLATE_TIME

enum
{
	MSG_DEV_FOUND = 1,
};

enum
{
	TIMER_INTERFACE     = 1000,
	TIMER_RECORD_ID,
	TIMER_CHECK_OFFLINE,
};

typedef struct tagDEVICE_INFO
{
	char szIPAddr[16];
	WORD usPort1;
	WORD usPort2;
	unsigned int SL;
	unsigned int SH;
	char netmask[16];
	char gateway[16];
}DEVICE_INFO;


const int TIMER_RECORD_VALUE	= 10*60*1000; // 定时器10分钟

void OutputWin::SetClientHandle(int hClient)
{
	m_hClient = hClient;

	if(hClient)
	{
		m_bEnableShow = true;
		m_struInterV.SetInUse(true);
	}
	else
	{
		m_bEnableFrame = false;
		m_bEnableShow = false;
		m_struInterV.SetInUse(false);
	}
}

int OutputWin::GetClientHandle()
{
	return(m_hClient);
}

void OutputWin::ShowString()
{
	//只有稳定图像才需要取消
	if(m_bEnableFrame == false)
	{
		if(m_nCDNoString >= 0)
			m_nCDNoString--;
	}

	if(m_nCDNoString < 0)
		m_strResult[0] = 0;

	pEditResult->SetWindowText(m_strResult);
}

void OutputWin::UpdateString(const char *pStr)
{
	m_nCDNoString = 4;

	strcpy_s(m_strResult, MAX_LEN_STR, pStr);
}

void OutputWin::SetFrame(const unsigned char *pFrame, unsigned uWidth, unsigned uHeight, unsigned uPitch)
{
	EnterCriticalSection(&m_csFrame);

	m_bufFrame.SetFrame(pFrame, uWidth, uHeight, uPitch);
	//允许显示静态帧
	m_bEnableFrame = true;
	m_struInterV.SetInUse(true);
	LeaveCriticalSection(&m_csFrame);

	m_struInterV.Invalidate();
}

void OutputWin::AddPatch(const unsigned char *pPatch, unsigned uWidth, unsigned uHeight, unsigned uPitch,
						 unsigned uX, unsigned uY)
{
	EnterCriticalSection(&m_csFrame);

	m_bufFrame.AddPatch(pPatch, uWidth, uHeight, uPitch, uX, uY);
	//允许显示静态帧
	LeaveCriticalSection(&m_csFrame);
}

unsigned char *OutputWin::GetFrame(unsigned &uWidth, unsigned &uHeight, unsigned &uPitch)
{
	unsigned char *pRT = NULL;
	EnterCriticalSection(&m_csFrame);
	if(m_bufFrame.m_pBuffer)
	{
		pRT = m_bufFrame.m_pBuffer;
		uWidth = m_bufFrame.m_uWidth;
		uHeight = m_bufFrame.m_uHeight;
		uPitch = m_bufFrame.m_uWidth*3;
	}	
	LeaveCriticalSection(&m_csFrame);

	return(pRT);
}

void OutputWin::ShowFrame()
{
	EnterCriticalSection(&m_csFrame);
	do
	{
		if(m_bEnableFrame == false)
			break;

		if(iGetDraw() == false)
			break;

		m_pDraw->DrawRGB24(m_bufFrame.m_pBuffer, m_bufFrame.m_uWidth, m_bufFrame.m_uHeight);

	}
	while(0);

	LeaveCriticalSection(&m_csFrame);
}

bool OutputWin::iGetDraw()
{
	if(m_pDraw == NULL)
	{
		m_pDraw = new GDIDraw();
		if(m_pDraw->InitHWND(m_struInterV.GetSafeHwnd()) == false)
		{
			delete m_pDraw;
			m_pDraw = NULL;
			return(false);
		}
	}

	return(true);
}

HWND OutputWin::GetHWnd()
{
	return(m_struInterV.GetSafeHwnd());
}

int WriteFileB(const char *filepath, unsigned char *pData, unsigned int len)
{
	int num = 0;

	FILE *fp = NULL;
	fopen_s(&fp, filepath, "wb+");

	if( fp == NULL )
	{
		return false;
	}

	fseek(fp, 0, SEEK_END);
	num = fwrite( pData, sizeof(char), len, fp );
	fclose( fp );

	return num > 0;
}

// #ifdef TEST_OUTPUT_PLATE_TIME
bool WriteLogFile(const char *filepath, LPCSTR strText)
{
	int num = 0;

	FILE *fp = NULL;
	fp = fopen(filepath, "a+");

	if( fp == NULL )
	{
		return false;
	}

	num = fwrite( strText, sizeof(char), strlen(strText), fp );
	fclose( fp );

	return num > 0;
}
// #endif


// CVzLPRSDKDemoDlg 对话框
void __stdcall OnCommonNotify(VzLPRClientHandle handle, void *pUserData, 
							  VZ_LPRC_COMMON_NOTIFY eNotify, const char *pStrDetail)
{
	CVzLPRSDKDemoDlg *pInstance = (CVzLPRSDKDemoDlg *)pUserData;
	pInstance->OnCommonNotify0(handle, eNotify, pStrDetail);
}

int __stdcall OnPlateInfo(VzLPRClientHandle handle, void *pUserData,
						  const TH_PlateResult *pResult, unsigned uNumPlates,
						  VZ_LPRC_RESULT_TYPE eResultType,
						  const VZ_LPRC_IMAGE_INFO *pImgFull,
						  const VZ_LPRC_IMAGE_INFO *pImgPlateClip)
{
	CVzLPRSDKDemoDlg *pInstance = (CVzLPRSDKDemoDlg *)pUserData;
	if ( strlen(pResult->license) == 0 )
	{
		int mm = 0;
	}
	pInstance->OnPlateInfo0(handle, pResult, uNumPlates, eResultType, pImgFull, pImgPlateClip);

	return(0);
}

void __stdcall OnFaceResultInfo(VzLPRClientHandle handle, TH_FaceResult* face_result, void* pUserData)
{
	if( face_result != NULL )
	{
		WriteFileB("d:\\snap.jpg", face_result->snap_img.img_buf, face_result->snap_img.img_len);
		WriteFileB("d:\\face.jpg", face_result->face_imgs[0].img_buf, face_result->face_imgs[0].img_len);
	}
	int mm = 0;
}


int __stdcall OnDisconnectPlateInfo(VzLPRClientHandle handle, void *pUserData,
									const TH_PlateResult *pResult, unsigned uNumPlates,
									VZ_LPRC_RESULT_TYPE eResultType,
									const VZ_LPRC_IMAGE_INFO *pImgFull,
									const VZ_LPRC_IMAGE_INFO *pImgPlateClip)
{

	CVzLPRSDKDemoDlg *pInstance = (CVzLPRSDKDemoDlg *)pUserData;
	if ( pInstance != NULL )
	{
		char plate_log[MAX_PATH] = {0};
		sprintf_s(plate_log, sizeof(plate_log), "disconnect plate: %s, time:%d-%02d-%02d %02d:%02d:%02d\n", pResult->license, pResult->struBDTime.bdt_year, pResult->struBDTime.bdt_mon, 
			pResult->struBDTime.bdt_mday, pResult->struBDTime.bdt_hour, pResult->struBDTime.bdt_min, pResult->struBDTime.bdt_sec);

		char log_path[MAX_PATH] = {0};
		sprintf_s( log_path, sizeof(log_path), "%s\\disconn_plate.log", pInstance->GetModuleDir() );

		WriteLogFile(log_path, plate_log);
	}

	return(0);
}

int __stdcall OnQueryPlateInfo(VzLPRClientHandle handle, void *pUserData,
							   const TH_PlateResult *pResult, unsigned uNumPlates,
							   VZ_LPRC_RESULT_TYPE eResultType,
							   const VZ_LPRC_IMAGE_INFO *pImgFull,
							   const VZ_LPRC_IMAGE_INFO *pImgPlateClip)
{
	return 0;
}

int __stdcall OnVideoFrame(VzLPRClientHandle handle, void *pUserData,
						   WORD wVideoID, const VzYUV420P *pFrame)
{
	CVzLPRSDKDemoDlg *pInstance = (CVzLPRSDKDemoDlg *)pUserData;
	pInstance->OnVideoFrame0(handle, wVideoID, pFrame);

	return(0);
}

void CALLBACK iOnViewerMouse(IV_EVENT eEvent, int x, int y, void *pUserData, int nId)
{
	CVzLPRSDKDemoDlg *pInstance = (CVzLPRSDKDemoDlg *)pUserData;
	pInstance->OnViewerMouse0(eEvent, x, y, nId);
}

void __stdcall OnVZLPRC_GPIO_RECV_CALLBACK(int nGPIOId, int nVal, void* pUserData)
{

}

void __stdcall gVZLPRC_REQUEST_TALK_CALLBACK(VzLPRClientHandle handle, int state, const char* error_msg, void* pUserData)
{
	int mm = 0;
}

CString GetEventTypeName(int type) {
	CString type_name;

	if (type == 0x50) {
		type_name = "人滞留";
	}
	else if (type == 0x51) {
		type_name = "车滞留";
	}
	else if (type == 0x53) {
		type_name = "机动车辆通行事件";
	}
	else if (type == 0x54) {
		type_name = "机动车辆折返事件";
	}
	else if (type == 0x70) {
		type_name = "非机动车滞留";
	}
	else if (type == 0x80) {
		type_name = "道闸正常";
	}
	else if (type == 0x81) {
		type_name = "道闸异常";
	}
	else if (type == 0x82) {
		type_name = "道闸回落";
	}
	else if (type == 0x83) {
		type_name = "道闸抬升";
	}
	else {
		type_name = "未知事件";
	}

	return type_name;
}

void __STDCALL OnCommonResult(VzLPRClientHandle handle, int type, const char *pResultInfo, int len, VZ_IMAGE_INFO *imgs, int count, void *pUserData)
{
	CVzLPRSDKDemoDlg *pInstance = (CVzLPRSDKDemoDlg *)pUserData;
	if (pInstance) {
		pInstance->OnRecvCommonResult(handle, type, pResultInfo, len, imgs, count);
	}
}


CVzLPRSDKDemoDlg::CVzLPRSDKDemoDlg(CWnd* pParent /*=NULL*/)
: CDialog(CVzLPRSDKDemoDlg::IDD, pParent)
, m_nIdxWinCurrSelected(0), m_bSaveJPG(false)
, m_pFSaveLP(NULL)
, m_bSetOffline(FALSE)
, m_bChkOfflineData(FALSE)
, m_bChkOutputRealResult(FALSE)
{
	m_hIcon = AfxGetApp()->LoadIcon(IDB_BMP_ZZ_LOGO);
	cloud_setup_ = false;

	m_pBufRGB = NULL;
	m_uSizeBufRGB = 2560 * 1296 * 3;
}

static const char *iGetNotifyString(VZ_LPRC_COMMON_NOTIFY eNotify)
{
	const int StrLenNotifyName = 64;
	static char strNotifyName[StrLenNotifyName];
	if(eNotify == VZ_LPRC_ONLINE)
		strcpy_s(strNotifyName, StrLenNotifyName, "VZ_LPRC_ONLINE");
	else if(eNotify == VZ_LPRC_OFFLINE)
		strcpy_s(strNotifyName, StrLenNotifyName, "VZ_LPRC_OFFLINE");
	else
		sprintf_s(strNotifyName, StrLenNotifyName, "%d", eNotify);

	return(strNotifyName);
}


void CVzLPRSDKDemoDlg::OnCommonNotify0(VzLPRClientHandle handle,
									   VZ_LPRC_COMMON_NOTIFY eNotify, const char *pStrDetail)
{
	char device_ip[32] = {0};
	VzLPRClient_GetDeviceIP(handle, device_ip, sizeof(device_ip));

	char strDetail[MAX_LEN_STR];

	SYSTEMTIME st;
	GetLocalTime(&st);

	char szCurrentTime[128] = {0};
	sprintf_s(szCurrentTime, sizeof(szCurrentTime), "%d-%02d-%02d: %2d:%2d:%2d %d,", st.wYear, st.wMonth, st.wDay, st.wHour,st.wMinute,st.wSecond, st.wMilliseconds);

	if(pStrDetail)
		sprintf_s(strDetail, MAX_LEN_STR, "[0x%08x]Noitfy=%d: %s,ip:%s,time:%s", handle, eNotify, pStrDetail, device_ip, szCurrentTime);
	else
		sprintf_s(strDetail, MAX_LEN_STR, "[0x%08x]Noitfy=%s, ip:%s, time:%s", handle, iGetNotifyString(eNotify), device_ip, szCurrentTime);


	WriteLogFile("connet.log", strDetail);
	WriteLogFile("connet.log", "\n");

	/*if(eNotify == 4)
	{
	sndPlaySound("testring.wav", SND_ASYNC);
	}*/

	iSetCommonInfo(strDetail);
}

static const char *iGetResultTypeName(VZ_LPRC_RESULT_TYPE eResultType)
{
	static const char *pStrTypeName[VZ_LPRC_RESULT_TYPE_NUM]
	= {"实时", "自动触发", "软件触发", "外部触发", "虚拟线圈", "多重触发"};

	return(pStrTypeName[eResultType]);
}

static const char *iGetResultTypeNameByBits(unsigned uBits)
{
	const int LenStr = 256;
	static char strTypes[LenStr];
	strTypes[0] = 0;
	if(uBits == 0)
	{
		strcat_s(strTypes, LenStr, iGetResultTypeName(VZ_LPRC_RESULT_REALTIME));
	}
	if(uBits & TRIGGER_TYPE_AUTO_BIT)
	{
		strcat_s(strTypes, LenStr, iGetResultTypeName(VZ_LPRC_RESULT_STABLE));
	}
	if(uBits & TRIGGER_TYPE_EXTERNAL_BIT)
	{
		strcat_s(strTypes, LenStr, iGetResultTypeName(VZ_LPRC_RESULT_IO_TRIGGER));
		strcat_s(strTypes, LenStr, "|");
	}
	if(uBits & TRIGGER_TYPE_SOFTWARE_BIT)
	{
		strcat_s(strTypes, LenStr, iGetResultTypeName(VZ_LPRC_RESULT_FORCE_TRIGGER));
		strcat_s(strTypes, LenStr, "|");
	}
	if(uBits & TRIGGER_TYPE_VLOOP_BIT)
	{
		strcat_s(strTypes, LenStr, iGetResultTypeName(VZ_LPRC_RESULT_VLOOP_TRIGGER));
		strcat_s(strTypes, LenStr, "|");
	}

	return(strTypes);
};

static const char *iGetDirString(int nDir)
{
	if(nDir == 3)
		return("向上");
	if(nDir == 4)
		return("向下");
	return(" ");
}

bool WriteImgFile(const char *filepath, unsigned char *pData, unsigned int len)
{
	int num = 0;

	FILE *fp = NULL;
	fopen_s(&fp, filepath, "wb+");

	if (fp == NULL)
	{
		return false;
	}

	num = fwrite(pData, sizeof(char), len, fp);
	fclose(fp);

	return num > 0;
}

void CVzLPRSDKDemoDlg::OnPlateInfo0(VzLPRClientHandle handle,
									const TH_PlateResult *pResult, unsigned uNumPlates,
									VZ_LPRC_RESULT_TYPE eResultType,
									const VZ_LPRC_IMAGE_INFO *pImgFull,
									const VZ_LPRC_IMAGE_INFO *pImgPlateClip)
{
	if(handle == NULL || pResult == NULL || uNumPlates == 0)
		return;

#ifdef TEST_OUTPUT_PLATE_TIME
	if( eResultType == VZ_LPRC_RESULT_VLOOP_TRIGGER )
	{
		SYSTEMTIME st;
		GetLocalTime(&st);

		char szTime[256] = {0};
		sprintf(szTime, "demo receive time %02d:%02d:%02d %03d\n",st.wHour,st.wMinute,st.wSecond, st.wMilliseconds);
		WriteLogFile("plateTime.txt", szTime);
	}
#endif

	// 继电器输出
	if( eResultType != VZ_LPRC_RESULT_REALTIME )
	{
		//VzLPRClient_SetIOOutputAuto(handle, 0, 500);
	}

#ifdef TEST_OUTPUT_PLATE_TIME
	if( eResultType == VZ_LPRC_RESULT_VLOOP_TRIGGER )
	{
		SYSTEMTIME st;
		GetLocalTime(&st);

		char szTime[256] = {0};
		sprintf(szTime, "setiooutputauto time %02d:%02d:%02d %03d\n",st.wHour,st.wMinute,st.wSecond, st.wMilliseconds);
		WriteLogFile("plateTime.txt", szTime);
	}
#endif

	DeviceLPR *pDev = iGetLocalDev(handle);
	if(pDev == NULL)
	{
		return;
	}

	char strPlate[MAX_PLATE_LENGTH] = {0};
	char strMsg[MAX_LEN_STR] = {0};
	for(unsigned i=0; i<uNumPlates; i++)
	{
		char strTmp[MAX_LEN_STR];

		//车牌解密
		bool decrypt = (pResult->nIsEncrypt >= 1);


		char *sUserPwd = pDev->GetUserPwd();
		if (decrypt && strlen(sUserPwd) > 0 )
		{
			char strDecodPlate[MAX_PLATE_LENGTH] = {0} ;

			int nRet = VzLPRClient_AesCtrDecrypt(pResult[i].license, 16, sUserPwd, strDecodPlate, 16);
			if (nRet == 0)
			{
				bool decode_success = false;
				for (int j = 0; j < MAX_PLATE_LENGTH - 1; j++)
				{
					//判断是否解密成功
					if (strDecodPlate[j] == 0)
					{
						decode_success = true;
						break;
					}
				}
				
				if (decode_success) {
					strcpy_s(strPlate,MAX_PLATE_LENGTH -1, strDecodPlate);
				}
				else {
					strcpy(strPlate,"******");
				}

				memset(strTmp, 0, MAX_LEN_STR);
				sprintf_s(strTmp, MAX_LEN_STR, " [%s:%s(宽度=%d)] %s %02d:%02d:%02d 触发时间:%d",
					iGetResultTypeNameByBits(pResult[i].uBitsTrigType),	strPlate,
					pResult[i].rcLocation.right - pResult[i].rcLocation.left, iGetDirString(pResult[i].nDirection),
					//pResult[i].struBDTime.bdt_year, pResult[i].struBDTime.bdt_mon, pResult[i].struBDTime.bdt_mday,
					pResult[i].struBDTime.bdt_hour, pResult[i].struBDTime.bdt_min, pResult[i].struBDTime.bdt_sec, pResult[i].triggerTimeMS);
			}
		}
		else
		{
			memset(strTmp, 0, MAX_LEN_STR);


			if (decrypt) {
				sprintf_s(strTmp, MAX_LEN_STR, " [%s:%s(宽度=%d)] %s %02d:%02d:%02d 触发时间:%d",
					iGetResultTypeNameByBits(pResult[i].uBitsTrigType),	"******",
					pResult[i].rcLocation.right - pResult[i].rcLocation.left, iGetDirString(pResult[i].nDirection),
					//pResult[i].struBDTime.bdt_year, pResult[i].struBDTime.bdt_mon, pResult[i].struBDTime.bdt_mday,
					pResult[i].struBDTime.bdt_hour, pResult[i].struBDTime.bdt_min, pResult[i].struBDTime.bdt_sec, pResult[i].triggerTimeMS);

				strcpy(strPlate,"******");
			}
			else {
				sprintf_s(strTmp, MAX_LEN_STR, " [%s:%s(宽度=%d)] %s %02d:%02d:%02d 触发时间:%d",
					iGetResultTypeNameByBits(pResult[i].uBitsTrigType),	pResult[i].license,
					pResult[i].rcLocation.right - pResult[i].rcLocation.left, iGetDirString(pResult[i].nDirection),
					// pResult[i].struBDTime.bdt_year, pResult[i].struBDTime.bdt_mon, pResult[i].struBDTime.bdt_mday,
					pResult[i].struBDTime.bdt_hour, pResult[i].struBDTime.bdt_min, pResult[i].struBDTime.bdt_sec, pResult[i].triggerTimeMS);

				strcpy_s(strPlate, MAX_PLATE_LENGTH -1, pResult[i].license);
			}
			
		}

		strcat_s(strMsg, MAX_LEN_STR, strTmp);
	}

	OutputWin *pOutWin = eResultType == VZ_LPRC_RESULT_REALTIME 
		? iGetOutputWinByIdx(pDev->GetWinIdx()) : iGetStableOutputWinByIdx(pDev->GetWinIdx());

	if(pOutWin)
	{
		pOutWin->UpdateString(strMsg);
		if(pImgFull)
		{
			pOutWin->SetFrame(pImgFull->pBuffer, pImgFull->uWidth, pImgFull->uHeight, pImgFull->uPitch);
			unsigned uYOffset = 4;
			for(unsigned i=0; i<uNumPlates; i++)
			{
				pOutWin->AddPatch(pImgPlateClip[i].pBuffer, pImgPlateClip[i].uWidth,
					pImgPlateClip[i].uHeight, pImgPlateClip[i].uPitch, 4, uYOffset);
				uYOffset += pImgPlateClip[i].uHeight;
			}
		}
	}

	if(m_bSaveJPG)
	{
		iSaveJPEG(pImgFull, eResultType, strPlate, pResult, pDev->device_sn);	
	}

	//保存触发类型的结果到文本（非实时）
	if(m_pFSaveLP && eResultType!=VZ_LPRC_RESULT_REALTIME)
	{
		if(uNumPlates > 0)
		{
			for(unsigned i=0; i<uNumPlates; i++)
			{
				fprintf(m_pFSaveLP, "%s ", pResult[0].license);
			}
			fprintf(m_pFSaveLP, "\n");
		}
		fflush(m_pFSaveLP);
	}
}

void CVzLPRSDKDemoDlg::iSaveJPEG(const VZ_LPRC_IMAGE_INFO *pImgInfo, VZ_LPRC_RESULT_TYPE eType, const char *pStrLicense, const TH_PlateResult *pResult, std::string sn)
{
	if (eType == VZ_LPRC_RESULT_REALTIME)
	{
		return;
	}
	char strFilePath[MAX_PATH];

	sprintf_s(strFilePath, MAX_PATH, "%s/%s/%s",
		m_strModuleDir, "Cap", iGetResultTypeName(eType));

	if(!PathIsDirectory(strFilePath))
	{
		CreateDirectory(strFilePath, NULL);
	}

	strcat(strFilePath, "/");
	strcat(strFilePath,sn.c_str());
	if(!PathIsDirectory(strFilePath)) {
		CreateDirectory(strFilePath, NULL);
	}

	char strFileName[MAX_PATH];

	SYSTEMTIME st;
	GetLocalTime(&st);

	if (pResult->car_brand.brand >= 0 && pResult->car_brand.brand < MAX_BRAND_COUNT && pResult->car_brand.type >= 0 && pResult->car_brand.type < 13) {
		sprintf_s(strFileName, MAX_PATH, "%s/%02d%02d%02d_%d%02d%02d_%03d_%s_%s_%s_%d_%d.jpg",
			strFilePath,
			st.wYear%100, st.wMonth, st.wDay,
			st.wHour, st.wMinute, st.wSecond,st.wMilliseconds,
			pStrLicense, s_car_make_lut[pResult->car_brand.brand], s_car_style_name[pResult->car_brand.type],pResult->nIsFakePlate, pResult->nCarColor);
	}
	else {
		sprintf_s(strFileName, MAX_PATH, "%s/%02d%02d%02d_%d%02d%02d_%03d_%s_%d_%d.jpg",
			strFilePath,
			st.wYear%100, st.wMonth, st.wDay,
			st.wHour, st.wMinute, st.wSecond,st.wMilliseconds,
			pStrLicense, pResult->nIsFakePlate, pResult->nCarColor);
	}

	if(pImgInfo)
	{
		int nCurMode = m_cmbSize.GetCurSel( );
		VzLPRClient_ImageSaveToJpegEx(pImgInfo, strFileName, 80, (IMG_SIZE_MODE)nCurMode);
		// VzLPRClient_ImageSaveToJpeg(pImgInfo, strFileName, 80);
	}
}

void CVzLPRSDKDemoDlg::OnVideoFrame0(VzLPRClientHandle handle, WORD wVideoID, const VzYUV420P *pFrame)
{
	OutputWin *pWinOut = iGetOutputWinByIdx(wVideoID);
	if(pWinOut == NULL)
		return;
}

void CVzLPRSDKDemoDlg::OnViewerMouse0(IV_EVENT eEvent, int x, int y, int nId)
{
	if(nId == 2 || nId == 3)
		nId -= 2;

	OutputWin &winOut = m_winOut[nId];
	OutputWin &winOut2 = m_winOut[nId + 2];

	if(eEvent == IV_EVENT_L_BTN_DOWN)
	{
		m_nIdxWinCurrSelected = nId;
		winOut.m_struInterV.SetActive(true);
		winOut2.m_struInterV.SetActive(true);
		for(int i=0; i<MAX_OUTPUT_NUM; i++)
		{
			if(i==nId || i==nId+2)
				continue;
			m_winOut[i].m_struInterV.SetActive(false);
		}
	}
}

void CALLBACK CVzLPRSDKDemoDlg::iOnIVPaint(int nID, bool bActive, bool bInUse, void *pUserData)
{
	CVzLPRSDKDemoDlg *pInstance = (CVzLPRSDKDemoDlg *)pUserData;
	pInstance->iOnIVPaint1(nID, bActive, bInUse);
}

void CVzLPRSDKDemoDlg::iOnIVPaint1(int nID, bool bActive, bool bInUse)
{
	m_winOut[nID].ShowString();

	if(bInUse)
	{
		if(nID>= (MAX_OUTPUT_NUM>>1))
			m_winOut[nID].ShowFrame();
	}
}

void CVzLPRSDKDemoDlg::DoDataExchange(CDataExchange* pDX)
{
	CDialog::DoDataExchange(pDX);
	DDX_Control(pDX, IDC_STATIC_SHOW_1_1, m_winOut[0].m_struInterV);
	DDX_Control(pDX, IDC_STATIC_SHOW_1_2, m_winOut[1].m_struInterV);
	DDX_Control(pDX, IDC_STATIC_SHOW_2_1, m_winOut[2].m_struInterV);
	DDX_Control(pDX, IDC_STATIC_SHOW_2_2, m_winOut[3].m_struInterV);
	DDX_Control(pDX, IDC_TREE_DEVICE, m_treeDeviceList);
	DDX_Control(pDX, IDC_BTN_START_RECORD, m_btnStartRecord);
	DDX_Control(pDX, IDC_BTN_STOP_RECORD, m_btnStopRecord);
	DDX_Check(pDX, IDC_CHK_SET_OFFLINE, m_bSetOffline);
	DDX_Check(pDX, IDC_CHK_OFFLINE_DATA, m_bChkOfflineData);
	DDX_Check(pDX, IDC_CHK_OUTPUT_REALRESULT, m_bChkOutputRealResult);
	DDX_Control(pDX, IDC_CMB_SIZE, m_cmbSize);
	DDX_Control(pDX, IDC_LIST2, m_ListDevice);
	DDX_Control(pDX, IDC_LOGIN_TYPE, m_cmbType);
}

BEGIN_MESSAGE_MAP(CVzLPRSDKDemoDlg, CDialog)
	ON_WM_PAINT()
	ON_WM_QUERYDRAGICON()
	//}}AFX_MSG_MAP
	ON_BN_CLICKED(IDC_BTN_OPEN, &CVzLPRSDKDemoDlg::OnBnClickedBtnOpen)
	ON_WM_TIMER()
	ON_BN_CLICKED(IDC_BTN_SET_OUTPUT, &CVzLPRSDKDemoDlg::OnBnClickedBtnSetOutput)
	ON_BN_CLICKED(IDC_BTN_STOP, &CVzLPRSDKDemoDlg::OnBnClickedBtnStop)
	ON_BN_CLICKED(IDC_BTN_CLOSE, &CVzLPRSDKDemoDlg::OnBnClickedBtnClose)
	ON_BN_CLICKED(IDC_BTN_SET_VLOOP, &CVzLPRSDKDemoDlg::OnBnClickedBtnSetVloop)
	ON_BN_CLICKED(IDC_BTN_SW_TRIGGER, &CVzLPRSDKDemoDlg::OnBnClickedBtnSwTrigger)
	ON_BN_CLICKED(IDC_BTN_START_FIND, &CVzLPRSDKDemoDlg::OnBnClickedBtnStartFind)
	ON_BN_CLICKED(IDC_BTN_STOP_FIND, &CVzLPRSDKDemoDlg::OnBnClickedBtnStopFind)
	ON_BN_CLICKED(IDC_CHK_SAVE_STABLE, &CVzLPRSDKDemoDlg::OnBnClickedChkSaveStable)
	ON_BN_CLICKED(IDC_BTN_SNAP_SHOOT, &CVzLPRSDKDemoDlg::OnBnClickedBtnSnapShoot)
	ON_BN_CLICKED(IDC_CHK_OUT1, &CVzLPRSDKDemoDlg::OnBnClickedChkOut1)
	ON_CBN_SELCHANGE(IDC_CMB_OUT_PORT_ID, &CVzLPRSDKDemoDlg::OnCbnSelchangeCmbOutPortId)	
	ON_BN_CLICKED(IDC_BTN_CONFIG_WLIST, &CVzLPRSDKDemoDlg::OnBnClickedBtnConfigWlist)
	ON_BN_CLICKED(IDC_BTN_SETUP_EXT, &CVzLPRSDKDemoDlg::OnBnClickedBtnSetupExt)
	ON_NOTIFY(TVN_SELCHANGED, IDC_TREE_DEVICE, &CVzLPRSDKDemoDlg::OnTvnSelchangedTreeDevice)
	ON_MESSAGE(WM_USER_MSG, &CVzLPRSDKDemoDlg::OnUserMsg)
	ON_MESSAGE(WM_PLATE_INFO_MSG, &CVzLPRSDKDemoDlg::OnPlateInfoMsg)
	ON_BN_CLICKED(IDC_BTN_START_RECORD, &CVzLPRSDKDemoDlg::OnBnClickedBtnStartRecord)
	ON_BN_CLICKED(IDC_BTN_STOP_RECORD, &CVzLPRSDKDemoDlg::OnBnClickedBtnStopRecord)
	ON_BN_CLICKED(IDC_BTN_OSD, &CVzLPRSDKDemoDlg::OnBnClickedBtnOsd)
	ON_BN_CLICKED(IDC_BTN_QUERY, &CVzLPRSDKDemoDlg::OnBnClickedBtnQuery)
	ON_BN_CLICKED(IDC_CHK_SET_OFFLINE, &CVzLPRSDKDemoDlg::OnBnClickedChkSetOffline)
	ON_BN_CLICKED(IDC_BTN_GET_INPUT, &CVzLPRSDKDemoDlg::OnBnClickedBtnGetInput)
	ON_BN_CLICKED(IDC_CHK_OFFLINE_DATA, &CVzLPRSDKDemoDlg::OnBnClickedChkOfflineData)
	ON_BN_CLICKED(IDC_BTN_UPDATE, &CVzLPRSDKDemoDlg::OnBnClickedBtnUpdate)
	ON_BN_CLICKED(IDC_BTN_OUTPUT, &CVzLPRSDKDemoDlg::OnBnClickedBtnOutput)
	ON_BN_CLICKED(IDC_BTN_NETWORK, &CVzLPRSDKDemoDlg::OnBnClickedBtnNetwork)
	ON_BN_CLICKED(IDC_BTN_VOICE, &CVzLPRSDKDemoDlg::OnBnClickedBtnVoice)
	ON_BN_CLICKED(IDC_BTN_MODIFY_IP, &CVzLPRSDKDemoDlg::OnBnClickedBtnModifyIp)
	ON_BN_CLICKED(IDC_BTN_SNAPIMAGE, &CVzLPRSDKDemoDlg::OnBnClickedBtnSnapImage)
	ON_MESSAGE(WM_SHOWMSG, &CVzLPRSDKDemoDlg::OnShowMsg)
	ON_EN_CHANGE(IDC_EDIT_NOTIFY, &CVzLPRSDKDemoDlg::OnEnChangeEditNotify)
	ON_BN_CLICKED(IDC_BTN_ENCRYPT, &CVzLPRSDKDemoDlg::OnBnClickedBtnEncrypt)
	ON_BN_CLICKED(IDC_BTN_GROUP_CFG, &CVzLPRSDKDemoDlg::OnBnClickedBtnGroupCfg)
	ON_BN_CLICKED(IDC_CHK_OUTPUT_REALRESULT, &CVzLPRSDKDemoDlg::OnBnClickedChkOutputRealresult)
	ON_BN_CLICKED(IDC_BTN_VIDEO_CFG, &CVzLPRSDKDemoDlg::OnBnClickedBtnVideoCfg)
	ON_BN_CLICKED(IDC_BTN_BASE_CFG, &CVzLPRSDKDemoDlg::OnBnClickedBtnBaseCfg)	ON_BN_CLICKED(IDC_BTN_OutIn, &CVzLPRSDKDemoDlg::OnBnClickedBtnOutin)
	ON_EN_CHANGE(IDC_EDIT_IP, &CVzLPRSDKDemoDlg::OnEnChangeEditIp)
	ON_BN_CLICKED(IDC_BUTTON5, &CVzLPRSDKDemoDlg::OnBnClickedButton5)
	ON_NOTIFY(NM_CLICK, IDC_LIST2, &CVzLPRSDKDemoDlg::OnNMClickList2)
	ON_BN_CLICKED(IDC_BTN_STORE, &CVzLPRSDKDemoDlg::OnBnClickedBtnStore)
	ON_CBN_SELCHANGE(IDC_CMB_SIZE, &CVzLPRSDKDemoDlg::OnCbnSelchangeCmbSize)
	ON_BN_CLICKED(IDC_BTN_TTS, &CVzLPRSDKDemoDlg::OnBnClickedBtnTts)
	ON_BN_CLICKED(IDC_BTN_ACTIVE_STATUS, &CVzLPRSDKDemoDlg::OnBnClickedBtnActiveStatus)
	ON_BN_CLICKED(IDC_BTN_SERIAL_DATA, &CVzLPRSDKDemoDlg::OnBnClickedBtnSerialData)
	ON_BN_CLICKED(IDC_BTN_TCP_TRAN, &CVzLPRSDKDemoDlg::OnBnClickedBtnTcpTran)
	ON_BN_CLICKED(IDC_BTN_DISCONN_RECORD, &CVzLPRSDKDemoDlg::OnBnClickedBtnDisconnRecord)
	ON_BN_CLICKED(IDC_BTN_TALK_VOICE, &CVzLPRSDKDemoDlg::OnBnClickedBtnTalkVoice)
	ON_BN_CLICKED(IDC_BTN_USER_OSD, &CVzLPRSDKDemoDlg::OnBnClickedBtnUserOsd)
	ON_CBN_SELCHANGE(IDC_LOGIN_TYPE, &CVzLPRSDKDemoDlg::OnCbnSelchangeLoginType)
	ON_WM_CLOSE()
	ON_BN_CLICKED(IDC_BTN_START_TALK, &CVzLPRSDKDemoDlg::OnBnClickedBtnStartTalk)
	ON_BN_CLICKED(IDC_BTN_STOP_TALK, &CVzLPRSDKDemoDlg::OnBnClickedBtnStopTalk)
	ON_BN_CLICKED(IDC_BTN_CLOUD_VOD, &CVzLPRSDKDemoDlg::OnBnClickedBtnCloudVod)
	ON_BN_CLICKED(IDC_BTN_DEV_MATE, &CVzLPRSDKDemoDlg::OnBnClickedBtnDevMate)
END_MESSAGE_MAP()


// CVzLPRSDKDemoDlg 消息处理程序

static void iImageListLoadIDB(int IDB_, CImageList *pImgList)
{
	CBitmap bitmap;
	bitmap.LoadBitmap(IDB_);
	pImgList->Add(&bitmap, RGB(0,0,0));
}

enum
{
	DEV_STATUS_ONLINE,
	DEV_STATUS_OFFLINE,
};


string getCurTime()
{
	time_t t = time(NULL);

	char szTime[64] = { 0 };
	struct tm tm1;
	tm1 = *localtime(&t);
	sprintf_s(szTime, sizeof(szTime), "%d:%02d:%02d:%02d:%02d:%02d", tm1.tm_year + 1900, tm1.tm_mon + 1, tm1.tm_mday,
		tm1.tm_hour, tm1.tm_min, tm1.tm_sec);
	return string(szTime);
}

void __stdcall gTestGPIOCallBack(VzLPRClientHandle handle, int nGPIOId, int nVal, void* pUserData)
{
	char cNew[128];
	memset(cNew, '/0', 128);
	string sTime = getCurTime() + " >> ";
	sprintf_s(cNew, "%s VzLPRClient_SetGPIORecvCallBack GPIO Id = %d Value = %d\n"
		, sTime.c_str()
		, nGPIOId
		, nVal);

	string* sMsg = new string;
	sMsg->append(cNew, strlen(cNew));

	WriteFileB("./gpiotest.txt", (unsigned char*)cNew, strlen(cNew));
	PostMessage(HWND(pUserData), WM_SHOWMSG, (WPARAM)NULL, (LPARAM)sMsg);
}

BOOL CVzLPRSDKDemoDlg::OnInitDialog()
{
	CDialog::OnInitDialog();

	// 设置此对话框的图标。当应用程序主窗口不是对话框时，框架将自动
	//  执行此操作
	SetIcon(m_hIcon, TRUE);			// 设置大图标
	SetIcon(m_hIcon, FALSE);		// 设置小图标

	// TODO: 在此添加额外的初始化代码
#ifdef _DEBUG
	_CrtSetDbgFlag( _CRTDBG_ALLOC_MEM_DF | _CRTDBG_LEAK_CHECK_DF );
	//	_CrtSetBreakAlloc(100);
#endif

	//初始化结果输出窗口
	GetModuleFileName(NULL, m_strModuleDir, MAX_PATH);
	*strrchr(m_strModuleDir, '\\') = 0;
	strcpy_s(m_strModuleName, MAX_PATH, "VzLPRSDKDemoDlg");

	VZLPRClient_SetCommonNotifyCallBack(OnCommonNotify, this);

	int nIDCEditResult[MAX_OUTPUT_NUM] =
	{
		IDC_EDIT_RESULT_1_1, IDC_EDIT_RESULT_1_2,
		IDC_EDIT_RESULT_2_1, IDC_EDIT_RESULT_2_2
	};
	for(int i=0; i<MAX_OUTPUT_NUM; i++)
	{
		m_winOut[i].pEditResult = (CEdit *)GetDlgItem(nIDCEditResult[i]);
		m_winOut[i].m_struInterV.SetID(i);
		m_winOut[i].m_struInterV.SetInteractCallback(iOnViewerMouse, this);
		m_winOut[i].m_struInterV.SetCallBackOnPaint(iOnIVPaint, this);
	}

	//为m_treeDeviceList建立图像列表
	m_pImageList = new CImageList();
	m_pImageList->Create(16, 16, ILC_COLOR32, 0, 2);
	iImageListLoadIDB(IDB_BMP_ONLINE, m_pImageList);
	iImageListLoadIDB(IDB_BMP_OFFLINE, m_pImageList);
	m_treeDeviceList.SetImageList(m_pImageList, TVSIL_NORMAL);

	char strIP[32] = {0};
	{
		//使用上一次的IP地址
		char strINI[MAX_PATH];
		sprintf_s(strINI, MAX_PATH, "%s/user.ini", m_strModuleDir);
		GetPrivateProfileString(m_strModuleName, "LastIP", "", strIP, 32, strINI);
	}
	SetDlgItemText(IDC_EDIT_IP, strIP);
	SetDlgItemText(IDC_EDIT_PORT, "80");
	SetDlgItemText(IDC_EDIT_USERNAME, "admin");
	SetDlgItemText(IDC_EDIT_PASSWORD, "admin");
	SetDlgItemText(IDC_EDIT_USERPWD, "123456789");

	((CButton *)GetDlgItem(IDC_CHK_SAVE_STABLE))->SetCheck(BST_CHECKED);
	GetDlgItem(IDC_EDIT_DEVICE_SN)->EnableWindow(FALSE);
	m_bSaveJPG = true;

	char strFilePath[MAX_PATH];

	sprintf_s(strFilePath, MAX_PATH, "%s/%s",
		m_strModuleDir, "Cap");

	if(!PathIsDirectory(strFilePath))
	{
		CreateDirectory(strFilePath, NULL);
	}

	m_strCommNotify = "";

	m_dlgTriggerShow.Create(m_dlgTriggerShow.IDD, this);

	m_cmbSize.AddString(_T("无"));
	m_cmbSize.AddString(_T("CIF"));
	m_cmbSize.AddString(_T("D1"));
	m_cmbSize.AddString(_T("720P"));
	m_cmbSize.SetCurSel(0);

	m_cmbType.AddString(_T("局域网登录"));
	m_cmbType.AddString(_T("云平台转发登录"));
	m_cmbType.AddString(_T("云平台扩展登录"));
	m_cmbType.SetCurSel(0);

	SetDlgItemText(IDC_EDIT_OUT_DELAY, "1000");

	SetTimer(TIMER_INTERFACE, 300, NULL);

	SetTimer(TIMER_RECORD_ID, TIMER_RECORD_VALUE, NULL);

	SetTimer(TIMER_CHECK_OFFLINE, 2000, NULL);

	m_ListDevice.InsertColumn( 0, "设备IP",   LVCFMT_LEFT, 185);

#ifdef SAVE_LP2FILE
	time_t ttCurr;
	time(&ttCurr);
	tm tmCurr;
	localtime_s(&tmCurr, &ttCurr);
	sprintf_s(strFilePath, MAX_PATH, "%s/LP_%d%02d%02d_%2d%02d%02d.log",
		m_strModuleDir, tmCurr.tm_year+1900, tmCurr.tm_mon+1, tmCurr.tm_mday,
		tmCurr.tm_hour, tmCurr.tm_min, tmCurr.tm_sec);

	fopen_s(&m_pFSaveLP, strFilePath, "w");
#endif

	return TRUE;  // 除非将焦点设置到控件，否则返回 TRUE
}

void CVzLPRSDKDemoDlg::iDeleteDeviceInfo( )
{
	for (int i = 0; i < m_ListDevice.GetItemCount(); ++i)
	{
		DEVICE_INFO *pInfo = (DEVICE_INFO *)m_ListDevice.GetItemData(i);
		if( pInfo != NULL )
		{
			free(pInfo);
		}
	}
	m_ListDevice.DeleteAllItems();
}

void CVzLPRSDKDemoDlg::addMsg(const string& sMsg)
{
	string sNewMsg(sMsg);
	if (!m_strCommNotify.empty())
		sNewMsg += "\r\n";

	sNewMsg += m_strCommNotify;
	m_strCommNotify = sNewMsg;

	SetDlgItemText(IDC_EDIT_NOTIFY, m_strCommNotify.c_str());

}

BOOL CVzLPRSDKDemoDlg::DestroyWindow()
{
	VZLPRClient_StopFindDevice( );
	iDeleteDeviceInfo( );

	for(unsigned i=0; i<m_vDev.size(); i++)
	{
		DeviceLPR *pDev = m_vDev[i];
		//	VzLPRClient_Close(pDev->hLPRClient);
		VzLPRClient_CloseByIP(pDev->strIP);
		delete pDev;
	}

	delete m_pImageList;

	KillTimer(1000);
	KillTimer(TIMER_RECORD_ID);

	if(m_pFSaveLP)
		fclose(m_pFSaveLP);

	return CDialog::DestroyWindow();
}

// 如果向对话框添加最小化按钮，则需要下面的代码
//  来绘制该图标。对于使用文档/视图模型的 MFC 应用程序，
//  这将由框架自动完成。

void CVzLPRSDKDemoDlg::OnPaint()
{
	if (IsIconic())
	{
		CPaintDC dc(this); // 用于绘制的设备上下文

		SendMessage(WM_ICONERASEBKGND, reinterpret_cast<WPARAM>(dc.GetSafeHdc()), 0);

		// 使图标在工作区矩形中居中
		int cxIcon = GetSystemMetrics(SM_CXICON);
		int cyIcon = GetSystemMetrics(SM_CYICON);
		CRect rect;
		GetClientRect(&rect);
		int x = (rect.Width() - cxIcon + 1) / 2;
		int y = (rect.Height() - cyIcon + 1) / 2;

		// 绘制图标
		dc.DrawIcon(x, y, m_hIcon);
	}
	else
	{
		CDialog::OnPaint();
	}
}

//当用户拖动最小化窗口时系统调用此函数取得光标
//显示。
HCURSOR CVzLPRSDKDemoDlg::OnQueryDragIcon()
{
	return static_cast<HCURSOR>(m_hIcon);
}


void CVzLPRSDKDemoDlg::OnBnClickedBtnOpen()
{
	char strIP[256];
	int nPort;
	int nRtspPort;
	char strUserName[32];
	char strPassword[32];
	char strUserPwd[32] = {0};
	char strDeviceSN[32] = {0};

	char strAppId[64] = {0};
	char strAppKey[64] = {0};

	GetDlgItemText(IDC_EDIT_IP, strIP, 256);
	nPort = GetDlgItemInt(IDC_EDIT_PORT, NULL, FALSE);
	nRtspPort = GetDlgItemInt(IDC_EDIT_PORT2, NULL, FALSE);
	GetDlgItemText(IDC_EDIT_USERNAME, strUserName, 32);
	GetDlgItemText(IDC_EDIT_PASSWORD, strPassword, 32);
	GetDlgItemText(IDC_EDIT_USERPWD, strUserPwd, 32);
	GetDlgItemText(IDC_EDIT_DEVICE_SN, strDeviceSN, 32);

	GetDlgItemText(IDC_EDIT_APP_ID, strAppId, 64);
	GetDlgItemText(IDC_EDIT_APP_KEY, strAppKey, 64);

	{
		//保存地址
		char strINI[MAX_PATH];
		sprintf_s(strINI, MAX_PATH, "%s/user.ini", m_strModuleDir);
		WritePrivateProfileString(m_strModuleName, "LastIP", strIP, strINI);
	}

	VzLPRClientHandle hLPRClient = NULL; 
	if(m_cmbType.GetCurSel() > 0)
	{
		if(strlen(strDeviceSN) == 0)
		{
			MessageBox("请输入远程设备的序列号");
			return;
		}

		if (m_cmbType.GetCurSel() == 2) {
			if(!cloud_setup_)
			{
				CString cloud_url;
				CString cloud_user_id;
				CString cloud_user_pwd;

				DlgPdnsLoginInfo pdns_dlg;
				pdns_dlg.pdns_url = &cloud_url;
				pdns_dlg.pdns_user_id = &cloud_user_id;
				pdns_dlg.pdns_user_pwd = &cloud_user_pwd;

				if (IDOK == pdns_dlg.DoModal()) {
					int cloud_ret = VzClient_CloudSetup(cloud_url.GetBuffer(0), strDeviceSN, cloud_user_id.GetBuffer(0), cloud_user_pwd.GetBuffer(0), "");
					if (cloud_ret == 0) {
						cloud_setup_ = true;
						cloud_url_ = cloud_url;
						cloud_user_id_ = cloud_user_id;
						cloud_user_pwd_ = cloud_user_pwd;
					}
					else {
						MessageBox("登录云平台失败!");
					}
				}

				if (!cloud_setup_) {
					return;
				}
			}
			
			VZ_LPRC_LOGIN_PARAM login_param = {0};
			strncpy_s(login_param.dev_ip, sizeof(login_param.dev_ip), cloud_url_.GetBuffer(0), cloud_url_.GetLength()); 
			login_param.dev_port = nPort;
			strncpy_s(login_param.username, sizeof(login_param.username), strUserName, strlen(strUserName));
			strncpy_s(login_param.password, sizeof(login_param.password), strPassword, strlen(strPassword));
			login_param.protocol_type = 0;
			login_param.network_type = 3;
			strncpy_s(login_param.sn, sizeof(login_param.sn), strDeviceSN, strlen(strDeviceSN));
			strncpy_s(login_param.ak_id, sizeof(login_param.ak_id), cloud_user_id_.GetBuffer(0), cloud_user_id_.GetLength());
			strncpy_s(login_param.ak_secret, sizeof(login_param.ak_secret), cloud_user_pwd_.GetBuffer(0), cloud_user_pwd_.GetLength());
			hLPRClient = VzLPRClient_OpenV4(&login_param);
		}
		else {
			if (strlen(strAppId) > 0 && strlen(strAppKey) > 0) {
				hLPRClient = VzLPRClient_CloudOpen(strIP, nPort, strUserName, strPassword,nRtspPort, 1, strDeviceSN, strAppId, strAppKey, "");
			}
			else {
				hLPRClient = VzLPRClient_OpenV2(strIP, nPort, strUserName, strPassword,nRtspPort, 1, strDeviceSN);
			}
		}
	}
	else
	{
		hLPRClient = VzLPRClient_OpenEx(strIP, nPort, strUserName, strPassword,nRtspPort);
	}

	if(hLPRClient == NULL)
	{
		char str[128];
		sprintf_s(str, 128, "设备[%s:%d]打开失败，请确认设备已连接", strIP, nPort);
		MessageBox(str);//弹出窗口
		return;
	}

	// 设置RG设备通用结果回调
	VzClient_SetCommonResultCallBack(hLPRClient, 0, OnCommonResult, this);

	//给设备设置数据回调
	VzLPRClient_SetPlateInfoCallBack(hLPRClient, OnPlateInfo, this, 1);  

	VzLPRClient_SetGPIORecvCallBack(hLPRClient, gTestGPIOCallBack, this->m_hWnd);//设置GPIO输入回调函数
	bool bFound = false;
	for(unsigned i=0; i<m_vDev.size(); i++)
	{
		if(hLPRClient == m_vDev[i]->hLPRClient)
		{
			bFound = true;
			break;
		}
	}

	// 先进行二次登录
	VZ_LPRC_ACTIVE_ENCRYPT data = {0};
	int ret = VzLPRClient_GetEMS(hLPRClient, &data);
	if ( data.uActiveID == 2 )
	{
		// 验证级别为level,执行登录操作
		ret = VzLPRClient_StartLogin(hLPRClient, &data);
		ret = VzLPRClient_LoginAuth(hLPRClient, strUserPwd);
		if ( ret != 0 )
		{
			MessageBox("二次登录失败,请检查用户密码是否正确");

			// 登录失败，关闭设备
			VzLPRClient_Close(hLPRClient);
			return;
		}
	}

	DeviceLPR *pDev = NULL;
	if(bFound == false)
	{
		//保存到本地存储
		pDev = new DeviceLPR(strIP, hLPRClient);
		m_vDev.push_back(pDev);

		//添加到设备列表
		HTREEITEM hItem = m_treeDeviceList.InsertItem(strIP, 0, 1);
		m_treeDeviceList.SetItemData(hItem, hLPRClient);//设置指定项与现在打开的句柄相关联
		m_treeDeviceList.SelectItem(hItem);//选中特定树项SelectItem

		pDev->SetUserPwd(strUserPwd);//保存加密的用户密码

		if(m_cmbType.GetCurSel() > 0)
		{
			pDev->device_sn = strDeviceSN;

			if (m_cmbType.GetCurSel() == 2) {
				pDev->m_nCloudLogin = 1;
			}
		}
	}

	iChangeDlgImageItem(hLPRClient, DEV_STATUS_ONLINE);

	CComboBox *pCmbOP = (CComboBox *)GetDlgItem(IDC_CMB_OUT_PORT_ID);
	pCmbOP->ResetContent();

	int board_type = 0;
	int ret_board = VzLPRClient_GetHwBoardType(hLPRClient, &board_type);
	VzLPRClient_SetIsShowPlateRect(hLPRClient, 0);

	if (board_type == 2)
	{
		pDev->SetBoardType(2);

		pCmbOP->AddString("开关量 1");
		pCmbOP->AddString("开关量 2");
		pCmbOP->AddString("5V TTL 1");
		pCmbOP->AddString("5V TTL 2");
		pCmbOP->AddString("开关量 3");

		pCmbOP->SetCurSel(0);
	}
	else if( board_type == 3 )
	{
		pDev->SetBoardType(3);

		pCmbOP->AddString("开关量 1");
		pCmbOP->AddString("开关量 2");
		pCmbOP->SetCurSel(0);
	}
	else
	{
		pDev->SetBoardType(1);

		pCmbOP->AddString("开关量 1");
		pCmbOP->AddString("开关量 2");
		pCmbOP->AddString("5V TTL 1");
		pCmbOP->AddString("5V TTL 2");
		pCmbOP->AddString("开关量 3");
		pCmbOP->AddString("开关量 4");

		pCmbOP->SetCurSel(0);
	}

	// VzLPRClient_SetPlateInfoCallBack(hLPRClient, OnPlateInfo, this, 1); 

	// OnBnClickedBtnSetOutput();
	int ver1 = 0,ver2 = 0, ver3 = 0,ver4 = 0;
	int retSV = VzLPRClient_GetRemoteSoftWareVersion(hLPRClient,&ver1,&ver2,&ver3,&ver4);
	if (ver1 == 21) {
		EnableOperButton(2);
	}
	else {
		EnableOperButton(1);
	}
}

OutputWin *CVzLPRSDKDemoDlg::iGetOutputWinByIdx(int nIdxWin)
{
	if(nIdxWin >= 0 && nIdxWin < (MAX_OUTPUT_NUM>>1))
		return(m_winOut + nIdxWin);

	return(NULL);
}

OutputWin *CVzLPRSDKDemoDlg::iGetStableOutputWinByIdx(int nIdx)
{
	OutputWin *pWin = iGetOutputWinByIdx(nIdx);
	if(pWin == NULL)
		return(NULL);

	return(pWin + (MAX_OUTPUT_NUM>>1));
}

//找到一个合适的输出窗口：
int CVzLPRSDKDemoDlg::iGetOutputWinIndex(VzLPRClientHandle hLPRClient)
{
	//如果未指定客户端句柄
	if(hLPRClient == NULL)
	{
		//如果以选中某个窗口，则直接使用该窗口
		if(m_nIdxWinCurrSelected >= 0)
			return(m_nIdxWinCurrSelected);

		//如果未选中任一窗口，则使用未被占用的一个空闲窗口
		for(int i=0; i<MAX_OUTPUT_NUM>>1; i++)
		{
			if(m_winOut[i].GetClientHandle() == NULL)
				return(i);
		}
	}
	else	//如果以指定一个客户端句柄，则使用已和该客户端句柄关联的窗口
	{
		for(int i=0; i<MAX_OUTPUT_NUM>>1; i++)
		{
			if(m_winOut[i].GetClientHandle() == hLPRClient)
				return(i);
		}
	}

	return(-1);
}

void CVzLPRSDKDemoDlg::OnTimer(UINT_PTR nIDEvent)
{
	// TODO: Add your message handler code here and/or call default
	if( nIDEvent == TIMER_RECORD_ID )
	{
		// 定时全部重新录像
		iReRecordAllVideo( );
	}
	else if(nIDEvent == TIMER_CHECK_OFFLINE)
	{
		//轮询所有设备的状态
		for(unsigned i=0; i<m_vDev.size(); i++)
		{
			BYTE cStatus = 0;
			VzLPRClient_IsConnected(m_vDev[i]->hLPRClient, &cStatus);
			if(cStatus == 1)
			{
				iChangeDlgImageItem(m_vDev[i]->hLPRClient, DEV_STATUS_ONLINE);
			}
			else
			{
				iChangeDlgImageItem(m_vDev[i]->hLPRClient, DEV_STATUS_OFFLINE);
			}
		}
	}
	else
	{
		//更新界面内容
		for(unsigned i=0; i<MAX_OUTPUT_NUM>>1; i++)
		{
			m_winOut[i].ShowString();
		}
	}

	CDialog::OnTimer(nIDEvent);
}

void CVzLPRSDKDemoDlg::iChangeDlgImageItem(VzLPRClientHandle handle, int imageID)
{
	HTREEITEM hRoot = m_treeDeviceList.GetRootItem();
	while(hRoot) 
	{
		if(handle == m_treeDeviceList.GetItemData(hRoot))
		{
			m_treeDeviceList.SetItemImage(hRoot, imageID, imageID);//设置与树项关联图像SetItemImage
			return;
		}
		hRoot = m_treeDeviceList.GetNextItem(hRoot, TVGN_NEXT);	
	}
}

void CVzLPRSDKDemoDlg::OnBnClickedBtnSetOutput()
{
	//获取一个合适的窗口
	int nIdxWin = iGetOutputWinIndex();
	if(nIdxWin < 0)
		return;

	//配合一个用于输出稳定结果的窗口
	int nIdxWinStable = nIdxWin + (MAX_OUTPUT_NUM>>1);

	//俩窗口，用于显示实时视频和稳定结果截图
	OutputWin *pWinOut = m_winOut + nIdxWin;
	OutputWin *pWinOut2 = m_winOut + nIdxWinStable;

	//如果当前窗口正在使用则先清理
	iCleanOutputWin(nIdxWin);

	HTREEITEM hItem = m_treeDeviceList.GetSelectedItem();
	if(hItem == NULL)
	{
		MessageBox("请先选择一台设备");
		return;
	}

	VzLPRClientHandle hLPRClient = m_treeDeviceList.GetItemData(hItem);

	DeviceLPR *pDev = iGetLocalDev(hLPRClient);
	if(pDev == NULL)
	{
		return;
	}
	//取消与当前设备相关的其他窗口
	// iCleanOutputWin(pDev->GetWinIdx());

	//关联窗口与设备
	pDev->SetWinIdx(nIdxWin);
	pWinOut->SetClientHandle(pDev->hLPRClient);
  
	if (pDev->m_nCloudLogin == 1) {
		pWinOut->nPlayHandle = VzClient_CloudStartRealPlay(pDev->hLPRClient, m_winOut->GetHWnd());
	}
	else {
		pWinOut->nPlayHandle = VzLPRClient_StartRealPlay(pDev->hLPRClient, pWinOut->GetHWnd());
	}
	
	//pWinOut->nPlayHandle = VzLPRClient_StartSubStreamRealPlay(pDev->hLPRClient, pWinOut->GetHWnd());
}

DeviceLPR *CVzLPRSDKDemoDlg::iGetLocalDev(VzLPRClientHandle hLPRClient)
{
	for(unsigned i=0; i<m_vDev.size(); i++)
	{
		if(m_vDev[i]->hLPRClient == hLPRClient)
			return(m_vDev[i]);
	}

	return(NULL);
}
//取消与当前设备相关的其他窗口
void CVzLPRSDKDemoDlg::iCleanOutputWin(int nIdx)
{
	OutputWin *pOutWin = iGetOutputWinByIdx(nIdx);
	if(pOutWin == NULL)
		return;
	OutputWin *pOutWin2 = iGetStableOutputWinByIdx(nIdx);

	//停止该窗口的实时视频播放
	VzLPRClientHandle handle = pOutWin->GetClientHandle();
	DeviceLPR *pDev = iGetLocalDev(handle);
	if(pDev)
	{
		if (pDev->m_nCloudLogin == 1) {
			VzClient_CloudStopRealPlay(pOutWin->nPlayHandle);
		}
		else {
			VzLPRClient_StopRealPlay(pOutWin->nPlayHandle);
		}
	}
	
	pOutWin->nPlayHandle = 0;

	//停止该窗口的截图显示
	if(handle)
	{
		VzLPRClient_SetPlateInfoCallBack(handle, NULL, NULL, 0);
		DeviceLPR *pDev = iGetLocalDev(handle);
		if(pDev)
		{
			pDev->SetWinIdx(-1);
		}
	}

	pOutWin->SetClientHandle(NULL);
	pOutWin->UpdateString(" ");
	pOutWin->m_struInterV.Invalidate();
	pOutWin2->SetClientHandle(NULL);
	pOutWin2->UpdateString(" ");
	pOutWin2->m_struInterV.Invalidate();
}

void CVzLPRSDKDemoDlg::OnBnClickedBtnStop()
{
	iCleanOutputWin(m_nIdxWinCurrSelected);
}

void CVzLPRSDKDemoDlg::OnBnClickedBtnClose()
{
	HTREEITEM hItem = m_treeDeviceList.GetSelectedItem();
	if(hItem == NULL)
	{
		MessageBox("请先选择一台设备");
		return;
	}

	VzLPRClientHandle hLPRClient = m_treeDeviceList.GetItemData(hItem);

	vector <DeviceLPR *>::iterator iter;
	for(iter=m_vDev.begin(); iter!=m_vDev.end(); iter++)
	{
		if(hLPRClient == (*iter)->hLPRClient)
		{
			iCleanOutputWin((*iter)->nIdxWin);

			VzLPRClient_Close(hLPRClient);

			delete (*iter);

			m_vDev.erase(iter);	

			break;
		}
	}

	m_treeDeviceList.DeleteItem(hItem);
}


void CVzLPRSDKDemoDlg::OnBnClickedBtnSetVloop()
{
	HTREEITEM hItem = m_treeDeviceList.GetSelectedItem();
	if(hItem == NULL)
	{
		MessageBox("请先选择一台设备");
		return;
	}
	VzLPRClientHandle hLPRClient = m_treeDeviceList.GetItemData(hItem);

	DeviceLPR *pDev = iGetLocalDev(hLPRClient);
	if (pDev == NULL)
	{
		return;
	}

	DlgConfigRules dlgCfgRule(hLPRClient, this);
	dlgCfgRule.SetHWType(pDev->m_nBoardType);
	dlgCfgRule.DoModal();
}

void CVzLPRSDKDemoDlg::OnBnClickedBtnSwTrigger()
{
	HTREEITEM hItem = m_treeDeviceList.GetSelectedItem();
	if(hItem == NULL)
	{
		MessageBox("请先选择一台设备");
		return;
	}

	VzLPRClientHandle hLPRClient = m_treeDeviceList.GetItemData(hItem);

#ifdef TEST_OUTPUT_PLATE_TIME
	SYSTEMTIME st;
	GetLocalTime(&st);

	char szTime[256] = {0};
	sprintf(szTime, "start %02d:%02d:%02d %03d\n",st.wHour,st.wMinute,st.wSecond, st.wMilliseconds);
	WriteLogFile("plateTime.txt", szTime);
#endif

	VzLPRClient_ForceTriggerEx(hLPRClient);// 发送软件触发信号，强制处理当前时刻的数据并输出结果
}

BOOL CVzLPRSDKDemoDlg::PreTranslateMessage(MSG* pMsg)
{
	//设置对话框不接收某些键
	if(pMsg->message == WM_KEYDOWN)
	{
		if(pMsg->wParam == VK_ESCAPE || pMsg->wParam == VK_RETURN)
		{
			return(TRUE);
		}
	}

	return CDialog::PreTranslateMessage(pMsg);
}

void __stdcall FIND_DEVICE_CALLBACK_EX(const char *pStrDevName, const char *pStrIPAddr,
									   WORD usPort1, WORD usPort2, unsigned int SL,unsigned int SH, char* netmask, char* gateway, void *pUserData)
{
	//if(strcmp(pStrIPAddr, "192.168.109.165") == 0 )
	//{
	//}

	CVzLPRSDKDemoDlg *pInstance = (CVzLPRSDKDemoDlg *)pUserData;
	pInstance->OnDevFound(pStrDevName, pStrIPAddr, usPort1, usPort2, SL, SH, netmask, gateway);
}

void CVzLPRSDKDemoDlg::OnDevFound(const char *pStrDevName, const char *pStrIPAddr, WORD usPort1, WORD usPort2, unsigned int SL, unsigned int SH, char* netmask, char* gateway)
{
	if( pStrIPAddr != NULL && usPort1 > 0 )
	{
		DEVICE_INFO *pDeviceInfo = (DEVICE_INFO *)malloc( sizeof(DEVICE_INFO) );
		strncpy_s(pDeviceInfo->szIPAddr, sizeof(pDeviceInfo->szIPAddr), pStrIPAddr, strlen(pStrIPAddr) );
		pDeviceInfo->usPort1 = usPort1;
		pDeviceInfo->usPort2 = usPort2;
		pDeviceInfo->SL		 = SL;
		pDeviceInfo->SH		 = SH;
		strncpy_s(pDeviceInfo->netmask, sizeof(pDeviceInfo->netmask), netmask, strlen(netmask) );
		strncpy_s(pDeviceInfo->gateway, sizeof(pDeviceInfo->gateway), gateway, strlen(gateway) );

		PostMessage(WM_USER_MSG, (WPARAM)MSG_DEV_FOUND, (LPARAM)pDeviceInfo);
	}
}

void CVzLPRSDKDemoDlg::OnBnClickedBtnStartFind()
{
	// 先停止搜索
	VZLPRClient_StopFindDevice( );

	iDeleteDeviceInfo( );

	VZLPRClient_StartFindDeviceEx(FIND_DEVICE_CALLBACK_EX, this);
}

void CVzLPRSDKDemoDlg::OnBnClickedBtnStopFind()
{
	VZLPRClient_StopFindDevice();
}

void CVzLPRSDKDemoDlg::OnBnClickedChkSaveStable()
{
	UINT uBtnState = ((CButton *)GetDlgItem(IDC_CHK_SAVE_STABLE))->GetCheck();
	m_bSaveJPG = uBtnState == BST_CHECKED ? true : false;

	DeviceLPR *pDev = GetCurDeviceInfo( );
	if ( pDev != NULL )
	{
		pDev->SetIsSaveImg( m_bSaveJPG );
	}
}

void CVzLPRSDKDemoDlg::OnBnClickedBtnSnapShoot()
{
	if(m_nIdxWinCurrSelected < 0)
		return;

	OutputWin *pWinOut = m_winOut + m_nIdxWinCurrSelected;

	char strFilePath[MAX_PATH];

	sprintf_s(strFilePath, MAX_PATH, "%s/%s/%s",
		m_strModuleDir, "Cap", "Snap");

	if(!PathIsDirectory(strFilePath))
	{
		CreateDirectory(strFilePath, NULL);
	}

	char strFileName[MAX_PATH];

	CTime time = CTime::GetCurrentTime();
	sprintf_s(strFileName, MAX_PATH, "%s/%02d%02d%02d_%d%02d%02d.jpg",
		strFilePath,
		time.GetYear()%100, time.GetMonth(), time.GetDay(),
		time.GetHour(), time.GetMinute(), time.GetSecond());

	VzLPRClient_GetSnapShootToJpeg2(pWinOut->nPlayHandle, strFileName, 80);
}

void CVzLPRSDKDemoDlg::OnBnClickedChkOut1()
{
	HTREEITEM hItem = m_treeDeviceList.GetSelectedItem();
	if(hItem == NULL)
	{
		MessageBox("请先选择一台设备");
		return;
	}

	VzLPRClientHandle hLPRClient = m_treeDeviceList.GetItemData(hItem);
	UINT uBtnState = ((CButton *)GetDlgItem(IDC_CHK_OUT1))->GetCheck();

	int nOPID = ((CComboBox *)GetDlgItem(IDC_CMB_OUT_PORT_ID))->GetCurSel();

	VzLPRClient_SetIOOutput(hLPRClient, nOPID, uBtnState == BST_CHECKED ? 1 : 0);
}

void CVzLPRSDKDemoDlg::OnCbnSelchangeCmbOutPortId()
{
	HTREEITEM hItem = m_treeDeviceList.GetSelectedItem();
	if(hItem == NULL)
	{
		return;
	}

	VzLPRClientHandle hLPRClient = m_treeDeviceList.GetItemData(hItem);

	int nOPID = ((CComboBox *)GetDlgItem(IDC_CMB_OUT_PORT_ID))->GetCurSel();
	if(nOPID < 0)
		return;

	int nOutRT = 0;

	VzLPRClient_GetIOOutput(hLPRClient, nOPID, &nOutRT);

	((CButton *)GetDlgItem(IDC_CHK_OUT1))->SetCheck(nOutRT == 1
		? BST_CHECKED : BST_UNCHECKED);

	DeviceLPR *pDev = GetCurDeviceInfo( );
	if ( pDev != NULL )
	{
		pDev->SetCurIO( nOPID );
	}
}

void CVzLPRSDKDemoDlg::OnBnClickedBtnConfigWlist()
{
	HTREEITEM hItem = m_treeDeviceList.GetSelectedItem();
	if(hItem == NULL)
	{
		MessageBox("请先选择一台设备");
		return;
	}
	VzLPRClientHandle hLPRClient = m_treeDeviceList.GetItemData(hItem);
	DlgWhiteList dlgCfgWList(this);
	dlgCfgWList.SetHandle(hLPRClient);

	dlgCfgWList.DoModal();
}

void CVzLPRSDKDemoDlg::OnBnClickedBtnSetupExt()
{
	HTREEITEM hItem = m_treeDeviceList.GetSelectedItem();
	if(hItem == NULL)
	{
		MessageBox("请先选择一台设备");
		return;
	}
	VzLPRClientHandle hLPRClient = m_treeDeviceList.GetItemData(hItem);

	DlgConfigExt dlgCfgExt(hLPRClient, this);

	dlgCfgExt.DoModal();
}

void CVzLPRSDKDemoDlg::OnTvnSelchangedTreeDevice(NMHDR *pNMHDR, LRESULT *pResult)
{
	LPNMTREEVIEW pNMTreeView = reinterpret_cast<LPNMTREEVIEW>(pNMHDR);
	// TODO: Add your control notification handler code here
	*pResult = 0;

	HTREEITEM hItem = m_treeDeviceList.GetSelectedItem();
	if(hItem == NULL)
	{
		MessageBox("请先选择一台设备");
		return;
	}
	VzLPRClientHandle hLPRClient = m_treeDeviceList.GetItemData(hItem);



	HTREEITEM treeItem = pNMTreeView->itemNew.hItem;
	if ( treeItem != NULL )
	{
		VzLPRClientHandle hLPRClient = m_treeDeviceList.GetItemData(treeItem);
		if ( hLPRClient != NULL )
		{
			vector<int>::iterator it = std::find(m_vecRecordHandle.begin(), m_vecRecordHandle.end(), (int)hLPRClient);
			if( it != m_vecRecordHandle.end() )
			{
				m_btnStartRecord.EnableWindow(FALSE);
				m_btnStopRecord.EnableWindow(TRUE);
			}
			else
			{
				m_btnStartRecord.EnableWindow(TRUE);
				m_btnStopRecord.EnableWindow(FALSE);
			}

			int ver1 = 0,ver2 = 0, ver3 = 0,ver4 = 0;
			int retSV = VzLPRClient_GetRemoteSoftWareVersion(hLPRClient,&ver1,&ver2,&ver3,&ver4);
			if (ver1 == 21) {
				EnableOperButton(2);
			}
			else {
				EnableOperButton(1);
			}
		}
	}

	// 更新状态值

	DeviceLPR *pDev = GetCurDeviceInfo( );
	if ( pDev != NULL )
	{
		m_bSetOffline = pDev->GetIsSetOffline( );
		m_bChkOfflineData = pDev->GetIsLoadOfflinePlate( );
		m_bChkOutputRealResult = pDev->GetIsShowRealResult( );

		BOOL bShow = pDev->GetIsShowPlate( );

		BOOL bSave = pDev->GetIsSaveImg( );
		((CButton *)GetDlgItem(IDC_CHK_SAVE_STABLE))->SetCheck(bSave);

		int nSize = pDev->GetImgSize( );
		m_cmbSize.SetCurSel(nSize);

		CComboBox *pCmbIO = (CComboBox *)GetDlgItem(IDC_CMB_OUT_PORT_ID);
		pCmbIO->ResetContent();

		int nIO = pDev->GetCurIO( );
		if( pDev->GetBoardType() == 2 )
		{
			pCmbIO->AddString("开关量 1");
			pCmbIO->AddString("开关量 2");
			pCmbIO->AddString("5V TTL 1");
			pCmbIO->AddString("5V TTL 2");
			pCmbIO->AddString("开关量 3");
		}
		else
		{
			pCmbIO->AddString("开关量 1");
			pCmbIO->AddString("开关量 2");
			pCmbIO->AddString("5V TTL 1");
			pCmbIO->AddString("5V TTL 2");
			pCmbIO->AddString("开关量 3");
			pCmbIO->AddString("开关量 4");
		}

		pCmbIO->SetCurSel(nIO);

		UpdateData(FALSE);
	}

}


bool lessthen (const std::string& ip1, const std::string& ip2)
{
	vector<string> ipVec1;
	CVzString::split(string(ip1), string("."), &ipVec1);

	vector<string> ipVec2;
	CVzString::split(string(ip2), string("."), &ipVec2);

	if (ipVec1.size() == 4 && ipVec2.size() == 4)
	{
		for (int nIndex = 0; nIndex < 4; ++nIndex)
		{
			int nVal1 = atoi(ipVec1[nIndex].c_str());
			int nVal2 = atoi(ipVec2[nIndex].c_str());
			if (nVal1 < nVal2)
				return true;
			else if (nVal1 > nVal2)
				return false;
		}
	}
	return false;
};

LRESULT CVzLPRSDKDemoDlg::OnUserMsg(WPARAM w,LPARAM l)
{
	switch((int)w)
	{
	case(MSG_DEV_FOUND):
		{

			DEVICE_INFO *pDevInfo = (DEVICE_INFO *)l;
			if( pDevInfo != NULL )
			{
				int nIndex = 0;
				//根据IP大小进行排序
				//插入到链表的中间部分
				for (int it = 0; it < m_ListDevice.GetItemCount(); ++it)
				{
					DEVICE_INFO *pCompareItem = (DEVICE_INFO *)m_ListDevice.GetItemData(it);		
					if (lessthen(pDevInfo->szIPAddr, pCompareItem->szIPAddr))
					{
						break;
					}
					++nIndex;
				}	

				m_ListDevice.InsertItem(nIndex, pDevInfo->szIPAddr);
				m_ListDevice.SetItemState(nIndex, LVNI_FOCUSED | LVIS_SELECTED, LVNI_FOCUSED | LVIS_SELECTED);
				m_ListDevice.SetItemData(nIndex, (DWORD_PTR)pDevInfo);

				//默认选中第一个
				m_ListDevice.SetSelectionMark(0);

				// free(pDevInfo);
			}
		}
		break;
	}

	return(0);
}

char* genRandomString(int length)  
{  
	int flag, i;  
	char* string = (char*) malloc(length + 1);
	memset(string, 0, length + 1);
	srand((unsigned) time(NULL ));  

	for (i = 0; i < length - 1; i++)  
	{  
		flag = rand() % 3;  
		switch (flag)  
		{  
		case 0:  
			string[i] = 'A' + rand() % 26;  
			break;  

		case 1:  
			string[i] = 'a' + rand() % 26;  
			break;  

		case 2:  
			string[i] = '0' + rand() % 10;  
			break;  

		default:  
			string[i] = 'x';  
			break;  
		}  
	} 

	string[length - 1] = ' ';  
	return string;  
}  

LRESULT CVzLPRSDKDemoDlg::OnPlateInfoMsg(WPARAM w,LPARAM l)
{
	VzLPRClientHandle handle = (VzLPRClientHandle)w;
	DeviceLPR *pDev = iGetLocalDev(handle);
	if( pDev == NULL )
	{
		return -1;
	}

	VzLPRClient_SetIOOutputAuto(handle, 0, 500);
	VzLPRClient_SetIOOutputAuto(handle, 1, 500);

	// 发送485数据
	if(pDev->nSerialHandle[0] == 0)
	{
		pDev->nSerialHandle[0] = VzLPRClient_SerialStart(handle, 0, NULL, NULL);
		Sleep(200);
	}

	if(pDev->nSerialHandle[1] == 0)
	{
		pDev->nSerialHandle[1] = VzLPRClient_SerialStart(handle, 1, NULL, NULL);
		Sleep(200);
	}

	srand((unsigned) time(NULL ));
	int count = rand()%100 + 1;

	char *string = genRandomString(64);
	VzLPRClient_SerialSend(pDev->nSerialHandle[0], (const unsigned char *)string, strlen(string));
	VzLPRClient_SerialSend(pDev->nSerialHandle[1], (const unsigned char *)string, strlen(string));
	free(string);

	VzLPRClient_SetIOOutputAuto(handle, 0, 500);
	VzLPRClient_SetIOOutputAuto(handle, 1, 500);

	return 0;
}

void CVzLPRSDKDemoDlg::OnBnClickedBtnStartRecord()
{
	HTREEITEM hItem = m_treeDeviceList.GetSelectedItem();
	if(hItem == NULL)
	{
		MessageBox("请先选择一台设备");
		return;
	}
	VzLPRClientHandle hLPRClient = m_treeDeviceList.GetItemData(hItem);

	CString strDevice = m_treeDeviceList.GetItemText(hItem);

	iRecordVideo(hLPRClient, strDevice);

	m_vecRecordHandle.push_back((int)hLPRClient);

	m_btnStartRecord.EnableWindow(FALSE);
	m_btnStopRecord.EnableWindow(TRUE);
}

void CVzLPRSDKDemoDlg::OnBnClickedBtnStopRecord()
{
	HTREEITEM hItem = m_treeDeviceList.GetSelectedItem();
	if(hItem == NULL)
	{
		MessageBox("请先选择一台设备");
		return;
	}
	VzLPRClientHandle hLPRClient = m_treeDeviceList.GetItemData(hItem);

	VzLPRClient_StopSaveRealData(hLPRClient);

	vector<int>::iterator it = std::find(m_vecRecordHandle.begin(), m_vecRecordHandle.end(), (int)hLPRClient);
	if( it != m_vecRecordHandle.end() )
	{
		m_vecRecordHandle.erase(it);
	}

	m_btnStartRecord.EnableWindow(TRUE);
	m_btnStopRecord.EnableWindow(FALSE);
}

// OSD配置功能
void CVzLPRSDKDemoDlg::OnBnClickedBtnOsd()
{
	HTREEITEM hItem = m_treeDeviceList.GetSelectedItem();
	if(hItem == NULL)
	{
		MessageBox("请先选择一台设备");
		return;
	}

	VzLPRClientHandle hLPRClient = m_treeDeviceList.GetItemData(hItem);

	DeviceLPR *pDev = iGetLocalDev(hLPRClient);
	if (pDev == NULL)
	{
		return;
	}

	int verion = 0;
	long long exdataSize = 0;

	VzLPRClient_GetHwBoardVersion(hLPRClient,&verion, &exdataSize);

	CDlgOSDSet dlg;
	dlg.SetLPRHandle(hLPRClient);
	dlg.SetHwType(pDev->GetBoardType(), verion);

	dlg.DoModal();
}

// 车牌查询接口
void CVzLPRSDKDemoDlg::OnBnClickedBtnQuery()
{
	HTREEITEM hItem = m_treeDeviceList.GetSelectedItem();
	if(hItem == NULL)
	{
		MessageBox("请先选择一台设备");
		return;
	}

	VzLPRClientHandle hLPRClient = m_treeDeviceList.GetItemData(hItem);

	CDlgPlateQuery dlg;
	dlg.SetLPRHandle( hLPRClient );
	dlg.DoModal( );
}

void CVzLPRSDKDemoDlg::iReRecordAllVideo( )
{
	char szPath[MAX_PATH] = {0};

	int nSize = m_vecRecordHandle.size( );
	for ( int i = 0; i < nSize; i++ )
	{
		VzLPRClientHandle hLPRClient = (VzLPRClientHandle)m_vecRecordHandle[i];
		if ( hLPRClient != NULL )
		{
			CString strDevice = iGetDeviceItemText(hLPRClient);
			if ( strDevice != "" )
			{
				// 停止录像
				VzLPRClient_StopSaveRealData( hLPRClient );

				// 重新录像
				iRecordVideo(hLPRClient, strDevice);
			}
		}
	}
}

void CVzLPRSDKDemoDlg::iRecordVideo(VzLPRClientHandle hLPRClient, CString strDevice )
{
	COleDateTime dtNow = COleDateTime::GetCurrentTime();

	char strFilePath[MAX_PATH] = {0};
	sprintf_s(strFilePath, MAX_PATH, "%s\\Video", m_strModuleDir );

	if(!PathIsDirectory(strFilePath))
	{
		CreateDirectory(strFilePath, NULL);
	}

	char szPath[MAX_PATH] = {0};
	sprintf_s(szPath, sizeof(szPath), "%s\\%s_%d%02d%02d%02d%02d%02d.avi", strFilePath, strDevice.GetBuffer(0), dtNow.GetYear(), dtNow.GetMonth(), dtNow.GetDay(),
		dtNow.GetHour(), dtNow.GetMinute(),dtNow.GetSecond() );

	VzLPRClient_SaveRealData(hLPRClient, szPath);
}

CString CVzLPRSDKDemoDlg::iGetDeviceItemText( VzLPRClientHandle hLPRClient )
{
	CString strDevice;

	HTREEITEM hItem = m_treeDeviceList.GetRootItem();
	VzLPRClientHandle hCurClient;

	while( hItem != NULL )
	{
		hCurClient = m_treeDeviceList.GetItemData(hItem);
		if( hCurClient == hLPRClient )
		{
			strDevice = m_treeDeviceList.GetItemText(hItem);
			break;
		}

		hItem = m_treeDeviceList.GetNextItem(hItem, TVGN_NEXT);
	}

	return strDevice;
}

void CVzLPRSDKDemoDlg::OnBnClickedChkSetOffline()
{
	// TODO: Add your control notification handler code here
	HTREEITEM hItem = m_treeDeviceList.GetSelectedItem();
	if(hItem == NULL)
	{
		MessageBox("请先选择一台设备");
		return;
	}

	UpdateData(TRUE);

	VzLPRClientHandle hLPRClient = m_treeDeviceList.GetItemData(hItem);

	if( m_bSetOffline )
	{
		VzLPRClient_SetOfflineCheck(hLPRClient);
	}
	else
	{
		VzLPRClient_CancelOfflineCheck(hLPRClient);
	}

	DeviceLPR *pDev = GetCurDeviceInfo( );
	if ( pDev != NULL )
	{
		pDev->SetIsSetOffline( m_bSetOffline );
	}
}

void CVzLPRSDKDemoDlg::iSetCommonInfo(const char *pStrInfo)
{
	if(pStrInfo == NULL)
		return;

	m_nCDCleanCommNotify = 10;
	string sMsg;
	sMsg.append(pStrInfo, strlen(pStrInfo));
	addMsg(sMsg);
}

static const char *iGetIOInStatusString(int nIOIn)
{
	return(nIOIn == 0 ? "闭路" : "开路");
}

void CVzLPRSDKDemoDlg::OnBnClickedBtnGetInput()
{
	HTREEITEM hItem = m_treeDeviceList.GetSelectedItem();
	if(hItem == NULL)
	{
		MessageBox("请先选择一台设备");
		return;
	}

	VzLPRClientHandle hLPRClient = m_treeDeviceList.GetItemData(hItem);


	int nValue0 = -1, nValue1 = -1, nValue2 = -1;

	VzLPRClient_GetGPIOValue(hLPRClient, 0, &nValue0);
	VzLPRClient_GetGPIOValue(hLPRClient, 1, &nValue1);

	DeviceLPR *pDev = iGetLocalDev(hLPRClient);
	if (pDev == NULL)
	{
		return;
	}
	char strInfo[64] = {0};
	if(pDev->GetBoardType() == 1)
	{
		VzLPRClient_GetGPIOValue(hLPRClient, 2, &nValue2);
		sprintf_s(strInfo, 64, "IOInput [%s] [%s] [%s]", iGetIOInStatusString(nValue0)
			, iGetIOInStatusString(nValue1)
			, iGetIOInStatusString(nValue2));
	}
	else if(pDev->GetBoardType() == 2)
	{
		sprintf_s(strInfo, 64, "IOInput [%s] [%s]", iGetIOInStatusString(nValue0)
			, iGetIOInStatusString(nValue1));
	}
	else if(pDev->GetBoardType() == 3)
	{
		sprintf_s(strInfo, 64, "IOInput [%s] [%s]", iGetIOInStatusString(nValue0)
			, iGetIOInStatusString(nValue1));
	}

	iSetCommonInfo(strInfo);
}

void CVzLPRSDKDemoDlg::OnBnClickedChkOfflineData()
{
	HTREEITEM hItem = m_treeDeviceList.GetSelectedItem();
	if(hItem == NULL)
	{
		MessageBox("请先选择一台设备");
		return;
	}

	UpdateData(TRUE);

	VzLPRClientHandle hLPRClient = m_treeDeviceList.GetItemData(hItem);

	if( m_bChkOfflineData )
	{
		VzLPRClient_SetOfflinePlateInfoCallBack(hLPRClient, OnPlateInfo, this, TRUE);
	}
	else
	{
		VzLPRClient_SetOfflinePlateInfoCallBack(hLPRClient, NULL, NULL, FALSE);
	}

	DeviceLPR *pDev = GetCurDeviceInfo( );
	if ( pDev != NULL )
	{
		pDev->SetIsLoadOfflinePlate( m_bChkOfflineData );
	}
}



// 设备恢复默认，升级的功能
void CVzLPRSDKDemoDlg::OnBnClickedBtnUpdate()
{
	HTREEITEM hItem = m_treeDeviceList.GetSelectedItem();
	if(hItem == NULL)
	{
		MessageBox("请先选择一台设备");
		return;
	}

	VzLPRClientHandle hLPRClient = m_treeDeviceList.GetItemData(hItem);

	CDlgMaintain dlg;
	dlg.SetLPRHandle( hLPRClient );
	dlg.DoModal( );
}

void CVzLPRSDKDemoDlg::OnBnClickedBtnOutput()
{
	// TODO: Add your control notification handler code here
	HTREEITEM hItem = m_treeDeviceList.GetSelectedItem();
	if(hItem == NULL)
	{
		MessageBox("请先选择一台设备");
		return;
	}

	VzLPRClientHandle hLPRClient = m_treeDeviceList.GetItemData(hItem);

	int nOPID = ((CComboBox *)GetDlgItem(IDC_CMB_OUT_PORT_ID))->GetCurSel();

	int nDelayReset = GetDlgItemInt(IDC_EDIT_OUT_DELAY);
	if(nDelayReset < 500 || nDelayReset > 5000)
		nDelayReset = 1000;

	if (nOPID >= 4  && nOPID <= 5)
		nOPID -= 2;
	VzLPRClient_SetIOOutputAuto(hLPRClient, nOPID, nDelayReset);
	// int ret = VzLPRClient_SetIOOutputAutoResp(hLPRClient, nOPID, nDelayReset);
	// ret = -1;
}

// 修改网络参数
void CVzLPRSDKDemoDlg::OnBnClickedBtnNetwork()
{
	HTREEITEM hItem = m_treeDeviceList.GetSelectedItem();
	if(hItem == NULL)
	{
		MessageBox("请先选择一台设备");
		return;
	}

	VzLPRClientHandle hLPRClient = m_treeDeviceList.GetItemData(hItem);

	CDlgNetwork dlg;
	dlg.SetLPRHandle( hLPRClient );
	dlg.DoModal( );

}

// 语音的功能
void CVzLPRSDKDemoDlg::OnBnClickedBtnVoice()
{
	HTREEITEM hItem = m_treeDeviceList.GetSelectedItem();
	if(hItem == NULL)
	{
		MessageBox("请先选择一台设备");
		return;
	}
	VzLPRClientHandle hLPRClient = m_treeDeviceList.GetItemData(hItem);

	DlgDeviceInfo dlgDevInfo;
	dlgDevInfo.SetLPRHandle(hLPRClient);
	dlgDevInfo.DoModal();
}

// 修改设备IP的功能
void CVzLPRSDKDemoDlg::OnBnClickedBtnModifyIp()
{
	int nIndex = m_ListDevice.GetSelectionMark();		
	if(nIndex < 0)
	{
		MessageBox("请先选择一台设备", "提示", MB_OK);
		return;
	}

	DEVICE_INFO *pInfo = (DEVICE_INFO*)m_ListDevice.GetItemData(nIndex);
	if ( pInfo != NULL )
	{
		CDlgModifyIP dlg;
		dlg.SetNetParam(pInfo->szIPAddr, pInfo->SL, pInfo->SH, pInfo->netmask, pInfo->gateway);
		dlg.DoModal( );
	}
}

//***********************************************************
// 函数:      OnBnClickedBtnSnapImage
// 全称:  	  CVzLPRSDKDemoDlg::OnBnClickedBtnSnapImage
// 属性:      public 
// 返回:      void
// 说明:      不从视频帧中抓取，通过tcp协议抓图
//***********************************************************
void CVzLPRSDKDemoDlg::OnBnClickedBtnSnapImage()
{
	HTREEITEM hItem = m_treeDeviceList.GetSelectedItem();
	if(hItem == NULL)
	{
		MessageBox("请先选择一台设备");
		return;
	}

	if(m_nIdxWinCurrSelected < 0)
		return;

	OutputWin *pWinOut = m_winOut + m_nIdxWinCurrSelected;

	char strFilePath[MAX_PATH];

	sprintf_s(strFilePath, MAX_PATH, "%s/%s/%s",
		m_strModuleDir, "Cap", "DirectSnap");

	if(!PathIsDirectory(strFilePath))
	{
		CreateDirectory(strFilePath, NULL);
	}

	char strFileName[MAX_PATH];
	CTime time = CTime::GetCurrentTime();
	sprintf_s(strFileName, MAX_PATH, "%s/%02d%02d%02d_%d%02d%02d.jpg",
		strFilePath,
		time.GetYear()%100, time.GetMonth(), time.GetDay(),
		time.GetHour(), time.GetMinute(), time.GetSecond());

	VzLPRClientHandle hLPRClient = m_treeDeviceList.GetItemData(hItem);
	int nRet = VzLPRClient_SaveSnapImageToJpeg(hLPRClient, strFileName);
	const string cSuccess = "截图成功！";
	const string cFaild = "截图失败！";
	string sMsgInfo = nRet == 0 ? cSuccess : cFaild;
	MessageBox(sMsgInfo.c_str(), "提示", MB_OK);
}

void CVzLPRSDKDemoDlg::OnEnChangeEditNotify()
{

}

void CVzLPRSDKDemoDlg::OnBnClickedBtnEncrypt()
{
	HTREEITEM hItem = m_treeDeviceList.GetSelectedItem();
	if(hItem == NULL)
	{
		MessageBox("请先选择一台设备");
		return;
	}

	VzLPRClientHandle hLPRClient = m_treeDeviceList.GetItemData(hItem);

	DeviceLPR *pDev = iGetLocalDev(hLPRClient);
	if(pDev == NULL)
	{
		return;
	}

	CDlgEncrypt dlg;
	dlg.SetDeviceInfo(pDev);
	dlg.SetLPRHandle( hLPRClient );
	dlg.DoModal( );
}

// 组网配置功能
void CVzLPRSDKDemoDlg::OnBnClickedBtnGroupCfg()
{
	HTREEITEM hItem = m_treeDeviceList.GetSelectedItem();
	if(hItem == NULL)
	{
		MessageBox("请先选择一台设备");
		return;
	}

	VzLPRClientHandle hLPRClient = m_treeDeviceList.GetItemData(hItem);

	CString strIP = m_treeDeviceList.GetItemText(hItem);

	CDlgGroupCfg dlg(this);
	dlg.SetLPRHandle( hLPRClient );
	dlg.SetDeviceIP( strIP );
	dlg.DoModal( );
}

LRESULT CVzLPRSDKDemoDlg::OnShowMsg(WPARAM w,LPARAM l)
{
	string* sMsg = (string*) l;
	if (sMsg)
	{
		addMsg(*sMsg);
		delete sMsg;
	}

	return 0;
}

void CVzLPRSDKDemoDlg::OnBnClickedChkOutputRealresult()
{
	HTREEITEM hItem = m_treeDeviceList.GetSelectedItem();
	if(hItem == NULL)
	{
		MessageBox("请先选择一台设备");
		return;
	}

	UpdateData(TRUE);

	VzLPRClientHandle hLPRClient = m_treeDeviceList.GetItemData(hItem);
	if( !m_bChkOutputRealResult )
	{
		VzLPRClient_SetIsOutputRealTimeResult(hLPRClient, true);
	}
	else
	{
		VzLPRClient_SetIsOutputRealTimeResult(hLPRClient, false);
	}

	DeviceLPR *pDev = GetCurDeviceInfo( );
	if ( pDev != NULL )
	{
		pDev->SetIsShowRealResult( m_bChkOutputRealResult );
	}
}
void CVzLPRSDKDemoDlg::OnBnClickedBtnVideoCfg()
{
	HTREEITEM hItem = m_treeDeviceList.GetSelectedItem();
	if(hItem == NULL)
	{
		MessageBox("请先选择一台设备");
		return;
	}

	VzLPRClientHandle hLPRClient = m_treeDeviceList.GetItemData(hItem);

	DeviceLPR *pDev = iGetLocalDev(hLPRClient);
	if (pDev == NULL)
	{
		return;
	}

	if( pDev->GetBoardType() == 3 )
	{
		CDlgRVideoCfg dlg;
		dlg.SetLPRHandle(hLPRClient);
		dlg.DoModal();
	}
	else
	{
		CDlgVideoCfg dlgCfg;
		dlgCfg.SetLPRHandle(hLPRClient);
		dlgCfg.SetBoardType(pDev->GetBoardType());
		dlgCfg.DoModal( );
	}

}
void CVzLPRSDKDemoDlg::OnBnClickedBtnBaseCfg()
{
	// TODO: Add your control notification handler code here
}

void CVzLPRSDKDemoDlg::OnBnClickedBtnOutin()
{
	HTREEITEM hItem = m_treeDeviceList.GetSelectedItem();
	if(hItem == NULL)
	{
		MessageBox("请先选择一台设备");
		return;
	}

	UpdateData(TRUE);
	VzLPRClientHandle hLPRClient = m_treeDeviceList.GetItemData(hItem);

	COutAndIn oOutAndIn;
	oOutAndIn.setHandle(hLPRClient);
	int nRet = oOutAndIn.DoModal();
}
void CVzLPRSDKDemoDlg::OnEnChangeEditIp()
{
	// TODO:  If this is a RICHEDIT control, the control will not
	// send this notification unless you override the CDialog::OnInitDialog()
	// function and call CRichEditCtrl().SetEventMask()
	// with the ENM_CHANGE flag ORed into the mask.

	// TODO:  Add your control notification handler code here
}

void CVzLPRSDKDemoDlg::OnBnClickedButton5()
{
	// TODO: Add your control notification handler code here
	HTREEITEM hItem = m_treeDeviceList.GetSelectedItem();
	if (hItem == NULL)
	{
		MessageBox("请先选择一台设备");
		return;
	}
	VzLPRClientHandle hLPRClient = m_treeDeviceList.GetItemData(hItem);

	DeviceLPR *pDev = iGetLocalDev(hLPRClient);
	if (pDev == NULL)
	{
		return;
	}

	VzDlgSerialParam dlgSerCfg;
	dlgSerCfg.SetBoardType(pDev->GetBoardType());
	dlgSerCfg.SetLPRHandle(hLPRClient);
	dlgSerCfg.DoModal();
}

void CVzLPRSDKDemoDlg::OnNMClickList2(NMHDR *pNMHDR, LRESULT *pResult)
{
	LPNMITEMACTIVATE pNMItemActivate = reinterpret_cast<LPNMITEMACTIVATE>(pNMHDR);
	if (pNMItemActivate->iItem < 0)
		return;

	DEVICE_INFO *pLoginInfo = (DEVICE_INFO *)(m_ListDevice.GetItemData(pNMItemActivate->iItem));
	if( pLoginInfo == NULL )
	{
		return;
	}

	SetDlgItemText(IDC_EDIT_IP, pLoginInfo->szIPAddr);
	char sPort[4];
	SetDlgItemText(IDC_EDIT_PORT, itoa(pLoginInfo->usPort1, sPort, 10));
}

void CVzLPRSDKDemoDlg::OnBnClickedBtnStore()
{
	// TODO: Add your control notification handler code here
	HTREEITEM hItem = m_treeDeviceList.GetSelectedItem();
	if (hItem == NULL)
	{
		MessageBox("请先选择一台设备");
		return;
	}

	VzLPRClientHandle hLPRClient = m_treeDeviceList.GetItemData(hItem);

	CDlgStore dlg;

	dlg.SetLPRHandle(hLPRClient);

	dlg.DoModal();

}

DeviceLPR* CVzLPRSDKDemoDlg::GetCurDeviceInfo( )
{
	HTREEITEM hItem = m_treeDeviceList.GetSelectedItem();
	if(hItem == NULL)
	{
		return NULL;
	}

	VzLPRClientHandle hLPRClient = m_treeDeviceList.GetItemData(hItem);

	DeviceLPR *pDev = iGetLocalDev(hLPRClient);
	return pDev;
}

char* CVzLPRSDKDemoDlg::GetModuleDir( )
{
	return m_strModuleDir;
}

void CVzLPRSDKDemoDlg::OnCbnSelchangeCmbSize()
{
	// TODO: Add your control notification handler code here

	int cmbSize = m_cmbSize.GetCurSel( );

	DeviceLPR *pDev = GetCurDeviceInfo( );
	if ( pDev != NULL && cmbSize >= 0 )
	{
		pDev->SetImgSize( cmbSize );
	}
}

void CVzLPRSDKDemoDlg::OnBnClickedBtnTts()
{
	HTREEITEM hItem = m_treeDeviceList.GetSelectedItem();
	if (hItem == NULL)
	{
		MessageBox("请先选择一台设备");
		return;
	}

	VzLPRClientHandle hLPRClient = m_treeDeviceList.GetItemData(hItem);

	CDlgPlayVoice dlg;
	dlg.SetLPRHandle(hLPRClient);
	dlg.DoModal();
}

void CVzLPRSDKDemoDlg::OnBnClickedBtnActiveStatus()
{
	HTREEITEM hItem = m_treeDeviceList.GetSelectedItem();
	if (hItem == NULL)
	{
		MessageBox("请先选择一台设备");
		return;
	}

	VzLPRClientHandle hLPRClient = m_treeDeviceList.GetItemData(hItem);

	CDlgActiveStatus dlg;
	dlg.SetLPRHandle(hLPRClient);
	dlg.DoModal();
}



void CVzLPRSDKDemoDlg::OnBnClickedBtnSerialData()
{
	HTREEITEM hItem = m_treeDeviceList.GetSelectedItem();
	if (hItem == NULL)
	{
		MessageBox("请先选择一台设备");
		return;
	}

	VzLPRClientHandle hLPRClient = m_treeDeviceList.GetItemData(hItem);

	DeviceLPR *pDev = iGetLocalDev(hLPRClient);
	if (pDev == NULL)
	{
		return;
	}

	DlgSerialData dlg;
	dlg.SetLPRHandle(hLPRClient);
	dlg.SetBoardType(pDev->GetBoardType());
	dlg.DoModal();
}


// Tcp透传的功能
void CVzLPRSDKDemoDlg::OnBnClickedBtnTcpTran()
{
	HTREEITEM hItem = m_treeDeviceList.GetSelectedItem();
	if (hItem == NULL)
	{
		MessageBox("请先选择一台设备");
		return;
	}

	VzLPRClientHandle hLPRClient = m_treeDeviceList.GetItemData(hItem);

	CDlgTcpTran dlg;
	dlg.SetLPRHandle( hLPRClient );
	dlg.DoModal( );
}

void CVzLPRSDKDemoDlg::OnBnClickedBtnDisconnRecord()
{
	// TODO: Add your control notification handler code here
	HTREEITEM hItem = m_treeDeviceList.GetSelectedItem();
	if (hItem == NULL)
	{
		MessageBox("请先选择一台设备");
		return;
	}

	VzLPRClientHandle hLPRClient = m_treeDeviceList.GetItemData(hItem);
}

void CVzLPRSDKDemoDlg::OnBnClickedBtnTalkVoice()
{
	HTREEITEM hItem = m_treeDeviceList.GetSelectedItem();
	if (hItem == NULL)
	{
		MessageBox("请先选择一台设备");
		return;
	}

	VzLPRClientHandle hLPRClient = m_treeDeviceList.GetItemData(hItem);
	
	CDlgTalkClient dlg;
	dlg.InitTreeData(&m_vDev);
	dlg.DoModal();
	
}

void CVzLPRSDKDemoDlg::OnBnClickedBtnUserOsd()
{
	HTREEITEM hItem = m_treeDeviceList.GetSelectedItem();
	if (hItem == NULL)
	{
		MessageBox("请先选择一台设备");
		return;
	}

	VzLPRClientHandle hLPRClient = m_treeDeviceList.GetItemData(hItem);
	CDlgUserOSD dlg;
	dlg.SetLPRHandle(hLPRClient);
	dlg.DoModal();
}

void CVzLPRSDKDemoDlg::OnCbnSelchangeLoginType()
{
	// TODO: Add your control notification handler code here
	if(m_cmbType.GetCurSel() > 0)
	{
		GetDlgItem(IDC_EDIT_DEVICE_SN)->EnableWindow(TRUE);
	}
	else
	{
		GetDlgItem(IDC_EDIT_DEVICE_SN)->EnableWindow(FALSE);
	}
}

void CVzLPRSDKDemoDlg::OnClose()
{
	if (cloud_setup_) {
		VzClient_CloudCleanup();
	}

	VzLPRClient_Cleanup();

	if (m_pBufRGB) {
		delete[] m_pBufRGB;
		m_pBufRGB = NULL;
	}

	CDialog::OnClose();
}

void CVzLPRSDKDemoDlg::OnBnClickedBtnStartTalk()
{
	HTREEITEM hItem = m_treeDeviceList.GetSelectedItem();
	if (hItem == NULL)
	{
		MessageBox("请先选择一台设备");
		return;
	}

	VzLPRClientHandle hLPRClient = m_treeDeviceList.GetItemData(hItem);
	DeviceLPR *pDev = iGetLocalDev(hLPRClient);
	if(pDev == NULL)
	{
		return;
	}
	
	if (pDev->m_nCloudLogin == 1) {
		pDev->m_nTalkHandle = VzClient_CloudStreamTalk(hLPRClient);
	}
	else {
		MessageBox("当前登录方式不支持云平台对讲,请选择云台平扩展登录!");
	}
}

void CVzLPRSDKDemoDlg::OnBnClickedBtnStopTalk()
{
	HTREEITEM hItem = m_treeDeviceList.GetSelectedItem();
	if (hItem == NULL)
	{
		MessageBox("请先选择一台设备");
		return;
	}

	VzLPRClientHandle hLPRClient = m_treeDeviceList.GetItemData(hItem);
	DeviceLPR *pDev = iGetLocalDev(hLPRClient);
	if(pDev == NULL)
	{
		return;
	}

	if (pDev->m_nCloudLogin == 1) {
		VzClient_CloudStopTalk(pDev->m_nTalkHandle);
		pDev->m_nTalkHandle = 0;
	}
	else {
		MessageBox("当前登录方式不支持云平台对讲,请选择云台平扩展登录!");
	}
}


void CVzLPRSDKDemoDlg::OnRecvCommonResult(VzLPRClientHandle handle, int type, const char *pResultInfo, int len, VZ_IMAGE_INFO *imgs, int count) {
	if (pResultInfo == NULL || len <= 0) {
		return;
	}

	DeviceLPR *pDev = iGetLocalDev(handle);
	if(pDev == NULL) {
		return;
	}

	Json::Value value;
	Json::Reader reader;

	if (!reader.parse(pResultInfo, value)) {
		return;
	}

	int event_type = 0;
	std::string time;
	std::string dev_ip;
	std::string license;
	if (value["device_info"]["dev_ip"].isString()) {
		dev_ip = value["device_info"]["dev_ip"].asString();
	}

	int timestamp = 0;
	int result_id = 0;

	if (value["results"].isArray())
	{
		int array_size = value["results"].size();
		if (array_size <= 0) {
			return;
		}

		if (value["results"][0]["event_type"].isInt()) {
			event_type = value["results"][0]["event_type"].asInt();
		}

		if (value["results"][0]["time"].isString()) {
			time = value["results"][0]["time"].asString();
		}

		if (value["results"][0]["plate_result"].isObject() && value["results"][0]["plate_result"]["license"].isString()) {
			std::string base64_license = value["results"][0]["plate_result"]["license"].asString();

			unsigned int len = 0;
			unsigned char* pDecodeStr = base64Decode(base64_license.c_str(), len);
			if (pDecodeStr) {
				char *gbk_license = utf8_to_gb2312_ex((char *)pDecodeStr);
				if ( gbk_license != NULL) {
					license.assign(gbk_license);
					free(gbk_license);
				}

				free(pDecodeStr);
			}
		}

		if (value["results"][0]["timestamp"].isInt()) {
			timestamp = value["results"][0]["timestamp"].asInt();
		}

		if (value["results"][0]["result_id"].isInt()) {
			result_id = value["results"][0]["result_id"].asInt();
		}
	}

	CString event_name = GetEventTypeName(event_type);

	if(imgs[0].buffer != NULL && imgs[0].img_len > 0) {
		if(m_pBufRGB == NULL) {
			m_pBufRGB = new unsigned char[m_uSizeBufRGB];
		}

		OutputWin *pOutWin = iGetStableOutputWinByIdx(pDev->GetWinIdx());
		if(pOutWin)
		{
			CString result_info;
			result_info.Format("%s %s  %s", dev_ip.c_str(), event_name, time.c_str());
			pOutWin->UpdateString(result_info);

			VZ_LPRC_IMAGE_INFO struImage = {0};
			int nRT = VzLPRClient_JpegDecodeToImage(imgs[0].buffer, imgs[0].img_len,
				&struImage, m_pBufRGB, m_uSizeBufRGB);
			if(nRT == VZ_API_SUCCESS) {
				pOutWin->SetFrame(struImage.pBuffer, struImage.uWidth, struImage.uHeight, struImage.uPitch);
			}
		}

		char strFilePath[MAX_PATH] = {0};
		sprintf_s(strFilePath, MAX_PATH, "%s/%s/", m_strModuleDir, "Cap");

		if(!PathIsDirectory(strFilePath)) {
			CreateDirectory(strFilePath, NULL);
		}

		VZ_DEV_SERIAL_NUM struSN = {0};
		int retSN = VzLPRClient_GetSerialNumber(handle,&struSN);
		char strSN[64];
		sprintf_s(strSN,"%08x-%08x",struSN.uHi,struSN.uLo);

		strcat(strFilePath, strSN);

		if(!PathIsDirectory(strFilePath)) {
			CreateDirectory(strFilePath, NULL);
		}

		char strFileName[MAX_PATH] = {0};
		SYSTEMTIME st;
		GetLocalTime(&st);
		if (event_type == 0x90) {
			sprintf_s(strFileName, MAX_PATH, "%s/%d_%d.jpg",
				strFilePath,timestamp, event_type);
		}
		else {
			sprintf_s(strFileName, MAX_PATH, "%s/%s_%s_%02d%02d%02d_%d%02d%02d_%03d.jpg",
				strFilePath,event_name.GetBuffer(0),license.c_str(),
				st.wYear%100, st.wMonth, st.wDay,
				st.wHour, st.wMinute, st.wSecond,st.wMilliseconds);
		}

		WriteFileB(strFileName, imgs[0].buffer, imgs[0].img_len);

		if(imgs[1].buffer != NULL && imgs[1].img_len > 0) {
			memset(strFileName, 0, sizeof(strFileName));
			sprintf_s(strFileName, MAX_PATH, "%s/%s_%s_%02d%02d%02d_%d%02d%02d_%03d_small.jpg",
				strFilePath,event_name.GetBuffer(0),license.c_str(),
				st.wYear%100, st.wMonth, st.wDay,
				st.wHour, st.wMinute, st.wSecond,st.wMilliseconds);

			WriteFileB(strFileName, imgs[1].buffer, imgs[1].img_len);
		}
	}
	else {
		OutputWin *pOutWin = iGetStableOutputWinByIdx(pDev->GetWinIdx());
		if(pOutWin) {
			pOutWin->ShowString();
		}
	}
}

void CVzLPRSDKDemoDlg::OnBnClickedBtnCloudVod()
{
	HTREEITEM hItem = m_treeDeviceList.GetSelectedItem();
	if (hItem == NULL)
	{
		MessageBox("请先选择一台设备");
		return;
	}

	VzLPRClientHandle hLPRClient = m_treeDeviceList.GetItemData(hItem);

	CDlgStreamCloud dlg;
	dlg.SetLPRHandle(hLPRClient);
	dlg.DoModal();
}

void CVzLPRSDKDemoDlg::OnBnClickedBtnDevMate()
{
	HTREEITEM hItem = m_treeDeviceList.GetSelectedItem();
	if (hItem == NULL)
	{
		MessageBox("请先选择一台设备");
		return;
	}

	VzLPRClientHandle hLPRClient = m_treeDeviceList.GetItemData(hItem);

	DlgDevMate dlg;
	dlg.lpr_handle_ = hLPRClient;
	dlg.DoModal();
}

void CVzLPRSDKDemoDlg::EnableOperButton(int type) {

	if(type == 1) {
		GetDlgItem(IDC_BTN_START_TALK)->EnableWindow(FALSE);
		GetDlgItem(IDC_BTN_STOP_TALK)->EnableWindow(FALSE);
		GetDlgItem(IDC_BTN_CLOUD_VOD)->EnableWindow(FALSE);

		GetDlgItem(IDC_BTN_CONFIG_WLIST)->EnableWindow(TRUE);
		GetDlgItem(IDC_BTN_QUERY)->EnableWindow(TRUE);
		GetDlgItem(IDC_BTN_SET_VLOOP)->EnableWindow(TRUE);
		GetDlgItem(IDC_BTN_ENCRYPT)->EnableWindow(TRUE);
		GetDlgItem(IDC_BTN_SETUP_EXT)->EnableWindow(TRUE);
		GetDlgItem(IDC_BUTTON5)->EnableWindow(TRUE);
		GetDlgItem(IDC_BTN_SERIAL_DATA)->EnableWindow(TRUE);
		GetDlgItem(IDC_BTN_OutIn)->EnableWindow(TRUE);
		GetDlgItem(IDC_BTN_STORE)->EnableWindow(TRUE);
		GetDlgItem(IDC_BTN_ACTIVE_STATUS)->EnableWindow(TRUE);
		GetDlgItem(IDC_BTN_GET_INPUT)->EnableWindow(TRUE);
		GetDlgItem(IDC_BTN_OUTPUT)->EnableWindow(TRUE);

		GetDlgItem(IDC_CHK_SET_OFFLINE)->EnableWindow(TRUE);
		GetDlgItem(IDC_CHK_OFFLINE_DATA)->EnableWindow(TRUE);
		GetDlgItem(IDC_CHK_OUT1)->EnableWindow(TRUE);
		GetDlgItem(IDC_BTN_DEV_MATE)->EnableWindow(TRUE);
		GetDlgItem(IDC_BTN_SW_TRIGGER)->EnableWindow(TRUE);
	}
	else if(type == 2) {
		GetDlgItem(IDC_BTN_START_TALK)->EnableWindow(TRUE);
		GetDlgItem(IDC_BTN_STOP_TALK)->EnableWindow(TRUE);
		GetDlgItem(IDC_BTN_CLOUD_VOD)->EnableWindow(TRUE);

		GetDlgItem(IDC_BTN_CONFIG_WLIST)->EnableWindow(FALSE);
		GetDlgItem(IDC_BTN_QUERY)->EnableWindow(FALSE);
		GetDlgItem(IDC_BTN_SET_VLOOP)->EnableWindow(FALSE);
		GetDlgItem(IDC_BTN_ENCRYPT)->EnableWindow(FALSE);
		GetDlgItem(IDC_BTN_SETUP_EXT)->EnableWindow(FALSE);
		GetDlgItem(IDC_BUTTON5)->EnableWindow(FALSE);
		GetDlgItem(IDC_BTN_SERIAL_DATA)->EnableWindow(FALSE);
		GetDlgItem(IDC_BTN_OutIn)->EnableWindow(FALSE);
		GetDlgItem(IDC_BTN_STORE)->EnableWindow(FALSE);
		GetDlgItem(IDC_BTN_ACTIVE_STATUS)->EnableWindow(FALSE);
		GetDlgItem(IDC_BTN_GET_INPUT)->EnableWindow(FALSE);
		GetDlgItem(IDC_BTN_OUTPUT)->EnableWindow(FALSE);

		GetDlgItem(IDC_CHK_SET_OFFLINE)->EnableWindow(FALSE);
		GetDlgItem(IDC_CHK_OFFLINE_DATA)->EnableWindow(FALSE);
		GetDlgItem(IDC_CHK_OUT1)->EnableWindow(FALSE);
		GetDlgItem(IDC_BTN_DEV_MATE)->EnableWindow(FALSE);
		GetDlgItem(IDC_BTN_SW_TRIGGER)->EnableWindow(FALSE);
	}
}