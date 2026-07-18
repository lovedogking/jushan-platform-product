// DlgConfigRules.cpp : implementation file
//

#include "stdafx.h"
#include "VzLPRSDKDemo.h"
#include "DlgConfigRules.h"
#include <_ivzDraw.h>
#include <assert.h>
#include <atltime.h>
#include <string>
#include <vector>
using namespace std;

const int MIN_GAP_SELECT = 20;

#define ID_TIMER_DATE   100002

static void iDrawLoop(unsigned char *pFrame, unsigned uW, unsigned uH, unsigned uP,
					  const int px[], const int py[], int nNumP, const unsigned char color[3]);

static void iDrawRect(unsigned char *pFrame, unsigned uW, unsigned uH, unsigned uP,
					  int x, int y, int w, int h, const unsigned char color[]);

static bool PointInPolygon(int x, int y, const int px[], const int py[], int nNumP);
static void iGetCenter(int xy[2], const int px[], const int py[], int nNumP);
static int iDist_2(int x1, int y1, int x2, int y2);
typedef enum
{
	_COLOR_WHITE,
	_COLOR_RED,
	_COLOR_GREEN,
	_COLOR_ORANGE,
	_COLOR_YELLOW,
	_COLOR_BLUE,
	_COLOR_PURPLE,
	_COLOR_CYAN,
}
_COLOR_TYPE;

static const unsigned char *iGetColor(_COLOR_TYPE eColorType, int nPF);

// DlgConfigRules dialog
void __stdcall iOnVideoFrame(VzLPRClientHandle handle, void *pUserData,
							 const VZ_LPRC_IMAGE_INFO *pFrame)
{
	DlgConfigRules *pInstance = (DlgConfigRules *)pUserData;
	pInstance->iOnVideoFrame1(pFrame);
}

void DlgConfigRules::iOnVideoFrame1(const VZ_LPRC_IMAGE_INFO *pFrame)
{
	if(m_bEnableShow == false)
		return;

	if(m_eSelObjType == SEL_VL)
		iCtrl(m_struVLoops);
	else if(m_eSelObjType == SEL_ROI)
		iCtrl(m_struROI);

	int nIdxVLoopHover = m_nIdxVLoopHover;
	int nIdxVLoopSelect = m_nIdxVLoopSelect;
	if(m_eSelObjType != SEL_VL) 
	{
		nIdxVLoopHover = -1;
		nIdxVLoopSelect = -1;
	}
	iDrawLoops(pFrame->pBuffer, pFrame->uWidth, pFrame->uHeight, pFrame->uPitch, pFrame->uPixFmt,
		m_struVLoops, nIdxVLoopHover, nIdxVLoopSelect, m_nIdxPointHover);

	if(m_bHaveROI)
	{
		bool bROIHover = m_bROIHover;
		bool bROISelect = m_bROISelect;
		if(m_eSelObjType != SEL_ROI)
		{
			bROIHover = false;
			bROISelect = false;
		}
		iDrawROI(pFrame->pBuffer, pFrame->uWidth, pFrame->uHeight, pFrame->uPitch, pFrame->uPixFmt, 
			m_struROI, bROIHover, bROISelect, m_nIdxVtxROIHover);
	}
}

void CALLBACK iOnVieweMouse(IV_EVENT eEvent, int x, int y, void *pUserData, int nId)
{
	DlgConfigRules *pInstance = (DlgConfigRules *)pUserData;
	pInstance->iOnVieweMouse1(eEvent, x, y, nId);
}

void DlgConfigRules::iOnVieweMouse1(IV_EVENT eEvent, int x, int y, int nId)
{
	int X_1000 = x*1000/m_uWinWidth;
	int Y_1000 = y*1000/m_uWinHeight;

	m_struIVInfo.eEvent = eEvent;
	m_struIVInfo.x = X_1000;
	m_struIVInfo.y = Y_1000;
	if(eEvent == IV_EVENT_L_BTN_DOWN)
		m_struIVInfo.m_bBtnDown = true;
	else if(eEvent == IV_EVENT_L_BTN_UP)
		m_struIVInfo.m_bBtnUp = true;
}

IMPLEMENT_DYNAMIC(DlgConfigRules, CDialog)

DlgConfigRules::DlgConfigRules(VzLPRClientHandle handle, CWnd* pParent /*=NULL*/)
: CDialog(DlgConfigRules::IDD, pParent)
, m_hLPRC(handle), m_bEnableShow(true)
, m_bHaveROI(false)
, m_nIdxVLoopHover(-1), m_nIdxVLoopSelect(-1)
, m_nIdxPointHover(-1)
, m_bActiveUpdate(false), m_bActiveROIUpdate(false)
, m_bROIHover(false), m_bROISelect(false)
, m_nIdxVtxROIHover(-1)
, m_nHWType(0)
{
	memset(&m_struVLoops, 0, sizeof(VZ_LPRC_VIRTUAL_LOOPS_EX));
	memset(&m_struVLoopsBackup, 0, sizeof(VZ_LPRC_VIRTUAL_LOOPS_EX));
	memset(&m_struIVInfo, 0, sizeof(_IV_INFO));
	memset(&m_struIVInfoLocal, 0, sizeof(_IV_INFO));

	memset(&m_struROI, 0, sizeof(VZ_LPRC_ROI_EX));
	memset(&m_struROIBackup, 0, sizeof(VZ_LPRC_ROI_EX));
	memset(&m_struROIBackupInModify, 0, sizeof(VZ_LPRC_ROI_EX));

	m_timeControlFoucs = false;
	m_nDeviceType = 0;
}

DlgConfigRules::~DlgConfigRules()
{
}

void DlgConfigRules::DoDataExchange(CDataExchange* pDX)
{
	CDialog::DoDataExchange(pDX);
	DDX_Control(pDX, IDC_STATIC_SHOW, m_struInterV);
	DDX_Control(pDX, IDC_DATETIMEPICKER_DAY, m_DTPDevDay);
	DDX_Control(pDX, IDC_DATETIMEPICKER_TIME, m_dtpDevTime);
	DDX_Control(pDX, IDC_CMB_LIGHT_CTRL1, m_cbxLightCtrl1);
	DDX_Control(pDX, IDC_CMB_LIGHT_CTRL2, m_cbxLightCtrl2);
	DDX_Control(pDX, IDC_CMB_LIGHT_CTRL3, m_cbxLightCtrl3);
	DDX_Control(pDX, IDC_DT_TIME1_START, m_dtTimeStart1);
	DDX_Control(pDX, IDC_DT_TIME1_END, m_dtTimeEnd1);
	DDX_Control(pDX, IDC_DT_TIME2_START, m_dtTimeStart2);
	DDX_Control(pDX, IDC_DT_TIME2_END, m_dtTimeEnd2);
	DDX_Control(pDX, IDC_DT_TIME3_START, m_dtTimeStart3);
	DDX_Control(pDX, IDC_DT_TIME3_END, m_dtTimeEnd3);
}


BEGIN_MESSAGE_MAP(DlgConfigRules, CDialog)
	ON_BN_CLICKED(IDC_BTN_ADD_VLOOP, &DlgConfigRules::OnBnClickedBtnAddVloop)
	ON_EN_CHANGE(IDC_EDIT_RULE_NAME, &DlgConfigRules::OnEnChangeEditRuleName)
	ON_BN_CLICKED(IDC_BTN_RMV_VLOOP, &DlgConfigRules::OnBnClickedBtnRmvVloop)
	ON_BN_CLICKED(IDC_CHK_RULE_ENABLE, &DlgConfigRules::OnBnClickedChkRuleEnable)
	ON_BN_CLICKED(IDC_CHK_RULE_SHOW, &DlgConfigRules::OnBnClickedChkRuleShow)
	ON_BN_CLICKED(IDC_BTN_RULE_SAVE, &DlgConfigRules::OnBnClickedBtnRuleSave)
	ON_BN_CLICKED(IDC_BTN_RULE_CANCEL, &DlgConfigRules::OnBnClickedBtnRuleCancel)
	ON_BN_CLICKED(IDC_BTN_RESET_ALL, &DlgConfigRules::OnBnClickedBtnResetAll)
	ON_CBN_SELCHANGE(IDC_CMB_PROV, &DlgConfigRules::OnCbnSelchangeCmbProv)
	ON_EN_CHANGE(IDC_EDIT_TRIG_GAP, &DlgConfigRules::OnEnChangeEditTrigGap)
	ON_CBN_SELCHANGE(IDC_CMB_PASS_DIR, &DlgConfigRules::OnCbnSelchangeCmbPassDir)
	ON_EN_CHANGE(IDC_EDIT_LP_WIDTH_MIN, &DlgConfigRules::OnEnChangeEditLpWidthMin)
	ON_EN_CHANGE(IDC_EDIT_LP_WIDTH_MAX, &DlgConfigRules::OnEnChangeEditLpWidthMax)
	ON_BN_CLICKED(IDC_RADIO_SELECT_VL, &DlgConfigRules::OnBnClickedRadioSelectVl)
	ON_BN_CLICKED(IDC_RADIO_SELECT_ROI, &DlgConfigRules::OnBnClickedRadioSelectRoi)
	ON_BN_CLICKED(IDC_BTN_ADD_ROI, &DlgConfigRules::OnBnClickedBtnAddRoi)
	ON_BN_CLICKED(IDC_BTN_RMV_ROI, &DlgConfigRules::OnBnClickedBtnRmvRoi)
	ON_BN_CLICKED(IDC_CHK_ROI_ENABLE, &DlgConfigRules::OnBnClickedChkRoiEnable)
	ON_BN_CLICKED(IDC_CHK_ROI_SHOW, &DlgConfigRules::OnBnClickedChkRoiShow)
	ON_BN_CLICKED(IDC_BTN_AUTO_FOUCS, &DlgConfigRules::OnBnClickedBtnAutoFoucs)
	
	ON_BN_CLICKED(IDC_RAD_LED_AUTO, &DlgConfigRules::OnBnClickedRadLedAuto)
	ON_BN_CLICKED(IDC_RAD_LED_ON, &DlgConfigRules::OnBnClickedRadLedOn)
	ON_BN_CLICKED(IDC_RAD_LED_OFF, &DlgConfigRules::OnBnClickedRadLedOff)
	ON_BN_CLICKED(IDC_BTN_CONFIG_DATA_TIME, &DlgConfigRules::OnBnClickedBtnConfigDataTime)
	ON_WM_TIMER()
	ON_NOTIFY(NM_SETFOCUS, IDC_DATETIMEPICKER_DAY, &DlgConfigRules::OnNMSetfocusDatetimepickerDay)
	ON_NOTIFY(NM_KILLFOCUS, IDC_DATETIMEPICKER_DAY, &DlgConfigRules::OnNMKillfocusDatetimepickerDay)
	ON_NOTIFY(NM_KILLFOCUS, IDC_DATETIMEPICKER_TIME, &DlgConfigRules::OnNMKillfocusDatetimepickerTime)
	ON_NOTIFY(NM_SETFOCUS, IDC_DATETIMEPICKER_TIME, &DlgConfigRules::OnNMSetfocusDatetimepickerTime)
	ON_BN_CLICKED(IDC_BTN_FAST_FOCUS, &DlgConfigRules::OnBnClickedBtnFastFocus)
	ON_CBN_SELCHANGE(IDC_CMB_LIGHT_LEVEL, &DlgConfigRules::OnCbnSelchangeCmbLightLevel)
	ON_BN_CLICKED(IDC_BTN_SAVE_LED_PARAM, &DlgConfigRules::OnBnClickedBtnSaveLedParam)
	ON_NOTIFY(DTN_DATETIMECHANGE, IDC_DT_TIME1_END, &DlgConfigRules::OnDtnDatetimechangeDtTime1End)
	ON_NOTIFY(DTN_DATETIMECHANGE, IDC_DT_TIME2_START, &DlgConfigRules::OnDtnDatetimechangeDtTime2Start)
	ON_NOTIFY(DTN_DATETIMECHANGE, IDC_DT_TIME2_END, &DlgConfigRules::OnDtnDatetimechangeDtTime2End)
	ON_NOTIFY(DTN_DATETIMECHANGE, IDC_DT_TIME3_START, &DlgConfigRules::OnDtnDatetimechangeDtTime3Start)
	ON_WM_CLOSE()
