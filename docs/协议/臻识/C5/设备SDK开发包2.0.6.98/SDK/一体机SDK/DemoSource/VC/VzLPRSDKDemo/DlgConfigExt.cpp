// DlgConfigExt.cpp : implementation file
//

#include "stdafx.h"
#include "VzLPRSDKDemo.h"
#include "DlgConfigExt.h"
#include "resource.h"

// DlgConfigExt dialog

IMPLEMENT_DYNAMIC(DlgConfigExt, CDialog)

DlgConfigExt::DlgConfigExt(VzLPRClientHandle handle, CWnd* pParent /*=NULL*/)
	: CDialog(DlgConfigExt::IDD, pParent)
	, m_hLPRC(handle)
{

}

DlgConfigExt::~DlgConfigExt()
{
}

void DlgConfigExt::DoDataExchange(CDataExchange* pDX)
{
	CDialog::DoDataExchange(pDX);
}


BEGIN_MESSAGE_MAP(DlgConfigExt, CDialog)
	ON_BN_CLICKED(IDC_BTN_SET_USER_DATA, &DlgConfigExt::OnBnClickedBtnSetUserData)
	ON_BN_CLICKED(IDC_BTN_GET_USER_DATA, &DlgConfigExt::OnBnClickedBtnGetUserData)
    ON_BN_CLICKED(IDC_REC_TYPE_BLUE, &DlgConfigExt::OnBnClickedRecTypeBlue)
    ON_BN_CLICKED(IDC_REC_TYPE_YELLOW, &DlgConfigExt::OnBnClickedRecTypeYellow)
    ON_BN_CLICKED(IDC_REC_TYPE_BLACK, &DlgConfigExt::OnBnClickedRecTypeBlack)
    ON_BN_CLICKED(IDC_REC_TYPE_COACH, &DlgConfigExt::OnBnClickedRecTypeCoach)
    ON_BN_CLICKED(IDC_REC_TYPE_POLICE, &DlgConfigExt::OnBnClickedRecTypePolice)
    ON_BN_CLICKED(IDC_REC_TYPE_AMPOL, &DlgConfigExt::OnBnClickedRecTypeAmpol)
    ON_BN_CLICKED(IDC_REC_TYPE_ARMY, &DlgConfigExt::OnBnClickedRecTypeArmy)
    ON_BN_CLICKED(IDC_REC_TYPE_GANGAO, &DlgConfigExt::OnBnClickedRecTypeGangao)
    ON_BN_CLICKED(IDC_REC_TYPE_EMBASSY, &DlgConfigExt::OnBnClickedRecTypeEmbassy)
    ON_BN_CLICKED(IDC_CHK_ENABLE_TRIG_STABLE, &DlgConfigExt::OnBnClickedChkEnableTrigStable)
    ON_BN_CLICKED(IDC_CHK_ENABLE_TRIG_VLOOP, &DlgConfigExt::OnBnClickedChkEnableTrigVloop)
    ON_BN_CLICKED(IDC_CHK_ENABLE_TRIG_IO_IN1, &DlgConfigExt::OnBnClickedChkEnableTrigIoIn1)
    ON_BN_CLICKED(IDC_CHK_ENABLE_TRIG_IO_IN2, &DlgConfigExt::OnBnClickedChkEnableTrigIoIn2)
	ON_BN_CLICKED(IDC_CHK_PLATE_POS, &DlgConfigExt::OnBnClickedChkPlatePos)
	ON_BN_CLICKED(IDC_CHK_REALTIME_RESULT, &DlgConfigExt::OnBnClickedChkRealtimeResult)
	ON_BN_CLICKED(IDC_CHK_VLOOP_AND_AREA, &DlgConfigExt::OnBnClickedChkVloopAndArea)
	ON_BN_CLICKED(IDC_CHK_ENABLE_TRIG_IO_IN3, &DlgConfigExt::OnBnClickedChkEnableTrigIoIn3)
	ON_BN_CLICKED(IDC_REC_TYPE_AVIATION, &DlgConfigExt::OnBnClickedRecTypeAviation)
	ON_BN_CLICKED(IDC_REC_TYPE_ENERGY, &DlgConfigExt::OnBnClickedRecTypeEnergy)
	ON_BN_CLICKED(IDC_REC_TYPE_EMERGENCY, &DlgConfigExt::OnBnClickedRecTypeEmergency)
	ON_BN_CLICKED(IDC_REC_TYPE_CONSULATE, &DlgConfigExt::OnBnClickedRecTypeConsulate)
