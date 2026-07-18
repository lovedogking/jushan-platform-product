#include "stdafx.h"
#include "OutputConfig.h"
#include "VzLPRClientSDK.h"
#include <VzLPRFaceClientSDK.h>

IMPLEMENT_DYNAMIC(COutputConfig, CDialog)

//***********************************************************
// 函数:      COutputConfig
// 全称:  	  COutputConfig::COutputConfig
// 属性:      public 
// 返回:      
// 说明:      默认构造函数，初始化数据
//***********************************************************
COutputConfig::COutputConfig()
	: CDlgOutputAndInBase()
{
	initData();
}

COutputConfig::~COutputConfig()
{
}

//***********************************************************
// 函数:      refresh
// 全称:  	  COutputConfig::refresh
// 属性:      public 
// 返回:      void
// 说明:      刷新数据
//***********************************************************
void COutputConfig::refresh()
{
	int nRet = VzLPRClient_GetOutputConfig(m_hLPRClient, &m_oConfigInfo);
	
	if (nRet != SUCCESS)
		return;
	UpdateData(false);
}

void COutputConfig::MoveCtrl(int ID, int left_value)
{
	CWnd *pWnd = GetDlgItem(ID);
	if( pWnd != NULL )
	{
		CRect rect;
		pWnd->GetWindowRect(&rect);
		ScreenToClient(&rect);
		rect.left  -= left_value;
		rect.right -= left_value;
	
		pWnd->MoveWindow(rect);
	}
}

//***********************************************************
// 函数:      initData
// 全称:  	  COutputConfig::initData
// 属性:      protected 
// 返回:      void
// 说明:      初始化数据
//***********************************************************
void COutputConfig::initData()
{
	VZ_LPRC_OutputConfig &oWL  = m_oConfigInfo.oConfigInfo[nWhiteList];		//通过
	VZ_LPRC_OutputConfig &oNWL = m_oConfigInfo.oConfigInfo[nNotWhiteList];	//未通过
	VZ_LPRC_OutputConfig &oNL  = m_oConfigInfo.oConfigInfo[nNoLicence];		//无车牌
	VZ_LPRC_OutputConfig &oBL  = m_oConfigInfo.oConfigInfo[nBlackList];		//黑名单
	VZ_LPRC_OutputConfig &oEI1 = m_oConfigInfo.oConfigInfo[nExtIoctl1];		//开关量/电平输入 1
	VZ_LPRC_OutputConfig &oEI2 = m_oConfigInfo.oConfigInfo[nExtIoctl2];		//开关量/电平输入 2
	VZ_LPRC_OutputConfig &oEI3 = m_oConfigInfo.oConfigInfo[nExtIoctl3];		//开关量/电平输入 3
	VZ_LPRC_OutputConfig &oSP  = m_oConfigInfo.oConfigInfo[nSpecialPlates];	//特殊车牌
	VZ_LPRC_OutputConfig &oEI4 = m_oConfigInfo.oConfigInfo[nExtIoctl4];		//开关量/电平输入 4

	oWL.eInputType  = nWhiteList;
	oNWL.eInputType = nNotWhiteList;
	oNL.eInputType  = nNoLicence;
	oBL.eInputType  = nBlackList;
	oEI1.eInputType = nExtIoctl1;
	oEI2.eInputType = nExtIoctl2;
	oEI3.eInputType = nExtIoctl3;
	oSP.eInputType	= nSpecialPlates;
	oEI4.eInputType = nExtIoctl4;
}