END_MESSAGE_MAP()


// DlgConfigRules message handlers

BOOL DlgConfigRules::OnInitDialog()
{
	CDialog::OnInitDialog();

	((CButton *)GetDlgItem(IDC_RADIO_SELECT_VL))->SetCheck(TRUE);

	m_struInterV.SetInteractCallback(iOnVieweMouse, this);
	m_struInterV.SetInUse(true);
	HWND hWnd = m_struInterV.GetSafeHwnd();

	m_nPlayHandle = VzLPRClient_StartRealPlayFrameCallBack(m_hLPRC, hWnd, iOnVideoFrame, this);
	if(m_nPlayHandle == -1)
	{
		MessageBox("Failed to Open Video");
	}

	RECT rectShow;
	m_struInterV.GetWindowRect(&rectShow);
	m_uWinWidth = rectShow.right - rectShow.left;
	m_uWinHeight = rectShow.bottom - rectShow.top;

	//获取支持的省份
	CComboBox *pCmb = (CComboBox *)GetDlgItem(IDC_CMB_PROV);
	pCmb->AddString("无");
	VZ_LPRC_PROVINCE_INFO struProvInfo;
	int rt = VzLPRClient_GetSupportedProvinces(m_hLPRC, &struProvInfo);
	if(rt == E_SUCCESS)
	{
		char strProv0[3] = {0};
		int nProvNum = strlen(struProvInfo.strProvinces)>>1;
		for(int i=0; i<nProvNum; i++)
		{
			memcpy(strProv0, struProvInfo.strProvinces + (i<<1), 2);
			pCmb->AddString(strProv0);
		}
		if(struProvInfo.nCurrIndex >= 0 && struProvInfo.nCurrIndex < nProvNum)
			pCmb->SetCurSel(struProvInfo.nCurrIndex+1);
		else
			pCmb->SetCurSel(0);
	}

	pCmb = (CComboBox *)GetDlgItem(IDC_CMB_PASS_DIR);
	pCmb->AddString("双向");
	pCmb->AddString("由上至下");
	pCmb->AddString("由下至上");
	pCmb->SetCurSel(-1);

	OnBnClickedBtnResetAll();


	//获取视频宽度和高度
	int v_width = 0,v_highth = 0;
    int retV = -1;
	if (m_nHWType == 1)
	{
		retV = VzLPRClient_GetVideoFrameSize(m_hLPRC,&v_width,&v_highth);

	}
	else if (m_nHWType == 2 || m_nHWType == 3)
	{
		int nSizeVal = 0;
		retV = VzLPRClient_GetVideoFrameSizeIndexEx(m_hLPRC, &nSizeVal);
		if( retV == 0 )
		{
			if(  nSizeVal == 23068960)
			{
				v_width = 352;
				v_highth = 288;
			}
			else if( nSizeVal == 41943400 ) 
			{
				v_width = 640;
				v_highth = 360;
			}
			else if( nSizeVal == 47186496 )
			{
				v_width = 720;
				v_highth = 576;
			}
			else if( nSizeVal == 46137920 )
			{
				v_width = 704;
				v_highth = 576;
			}
			else if(nSizeVal == 83886800)
			{
				v_width = 1280;
				v_highth = 720;
			}
			else if (nSizeVal == 125830200)
			{
				v_width = 1920;
				v_highth = 1080;
			}
		}
	}
	char strVS[64] = " ";
	if (retV == 0)
	{
		sprintf_s(strVS,"%d",v_width);
		SetDlgItemText(IDC_STATIC_VEDIO_HEIGHT,strVS);

		sprintf_s(strVS,"%d",v_highth);
		SetDlgItemText(IDC_STATIC_VEDIO_WIDTH,strVS);
	}

	//初始化日期和时间
	int retDTI = VzLPRClient_GetDateTime(m_hLPRC,&m_oDateTimeInfo);
	if (retDTI == 0)
	{
		COleDateTime dt(m_oDateTimeInfo.uYear,m_oDateTimeInfo.uMonth,m_oDateTimeInfo.uMDay,\
			m_oDateTimeInfo.uHour,m_oDateTimeInfo.uMin,m_oDateTimeInfo.uSec);

		m_DTPDevDay.SetTime(dt);
		m_dtpDevTime.SetTime(dt);

		SetTimer(ID_TIMER_DATE,1000,0);

	}

	if(m_nHWType == 2 || m_nHWType == 3)
	{
		CComboBox *pCmbLevel = (CComboBox *)GetDlgItem(IDC_CMB_LIGHT_LEVEL);
		pCmbLevel->ShowWindow(SW_HIDE);

		LoadLedCtrlParam( );
	}
	else
	{	
		Load3730LedCtrlParam( );
		ShowTimeCtrls(FALSE);
	}

	COleDateTime dtTime = COleDateTime::GetCurrentTime();
	
	dtTime.SetTime(0,0,0);
	m_dtTimeStart1.SetTime(dtTime);

	dtTime.SetTime(8,0,0);
	m_dtTimeEnd1.SetTime(dtTime);
	m_dtTimeStart2.SetTime(dtTime);

	dtTime.SetTime(19,0,0);
	m_dtTimeEnd2.SetTime(dtTime);
	m_dtTimeStart3.SetTime(dtTime);

	dtTime.SetTime(23,59,59);
	m_dtTimeEnd3.SetTime(dtTime);

	return TRUE;  
}

// 加载控制参数
void DlgConfigRules::LoadLedCtrlParam()
{
	VZ_LPRC_CTRL_PARAM led_ctrl_param = { 0 };
	int ret = VzLPRClient_GetLedCtrl(m_hLPRC, &led_ctrl_param);

	VzLPRClient_GetCameraConfig(m_hLPRC, VZ_GET_LPR_DEVICE_TYPE, 0, &m_nDeviceType, sizeof(int));
	if (m_nDeviceType == 1)
	{
		VZ_LPRC_LED_PROP led_prop = { 0 };
		ret = VzLPRClient_GetCameraConfig(m_hLPRC, VZ_GET_LED_PROP, 0, &led_prop, sizeof(VZ_LPRC_LED_PROP));
		if (ret == 0)
		{
			for (int i = 0; i < VZ_LPRC_MAX_LED_LEVEL; i++)
			{
				if (strlen(led_prop.level_items[i].content) > 0)
				{
					m_cbxLightCtrl1.AddString(led_prop.level_items[i].content);
					m_cbxLightCtrl2.AddString(led_prop.level_items[i].content);
					m_cbxLightCtrl3.AddString(led_prop.level_items[i].content);
				}
			}
		}
	}
	
	if(ret != 0)
	{
		m_cbxLightCtrl1.AddString("关闭");
		m_cbxLightCtrl1.AddString("2米");
		m_cbxLightCtrl1.AddString("4米");
		m_cbxLightCtrl1.AddString("6米");
		m_cbxLightCtrl1.AddString("8米");

		m_cbxLightCtrl2.AddString("关闭");
		m_cbxLightCtrl2.AddString("2米");
		m_cbxLightCtrl2.AddString("4米");
		m_cbxLightCtrl2.AddString("6米");
		m_cbxLightCtrl2.AddString("8米");

		m_cbxLightCtrl3.AddString("关闭");
		m_cbxLightCtrl3.AddString("2米");
		m_cbxLightCtrl3.AddString("4米");
		m_cbxLightCtrl3.AddString("6米");
		m_cbxLightCtrl3.AddString("8米");
	}

	m_cbxLightCtrl1.SetCurSel(0);
	m_cbxLightCtrl2.SetCurSel(0);
	m_cbxLightCtrl3.SetCurSel(0);

	// 手动模式
	if (led_ctrl_param.led_mode == 3 || led_ctrl_param.led_mode == 1)
	{
		LoadLedCtrlTimeParam(led_ctrl_param);

		((CButton *)GetDlgItem(IDC_RAD_LED_ON))->SetCheck(TRUE);
	}
	else if (led_ctrl_param.led_mode == 2)
	{
		((CButton *)GetDlgItem(IDC_RAD_LED_OFF))->SetCheck(TRUE);
		ShowTimeCtrls(FALSE);
	}
	else
	{
		((CButton *)GetDlgItem(IDC_RAD_LED_AUTO))->SetCheck(TRUE);
		ShowTimeCtrls(FALSE);
	}
	
}