END_MESSAGE_MAP()


// DlgConfigExt message handlers

void DlgConfigExt::OnBnClickedBtnSetUserData()
{
	char strUserData[VZ_LPRC_USER_DATA_MAX_LEN] = {0};

	GetDlgItemText(IDC_EDIT_SET_USER_DATA, strUserData, VZ_LPRC_USER_DATA_MAX_LEN);

	int rt = VzLPRClient_WriteUserData(m_hLPRC, (unsigned char *)strUserData, strlen(strUserData) + 1);
	if(rt != 0)
	{
		MessageBox("设置用户私有数据失败");
		return;
	}
	else {
		MessageBox("设置用户私有数据成功");
	}
}

void DlgConfigExt::OnBnClickedBtnGetUserData()
{
	char strUserData[VZ_LPRC_USER_DATA_MAX_LEN+1] = {0};

	int nSizeData = VzLPRClient_ReadUserData(m_hLPRC, (unsigned char *)strUserData, VZ_LPRC_USER_DATA_MAX_LEN);
	if(nSizeData < 0)
	{
		MessageBox("获取用户私有数据失败");
	}
	else
	{
		strUserData[nSizeData] = '\0';
	}

	BOOL bShowHex = GetIsCheckedByID( IDC_CHK_HEX );
	if( bShowHex )
	{
		char szHexUserData[512] = {0};
		for( int i = 0; i < nSizeData; i++)
		{
			char hex_value[3] = {0};
			sprintf(hex_value, "%02X", (unsigned char)strUserData[i]);

			strcat(szHexUserData, hex_value);
			strcat(szHexUserData, " ");
		}

		SetDlgItemText(IDC_EDIT_GET_USER_DATA, szHexUserData);
	}
	else
	{
		SetDlgItemText(IDC_EDIT_GET_USER_DATA, strUserData);
	}

	VZ_DEV_SERIAL_NUM struSN = {0};

	int rt = VzLPRClient_GetSerialNumber(m_hLPRC, &struSN);
	if(rt < 0)
	{
		MessageBox("获取设备序列号失败");
		return;
	}
	
	char strSN[64];
	sprintf_s(strSN, 64, "%08X-%08X", struSN.uHi, struSN.uLo);

	SetDlgItemText(IDC_EDIT_SERIAL_NUM, strSN);
}

