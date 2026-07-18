// DlgSubVideoCfgR2.cpp : implementation file
//

#include "stdafx.h"
#include "VzLPRSDKDemo.h"
#include "DlgSubVideoCfgR2.h"


// CDlgSubVideoCfgR2 dialog

IMPLEMENT_DYNAMIC(CDlgSubVideoCfgR2, CDialog)

CDlgSubVideoCfgR2::CDlgSubVideoCfgR2(CWnd* pParent /*=NULL*/)
	: CDialog(CDlgSubVideoCfgR2::IDD, pParent)
	, m_strBrt(_T(""))
	, m_strCst(_T(""))
	, m_strSat(_T(""))
	, m_strGain(_T(""))
{
	m_hLPRClient = NULL;

	memset(&m_video_param, 0, sizeof(VZ_LPRC_R_VIDEO_PARAM));
}

CDlgSubVideoCfgR2::~CDlgSubVideoCfgR2()
{
}

void CDlgSubVideoCfgR2::DoDataExchange(CDataExchange* pDX)
{
	CDialog::DoDataExchange(pDX);
	DDX_Control(pDX, IDC_SLIDER_BRIGHT, m_sliderBrt);
	DDX_Control(pDX, IDC_SLIDER_CONTRAST, m_sliderCst);
	DDX_Control(pDX, IDC_SLIDER_SATURATION, m_sliderSat);
	DDX_Control(pDX, IDC_CMB_EXPOSURE_TIME, m_cmbExposureTime);
	DDX_Control(pDX, IDC_CMB_IMG_POS, m_cmbImgPos);
	DDX_Control(pDX, IDC_SLIDER_DEFINITION, m_sliderGain);
	DDX_Text(pDX, IDC_LBL_BRIGHT, m_strBrt);
	DDX_Text(pDX, IDC_LBL_CONTRAST, m_strCst);
	DDX_Text(pDX, IDC_LBL_SATURATION, m_strSat);
	DDX_Text(pDX, IDC_LBL_DEFINITION, m_strGain);
}


BEGIN_MESSAGE_MAP(CDlgSubVideoCfgR2, CDialog)
	ON_WM_HSCROLL()
	ON_CBN_SELCHANGE(IDC_CMB_EXPOSURE_TIME, &CDlgSubVideoCfgR2::OnCbnSelchangeCmbExposureTime)
	ON_CBN_SELCHANGE(IDC_CMB_IMG_POS, &CDlgSubVideoCfgR2::OnCbnSelchangeCmbImgPos)
	ON_BN_CLICKED(IDOK, &CDlgSubVideoCfgR2::OnBnClickedOk)
END_MESSAGE_MAP()


// CDlgSubVideoCfgR2 message handlers

void CDlgSubVideoCfgR2::Load( )
{
	int brt = 0, cst = 0, sat = 0, hue = 0;
	int ret = VzLPRClient_GetVideoPara(m_hLPRClient, &brt, &cst, &sat, &hue);
	if ( ret == 0 )
	{
		m_strBrt.Format("%d", brt);
		m_strCst.Format("%d", cst);
		m_strSat.Format("%d", sat);
		
		m_sliderBrt.SetPos(brt);
		m_sliderCst.SetPos(cst);
		m_sliderSat.SetPos(sat);
	}

	ret = VzLPRClient_RGet_Video_Param(m_hLPRClient, &m_video_param);
	if ( ret == 0 )
	{
		int max_gain = m_video_param.max_gain;

		m_strGain.Format("%d", max_gain);
		m_sliderGain.SetPos(max_gain);
	}

	int flip = 0;
	ret = VzLPRClient_GetFlip(m_hLPRClient, &flip);
	if ( ret == 0 )
	{
		m_cmbImgPos.SetCurSel(flip);
	}
	else
	{
		m_cmbImgPos.SetCurSel(0);
	}

	int shutter = 0;
	ret = VzLPRClient_GetShutter(m_hLPRClient, &shutter);

	if( shutter >= 1 )
	{
		m_cmbExposureTime.SetCurSel(shutter - 1);
	}
	else
	{
		m_cmbExposureTime.SetCurSel( 0 );
	}

	UpdateData(FALSE);
	
}