void DlgConfigRules::Load3730LedCtrlParam()
{
	if(m_hLPRC)
	{
		//===LED模式控制===
		VZ_LED_CTRL eLEDCtrl = VZ_LED_AUTO;
		int nRT = VzLPRClient_GetLEDLightControlMode(m_hLPRC, &eLEDCtrl);
		if(nRT == 0)
		{
			int nRadBtn = IDC_RAD_LED_AUTO;
			if(eLEDCtrl == VZ_LED_MANUAL_ON)
				nRadBtn = IDC_RAD_LED_ON;
			else if(eLEDCtrl == VZ_LED_MANUAL_OFF)
				nRadBtn = IDC_RAD_LED_OFF;

			((CButton *)GetDlgItem(nRadBtn))->SetCheck(TRUE);
		}

		//===LED亮度===
		int nLevelNow = 0, nLevelMax = 1;
		nRT = VzLPRClient_GetLEDLightStatus(m_hLPRC, &nLevelNow, &nLevelMax);
		if(nRT == 0)
		{
			m_nLastLEDLevel = nLevelNow;

			CComboBox *pCmbLevel = (CComboBox *)GetDlgItem(IDC_CMB_LIGHT_LEVEL);

			int cmd_index = 0;

			if( m_nHWType == 3 )
			{
				for( int i = 2; i <=  nLevelMax * 2 + 2; i += 2 )
				{
					char light_level[32] = {0};
					sprintf_s(light_level, sizeof(light_level), "%d米", i);

					pCmbLevel->AddString(light_level);
					pCmbLevel->SetItemData(cmd_index, (DWORD_PTR)i);

					if( nLevelNow == i )
					{
						pCmbLevel->SetCurSel(cmd_index);
					}

					cmd_index++;
				}
			}
			else
			{
				for( int i = 2; i <=  nLevelMax + 2; i++ )
				{
					char light_level[32] = {0};
					sprintf_s(light_level, sizeof(light_level), "%d米", i);

					pCmbLevel->AddString(light_level);
					pCmbLevel->SetItemData(cmd_index, (DWORD_PTR)i);

					if( (nLevelNow+2) == i )
					{
						pCmbLevel->SetCurSel(cmd_index);
					}

					cmd_index++;
				}
			}
		}
	}
}

void DlgConfigRules::ShowTimeCtrls(BOOL bShow)
{
	if( bShow )
	{
		GetDlgItem(IDC_DT_TIME1_START)->ShowWindow(SW_SHOW);
		GetDlgItem(IDC_DT_TIME1_END)->ShowWindow(SW_SHOW);
		GetDlgItem(IDC_DT_TIME2_START)->ShowWindow(SW_SHOW);
		GetDlgItem(IDC_DT_TIME2_END)->ShowWindow(SW_SHOW);
		GetDlgItem(IDC_DT_TIME3_START)->ShowWindow(SW_SHOW);
		GetDlgItem(IDC_DT_TIME3_END)->ShowWindow(SW_SHOW);

		GetDlgItem(IDC_CMB_LIGHT_CTRL1)->ShowWindow(SW_SHOW);
		GetDlgItem(IDC_CMB_LIGHT_CTRL2)->ShowWindow(SW_SHOW);
		GetDlgItem(IDC_CMB_LIGHT_CTRL3)->ShowWindow(SW_SHOW);

		GetDlgItem(IDC_BTN_SAVE_LED_PARAM)->ShowWindow(SW_SHOW);
	}
	else
	{
		GetDlgItem(IDC_DT_TIME1_START)->ShowWindow(SW_HIDE);
		GetDlgItem(IDC_DT_TIME1_END)->ShowWindow(SW_HIDE);
		GetDlgItem(IDC_DT_TIME2_START)->ShowWindow(SW_HIDE);
		GetDlgItem(IDC_DT_TIME2_END)->ShowWindow(SW_HIDE);
		GetDlgItem(IDC_DT_TIME3_START)->ShowWindow(SW_HIDE);
		GetDlgItem(IDC_DT_TIME3_END)->ShowWindow(SW_HIDE);

		GetDlgItem(IDC_CMB_LIGHT_CTRL1)->ShowWindow(SW_HIDE);
		GetDlgItem(IDC_CMB_LIGHT_CTRL2)->ShowWindow(SW_HIDE);
		GetDlgItem(IDC_CMB_LIGHT_CTRL3)->ShowWindow(SW_HIDE);

		GetDlgItem(IDC_BTN_SAVE_LED_PARAM)->ShowWindow(SW_HIDE);
	}
}

void DlgConfigRules::LoadLedCtrlTimeParam(VZ_LPRC_CTRL_PARAM& led_ctrl_param)
{
	// 时间段一
	int time_hour_end1 = 0;
	int time_minute_end1 = 0;
	ParseCtrlTime(led_ctrl_param.time_ctrl[0].time_end, time_hour_end1, time_minute_end1);

	COleDateTime dtTime = COleDateTime::GetCurrentTime();

	// 显示时间段1
	if (time_hour_end1 > 0 || time_minute_end1 > 0)
	{
		dtTime.SetTime(0,0,0);
		m_dtTimeStart1.SetTime(dtTime);

		dtTime.SetTime(time_hour_end1,time_minute_end1,0);
		m_dtTimeEnd1.SetTime(dtTime);

		if (led_ctrl_param.time_ctrl[0].led_level >= 0) {
			m_cbxLightCtrl1.SetCurSel(led_ctrl_param.time_ctrl[0].led_level + 1);
		}
		else {
			m_cbxLightCtrl1.SetCurSel(0);
		}
	}

	// 时间段二
	int time_hour_begin2 = 0;
	int time_minute_begin2 = 0;
	ParseCtrlTime(led_ctrl_param.time_ctrl[1].time_begin, time_hour_begin2, time_minute_begin2);

	int time_hour_end2 = 0;
	int time_minute_end2 = 0;
	ParseCtrlTime(led_ctrl_param.time_ctrl[1].time_end, time_hour_end2, time_minute_end2);
	if (time_hour_end2 > 0 || time_minute_end2 > 0)
	{
		dtTime.SetTime(time_hour_begin2, time_minute_begin2, 0);
		m_dtTimeStart2.SetTime(dtTime);

		dtTime.SetTime(time_hour_end2, time_minute_end2, 0);
		m_dtTimeEnd2.SetTime(dtTime);

		if (led_ctrl_param.time_ctrl[1].led_level >= 0) {
			m_cbxLightCtrl2.SetCurSel(led_ctrl_param.time_ctrl[1].led_level + 1);
		}
		else {
			m_cbxLightCtrl2.SetCurSel(0);
		}
	}

	// 时间段三
	int time_hour_begin3 = 0;
	int time_minute_begin3 = 0;
	ParseCtrlTime(led_ctrl_param.time_ctrl[2].time_begin, time_hour_begin3, time_minute_begin3);
	if (time_hour_begin3 > 0 || time_minute_begin3 > 0)
	{
		dtTime.SetTime(time_hour_begin3, time_minute_begin3, 0);
		m_dtTimeStart3.SetTime(dtTime);

		dtTime.SetTime(23, 59, 59);
		m_dtTimeEnd3.SetTime(dtTime);

		if (led_ctrl_param.time_ctrl[2].led_level >= 0) {
			m_cbxLightCtrl3.SetCurSel(led_ctrl_param.time_ctrl[2].led_level+ 1);
		}
		else {
			m_cbxLightCtrl3.SetCurSel(0);
		}
	}
}

void split(const string& src, const string& delim, vector<string>& ret)
{
	size_t last = 0;
	size_t index = src.find_first_of(delim, last);
	while (index != std::string::npos)
	{
		ret.push_back(src.substr(last, index - last));
		last = index + 1;
		index = src.find_first_of(delim, last);
	}
	if (index - last>0)
	{
		ret.push_back(src.substr(last, index - last));
	}
}

void DlgConfigRules::ParseCtrlTime(const char* time_data, int& hour, int& minute)
{
	vector<string> time_value;
	split(time_data, ":", time_value);

	if (time_value.size() == 3)
	{
		hour	= atoi(time_value[0].c_str());
		minute  = atoi(time_value[1].c_str());
	}
}



BOOL DlgConfigRules::DestroyWindow()
{
	m_bEnableShow = false;

	VzLPRClient_StopRealPlay(m_nPlayHandle);

	return CDialog::DestroyWindow();
}

static void iDrawDirArrowOnPolygon(unsigned char *pFrame, 
								   int w, int h, int p,
								   int x[], int y[], int nPCount,
								   VZ_LPRC_DIR eDir, const unsigned char color[], bool bColor)
{
	if(nPCount != 4)
		return;

	//在第0、1个顶点的平均位置和第2、3个顶点的平均位置构成的连线上，
	//绘制方向箭头。
	int xUP = (x[0] + x[1])>>1;
	int yUP = (y[0] + y[1])>>1;
	int xDN = (x[2] + x[3])>>1;
	int yDN = (y[2] + y[3])>>1;
	if(yUP > yDN)
	{
		int temp = xUP;
		xUP = xDN;
		xDN = temp;
		temp = yUP;
		yUP = yDN;
		yDN = temp;
	}
	int xCen = (xUP + xDN)>>1;
	int yCen = (yUP + yDN)>>1;

	//由上至下
	if(eDir == VZ_LPRC_UP2DOWN)
	{
		ivzArrowRGB(pFrame, w, h, p, xDN, yDN, xUP, yUP, 
			16, 5, 16, false, color, bColor);
	}
	else if(eDir == VZ_LPRC_DOWN2UP)
	{
		ivzArrowRGB(pFrame, w, h, p, xUP, yUP, xDN, yDN, 
			16, 5, 16, false, color, bColor);
	}
}

void DlgConfigRules::iDrawLoops(unsigned char *pBuf,  int width, int height, int pitch, int nPF,
								const VZ_LPRC_VIRTUAL_LOOPS_EX &VLoops,
								int nIdxVLoopHover, int nIdxVLoopSelect, int nIdxPointHover)
{
	if(nPF != ImageFormatBGR)
		return;

	//先绘制多边形
	for(unsigned i=0; i<VLoops.uNumVirtualLoop; i++)
	{
		const VZ_LPRC_VIRTUAL_LOOP_EX &VL = VLoops.struLoop[i];

		const unsigned char *pColor = (i==nIdxVLoopHover) 
			? ((i==nIdxVLoopSelect) ? iGetColor(_COLOR_YELLOW, nPF) : iGetColor(_COLOR_ORANGE, nPF))
			: ((i==nIdxVLoopSelect) ? iGetColor(_COLOR_GREEN, nPF) : iGetColor(_COLOR_RED, nPF));

		int px[VZ_LPRC_VIRTUAL_LOOP_VERTEX_NUM_EX];
		int py[VZ_LPRC_VIRTUAL_LOOP_VERTEX_NUM_EX];
		for(unsigned j=0; j<VL.uNumVertex; j++)
		{
			px[j] = VL.struVertex[j].X_1000*width/1000;
			py[j] = VL.struVertex[j].Y_1000*height/1000;
		}
		iDrawLoop(pBuf, width, height, pitch, px, py, VL.uNumVertex, pColor);
		iDrawDirArrowOnPolygon(pBuf, width, height, pitch, px, py, VL.uNumVertex, 
			VL.eCrossDir, pColor, true);
	}

	if(nIdxVLoopSelect>=0)
	{
		const unsigned char *pColorP = nIdxVLoopHover >= 0 && nIdxVLoopHover == nIdxVLoopSelect
			? iGetColor(_COLOR_YELLOW, nPF) : iGetColor(_COLOR_GREEN, nPF);

		const VZ_LPRC_VIRTUAL_LOOP_EX &VL = VLoops.struLoop[nIdxVLoopSelect];
		//再绘制顶点
		for(unsigned j=0; j<VL.uNumVertex; j++)
		{
			const VZ_LPRC_VERTEX &P = VL.struVertex[j];
			int nSizePHalf = j==nIdxPointHover ? MIN_GAP_SELECT : 4;
			int nCenX = P.X_1000*width/1000;
			int nCenY = P.Y_1000*height/1000;
			iDrawRect(pBuf, width, height, pitch, nCenX - nSizePHalf, nCenY - nSizePHalf,
				2*nSizePHalf+1, 2*nSizePHalf+1, pColorP);
		}
	}    
}