//***********************************************************
// 函数:      initData
// 全称:  	  COutputConfig::initData
// 属性:      protected 
// 返回:      void
// 说明:      数据变量映射
// 参数:      CDataExchange * pDX
//***********************************************************
void COutputConfig::DoDataExchange(CDataExchange* pDX)
{
	CDialog::DoDataExchange(pDX);

	//白名单通过验证
	VZ_LPRC_OutputConfig& oWL = m_oConfigInfo.oConfigInfo[nWhiteList];
	DDX_Check(pDX, IDC_CHECK_1, oWL.switchout1);
	DDX_Check(pDX, IDC_CHECK_2, oWL.switchout2);
	DDX_Check(pDX, IDC_CHECK_3, oWL.switchout3);
	DDX_Check(pDX, IDC_CHECK_4, oWL.switchout4);
	DDX_Check(pDX, IDC_CHECK_5, oWL.levelout1);
	DDX_Check(pDX, IDC_CHECK_6, oWL.levelout2);
	DDX_Check(pDX, IDC_CHECK_7, oWL.rs485out1);
	DDX_Check(pDX, IDC_CHECK_8, oWL.rs485out2);

	//白名单未通过
	VZ_LPRC_OutputConfig& oNWL = m_oConfigInfo.oConfigInfo[nNotWhiteList];
	DDX_Check(pDX, IDC_CHECK_11, oNWL.switchout1);
	DDX_Check(pDX, IDC_CHECK_12, oNWL.switchout2);
	DDX_Check(pDX, IDC_CHECK_13, oNWL.switchout3);
	DDX_Check(pDX, IDC_CHECK_14, oNWL.switchout4);
	DDX_Check(pDX, IDC_CHECK_15, oNWL.levelout1);
	DDX_Check(pDX, IDC_CHECK_16, oNWL.levelout2);
	DDX_Check(pDX, IDC_CHECK_17, oNWL.rs485out1);
	DDX_Check(pDX, IDC_CHECK_18, oNWL.rs485out2);

	
	//无车牌
	VZ_LPRC_OutputConfig& oNL = m_oConfigInfo.oConfigInfo[nNoLicence];
	DDX_Check(pDX, IDC_CHECK_21, oNL.switchout1);
	DDX_Check(pDX, IDC_CHECK_22, oNL.switchout2);
	DDX_Check(pDX, IDC_CHECK_23, oNL.switchout3);
	DDX_Check(pDX, IDC_CHECK_24, oNL.switchout4);
	DDX_Check(pDX, IDC_CHECK_25, oNL.levelout1);
	DDX_Check(pDX, IDC_CHECK_26, oNL.levelout2);
	DDX_Check(pDX, IDC_CHECK_27, oNL.rs485out1);
	DDX_Check(pDX, IDC_CHECK_28, oNL.rs485out2);

	//黑名单
	VZ_LPRC_OutputConfig& oBL = m_oConfigInfo.oConfigInfo[nBlackList];
	DDX_Check(pDX, IDC_CHECK_31, oBL.switchout1);
	DDX_Check(pDX, IDC_CHECK_32, oBL.switchout2);
	DDX_Check(pDX, IDC_CHECK_33, oBL.switchout3);
	DDX_Check(pDX, IDC_CHECK_34, oBL.switchout4);
	DDX_Check(pDX, IDC_CHECK_35, oBL.levelout1);
	DDX_Check(pDX, IDC_CHECK_36, oBL.levelout2);
	DDX_Check(pDX, IDC_CHECK_37, oBL.rs485out1);
	DDX_Check(pDX, IDC_CHECK_38, oBL.rs485out2);

	//开关量/电平输入 1 
	VZ_LPRC_OutputConfig& oEI1 = m_oConfigInfo.oConfigInfo[nExtIoctl1];
	DDX_Check(pDX, IDC_CHECK_41, oEI1.switchout1);
	DDX_Check(pDX, IDC_CHECK_42, oEI1.switchout2);
	DDX_Check(pDX, IDC_CHECK_43, oEI1.switchout3);
	DDX_Check(pDX, IDC_CHECK_44, oEI1.switchout4);
	DDX_Check(pDX, IDC_CHECK_45, oEI1.levelout1);
	DDX_Check(pDX, IDC_CHECK_46, oEI1.levelout2);


	//开关量/电平输入 2 
	VZ_LPRC_OutputConfig& oEI2 = m_oConfigInfo.oConfigInfo[nExtIoctl2];
	DDX_Check(pDX, IDC_CHECK_51, oEI2.switchout1);
	DDX_Check(pDX, IDC_CHECK_52, oEI2.switchout2);
	DDX_Check(pDX, IDC_CHECK_53, oEI2.switchout3);
	DDX_Check(pDX, IDC_CHECK_54, oEI2.switchout4);
	DDX_Check(pDX, IDC_CHECK_55, oEI2.levelout1);
	DDX_Check(pDX, IDC_CHECK_56, oEI2.levelout2);

	//开关量/电平输入 3
	VZ_LPRC_OutputConfig& oEI3 = m_oConfigInfo.oConfigInfo[nExtIoctl3];
	DDX_Check(pDX, IDC_CHECK_61, oEI3.switchout1);
	DDX_Check(pDX, IDC_CHECK_62, oEI3.switchout2);
	DDX_Check(pDX, IDC_CHECK_63, oEI3.switchout3);
	DDX_Check(pDX, IDC_CHECK_64, oEI3.switchout4);
	DDX_Check(pDX, IDC_CHECK_65, oEI3.levelout1);
	DDX_Check(pDX, IDC_CHECK_66, oEI3.levelout2);


	// 特殊车牌
	VZ_LPRC_OutputConfig& oSP = m_oConfigInfo.oConfigInfo[nSpecialPlates];
	DDX_Check(pDX, IDC_CHECK_71, oSP.switchout1);
	DDX_Check(pDX, IDC_CHECK_72, oSP.switchout2);
	DDX_Check(pDX, IDC_CHECK_73, oSP.switchout3);
	DDX_Check(pDX, IDC_CHECK_74, oSP.switchout4);
	DDX_Check(pDX, IDC_CHECK_75, oSP.levelout1);
	DDX_Check(pDX, IDC_CHECK_76, oSP.levelout2);
	DDX_Check(pDX, IDC_CHECK_77, oSP.rs485out1);
	DDX_Check(pDX, IDC_CHECK_78, oSP.rs485out2);

	//开关量/电平输入 4
	VZ_LPRC_OutputConfig& oEI4 = m_oConfigInfo.oConfigInfo[nExtIoctl4];
	DDX_Check(pDX, IDC_CHECK_67, oEI4.switchout1);
	DDX_Check(pDX, IDC_CHECK_68, oEI4.switchout2);
	DDX_Check(pDX, IDC_CHECK_69, oEI4.switchout3);
	DDX_Check(pDX, IDC_CHECK_70, oEI4.switchout4);
	DDX_Check(pDX, IDC_CHECK_79, oEI4.levelout1);
	DDX_Check(pDX, IDC_CHECK_80, oEI4.levelout2);
}