BOOL DlgConfigExt::OnInitDialog()
{
    CDialog::OnInitDialog();


    m_uBitsRecType = 0;

    int rt = VzLPRClient_GetPlateRecType(m_hLPRC, &m_uBitsRecType);
    if(rt == 0)
    {
        ((CButton *)GetDlgItem(IDC_REC_TYPE_BLUE))->SetCheck(m_uBitsRecType & VZ_LPRC_REC_BLUE);
        ((CButton *)GetDlgItem(IDC_REC_TYPE_YELLOW))->SetCheck(m_uBitsRecType & VZ_LPRC_REC_YELLOW);
        ((CButton *)GetDlgItem(IDC_REC_TYPE_BLACK))->SetCheck(m_uBitsRecType & VZ_LPRC_REC_BLACK);
        ((CButton *)GetDlgItem(IDC_REC_TYPE_COACH))->SetCheck(m_uBitsRecType & VZ_LPRC_REC_COACH);
        ((CButton *)GetDlgItem(IDC_REC_TYPE_POLICE))->SetCheck(m_uBitsRecType & VZ_LPRC_REC_POLICE);
        ((CButton *)GetDlgItem(IDC_REC_TYPE_AMPOL))->SetCheck(m_uBitsRecType & VZ_LPRC_REC_AMPOL);
        ((CButton *)GetDlgItem(IDC_REC_TYPE_ARMY))->SetCheck(m_uBitsRecType & VZ_LPRC_REC_ARMY);
        ((CButton *)GetDlgItem(IDC_REC_TYPE_GANGAO))->SetCheck(m_uBitsRecType & VZ_LPRC_REC_GANGAO);
        ((CButton *)GetDlgItem(IDC_REC_TYPE_EMBASSY))->SetCheck(m_uBitsRecType & VZ_LPRC_REC_EMBASSY);
		((CButton *)GetDlgItem(IDC_REC_TYPE_AVIATION))->SetCheck(m_uBitsRecType & VZ_LPRC_REC_AVIATION);
		((CButton *)GetDlgItem(IDC_REC_TYPE_ENERGY))->SetCheck(m_uBitsRecType & VZ_LPRC_REC_ENERGY);
		((CButton *)GetDlgItem(IDC_REC_TYPE_EMERGENCY))->SetCheck(m_uBitsRecType & VZ_LPRC_REC_EMERGENCY);
		((CButton *)GetDlgItem(IDC_REC_TYPE_CONSULATE))->SetCheck(m_uBitsRecType & VZ_LPRC_REC_CONSULATE);
    }

    m_uBitsTrigType = 0;
    rt = VzLPRClient_GetPlateTrigType(m_hLPRC, &m_uBitsTrigType);
    if(rt == 0)
    {
        ((CButton *)GetDlgItem(IDC_CHK_ENABLE_TRIG_STABLE))->SetCheck(m_uBitsTrigType & VZ_LPRC_TRIG_ENABLE_STABLE);
        ((CButton *)GetDlgItem(IDC_CHK_ENABLE_TRIG_VLOOP))->SetCheck(m_uBitsTrigType & VZ_LPRC_TRIG_ENABLE_VLOOP);
        ((CButton *)GetDlgItem(IDC_CHK_ENABLE_TRIG_IO_IN1))->SetCheck(m_uBitsTrigType & VZ_LPRC_TRIG_ENABLE_IO_IN1);
        ((CButton *)GetDlgItem(IDC_CHK_ENABLE_TRIG_IO_IN2))->SetCheck(m_uBitsTrigType & VZ_LPRC_TRIG_ENABLE_IO_IN2);
		((CButton *)GetDlgItem(IDC_CHK_ENABLE_TRIG_IO_IN3))->SetCheck(m_uBitsTrigType & VZ_LPRC_TRIG_ENABLE_IO_IN3);
    }

	VZ_LPRC_DRAWMODE drawMode = {0};
	rt = VzLPRClient_GetDrawMode(m_hLPRC, &drawMode);
	if ( rt == 0 )
	{
		((CButton *)GetDlgItem(IDC_CHK_PLATE_POS))->SetCheck(drawMode.byDspAddTrajectory);
		((CButton *)GetDlgItem(IDC_CHK_REALTIME_RESULT))->SetCheck(drawMode.byDspAddTarget);
		((CButton *)GetDlgItem(IDC_CHK_VLOOP_AND_AREA))->SetCheck(drawMode.byDspAddRule);
	}

    return TRUE;  // return TRUE unless you set the focus to a control
    // EXCEPTION: OCX Property Pages should return FALSE
}

void DlgConfigExt::iOnCheckRecType(unsigned IDC, unsigned uBitRecType)
{
    if(((CButton *)GetDlgItem(IDC))->GetCheck())
    {
        m_uBitsRecType |= uBitRecType;
    }
    else
    {
        m_uBitsRecType &= ~uBitRecType;
    }

    VzLPRClient_SetPlateRecType(m_hLPRC, m_uBitsRecType);
}

void DlgConfigExt::OnBnClickedRecTypeBlue()
{
    iOnCheckRecType(IDC_REC_TYPE_BLUE, VZ_LPRC_REC_BLUE);
}

void DlgConfigExt::OnBnClickedRecTypeYellow()
{
    iOnCheckRecType(IDC_REC_TYPE_YELLOW, VZ_LPRC_REC_YELLOW);
}

void DlgConfigExt::OnBnClickedRecTypeBlack()
{
    iOnCheckRecType(IDC_REC_TYPE_BLACK, VZ_LPRC_REC_BLACK);
}

void DlgConfigExt::OnBnClickedRecTypeCoach()
{
    iOnCheckRecType(IDC_REC_TYPE_COACH, VZ_LPRC_REC_COACH);
}

void DlgConfigExt::OnBnClickedRecTypePolice()
{
    iOnCheckRecType(IDC_REC_TYPE_POLICE, VZ_LPRC_REC_POLICE);
}

void DlgConfigExt::OnBnClickedRecTypeAmpol()
{
    iOnCheckRecType(IDC_REC_TYPE_AMPOL, VZ_LPRC_REC_AMPOL);
}

void DlgConfigExt::OnBnClickedRecTypeArmy()
{
    iOnCheckRecType(IDC_REC_TYPE_ARMY, VZ_LPRC_REC_ARMY);
}