void DlgConfigRules
::iDrawROI(unsigned char *pBuf,  int width, int height, int pitch, int nPF,
		   const VZ_LPRC_ROI_EX &ROI, bool bHover, bool bSelect, int nIdxPointHoverROI)
{
	const unsigned char *pColor = bHover ? iGetColor(_COLOR_PURPLE, nPF) : iGetColor(_COLOR_CYAN, nPF);

	int px[VZ_LPRC_ROI_VERTEX_NUM_EX];
	int py[VZ_LPRC_ROI_VERTEX_NUM_EX];
	for(unsigned j=0; j<ROI.uNumVertex; j++)
	{
		px[j] = ROI.struVertex[j].X_1000*width/1000;
		py[j] = ROI.struVertex[j].Y_1000*height/1000;
	}
	iDrawLoop(pBuf, width, height, pitch, px, py, ROI.uNumVertex, pColor);

	if(bSelect)
	{
		const unsigned char *pColorP = nIdxPointHoverROI >= 0
			? iGetColor(_COLOR_YELLOW, nPF) : iGetColor(_COLOR_GREEN, nPF);
		//再绘制顶点
		for(unsigned j=0; j<ROI.uNumVertex; j++)
		{
			const VZ_LPRC_VERTEX &P = ROI.struVertex[j];
			int nSizePHalf = j==nIdxPointHoverROI ? MIN_GAP_SELECT : 4;
			int nCenX = P.X_1000*width/1000;
			int nCenY = P.Y_1000*height/1000;
			iDrawRect(pBuf, width, height, pitch, nCenX - nSizePHalf, nCenY - nSizePHalf,
				2*nSizePHalf+1, 2*nSizePHalf+1, pColorP);
		}
	}
}

void DlgConfigRules::OnBnClickedBtnAddVloop()
{
	if(m_struVLoops.uNumVirtualLoop == 1)
	{
		MessageBox("地感线圈已存在");
		return;
	}

	CRect rect;
	m_struInterV.GetClientRect(&rect);

	CPoint pt1(150, 750);
	CPoint pt2(850, 750);
	CPoint pt3(950, 900);
	CPoint pt4(50, 900);

	VZ_LPRC_VIRTUAL_LOOP_EX struVL = {0};
	struVL.byID		= m_struVLoops.uNumVirtualLoop;
	struVL.byEnable	= 1;
	struVL.byDraw	= 1;
	sprintf_s(struVL.strName, VZ_LPRC_VIRTUAL_LOOP_NAME_LEN, "电子线圈", struVL.byID);
	struVL.struVertex[0].X_1000 = pt1.x;
	struVL.struVertex[0].Y_1000 = pt1.y;
	struVL.struVertex[1].X_1000 = pt2.x;
	struVL.struVertex[1].Y_1000 = pt2.y;
	struVL.struVertex[2].X_1000 = pt3.x;
	struVL.struVertex[2].Y_1000 = pt3.y;
	struVL.struVertex[3].X_1000 = pt4.x;
	struVL.struVertex[3].Y_1000 = pt4.y;

	struVL.uNumVertex = 4;
	struVL.uTriggerTimeGap = 10;
	struVL.uMinLPWidth = 45;
	struVL.uMaxLPWidth = 400;


	int nDeviceType = 0;
	VzLPRClient_GetCameraConfig(m_hLPRC, VZ_GET_LPR_DEVICE_TYPE, 0, &nDeviceType, sizeof(int));
	if (nDeviceType == 1)
	{
		struVL.uMinLPWidth = 45;
		struVL.uMaxLPWidth = 600;
	}
	else
	{
		if (m_nHWType == 2 || m_nHWType == 3)
		{
			struVL.uMinLPWidth = 80;
			struVL.uMaxLPWidth = 600;
		}
	}

	memcpy(m_struVLoops.struLoop + m_struVLoops.uNumVirtualLoop, &struVL, sizeof(VZ_LPRC_VIRTUAL_LOOP_EX));

	iUpdateInfo(m_struVLoops.struLoop + m_struVLoops.uNumVirtualLoop);

	m_nIdxVLoopSelect = m_struVLoops.uNumVirtualLoop;

	m_struVLoops.uNumVirtualLoop++;
}

void DlgConfigRules::OnBnClickedBtnRmvVloop()
{
	if(m_nIdxVLoopSelect < 0 || m_nIdxVLoopSelect >= (int)m_struVLoops.uNumVirtualLoop
		|| m_struVLoops.uNumVirtualLoop == 0)
		return;

	m_struVLoops.uNumVirtualLoop--;

	m_nIdxVLoopSelect = -1;
}

void DlgConfigRules::SetHWType(int type)
{
	m_nHWType = type;
}


void DlgConfigRules::iUpdateInfo(const VZ_LPRC_VIRTUAL_LOOP_EX *pStruVL)
{
	m_bActiveUpdate = true;

	CComboBox *pCmbDir = (CComboBox *)GetDlgItem(IDC_CMB_PASS_DIR);

	if(pStruVL)
	{
		SetDlgItemInt(IDC_EDIT_RULE_ID, pStruVL->byID, FALSE);
		SetDlgItemText(IDC_EDIT_RULE_NAME, pStruVL->strName);
		((CButton *)GetDlgItem(IDC_CHK_RULE_ENABLE))->SetCheck(pStruVL->byEnable ? BST_CHECKED : BST_UNCHECKED);
		((CButton *)GetDlgItem(IDC_CHK_RULE_SHOW))->SetCheck(pStruVL->byDraw ? BST_CHECKED : BST_UNCHECKED);
		SetDlgItemInt(IDC_EDIT_TRIG_GAP, pStruVL->uTriggerTimeGap, FALSE);

		((CButton *)GetDlgItem(IDC_CHK_RULE_ENABLE))->EnableWindow(FALSE);
		((CButton *)GetDlgItem(IDC_CHK_RULE_SHOW))->EnableWindow(FALSE);

		pCmbDir->SetCurSel(pStruVL->eCrossDir);

		SetDlgItemInt(IDC_EDIT_LP_WIDTH_MIN, pStruVL->uMinLPWidth, FALSE);
		SetDlgItemInt(IDC_EDIT_LP_WIDTH_MAX, pStruVL->uMaxLPWidth, FALSE);
	}
	else
	{
		SetDlgItemText(IDC_EDIT_RULE_ID, "");
		SetDlgItemText(IDC_EDIT_RULE_NAME, "");
		((CButton *)GetDlgItem(IDC_CHK_RULE_ENABLE))->SetCheck(BST_UNCHECKED);
		((CButton *)GetDlgItem(IDC_CHK_RULE_SHOW))->SetCheck(BST_UNCHECKED);
		SetDlgItemText(IDC_EDIT_TRIG_GAP, "");
		pCmbDir->SetCurSel(-1);

		((CButton *)GetDlgItem(IDC_CHK_RULE_ENABLE))->EnableWindow(FALSE);
		((CButton *)GetDlgItem(IDC_CHK_RULE_SHOW))->EnableWindow(FALSE);

		SetDlgItemText(IDC_EDIT_LP_WIDTH_MIN, "");
		SetDlgItemText(IDC_EDIT_LP_WIDTH_MAX, "");
	}

	m_bActiveUpdate = false;
}

void DlgConfigRules::iUpdateInfoROI(const VZ_LPRC_ROI_EX *pStruROI)
{
	m_bActiveROIUpdate = true;

	if(pStruROI)
	{
		((CButton *)GetDlgItem(IDC_CHK_ROI_ENABLE))->SetCheck(pStruROI->byEnable ? BST_CHECKED : BST_UNCHECKED);
		((CButton *)GetDlgItem(IDC_CHK_ROI_SHOW))->SetCheck(pStruROI->byDraw ? BST_CHECKED : BST_UNCHECKED);

		((CButton *)GetDlgItem(IDC_CHK_ROI_ENABLE))->EnableWindow(FALSE);
		((CButton *)GetDlgItem(IDC_CHK_ROI_SHOW))->EnableWindow(FALSE);
	}
	else
	{
		((CButton *)GetDlgItem(IDC_CHK_ROI_ENABLE))->SetCheck(BST_UNCHECKED);
		((CButton *)GetDlgItem(IDC_CHK_ROI_SHOW))->SetCheck(BST_UNCHECKED);

		((CButton *)GetDlgItem(IDC_CHK_ROI_ENABLE))->EnableWindow(FALSE);
		((CButton *)GetDlgItem(IDC_CHK_ROI_SHOW))->EnableWindow(FALSE);
	}

	m_bActiveROIUpdate = false;
}

//返回最近的点的序号
static int iCalcNearestVertex(int X, int Y, const VZ_LPRC_VERTEX struVertex[], unsigned uNumVertex)
{
	int nIdxNearest = -1;
	int nDistNearestVtx = (int)(MIN_GAP_SELECT*MIN_GAP_SELECT*1.4);
	for(unsigned i=0; i<uNumVertex; i++)
	{
		int nDist = iDist_2(X, Y, struVertex[i].X_1000, struVertex[i].Y_1000);
		if(nDist > nDistNearestVtx)
			continue;

		nIdxNearest = i;
		nDistNearestVtx = nDist;
	}

	return(nIdxNearest);
}