BEGIN_MESSAGE_MAP(COutputConfig, CDialog)
	ON_BN_CLICKED(IDOK, &COutputConfig::OnBnClickedOk)
END_MESSAGE_MAP()


// COutputConfig message handlers

//***********************************************************
// 函数:      OnBnClickedOk
// 全称:  	  COutputConfig::OnBnClickedOk
// 属性:      public 
// 返回:      void
// 说明:      设置输出配置
//***********************************************************
void COutputConfig::OnBnClickedOk()
{
	UpdateData(true);
	// TODO: Add your control notification handler code here	
	bool bRet = VzLPRClient_SetOutputConfig(m_hLPRClient, &m_oConfigInfo)== SUCCESS;

	if (bRet)
		MessageBox("设置输出配置成功！", "输出配置", MB_OK);
	else
		MessageBox("设置输出配置失败！", "输出配置", MB_OK);
}
BOOL COutputConfig::OnInitDialog()
{
	CDlgOutputAndInBase::OnInitDialog();

	// TODO:  Add extra initialization here
	// int board_type = 0;
	// int ret_board = VzLPRClient_GetHwBoardType(m_hLPRClient, &board_type);

	int nDeviceType = 0;
	VzLPRClient_GetCameraConfig(m_hLPRClient, VZ_GET_LPR_DEVICE_TYPE, 0, &nDeviceType, sizeof(int));
	if (nDeviceType == 1)
	{
		GetDlgItem(IDC_STATIC_3)->ShowWindow(SW_HIDE);
		GetDlgItem(IDC_CHECK_3)->ShowWindow(SW_HIDE);
		GetDlgItem(IDC_CHECK_13)->ShowWindow(SW_HIDE);
		GetDlgItem(IDC_CHECK_23)->ShowWindow(SW_HIDE);
		GetDlgItem(IDC_CHECK_33)->ShowWindow(SW_HIDE);
		GetDlgItem(IDC_CHECK_73)->ShowWindow(SW_HIDE);
		GetDlgItem(IDC_CHECK_43)->ShowWindow(SW_HIDE);
		GetDlgItem(IDC_CHECK_53)->ShowWindow(SW_HIDE);
		GetDlgItem(IDC_CHECK_63)->ShowWindow(SW_HIDE);

		GetDlgItem(IDC_STATIC_4)->ShowWindow(SW_HIDE);
		GetDlgItem(IDC_CHECK_4)->ShowWindow(SW_HIDE);
		GetDlgItem(IDC_CHECK_14)->ShowWindow(SW_HIDE);
		GetDlgItem(IDC_CHECK_24)->ShowWindow(SW_HIDE);
		GetDlgItem(IDC_CHECK_34)->ShowWindow(SW_HIDE);
		GetDlgItem(IDC_CHECK_74)->ShowWindow(SW_HIDE);
		GetDlgItem(IDC_CHECK_44)->ShowWindow(SW_HIDE);
		GetDlgItem(IDC_CHECK_54)->ShowWindow(SW_HIDE);
		GetDlgItem(IDC_CHECK_64)->ShowWindow(SW_HIDE);

		GetDlgItem(IDC_STATIC_5)->ShowWindow(SW_HIDE);
		GetDlgItem(IDC_CHECK_5)->ShowWindow(SW_HIDE);
		GetDlgItem(IDC_CHECK_15)->ShowWindow(SW_HIDE);
		GetDlgItem(IDC_CHECK_25)->ShowWindow(SW_HIDE);
		GetDlgItem(IDC_CHECK_35)->ShowWindow(SW_HIDE);
		GetDlgItem(IDC_CHECK_75)->ShowWindow(SW_HIDE);
		GetDlgItem(IDC_CHECK_45)->ShowWindow(SW_HIDE);
		GetDlgItem(IDC_CHECK_55)->ShowWindow(SW_HIDE);
		GetDlgItem(IDC_CHECK_65)->ShowWindow(SW_HIDE);

		GetDlgItem(IDC_STATIC_6)->ShowWindow(SW_HIDE);
		GetDlgItem(IDC_CHECK_6)->ShowWindow(SW_HIDE);
		GetDlgItem(IDC_CHECK_16)->ShowWindow(SW_HIDE);
		GetDlgItem(IDC_CHECK_26)->ShowWindow(SW_HIDE);
		GetDlgItem(IDC_CHECK_36)->ShowWindow(SW_HIDE);
		GetDlgItem(IDC_CHECK_76)->ShowWindow(SW_HIDE);
		GetDlgItem(IDC_CHECK_46)->ShowWindow(SW_HIDE);
		GetDlgItem(IDC_CHECK_56)->ShowWindow(SW_HIDE);
		GetDlgItem(IDC_CHECK_66)->ShowWindow(SW_HIDE);

		GetDlgItem(IDC_STATIC_8)->ShowWindow(SW_HIDE);
		GetDlgItem(IDC_CHECK_8)->ShowWindow(SW_HIDE);
		GetDlgItem(IDC_CHECK_18)->ShowWindow(SW_HIDE);
		GetDlgItem(IDC_CHECK_28)->ShowWindow(SW_HIDE);
		GetDlgItem(IDC_CHECK_38)->ShowWindow(SW_HIDE);
		GetDlgItem(IDC_CHECK_78)->ShowWindow(SW_HIDE);

		GetDlgItem(IDC_CHECK_69)->ShowWindow(SW_HIDE);
		GetDlgItem(IDC_CHECK_70)->ShowWindow(SW_HIDE);
		GetDlgItem(IDC_CHECK_79)->ShowWindow(SW_HIDE);
		GetDlgItem(IDC_CHECK_80)->ShowWindow(SW_HIDE);

		MoveCtrl(IDC_STATIC_7, 320);
		MoveCtrl(IDC_CHECK_7, 320);
		MoveCtrl(IDC_CHECK_17, 320);
		MoveCtrl(IDC_CHECK_27, 320);
		MoveCtrl(IDC_CHECK_37, 320);
		MoveCtrl(IDC_CHECK_77, 320);
	}
	else
	{
		int board_version = 0;
		long long exdataSize = 0;
		int ret = VzLPRClient_GetHwBoardVersion( m_hLPRClient, &board_version, &exdataSize );
		if( board_version == 0x3005 || board_version == 0x3045 || board_version == 0x3085 || board_version == 0x3145 || board_version == 0x3185 || board_version == 0x31C5 )
		{
			GetDlgItem(IDC_STATIC_3)->ShowWindow(SW_HIDE);
			GetDlgItem(IDC_CHECK_3)->ShowWindow(SW_HIDE);
			GetDlgItem(IDC_CHECK_13)->ShowWindow(SW_HIDE);
			GetDlgItem(IDC_CHECK_23)->ShowWindow(SW_HIDE);
			GetDlgItem(IDC_CHECK_33)->ShowWindow(SW_HIDE);
			GetDlgItem(IDC_CHECK_73)->ShowWindow(SW_HIDE);
			GetDlgItem(IDC_CHECK_43)->ShowWindow(SW_HIDE);
			GetDlgItem(IDC_CHECK_53)->ShowWindow(SW_HIDE);
			GetDlgItem(IDC_CHECK_63)->ShowWindow(SW_HIDE);

			GetDlgItem(IDC_STATIC_4)->ShowWindow(SW_HIDE);
			GetDlgItem(IDC_CHECK_4)->ShowWindow(SW_HIDE);
			GetDlgItem(IDC_CHECK_14)->ShowWindow(SW_HIDE);
			GetDlgItem(IDC_CHECK_24)->ShowWindow(SW_HIDE);
			GetDlgItem(IDC_CHECK_34)->ShowWindow(SW_HIDE);
			GetDlgItem(IDC_CHECK_74)->ShowWindow(SW_HIDE);
			GetDlgItem(IDC_CHECK_44)->ShowWindow(SW_HIDE);
			GetDlgItem(IDC_CHECK_54)->ShowWindow(SW_HIDE);
			GetDlgItem(IDC_CHECK_64)->ShowWindow(SW_HIDE);

			GetDlgItem(IDC_STATIC_5)->ShowWindow(SW_HIDE);
			GetDlgItem(IDC_CHECK_5)->ShowWindow(SW_HIDE);
			GetDlgItem(IDC_CHECK_15)->ShowWindow(SW_HIDE);
			GetDlgItem(IDC_CHECK_25)->ShowWindow(SW_HIDE);
			GetDlgItem(IDC_CHECK_35)->ShowWindow(SW_HIDE);
			GetDlgItem(IDC_CHECK_75)->ShowWindow(SW_HIDE);
			GetDlgItem(IDC_CHECK_45)->ShowWindow(SW_HIDE);
			GetDlgItem(IDC_CHECK_55)->ShowWindow(SW_HIDE);
			GetDlgItem(IDC_CHECK_65)->ShowWindow(SW_HIDE);

			GetDlgItem(IDC_STATIC_6)->ShowWindow(SW_HIDE);
			GetDlgItem(IDC_CHECK_6)->ShowWindow(SW_HIDE);
			GetDlgItem(IDC_CHECK_16)->ShowWindow(SW_HIDE);
			GetDlgItem(IDC_CHECK_26)->ShowWindow(SW_HIDE);
			GetDlgItem(IDC_CHECK_36)->ShowWindow(SW_HIDE);
			GetDlgItem(IDC_CHECK_76)->ShowWindow(SW_HIDE);
			GetDlgItem(IDC_CHECK_46)->ShowWindow(SW_HIDE);
			GetDlgItem(IDC_CHECK_56)->ShowWindow(SW_HIDE);
			GetDlgItem(IDC_CHECK_66)->ShowWindow(SW_HIDE);

			GetDlgItem(IDC_STATIC_8)->ShowWindow(SW_HIDE);
			GetDlgItem(IDC_CHECK_8)->ShowWindow(SW_HIDE);
			GetDlgItem(IDC_CHECK_18)->ShowWindow(SW_HIDE);
			GetDlgItem(IDC_CHECK_28)->ShowWindow(SW_HIDE);
			GetDlgItem(IDC_CHECK_38)->ShowWindow(SW_HIDE);
			GetDlgItem(IDC_CHECK_78)->ShowWindow(SW_HIDE);

			MoveCtrl(IDC_STATIC_7, 320);
			MoveCtrl(IDC_CHECK_7, 320);
			MoveCtrl(IDC_CHECK_17, 320);
			MoveCtrl(IDC_CHECK_27, 320);
			MoveCtrl(IDC_CHECK_37, 320);
			MoveCtrl(IDC_CHECK_77, 320);
		}
		else if( board_version == 0x30C5 || board_version == 0x3105 )
		{
			GetDlgItem(IDC_STATIC_5)->ShowWindow(SW_HIDE);
			GetDlgItem(IDC_CHECK_5)->ShowWindow(SW_HIDE);
			GetDlgItem(IDC_CHECK_15)->ShowWindow(SW_HIDE);
			GetDlgItem(IDC_CHECK_25)->ShowWindow(SW_HIDE);
			GetDlgItem(IDC_CHECK_35)->ShowWindow(SW_HIDE);
			GetDlgItem(IDC_CHECK_75)->ShowWindow(SW_HIDE);
			GetDlgItem(IDC_CHECK_45)->ShowWindow(SW_HIDE);
			GetDlgItem(IDC_CHECK_55)->ShowWindow(SW_HIDE);
			GetDlgItem(IDC_CHECK_65)->ShowWindow(SW_HIDE);

			GetDlgItem(IDC_STATIC_6)->ShowWindow(SW_HIDE);
			GetDlgItem(IDC_CHECK_6)->ShowWindow(SW_HIDE);
			GetDlgItem(IDC_CHECK_16)->ShowWindow(SW_HIDE);
			GetDlgItem(IDC_CHECK_26)->ShowWindow(SW_HIDE);
			GetDlgItem(IDC_CHECK_36)->ShowWindow(SW_HIDE);
			GetDlgItem(IDC_CHECK_76)->ShowWindow(SW_HIDE);
			GetDlgItem(IDC_CHECK_46)->ShowWindow(SW_HIDE);
			GetDlgItem(IDC_CHECK_56)->ShowWindow(SW_HIDE);
			GetDlgItem(IDC_CHECK_66)->ShowWindow(SW_HIDE);

			MoveCtrl(IDC_STATIC_7, 150);
			MoveCtrl(IDC_CHECK_7, 150);
			MoveCtrl(IDC_CHECK_17, 150);
			MoveCtrl(IDC_CHECK_27, 150);
			MoveCtrl(IDC_CHECK_37, 150);
			MoveCtrl(IDC_CHECK_77, 150);

			MoveCtrl(IDC_STATIC_8, 120);
			MoveCtrl(IDC_CHECK_8, 120);
			MoveCtrl(IDC_CHECK_18, 120);
			MoveCtrl(IDC_CHECK_28, 120);
			MoveCtrl(IDC_CHECK_38, 120);
			MoveCtrl(IDC_CHECK_78, 120);
		}
	}
	
	//else if(board_version == 0x3105)
	//{

	//}

	return TRUE;  // return TRUE unless you set the focus to a control
	// EXCEPTION: OCX Property Pages should return FALSE
}