BOOL CDlgSubVideoCfgR2::OnInitDialog()
{
	CDialog::OnInitDialog();

	m_sliderBrt.SetRange(0, 100);
	m_sliderCst.SetRange(0, 100);
	m_sliderSat.SetRange(0, 100);
	m_sliderGain.SetRange(0, 100);

	m_cmbExposureTime.AddString("0~1ms");
	m_cmbExposureTime.AddString("0~2ms");
	m_cmbExposureTime.AddString("0~3ms");
	m_cmbExposureTime.AddString("0~4ms");
	m_cmbExposureTime.SetCurSel(0);
	
	m_cmbImgPos.AddString("原始图像");
	m_cmbImgPos.AddString("上下翻转");
	m_cmbImgPos.AddString("左右翻转");
	m_cmbImgPos.AddString("中心翻转");

	Load( );

	return TRUE;
}

void CDlgSubVideoCfgR2::SetLPRHandle( VzLPRClientHandle hLPRClient )
{
	m_hLPRClient = hLPRClient;
}

void CDlgSubVideoCfgR2::OnCbnSelchangeCmbExposureTime()
{
	int shutter = m_cmbExposureTime.GetCurSel();
	int ret = VzLPRClient_SetShutter(m_hLPRClient, shutter + 1);
}

void CDlgSubVideoCfgR2::OnCbnSelchangeCmbImgPos()
{
	int flip = m_cmbImgPos.GetCurSel();
	int ret = VzLPRClient_SetFlip(m_hLPRClient, flip);
}

void CDlgSubVideoCfgR2::OnHScroll(UINT nSBCode, UINT nPos, CScrollBar* pScrollBar)
{
	int brt =m_sliderBrt.GetPos();
	m_strBrt.Format("%d", brt);

	int cst =m_sliderCst.GetPos();
	m_strCst.Format("%d", cst);

	int sat =m_sliderSat.GetPos();
	m_strSat.Format("%d", sat);

	int hue = 50;

	int max_gain = m_sliderGain.GetPos(); 
	m_strGain.Format("%d", max_gain);

	UpdateData(FALSE);

	if( TB_ENDTRACK == nSBCode )
	{
		int nRet = VzLPRClient_SetVideoPara(m_hLPRClient, brt, cst, sat, hue);

		m_video_param.brightness = brt;
		m_video_param.contrast   = cst;
		m_video_param.saturation = sat;
		m_video_param.max_gain  = max_gain;
		VzLPRClient_RSet_Video_Param(m_hLPRClient, &m_video_param);
	}

	CDialog::OnHScroll(nSBCode, nPos, pScrollBar);
}

// 恢复默认
void CDlgSubVideoCfgR2::OnBnClickedOk()
{
		int brt = 50;
		m_sliderBrt.SetPos(brt);
		m_strBrt.Format("%d", brt);

		int cst = 50;
		m_sliderCst.SetPos(cst);
		m_strCst.Format("%d", cst);

		int sat = 50;
		m_sliderSat.SetPos(sat);
		m_strSat.Format("%d", sat);

		VzLPRClient_SetVideoPara(m_hLPRClient, brt, cst, sat, 50);

		int max_gain = 50; 
		m_sliderGain.SetPos(max_gain);
		m_strGain.Format("%d", max_gain);

		m_video_param.max_gain  = max_gain;
		m_video_param.brightness = brt;
		m_video_param.contrast = cst;
		m_video_param.saturation = sat;
		m_video_param.hue = 50;
		VzLPRClient_RSet_Video_Param(m_hLPRClient, &m_video_param);

		UpdateData(FALSE);

		VzLPRClient_SetShutter(m_hLPRClient, 3);
		m_cmbExposureTime.SetCurSel(2);

		VzLPRClient_SetFlip(m_hLPRClient, 0);
		m_cmbImgPos.SetCurSel(0);
}