//移动顶点时考虑边界
//确保外接矩形不过边界
static void iLimitOffsetByBoundary(const VZ_LPRC_VERTEX struVtx[], unsigned uNumVtx,
								   int &nOffsetX, int &nOffsetY,
								   int LeftB, int RightB, int TopB, int BottomB)
{
	int nLeft = RightB;
	int nRight = LeftB;
	int nTop = BottomB;
	int nBottom = TopB;
	for(unsigned i=0; i<uNumVtx; i++)
	{
		if((int)struVtx[i].X_1000 < nLeft)
			nLeft = struVtx[i].X_1000;
		if((int)struVtx[i].X_1000 > nRight)
			nRight = struVtx[i].X_1000;
		if((int)struVtx[i].Y_1000 < nTop)
			nTop = struVtx[i].Y_1000;
		if((int)struVtx[i].Y_1000 > nBottom)
			nBottom = struVtx[i].Y_1000;
	}

	//计算范围受限的偏移量
	int nOffsetX1 = nOffsetX > LeftB
		? (nRight+nOffsetX > RightB ? RightB - nRight : nOffsetX)
		: (nLeft+nOffsetX < LeftB ? LeftB-nLeft : nOffsetX);
	int nOffsetY1 = nOffsetY > TopB
		? (nBottom+nOffsetY > BottomB ? BottomB-nBottom : nOffsetY)
		: (nTop+nOffsetY < TopB ? TopB-nTop : nOffsetY);

	nOffsetX = nOffsetX1;
	nOffsetY = nOffsetY1;
}

void DlgConfigRules::iCtrl(VZ_LPRC_VIRTUAL_LOOPS_EX &struVLoops)
{
	//如果未按下按钮，则控制悬停状态
	if(m_struIVInfoLocal.m_bBtnDown == false)
	{
		//首先判断是否在多边形内，
		//如果是，则计算距离，并且记录最近的距离及序号
		unsigned uIdxNearest = -1;
		int nDistNearest = 1000000;

		for(unsigned i=0; i<struVLoops.uNumVirtualLoop; i++)
		{
			const VZ_LPRC_VIRTUAL_LOOP_EX &VL = struVLoops.struLoop[i];
			int PX[VZ_LPRC_VIRTUAL_LOOP_VERTEX_NUM_EX] = {0};
			int PY[VZ_LPRC_VIRTUAL_LOOP_VERTEX_NUM_EX] = {0};
			for(unsigned j=0; j<VL.uNumVertex; j++)
			{
				PX[j] = VL.struVertex[j].X_1000;
				PY[j] = VL.struVertex[j].Y_1000;
			}
			if(PointInPolygon(m_struIVInfo.x, m_struIVInfo.y, PX, PY, VL.uNumVertex) == false)
				continue;

			int nCenter[2];
			iGetCenter(nCenter, PX, PY, VL.uNumVertex);

			int nDist = iDist_2(m_struIVInfo.x, m_struIVInfo.y, nCenter[0], nCenter[1]);

			if(nDist < nDistNearest)
			{
				uIdxNearest = i;
				nDistNearest = nDist;
			}
		}

		//判断按钮按下的事件：状态发生变化且是按下的状态
		bool bEventBtnDown = m_struIVInfo.m_bBtnDown;

		//找到已经选中的线圈
		VZ_LPRC_VIRTUAL_LOOP_EX *pVLoopSelected = m_nIdxVLoopSelect >= 0 
			? struVLoops.struLoop + m_nIdxVLoopSelect : NULL;

		bool bOPTOnVtx = false;

		//如果已经选中了线圈，则计算其中顶点的悬停
		if(pVLoopSelected)
		{
			//首先清空
			m_nIdxPointHover = -1;

			int nIdxNearestVtx = iCalcNearestVertex(m_struIVInfo.x, m_struIVInfo.y, 
				pVLoopSelected->struVertex, pVLoopSelected->uNumVertex);

			if(nIdxNearestVtx >= 0)
			{
				m_nIdxPointHover = nIdxNearestVtx;

				if(bEventBtnDown)
				{
					//同时备份该顶点
					memcpy(&m_struPointBackup, pVLoopSelected->struVertex + nIdxNearestVtx,
						sizeof(VZ_LPRC_VERTEX));
				}

				bOPTOnVtx = true;
			}
		}

		//如果已经操作了顶点，则不操作线圈
		if(bOPTOnVtx == false)
		{
			if(bEventBtnDown)
			{
				//首先清空选中
				m_nIdxVLoopSelect = -1;
			}
			//设置悬停
			m_nIdxVLoopHover = uIdxNearest;
			if(uIdxNearest != -1)
			{
				//如果同时发生了按钮事件，则设置为选中
				if(bEventBtnDown)
				{
					m_nIdxVLoopSelect = uIdxNearest;

					//同时备份该线圈
					memcpy(&m_struLoopBackup, struVLoops.struLoop + uIdxNearest,
						sizeof(VZ_LPRC_VIRTUAL_LOOP_EX));

					iUpdateInfo(m_nIdxVLoopSelect >= 0 ? m_struVLoops.struLoop + m_nIdxVLoopSelect : NULL);
				}
			}

		}

		//记录按下去的XY坐标
		if(bEventBtnDown)
		{
			m_struIVInfoLocal.x = m_struIVInfo.x;
			m_struIVInfoLocal.y = m_struIVInfo.y;

			//恢复触发
			m_struIVInfo.m_bBtnDown = false;
			m_struIVInfoLocal.m_bBtnDown = true;
		}
	}
	else
	{
		if(m_struIVInfo.eEvent == IV_ENVET_MOUSE_MOVE)
		{
			if(m_nIdxVLoopSelect >= 0)
			{
				VZ_LPRC_VIRTUAL_LOOP_EX *pVLoopSelected = struVLoops.struLoop + m_nIdxVLoopSelect;

				//找到被选中的顶点
				int nOffsetX = m_struIVInfo.x - m_struIVInfoLocal.x;
				int nOffsetY = m_struIVInfo.y - m_struIVInfoLocal.y;
				//如果点被选中了则移动，否则移动线圈
				if(m_nIdxPointHover >= 0)
				{
					VZ_LPRC_VERTEX *pVtx = pVLoopSelected->struVertex + m_nIdxPointHover;
					pVtx->X_1000 = m_struPointBackup.X_1000 + nOffsetX;
					pVtx->Y_1000 = m_struPointBackup.Y_1000 + nOffsetY;
				}
				else
				{
					//移动线圈时考虑边界
					//确保外接矩形不过图像边界
					iLimitOffsetByBoundary(m_struLoopBackup.struVertex, m_struLoopBackup.uNumVertex,
						nOffsetX, nOffsetY, 0, 1000, 0, 1000);

					for(unsigned i=0; i<m_struLoopBackup.uNumVertex; i++)
					{
						pVLoopSelected->struVertex[i].X_1000 
							= m_struLoopBackup.struVertex[i].X_1000 + nOffsetX;

						pVLoopSelected->struVertex[i].Y_1000
							= m_struLoopBackup.struVertex[i].Y_1000 + nOffsetY;
					}

				}
			}
		}

		//判断按钮弹起的事件：状态发生变化且是弹起的状态
		bool bEventBtnUp = m_struIVInfo.m_bBtnUp;
		if(bEventBtnUp)
		{
			//恢复触发
			m_struIVInfo.m_bBtnUp = false;
			m_struIVInfoLocal.m_bBtnDown = false;
		}
	}
}

void DlgConfigRules::iCtrl(VZ_LPRC_ROI_EX &struROI)
{
	//如果未按下按钮，则控制悬停状态
	if(m_struIVInfoLocal.m_bBtnDown == false)
	{
		//判断按钮按下的事件：状态发生变化且是按下的状态
		bool bEventBtnDown = m_struIVInfo.m_bBtnDown;

		bool bOPTOnVtx = false;
		//计算顶点悬停
		m_nIdxVtxROIHover = iCalcNearestVertex(m_struIVInfo.x, m_struIVInfo.y,
			struROI.struVertex, struROI.uNumVertex);
		if(m_nIdxVtxROIHover >= 0)
		{
			if(bEventBtnDown)
			{
				//拖动前备份
				memcpy(&m_struPointBackup, struROI.struVertex + m_nIdxVtxROIHover,
					sizeof(VZ_LPRC_VERTEX));
			}

			bOPTOnVtx = true;
		}
		//未操作顶点，才操作线圈
		if(bOPTOnVtx == false)
		{
			if(bEventBtnDown)
			{
				//首先清空选中
				m_bROISelect = false;
			}
			//首先清空悬停
			m_bROIHover = false;

			int PX[VZ_LPRC_ROI_VERTEX_NUM_EX] = {0};
			int PY[VZ_LPRC_ROI_VERTEX_NUM_EX] = {0};
			for(unsigned j=0; j<struROI.uNumVertex; j++)
			{
				PX[j] = struROI.struVertex[j].X_1000;
				PY[j] = struROI.struVertex[j].Y_1000;
			}
			if(PointInPolygon(m_struIVInfo.x, m_struIVInfo.y, PX, PY, struROI.uNumVertex))
				m_bROIHover = true;

			//确认选中
			if(bEventBtnDown && m_bROIHover)
			{
				m_bROISelect = true;

				memcpy(&m_struROIBackupInModify, &struROI, sizeof(VZ_LPRC_ROI_EX));
			}
		}

		//记录按下去的XY坐标
		if(bEventBtnDown)
		{
			m_struIVInfoLocal.x = m_struIVInfo.x;
			m_struIVInfoLocal.y = m_struIVInfo.y;

			//恢复触发
			m_struIVInfo.m_bBtnDown = false;
			m_struIVInfoLocal.m_bBtnDown = true;
		}
	}
	else
	{
		if(m_struIVInfo.eEvent == IV_ENVET_MOUSE_MOVE)
		{
			if(m_bROISelect)
			{
				int nOffsetX = m_struIVInfo.x - m_struIVInfoLocal.x;
				int nOffsetY = m_struIVInfo.y - m_struIVInfoLocal.y;
				//如果点被选中了则移动，否则移动线圈
				if(m_nIdxVtxROIHover>=0)
				{
					VZ_LPRC_VERTEX *pVtx = struROI.struVertex + m_nIdxVtxROIHover;
					pVtx->X_1000 = m_struPointBackup.X_1000 + nOffsetX;
					pVtx->Y_1000 = m_struPointBackup.Y_1000 + nOffsetY;
				}
				else
				{
					//移动线圈时考虑边界
					//确保外接矩形不过图像边界
					iLimitOffsetByBoundary(m_struROIBackupInModify.struVertex,
						m_struROIBackupInModify.uNumVertex,
						nOffsetX, nOffsetY, 0, 1000, 0, 1000);

					for(unsigned i=0; i<m_struROIBackupInModify.uNumVertex; i++)
					{
						m_struROI.struVertex[i].X_1000 
							= m_struROIBackupInModify.struVertex[i].X_1000 + nOffsetX;

						m_struROI.struVertex[i].Y_1000
							= m_struROIBackupInModify.struVertex[i].Y_1000 + nOffsetY;
					}
				}
			}
		}
		//判断按钮弹起的事件：状态发生变化且是弹起的状态
		bool bEventBtnUp = m_struIVInfo.m_bBtnUp;
		if(bEventBtnUp)
		{
			//恢复触发
			m_struIVInfo.m_bBtnUp = false;
			m_struIVInfoLocal.m_bBtnDown = false;
		}
	}
}

