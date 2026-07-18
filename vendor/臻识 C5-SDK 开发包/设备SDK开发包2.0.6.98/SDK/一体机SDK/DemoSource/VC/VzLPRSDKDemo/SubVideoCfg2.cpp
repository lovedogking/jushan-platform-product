// SubVideoCfg2.cpp : implementation file
//

#include "stdafx.h"
#include "VzLPRSDKDemo.h"
#include "SubVideoCfg2.h"


// CSubVideoCfg2 dialog

IMPLEMENT_DYNAMIC(CSubVideoCfg2, CDialog)

CSubVideoCfg2::CSubVideoCfg2(CWnd* pParent /*=NULL*/)
	: CDialog(CSubVideoCfg2::IDD, pParent)
	, m_strBrt(_T(""))
	, m_strCst(_T(""))
	, m_strSat(_T(""))
	, m_strHue(_T(""))
{
	m_hLPRClient = NULL;
}

CSubVideoCfg2::~CSubVideoCfg2()
{
}

void CSubVideoCfg2::DoDataExchange(CDataExchange* pDX)
{
	CDialog::DoDataExchange(pDX);
	DDX_Control(pDX, IDC_CMB_EXPOSURE_TIME, m_cmbExposureTime);
	DDX_Control(pDX, IDC_CMB_IMG_POS, m_cmbImgPos);
	DDX_Text(pDX, IDC_LBL_BRIGHT, m_strBrt);
	DDX_Control(pDX, IDC_SLIDER_BRIGHT, m_sliderBrt);
	DDX_Text(pDX, IDC_LBL_CONTRAST, m_strCst);
	DDX_Control(pDX, IDC_SLIDER_CONTRAST, m_sliderCst);
	DDX_Text(pDX, IDC_LBL_SATURATION, m_strSat);
	DDX_Control(pDX, IDC_SLIDER_SATURATION, m_sliderSat);
	DDX_Text(pDX, IDC_LBL_DEFINITION, m_strHue);
	DDX_Control(pDX, IDC_SLIDER_DEFINITION, m_sliderHue);
	DDX_Control(pDX, IDC_CMB_DENOISEMODE, m_cmbDeNoiseMode);
	DDX_Control(pDX, IDC_CMB_DENOISELENTH, m_cmbDeNoiseLenth);
	DDX_Control(pDX, IDC_CMB_VIDEO_STANDARD, m_cmbStandard);
	DDX_Control(pDX, IDC_STATIC_DENOISEMODE, m_staticDNMode);
	DDX_Control(pDX, IDC_STATIC_VIDEO_STANDARD, m_staticStanderd);
	DDX_Control(pDX, IDC_STATIC_DENOISELENTH, m_staticDNStrength);
	DDX_Control(pDX, IDC_STATIC_IMGPOS, m_staticImgPos);
	DDX_Control(pDX, IDC_STATIC_TIME, m_staticExposureTime);
	DDX_Control(pDX, IDOK, m_btnRestore);
}


BEGIN_MESSAGE_MAP(CSubVideoCfg2, CDialog)
	ON_BN_CLICKED(IDOK, &CSubVideoCfg2::OnBnClickedOk)
	ON_WM_HSCROLL()
	ON_CBN_SELCHANGE(IDC_CMB_EXPOSURE_TIME, &CSubVideoCfg2::OnCbnSelchangeCmbExposureTime)
	ON_CBN_SELCHANGE(IDC_CMB_IMG_POS, &CSubVideoCfg2::OnCbnSelchangeCmbImgPos)
	ON_CBN_SELCHANGE(IDC_CMB_DENOISEMODE, &CSubVideoCfg2::OnCbnSelchangeCmbDNMode)
	ON_CBN_SELCHANGE(IDC_CMB_DENOISELENTH, &CSubVideoCfg2::OnCbnSelchangeCmbDNStrength)
	ON_CBN_SELCHANGE(IDC_CMB_VIDEO_STANDARD, &CSubVideoCfg2::OnCbnSelchangeCmbVideoStandard)