void DlgConfigExt::OnBnClickedRecTypeGangao()
{
    iOnCheckRecType(IDC_REC_TYPE_GANGAO, VZ_LPRC_REC_GANGAO);
}

void DlgConfigExt::OnBnClickedRecTypeEmbassy()
{
    iOnCheckRecType(IDC_REC_TYPE_EMBASSY, VZ_LPRC_REC_EMBASSY);
}

void DlgConfigExt::OnBnClickedRecTypeAviation()
{
	iOnCheckRecType(IDC_REC_TYPE_AVIATION, VZ_LPRC_REC_AVIATION);
}

void DlgConfigExt::OnBnClickedRecTypeEnergy()
{
	iOnCheckRecType(IDC_REC_TYPE_ENERGY, VZ_LPRC_REC_ENERGY);
}


void DlgConfigExt::iOnCheckTrigType(unsigned IDC, unsigned uBitTrigType)
{
    if(((CButton *)GetDlgItem(IDC))->GetCheck())
    {
        m_uBitsTrigType |= uBitTrigType;
    }
    else
    {
        m_uBitsTrigType &= ~uBitTrigType;
    }

    VzLPRClient_SetPlateTrigType(m_hLPRC, m_uBitsTrigType);
}

void DlgConfigExt::OnBnClickedChkEnableTrigStable()
{
    iOnCheckTrigType(IDC_CHK_ENABLE_TRIG_STABLE, VZ_LPRC_TRIG_ENABLE_STABLE);
}

void DlgConfigExt::OnBnClickedChkEnableTrigVloop()
{
    iOnCheckTrigType(IDC_CHK_ENABLE_TRIG_VLOOP, VZ_LPRC_TRIG_ENABLE_VLOOP);
}

void DlgConfigExt::OnBnClickedChkEnableTrigIoIn1()
{
    iOnCheckTrigType(IDC_CHK_ENABLE_TRIG_IO_IN1, VZ_LPRC_TRIG_ENABLE_IO_IN1);
}

void DlgConfigExt::OnBnClickedChkEnableTrigIoIn2()
{
    iOnCheckTrigType(IDC_CHK_ENABLE_TRIG_IO_IN2, VZ_LPRC_TRIG_ENABLE_IO_IN2);
}

void DlgConfigExt::OnBnClickedChkPlatePos()
{
	iOnCheckDrawMode( );
}

void DlgConfigExt::OnBnClickedChkRealtimeResult()
{
	iOnCheckDrawMode( );
}

void DlgConfigExt::OnBnClickedChkVloopAndArea()
{
	iOnCheckDrawMode( );
}

void DlgConfigExt::iOnCheckDrawMode( )
{
	BOOL bShowPlatePos			= GetIsCheckedByID( IDC_CHK_PLATE_POS );
	BOOL bShowRealtimeResult	= GetIsCheckedByID( IDC_CHK_REALTIME_RESULT );
	BOOL bShowVloopAndArea		= GetIsCheckedByID( IDC_CHK_VLOOP_AND_AREA );

	VZ_LPRC_DRAWMODE drawMode = {0};
	drawMode.byDspAddRule		= bShowVloopAndArea;
	drawMode.byDspAddTarget		= bShowRealtimeResult;
	drawMode.byDspAddTrajectory	= bShowPlatePos;
	int ret = VzLPRClient_SetDrawMode(m_hLPRC, &drawMode);
	ret = 0;
}

BOOL DlgConfigExt::GetIsCheckedByID( unsigned IDC )
{
	BOOL check = FALSE;

	if(((CButton *)GetDlgItem(IDC))->GetCheck())
	{
		check = TRUE;
	}

	return check;
}
void DlgConfigExt::OnBnClickedChkEnableTrigIoIn3()
{
	iOnCheckTrigType(IDC_CHK_ENABLE_TRIG_IO_IN3, VZ_LPRC_TRIG_ENABLE_IO_IN3);
}


void DlgConfigExt::OnBnClickedRecTypeEmergency()
{
	iOnCheckRecType(IDC_REC_TYPE_EMERGENCY, VZ_LPRC_REC_EMERGENCY);
}

void DlgConfigExt::OnBnClickedRecTypeConsulate()
{
	iOnCheckRecType(IDC_REC_TYPE_CONSULATE, VZ_LPRC_REC_CONSULATE);
}