static void iDrawLoop(unsigned char *pFrame, unsigned uW, unsigned uH, unsigned uP,
					  const int px[], const int py[], int nNumP, const unsigned char color[3])
{
	for(int i=0; i<nNumP-1; i++)
	{
		ivzLine(pFrame, uW, uH, uP, 3, px[i], py[i], px[i+1], py[i+1], color);
	}
	ivzLine(pFrame, uW, uH, uP, 3, px[0], py[0], px[nNumP-1], py[nNumP-1], color);
}

static void iDrawRect(unsigned char *pFrame, unsigned uW, unsigned uH, unsigned uP,
					  int x, int y, int w, int h, const unsigned char color[3])
{
	int px[4] = {x, x, x+w, x+w};
	int py[4] = {y, y+h, y+h, y};

	iDrawLoop(pFrame, uW, uH, uP, px, py, 4, color);
}

//测试向右的射线能与线段相交
static bool iRadialRightCanCutSegment(int x0, int y0, int segX1, int segY1, int segX2, int segY2)
{
	int resX;

	//根本没可能
	if((x0 > segX1 && x0 > segX2)
		||(y0 >= segY1 && y0 >= segY2)
		|| (y0 < segY1 && y0 < segY2))
	{
		return(false);
	}

	//统一化位置
	if(segX1 > segX2)
	{
		int temp = segX1;
		segX1 = segX2;
		segX2 = temp;
		temp = segY1;
		segY1 = segY2;
		segY2 = temp;
	}

	//特殊情况
	if(segX1 == segX2)
	{
		if(x0 > segX1)
		{
			return(false);
		}
		return(true);
	}
	if(segY1 == segY2)
	{
		return(false);
	}

	resX = (y0 - segY1)*(segX2 - segX1)/(segY2 - segY1) + segX1;

	if(resX < x0)
	{
		return(false);
	}

	return(true);
}

static bool PointInPolygon(int x, int y, const int px[], const int py[], int nNumP)
{
	int i;
	bool rt;
	int cutNum = 0;

	if(nNumP < 3)
	{
		return(false);
	}

	//统计多边形的边缘线段与给定点的右射线的相交数量，奇数为点在多边形内
	for(i=0; i<nNumP-1; i++)
	{
		rt = iRadialRightCanCutSegment(x, y, px[i],	py[i], px[i+1], py[i+1]);
		if(rt == true)
		{
			cutNum++;
		}
	}
	rt = iRadialRightCanCutSegment(x, y, px[0],	py[0], px[nNumP-1], py[nNumP-1]);
	if(rt == true)
	{
		cutNum++;
	}

	if(cutNum & 0x1)
	{
		return(true);
	}

	return(false);
}

static void iGetCenter(int xy[2], const int px[], const int py[], int nNumP)
{
	int nSumX = 0, nSumY = 0;
	for(int i=0; i<nNumP; i++)
	{
		nSumX += px[i];
		nSumY += py[i];
	}

	xy[0] = nSumX/nNumP;
	xy[1] = nSumY/nNumP;
}

static int iDist_2(int x1, int y1, int x2, int y2)
{
	return((x1-x2)*(x1-x2) + (y1-y2)*(y1-y2));
}

static const unsigned char *iGetColor(_COLOR_TYPE eColorType, int nPF)
{
	const unsigned char *pColor = NULL;
	const unsigned char *pColorW = NULL;
	const unsigned char *pColorR = NULL;
	const unsigned char *pColorG = NULL;
	const unsigned char *pColorOrg = NULL;
	const unsigned char *pColorYel = NULL;
	const unsigned char *pColorBlue = NULL;
	const unsigned char *pColorPurple = NULL;
	const unsigned char *pColorCyan = NULL;
	if(nPF == ImageFormatYUV420)
	{
		static const unsigned char colorW[3] = {255, 128, 128};
		static const unsigned char colorR[3] = {112, 98, 218};	//dark{112, 98, 218} light{190, 117, 161}
		static const unsigned char colorG[3] = {189, 90, 80};	//dark{189, 90, 80} light{208, 106, 100}
		static const unsigned char colorOrg[3] = {182, 32, 166};
		static const unsigned char colorYel[3] = {255, 32, 166};
		pColorW = colorW;
		pColorR = colorR;
		pColorG = colorG;
		pColorOrg = colorOrg;
		pColorYel = colorYel;
	}
	else if(nPF == ImageFormatBGR)
	{
		static const unsigned char colorW[3] = {255, 255, 255};
		static const unsigned char colorR[3] = {0, 0, 255};
		static const unsigned char colorG[3] = {0, 255, 0};
		static const unsigned char colorOrg[3] = {0, 150, 237};
		static const unsigned char colorYel[3] = {0, 250, 250};
		static const unsigned char colorBlue[3] = {232, 162, 0};
		static const unsigned char colorPurple[3] = {164, 73, 163};
		static const unsigned char colorCyan[3] = {250, 250, 0};
		pColorW = colorW;
		pColorR = colorR;
		pColorG = colorG;
		pColorOrg = colorOrg;
		pColorYel = colorYel;
		pColorBlue = colorBlue;
		pColorPurple = colorPurple;
		pColorCyan = colorCyan;
	}

	switch(eColorType)
	{
	case(_COLOR_RED):
		pColor = pColorR;
		break;
	case(_COLOR_GREEN):
		pColor = pColorG;
		break;
	case(_COLOR_ORANGE):
		pColor = pColorOrg;
		break;
	case(_COLOR_YELLOW):
		pColor = pColorYel;
		break;
	case(_COLOR_BLUE):
		pColor = pColorBlue;
		break;
	case(_COLOR_PURPLE):
		pColor = pColorPurple;
		break;
	case(_COLOR_CYAN):
		pColor = pColorCyan;
		break;
	case(_COLOR_WHITE):
	default:
		pColor = pColorW;
	}

	return(pColor);
}

void DlgConfigRules::OnEnChangeEditRuleName()
{
	// TODO:  If this is a RICHEDIT control, the control will not
	// send this notification unless you override the CDialog::OnInitDialog()
	// function and call CRichEditCtrl().SetEventMask()
	// with the ENM_CHANGE flag ORed into the mask.

	if(m_bActiveUpdate)
		return;

	if(m_nIdxVLoopSelect < 0 || m_nIdxVLoopSelect >= (int)m_struVLoops.uNumVirtualLoop)
		return;

	VZ_LPRC_VIRTUAL_LOOP_EX &VL = m_struVLoops.struLoop[m_nIdxVLoopSelect];
	GetDlgItemText(IDC_EDIT_RULE_NAME, VL.strName, VZ_LPRC_VIRTUAL_LOOP_NAME_LEN);
}

void DlgConfigRules::OnBnClickedChkRuleEnable()
{
	if(m_nIdxVLoopSelect < 0 || m_nIdxVLoopSelect >= (int)m_struVLoops.uNumVirtualLoop)
		return;

	int rt = ((CButton *)GetDlgItem(IDC_CHK_RULE_ENABLE))->GetCheck();

	VZ_LPRC_VIRTUAL_LOOP_EX &VL = m_struVLoops.struLoop[m_nIdxVLoopSelect];
	VL.byEnable = rt == BST_CHECKED ? 1 : 0;
}

void DlgConfigRules::OnBnClickedChkRuleShow()
{
	if(m_nIdxVLoopSelect < 0 || m_nIdxVLoopSelect >= (int)m_struVLoops.uNumVirtualLoop)
		return;

	int rt = ((CButton *)GetDlgItem(IDC_CHK_RULE_SHOW))->GetCheck();

	VZ_LPRC_VIRTUAL_LOOP_EX &VL = m_struVLoops.struLoop[m_nIdxVLoopSelect];
	VL.byDraw = rt == BST_CHECKED ? 1 : 0;
}

void DlgConfigRules::OnBnClickedBtnRuleSave()
{
	if(memcmp(&m_struVLoops, &m_struVLoopsBackup, sizeof(VZ_LPRC_VIRTUAL_LOOPS_EX)))
	{
		if (m_nHWType == 2 || m_nHWType == 3)
		{
			int nDeviceType = 0;
			VzLPRClient_GetCameraConfig(m_hLPRC, VZ_GET_LPR_DEVICE_TYPE, 0, &nDeviceType, sizeof(int));
			if (nDeviceType == 1)
			{
				if( m_struVLoops.struLoop[0].uMinLPWidth < 45 || m_struVLoops.struLoop[0].uMaxLPWidth > 600 )
				{
					MessageBox("车牌宽度范围应在45-600之间");
					return;
				}
			}
			else
			{
				if( m_struVLoops.struLoop[0].uMinLPWidth < 80 || m_struVLoops.struLoop[0].uMaxLPWidth > 600 )
				{
					MessageBox("车牌宽度范围应在80-600之间");
					return;
				}
			}
			
		}
		else
		{
			if( m_struVLoops.struLoop[0].uMinLPWidth < 45 || m_struVLoops.struLoop[0].uMaxLPWidth > 400 )
			{
				MessageBox("车牌宽度范围应在45-400");
				return;
			}
		}

		if( m_struVLoops.struLoop[0].uTriggerTimeGap <= 0 || m_struVLoops.struLoop[0].uTriggerTimeGap > 255 )
		{
			MessageBox("触发间隔时间应在1-255");
			return;
		}

		
		int rt = VzLPRClient_SetVirtualLoopEx(m_hLPRC, &m_struVLoops);
		if(rt == 0)
		{
			memcpy(&m_struVLoopsBackup, &m_struVLoops, sizeof(VZ_LPRC_VIRTUAL_LOOPS_EX));
			MessageBox("设置虚拟线圈成功");
		}
		else
		{
			MessageBox("设置规则失败");
		}
	}

	if(m_bHaveROI)
	{
		if(memcmp(&m_struROI, &m_struROIBackup, sizeof(VZ_LPRC_ROI_EX)))
		{
			int rt = VzLPRClient_SetRegionOfInterestEx(m_hLPRC, &m_struROI);
			if(rt == 0)
			{
				memcpy(&m_struROIBackup, &m_struROI, sizeof(VZ_LPRC_ROI_EX));
			}
			else
			{
				MessageBox("设置识别区域失败");
			}
		}
	}
	else
	{
		VzLPRClient_SetRegionOfInterest(m_hLPRC, NULL);
	}
}

void DlgConfigRules::OnBnClickedBtnRuleCancel()
{
	OnCancel();
}