END_MESSAGE_MAP()


void CSubVideoCfg2::SetLPRHandle( VzLPRClientHandle hLPRClient )
{
	m_hLPRClient = hLPRClient;
}

// 恢复默认
void CSubVideoCfg2::OnBnClickedOk()
{
	if ( m_hLPRClient != NULL )
	{
        if (m_nHWType == 0)
        {
			int brt = 50;
			m_sliderBrt.SetPos(brt);
			m_strBrt.Format("%d", brt);

			int cst =40;
			m_sliderCst.SetPos(cst);
			m_strCst.Format("%d", cst);

			int sat = 30;
			m_sliderSat.SetPos(sat);
			m_strSat.Format("%d", sat);

			int hue = 50;
			m_sliderHue.SetPos(hue); 
			m_strHue.Format("%d", hue);

			UpdateData(FALSE);

			VzLPRClient_SetVideoPara(m_hLPRClient, brt, cst, sat, hue);

			VzLPRClient_SetShutter(m_hLPRClient, 3);
			m_cmbExposureTime.SetCurSel(1);

			VzLPRClient_SetFlip(m_hLPRClient, 0);
			m_cmbImgPos.SetCurSel(0);

			VzLPRClient_SetFrequency(m_hLPRClient, 1);
			m_cmbStandard.SetCurSel(1);
        }
		else if (m_nHWType == 2)
		{
			int brt = 128;
			m_sliderBrt.SetPos(brt);
			m_strBrt.Format("%d", brt);

			int cst = 138;
			m_sliderCst.SetPos(cst);
			m_strCst.Format("%d", cst);

			int sat = 128;
			m_sliderSat.SetPos(sat);
			m_strSat.Format("%d", sat);

			int hue = 140;
			m_sliderHue.SetPos(hue); 
			m_strHue.Format("%d", hue);

			UpdateData(FALSE);

			VzLPRClient_SetVideoPara(m_hLPRClient, brt, cst, sat, hue);

			VzLPRClient_SetShutter(m_hLPRClient, 96);
			m_cmbExposureTime.SetCurSel(0);

			VzLPRClient_SetFlip(m_hLPRClient, 0);
			m_cmbImgPos.SetCurSel(0);

			VzLPRClient_SetDenoise(m_hLPRClient, 3, 2);
			m_cmbDeNoiseMode.SetCurSel(3);
			m_cmbDeNoiseLenth.SetCurSel(2);
		}
	}
}

BOOL CSubVideoCfg2::OnInitDialog()
{
	CDialog::OnInitDialog();

	int nBoardType = 0;
	int nRet = VzLPRClient_GetHwBoardType(m_hLPRClient,&nBoardType);

	if (nBoardType == 2)
	{
		m_nHWType = 2;
		Init8127VideoParam();	
	}
	else
	{
		m_nHWType = 0;
		Init3730VideoParam();
	}
		
	m_cmbImgPos.AddString("原始图像");
	m_cmbImgPos.AddString("上下翻转");
	m_cmbImgPos.AddString("左右翻转");
	m_cmbImgPos.AddString("中心翻转");
	m_cmbImgPos.SetCurSel(0);


	Load( );

	return TRUE;
}