void DlgConfigRules::OnBnClickedBtnResetAll()
{
	VzLPRClient_GetVirtualLoopEx(m_hLPRC, &m_struVLoopsBackup);
	memcpy(&m_struVLoops, &m_struVLoopsBackup, sizeof(VZ_LPRC_VIRTUAL_LOOPS_EX));
	m_nIdxVLoopSelect = m_struVLoops.struLoop > 0 ? 0 : -1;
	if(m_nIdxVLoopSelect >= 0)
	{
		iUpdateInfo(m_struVLoops.struLoop);
	}

	int rt = VzLPRClient_GetRegionOfInterestEx(m_hLPRC, &m_struROIBackup);
	if(rt == VZ_API_SUCCESS)
	{
		m_bHaveROI = true;
		memcpy(&m_struROI, &m_struROIBackup, sizeof(VZ_LPRC_ROI_EX));
		iUpdateInfoROI(&m_struROI);
	}
	else
	{
		m_bHaveROI = false;
		iUpdateInfoROI(NULL);
	}

	OnBnClickedRadioSelectVl();
}

void DlgConfigRules::OnCbnSelchangeCmbProv()
{
	CComboBox *pCmb = (CComboBox *)GetDlgItem(IDC_CMB_PROV);
	int idx = pCmb->GetCurSel();
	//	unsigned uIdx = GetDlgItemInt(IDC_CMB_PROV);
	int rt = VzLPRClient_PresetProvinceIndex(m_hLPRC, idx-1);
	if(rt == 0)
	{
		MessageBox("设置默认省份成功");
	}
	else
	{
		MessageBox("设置默认省份失败");
	}
}

void DlgConfigRules::OnEnChangeEditTrigGap()
{
	// TODO:  If this is a RICHEDIT control, the control will not
	// send this notification unless you override the CDialog::OnInitDialog()
	// function and call CRichEditCtrl().SetEventMask()
	// with the ENM_CHANGE flag ORed into the mask.

	// TODO:  Add your control notification handler code here
	if(m_bActiveUpdate)
		return;

	if(m_nIdxVLoopSelect < 0 || m_nIdxVLoopSelect >= (int)m_struVLoops.uNumVirtualLoop)
		return;

	VZ_LPRC_VIRTUAL_LOOP_EX &VL = m_struVLoops.struLoop[m_nIdxVLoopSelect];
	UINT uTrigGap = GetDlgItemInt(IDC_EDIT_TRIG_GAP);
	unsigned char ucTrigGap = uTrigGap <= 255 ? uTrigGap : 255;

	VL.uTriggerTimeGap = ucTrigGap;

	if(uTrigGap > 255)
		SetDlgItemInt(IDC_EDIT_TRIG_GAP, ucTrigGap);
}

static VZ_LPRC_DIR iGetDir(int idx)
{
	if(idx == 1)
		return(VZ_LPRC_UP2DOWN);

	if(idx == 2)
		return(VZ_LPRC_DOWN2UP);

	return(VZ_LPRC_BIDIR);
}

void DlgConfigRules::OnCbnSelchangeCmbPassDir()
{
	if(m_nIdxVLoopSelect < 0 || m_nIdxVLoopSelect >= (int)m_struVLoops.uNumVirtualLoop)
		return;

	VZ_LPRC_VIRTUAL_LOOP_EX &VL = m_struVLoops.struLoop[m_nIdxVLoopSelect];

	CComboBox *pCmb = (CComboBox *)GetDlgItem(IDC_CMB_PASS_DIR);
	int idx = pCmb->GetCurSel();

	VL.eCrossDir = iGetDir(idx);
}

void DlgConfigRules::OnEnChangeEditLpWidthMin()
{
	// TODO:  If this is a RICHEDIT control, the control will not
	// send this notification unless you override the CDialog::OnInitDialog()
	// function and call CRichEditCtrl().SetEventMask()
	// with the ENM_CHANGE flag ORed into the mask.

	// TODO:  Add your control notification handler code here
	if(m_bActiveUpdate)
		return;

	if(m_nIdxVLoopSelect < 0 || m_nIdxVLoopSelect >= (int)m_struVLoops.uNumVirtualLoop)
		return;

	VZ_LPRC_VIRTUAL_LOOP_EX &VL = m_struVLoops.struLoop[m_nIdxVLoopSelect];
	UINT uLPWidthMin = GetDlgItemInt(IDC_EDIT_LP_WIDTH_MIN);
	UINT uLPWidthMax = GetDlgItemInt(IDC_EDIT_LP_WIDTH_MAX);
	int ucLPWMin = uLPWidthMin <= uLPWidthMax ? uLPWidthMin : uLPWidthMax;
	VL.uMinLPWidth = ucLPWMin;

	/*if(uLPWidthMin > uLPWidthMax)
		SetDlgItemInt(IDC_EDIT_LP_WIDTH_MIN, ucLPWMin);*/
}

void DlgConfigRules::OnEnChangeEditLpWidthMax()
{
	// TODO:  If this is a RICHEDIT control, the control will not
	// send this notification unless you override the CDialog::OnInitDialog()
	// function and call CRichEditCtrl().SetEventMask()
	// with the ENM_CHANGE flag ORed into the mask.

	// TODO:  Add your control notification handler code here
	if(m_bActiveUpdate)
		return;

	if(m_nIdxVLoopSelect < 0 || m_nIdxVLoopSelect >= (int)m_struVLoops.uNumVirtualLoop)
		return;

	VZ_LPRC_VIRTUAL_LOOP_EX &VL = m_struVLoops.struLoop[m_nIdxVLoopSelect];
	UINT uLPWidthMin = GetDlgItemInt(IDC_EDIT_LP_WIDTH_MIN);
	UINT uLPWidthMax = GetDlgItemInt(IDC_EDIT_LP_WIDTH_MAX);
	int ucLPWMax = uLPWidthMin <= uLPWidthMax ? uLPWidthMax : uLPWidthMin;
	VL.uMaxLPWidth = ucLPWMax;

	/*if(uLPWidthMin > uLPWidthMax)
		SetDlgItemInt(IDC_EDIT_LP_WIDTH_MAX, ucLPWMax);*/
}

void DlgConfigRules::OnBnClickedRadioSelectVl()
{
	m_eSelObjType = SEL_VL;		
}

void DlgConfigRules::OnBnClickedRadioSelectRoi()
{
	m_eSelObjType = SEL_ROI;
	m_bROISelect = true;
}

void DlgConfigRules::OnBnClickedBtnAddRoi()
{
	if(m_bHaveROI)
	{
		MessageBox("识别区域已存在");
		return;
	}

	m_struROI.byEnable  = 1;
	m_struROI.byDraw    = 1;
	m_struROI.struVertex[0].X_1000  = 250;
	m_struROI.struVertex[0].Y_1000  = 250;
	m_struROI.struVertex[1].X_1000  = 750;
	m_struROI.struVertex[1].Y_1000  = 250;
	m_struROI.struVertex[2].X_1000  = 850;
	m_struROI.struVertex[2].Y_1000  = 850;
	m_struROI.struVertex[3].X_1000  = 500;
	m_struROI.struVertex[3].Y_1000  = 900;
	m_struROI.struVertex[4].X_1000  = 150;
	m_struROI.struVertex[4].Y_1000  = 850;
	m_struROI.uNumVertex = 5;

	iUpdateInfoROI(&m_struROI);

	m_bHaveROI = true;
}

void DlgConfigRules::OnBnClickedBtnRmvRoi()
{
	m_bHaveROI = false;
	iUpdateInfoROI(NULL);
}

void DlgConfigRules::OnBnClickedChkRoiEnable()
{
	if(m_bHaveROI == false)
	{
		MessageBox("先添加一个识别区域");
		return;
	}

	int rt = ((CButton *)GetDlgItem(IDC_CHK_ROI_ENABLE))->GetCheck();
	m_struROI.byEnable = rt == BST_CHECKED ? 1 : 0;
}

void DlgConfigRules::OnBnClickedChkRoiShow()
{
	if(m_bHaveROI == false)
	{
		MessageBox("先添加一个识别区域");
		return;
	}

	int rt = ((CButton *)GetDlgItem(IDC_CHK_ROI_SHOW))->GetCheck();
	m_struROI.byDraw = rt == BST_CHECKED ? 1 : 0;
}

void DlgConfigRules::OnBnClickedBtnAutoFoucs()
{
	// TODO: Add your control notification handler code here

	if (m_hLPRC )
	{
		if (VzLPRClient_DoAutoFocus(m_hLPRC) != 0 )
		{
			MessageBox("变倍后自动聚焦失败");
		}
	}
}




BOOL DlgConfigRules::PreTranslateMessage(MSG* pMsg)
{
	// TODO: Add your specialized code here and/or call the base class
	switch(pMsg->message)
	{
	case WM_LBUTTONDOWN:

		if (m_hLPRC)
		{
			if (pMsg->hwnd == GetDlgItem(IDC_BTN_ZOOM_ADD)->m_hWnd)
			{
				if (VzLPRClient_CtrlLens(m_hLPRC, VZ_LENS_ZOOM_TELE) != 0)
				{
					// MessageBox("长焦变倍失败");
				}

			}
			if (pMsg->hwnd == GetDlgItem(IDC_BTN_ZOOM_DEL)->m_hWnd)
			{
				if (VzLPRClient_CtrlLens(m_hLPRC, VZ_LENS_ZOOM_WIDE) != 0)
				{
					// MessageBox("短焦变倍失败");
				}
			}
			if (pMsg->hwnd == GetDlgItem(IDC_BTN_FOUCS_ADD)->m_hWnd)
			{
				if (VzLPRClient_CtrlLens(m_hLPRC, VZ_LENS_FOCUS_FAR) != 0)
				{
					// MessageBox("短焦变倍失败");
				}
			}
			if (pMsg->hwnd == GetDlgItem(IDC_BTN_FOUCS_DEL)->m_hWnd)
			{
				if (VzLPRClient_CtrlLens(m_hLPRC, VZ_LENS_FOCUS_NEAR) != 0)
				{
					// MessageBox("短焦变倍失败");
				}
			}
		}

		break;
	case WM_LBUTTONUP:
		if (pMsg->hwnd == GetDlgItem(IDC_BTN_ZOOM_ADD)->m_hWnd ||
			pMsg->hwnd == GetDlgItem(IDC_BTN_ZOOM_DEL)->m_hWnd ||
			pMsg->hwnd == GetDlgItem(IDC_BTN_FOUCS_ADD)->m_hWnd ||
			pMsg->hwnd == GetDlgItem(IDC_BTN_FOUCS_DEL)->m_hWnd)
		{
			VzLPRClient_CtrlLens(m_hLPRC, VZ_LENS_OPT_STOP);
		}
		break;
	}
	return CDialog::PreTranslateMessage(pMsg);
}