void CSubVideoCfg2::Init3730VideoParam()
{
	m_sliderBrt.SetRange(0, 100);
	m_sliderCst.SetRange(0, 100);
	m_sliderSat.SetRange(0, 100);
	m_sliderHue.SetRange(0, 100);

	m_cmbStandard.AddString("MaxOrZero");
	m_cmbStandard.AddString("50Hz");
	m_cmbStandard.AddString("60Hz");
	m_cmbStandard.SetCurSel(0);

	m_cmbExposureTime.AddString("0~8ms 停车场推荐");
	m_cmbExposureTime.AddString("0~4ms");
	m_cmbExposureTime.AddString("0~2ms 卡口推荐");
	m_cmbExposureTime.SetCurSel(0);

	m_cmbDeNoiseMode.ShowWindow(SW_HIDE);
	m_cmbDeNoiseLenth.ShowWindow(SW_HIDE);
    m_staticDNMode.ShowWindow(SW_HIDE);
	m_staticDNStrength.ShowWindow(SW_HIDE);

	m_staticStanderd.SetWindowPos(NULL, 57, 185, 0, 0, SWP_NOZORDER|SWP_NOSIZE);
	m_cmbStandard.SetWindowPos(NULL, 155, 185, 0, 0, SWP_NOZORDER|SWP_NOSIZE);

	m_staticExposureTime.SetWindowPos(NULL, 57, 220, 0, 0, SWP_NOZORDER|SWP_NOSIZE);
    m_cmbExposureTime.SetWindowPos(NULL, 155, 220, 0, 0, SWP_NOZORDER|SWP_NOSIZE);

    m_staticImgPos.SetWindowPos(NULL, 57, 255, 0, 0, SWP_NOZORDER|SWP_NOSIZE);
	m_cmbImgPos.SetWindowPos(NULL, 155, 255, 0, 0, SWP_NOZORDER|SWP_NOSIZE);

	m_btnRestore.SetWindowPos(NULL, 245, 315, 0, 0, SWP_NOZORDER|SWP_NOSIZE);
	
}

void CSubVideoCfg2::Init8127VideoParam()
{
	m_sliderBrt.SetRange(0, 255);
	m_sliderCst.SetRange(0, 255);
	m_sliderSat.SetRange(0, 255);
	m_sliderHue.SetRange(0, 255);

	m_cmbDeNoiseMode.AddString("OFF");
	m_cmbDeNoiseMode.AddString("SNF");
	m_cmbDeNoiseMode.AddString("TNF");
	m_cmbDeNoiseMode.AddString("SNF+TNF");
	m_cmbDeNoiseMode.SetCurSel(0);

	m_cmbDeNoiseLenth.AddString("自动");
	m_cmbDeNoiseLenth.AddString("低");
	m_cmbDeNoiseLenth.AddString("中");
	m_cmbDeNoiseLenth.AddString("高");
	m_cmbDeNoiseLenth.SetCurSel(0);

	m_cmbExposureTime.AddString("0~3ms");
	m_cmbExposureTime.AddString("0~2ms");
	m_cmbExposureTime.AddString("0~1ms");
	m_cmbExposureTime.SetCurSel(0);

	m_cmbStandard.ShowWindow(SW_HIDE);
	m_staticStanderd.ShowWindow(SW_HIDE);
}

void CSubVideoCfg2::Load( )
{
	if ( m_hLPRClient != NULL )
	{
		int brt = 0, cst = 0, sat = 0, hue = 0;
		int ret = VzLPRClient_GetVideoPara(m_hLPRClient, &brt, &cst, &sat, &hue);
		if ( ret == 0 )
		{
			m_strBrt.Format("%d", brt);
			m_strCst.Format("%d", cst);
			m_strSat.Format("%d", sat);
			m_strHue.Format("%d", hue);

			m_sliderBrt.SetPos(brt);
			m_sliderCst.SetPos(cst);
			m_sliderSat.SetPos(sat);
			m_sliderHue.SetPos(hue);

			UpdateData(FALSE);
		}

		

		int flip = 0;
		ret = VzLPRClient_GetFlip(m_hLPRClient, &flip);
		if ( ret == 0 )
		{
			m_cmbImgPos.SetCurSel(flip);
		}

		//3730
		if (m_nHWType == 0)
		{
			int shutter = 0;
			ret = VzLPRClient_GetShutter(m_hLPRClient, &shutter);
			if ( ret == 0 )
			{
				if ( shutter == 2 )
				{
					m_cmbExposureTime.SetCurSel(0);
				}
				else if( shutter == 3 )
				{
					m_cmbExposureTime.SetCurSel(1);
				}
				else if ( shutter == 4 )
				{
					m_cmbExposureTime.SetCurSel(2);
				}
			}


			int frequency = 0;
			int ret = VzLPRClient_GetFrequency(m_hLPRClient, &frequency);
			if ( ret == 0 )
			{
				m_cmbStandard.SetCurSel(frequency);
			}
		}//8127
		else if (m_nHWType == 2)
		{
			int shutter = 0;
			ret = VzLPRClient_GetShutter(m_hLPRClient, &shutter);
			if ( ret == 0 )
			{
				if ( shutter == 96 )
				{
					m_cmbExposureTime.SetCurSel(0);
				}
				else if( shutter == 64 )
				{
					m_cmbExposureTime.SetCurSel(1);
				}
				else if ( shutter == 32 )
				{
					m_cmbExposureTime.SetCurSel(2);
				}
			}


			int nMode = 0, nStrength = 0;
			int nRet = VzLPRClient_GetDenoise(m_hLPRClient, &nMode, &nStrength);
			if (nMode >= 0 && nStrength >= 0 )
			{
				m_cmbDeNoiseMode.SetCurSel(nMode);
				m_cmbDeNoiseLenth.SetCurSel(nStrength);
			}	
		}	
	}
}