void DlgConfigRules::OnBnClickedRadLedAuto()
{
	// TODO: Add your control notification handler code here

	if( m_hLPRC )
	{
		VzLPRClient_SetLEDLightControlMode(m_hLPRC, VZ_LED_AUTO);

		if(m_nHWType == 2 || m_nHWType == 3)
		{
			ShowTimeCtrls(FALSE);
		}
	}
	else
	{
		MessageBox("没有获取设备句柄");
	}

}

void DlgConfigRules::OnBnClickedRadLedOn()
{
	// TODO: Add your control notification handler code here
	if( m_hLPRC )
	{
		VzLPRClient_SetLEDLightControlMode(m_hLPRC, VZ_LED_MANUAL_ON);

		if(m_nHWType == 2 || m_nHWType == 3)
		{
			ShowTimeCtrls(TRUE);
		}
	}
	else
	{
		MessageBox("没有获取设备句柄");
	}
}

void DlgConfigRules::OnBnClickedRadLedOff()
{
	// TODO: Add your control notification handler code here
	if( m_hLPRC )
	{
		VzLPRClient_SetLEDLightControlMode(m_hLPRC, VZ_LED_MANUAL_OFF);
		
		if(m_nHWType == 2 || m_nHWType == 3)
		{
			ShowTimeCtrls(FALSE);
		}
	}
	else
	{
		MessageBox("没有获取设备句柄");
	}
}


//配置时间和日期
void DlgConfigRules::OnBnClickedBtnConfigDataTime()
{
	VZ_DATE_TIME_INFO m_uDateTimeInfo;

	m_DTPDevDay.SetFormat(_T("MM/dd/yyyy"));
	//解析年、月、日
	CTime m_date;
	m_DTPDevDay.GetTime(m_date);
	m_uDateTimeInfo.uYear = m_date.GetYear();
	m_uDateTimeInfo.uMonth = m_date.GetMonth();
	m_uDateTimeInfo.uMDay = m_date.GetDay();
    //解析时、分、秒
	CTime m_time;
	m_dtpDevTime.GetTime(m_time);
	m_uDateTimeInfo.uHour = m_time.GetHour();
	m_uDateTimeInfo.uMin = m_time.GetMinute();
	m_uDateTimeInfo.uSec = m_time.GetSecond();
    //设置日期、时间
	char SetDaystr[64],SetTimestr[64];
	int retSETDT = VzLPRClient_SetDateTime(m_hLPRC,&m_uDateTimeInfo);
	if (retSETDT == 0)
	{
		sprintf_s(SetDaystr,"%02d/%02d/%d",m_uDateTimeInfo.uMonth,m_uDateTimeInfo.uMDay,m_uDateTimeInfo.uYear);
		sprintf_s(SetTimestr,"%02d:%02d:%02d",m_uDateTimeInfo.uHour,m_uDateTimeInfo.uMin,m_uDateTimeInfo.uSec);
		return ;
	}
}

void DlgConfigRules::OnTimer(UINT_PTR nIDEvent)
{
	//更新日期和时间
	if( nIDEvent == ID_TIMER_DATE )
	{
		int retDTI = VzLPRClient_GetDateTime(m_hLPRC,&m_oDateTimeInfo);
		if (retDTI == 0 )
		{
			COleDateTime dt(m_oDateTimeInfo.uYear,m_oDateTimeInfo.uMonth,m_oDateTimeInfo.uMDay,\
				m_oDateTimeInfo.uHour,m_oDateTimeInfo.uMin,m_oDateTimeInfo.uSec);
		 
            if(!m_dateControlFoucs)
			m_DTPDevDay.SetTime(dt);

			if(!m_timeControlFoucs)
			m_dtpDevTime.SetTime(dt);

		}
	}

	CDialog::OnTimer(nIDEvent);
}

void DlgConfigRules::OnNMSetfocusDatetimepickerDay(NMHDR *pNMHDR, LRESULT *pResult)
{
	// TODO: Add your control notification handler code here
	*pResult = 0;

    m_dateControlFoucs = true;     //日期控件激活
}

void DlgConfigRules::OnNMKillfocusDatetimepickerDay(NMHDR *pNMHDR, LRESULT *pResult)
{
	// TODO: Add your control notification handler code here
	*pResult = 0;

	m_dateControlFoucs = false;   //日期控件未激活
}

void DlgConfigRules::OnNMKillfocusDatetimepickerTime(NMHDR *pNMHDR, LRESULT *pResult)
{
	// TODO: Add your control notification handler code here
	*pResult = 0;

	m_timeControlFoucs = false;    //时间控件未激活
}

void DlgConfigRules::OnNMSetfocusDatetimepickerTime(NMHDR *pNMHDR, LRESULT *pResult)
{
	// TODO: Add your control notification handler code here
	*pResult = 0;
    m_timeControlFoucs = true;   //时间控件激活

}


void DlgConfigRules::OnBnClickedBtnFastFocus()
{
	// TODO: Add your control notification handler code here

	if (m_hLPRC )
	{
		if (VzLPRClient_DoFastFocus(m_hLPRC) != 0 )
		{
			MessageBox("车牌自动变倍失败");
		}
	}
}
void DlgConfigRules::OnCbnSelchangeCmbLightLevel()
{
	if( m_hLPRC )
	{
		CComboBox *pCmbLevel = (CComboBox *)GetDlgItem(IDC_CMB_LIGHT_LEVEL);

		int cur_sel = pCmbLevel->GetCurSel();
		int nValue = (int)pCmbLevel->GetItemData(cur_sel);

		CSliderCtrl *pSldLED = (CSliderCtrl *)GetDlgItem(IDC_SLD_LED);
		if( m_nHWType == 3 )
		{
			VzLPRClient_SetLEDLightLevel(m_hLPRC, nValue);
		}
		else
		{
			VzLPRClient_SetLEDLightLevel(m_hLPRC, nValue - 2);
		}

	}
}

void DlgConfigRules::OnBnClickedBtnSaveLedParam()
{
	// 先获取一次数据，再保存
	VZ_LPRC_CTRL_PARAM led_ctrl_param = { 0 };
	int ret = VzLPRClient_GetLedCtrl(m_hLPRC, &led_ctrl_param);

	// 手动模式
	led_ctrl_param.led_mode = 3;

	CString strEndTime1;

	// 时间段1
	char time_end1[16] = { 0 };
	if (m_cbxLightCtrl1.GetCurSel() == 0)
	{
		led_ctrl_param.time_ctrl[0].timectrl_enable = 0;
		led_ctrl_param.time_ctrl[0].led_level = -1;
	}
	else
	{
		m_dtTimeEnd1.GetWindowText(strEndTime1);

		led_ctrl_param.time_ctrl[0].timectrl_enable = 1;
		strcpy(led_ctrl_param.time_ctrl[0].time_begin, "00:00:00");
		strcpy(led_ctrl_param.time_ctrl[0].time_end, strEndTime1.GetBuffer(0));
		led_ctrl_param.time_ctrl[0].led_level = m_cbxLightCtrl1.GetCurSel() - 1;
	}

	CString strEndTime2;

	// 时间段2
	char time_end2[16] = { 0 };
	if (m_cbxLightCtrl2.GetCurSel() == 0)
	{
		led_ctrl_param.time_ctrl[1].timectrl_enable = 0;
		led_ctrl_param.time_ctrl[1].led_level = -1;
	}
	else
	{
		m_dtTimeEnd2.GetWindowText(strEndTime2);

		led_ctrl_param.time_ctrl[1].timectrl_enable = 1;
		strcpy(led_ctrl_param.time_ctrl[1].time_begin, strEndTime1.GetBuffer(0));
		strcpy(led_ctrl_param.time_ctrl[1].time_end, strEndTime2.GetBuffer(0));
		led_ctrl_param.time_ctrl[1].led_level = m_cbxLightCtrl2.GetCurSel() - 1;
	}

	// 时间段3
	if (m_cbxLightCtrl3.GetCurSel() == 0)
	{
		led_ctrl_param.time_ctrl[2].timectrl_enable = 0;
		led_ctrl_param.time_ctrl[2].led_level = -1;
	}
	else
	{
		led_ctrl_param.time_ctrl[2].timectrl_enable = 1;
		strcpy(led_ctrl_param.time_ctrl[2].time_begin, strEndTime2.GetBuffer(0));
		strcpy(led_ctrl_param.time_ctrl[2].time_end, "23:59:59");
		led_ctrl_param.time_ctrl[2].led_level = m_cbxLightCtrl3.GetCurSel() - 1;
	}

	ret = VzLPRClient_SetLedCtrl(m_hLPRC, &led_ctrl_param);
	if (ret == 0)
	{
		MessageBox("保存成功！");
	}
	else
	{
		MessageBox("保存失败，请重试！");
	}
}
void DlgConfigRules::OnDtnDatetimechangeDtTime1End(NMHDR *pNMHDR, LRESULT *pResult)
{
	LPNMDATETIMECHANGE pDTChange = reinterpret_cast<LPNMDATETIMECHANGE>(pNMHDR);

	COleDateTime dtTime = COleDateTime::GetCurrentTime();
	m_dtTimeEnd1.GetTime(dtTime);

	m_dtTimeStart2.SetTime(dtTime);

	*pResult = 0;
}

void DlgConfigRules::OnDtnDatetimechangeDtTime2Start(NMHDR *pNMHDR, LRESULT *pResult)
{
	LPNMDATETIMECHANGE pDTChange = reinterpret_cast<LPNMDATETIMECHANGE>(pNMHDR);

	COleDateTime dtTime = COleDateTime::GetCurrentTime();
	m_dtTimeStart2.GetTime(dtTime);

	m_dtTimeEnd1.SetTime(dtTime);

	*pResult = 0;
}

void DlgConfigRules::OnDtnDatetimechangeDtTime2End(NMHDR *pNMHDR, LRESULT *pResult)
{
	LPNMDATETIMECHANGE pDTChange = reinterpret_cast<LPNMDATETIMECHANGE>(pNMHDR);

	COleDateTime dtTime = COleDateTime::GetCurrentTime();
	m_dtTimeEnd2.GetTime(dtTime);

	m_dtTimeStart3.SetTime(dtTime);

	*pResult = 0;
}

void DlgConfigRules::OnDtnDatetimechangeDtTime3Start(NMHDR *pNMHDR, LRESULT *pResult)
{
	LPNMDATETIMECHANGE pDTChange = reinterpret_cast<LPNMDATETIMECHANGE>(pNMHDR);

	COleDateTime dtTime = COleDateTime::GetCurrentTime();
	m_dtTimeStart3.GetTime(dtTime);

	m_dtTimeEnd2.SetTime(dtTime);

	*pResult = 0;
}

void DlgConfigRules::OnClose()
{
	// TODO: Add your message handler code here and/or call default
	VzLPRClient_StopRealPlay(m_nPlayHandle);
	m_nPlayHandle = -1;

	CDialog::OnClose();
}