void CSubVideoCfg2::OnHScroll(UINT nSBCode, UINT nPos, CScrollBar* pScrollBar)
{
	int brt =m_sliderBrt.GetPos();
	m_strBrt.Format("%d", brt);

	int cst =m_sliderCst.GetPos();
	m_strCst.Format("%d", cst);

	int sat =m_sliderSat.GetPos();
	m_strSat.Format("%d", sat);

	int hue =m_sliderHue.GetPos(); 
	m_strHue.Format("%d", hue);

	UpdateData(FALSE);

	if( TB_ENDTRACK == nSBCode )
	{
		int nRet = VzLPRClient_SetVideoPara(m_hLPRClient, brt, cst, sat, hue);
	}

	CDialog::OnHScroll(nSBCode, nPos, pScrollBar);
}


void CSubVideoCfg2::OnCbnSelchangeCmbExposureTime()
{
	if(m_nHWType == 0)
	{
		int shutter = 0;
		int curSel = m_cmbExposureTime.GetCurSel();
		if ( curSel == 0 )
		{
			shutter = 2;
		}
		else if( curSel == 1 )
		{
			shutter = 3;
		}
		else if ( curSel == 2 )
		{
			shutter = 4;
		}
		int ret = VzLPRClient_SetShutter(m_hLPRClient, shutter);

	}
	else if (m_nHWType == 2)
	{
		int shutter = 0;
		int curSel = m_cmbExposureTime.GetCurSel();
		if ( curSel == 0 )
		{
			shutter = 96;
		}
		else if( curSel == 1 )
		{
			shutter = 64;
		}
		else if ( curSel == 2 )
		{
			shutter = 32;
		}
		
		int ret = VzLPRClient_SetShutter(m_hLPRClient, shutter);
	}
}

void CSubVideoCfg2::OnCbnSelchangeCmbImgPos()
{
	int flip = m_cmbImgPos.GetCurSel();
	int ret = VzLPRClient_SetFlip(m_hLPRClient, flip);
}

void CSubVideoCfg2::OnCbnSelchangeCmbDNMode()
{
	int mode = m_cmbDeNoiseMode.GetCurSel();
	int strength = m_cmbDeNoiseLenth.GetCurSel();
	int ret = VzLPRClient_SetDenoise(m_hLPRClient, mode, strength);
}

void CSubVideoCfg2::OnCbnSelchangeCmbDNStrength()
{
	int mode = m_cmbDeNoiseMode.GetCurSel();
	int strength = m_cmbDeNoiseLenth.GetCurSel();
	int ret = VzLPRClient_SetDenoise(m_hLPRClient, mode, strength);	
}

void CSubVideoCfg2::OnCbnSelchangeCmbVideoStandard()
{
	int frequency = m_cmbStandard.GetCurSel();
	int ret = VzLPRClient_SetFrequency(m_hLPRClient, frequency);
}
