// SubDlgVideoCfg.cpp : implementation file
//

#include "stdafx.h"
#include "VzLPRSDKDemo.h"
#include "SubDlgVideoCfg.h"


// CSubDlgVideoCfg dialog

IMPLEMENT_DYNAMIC(CSubDlgVideoCfg, CDialog)

CSubDlgVideoCfg::CSubDlgVideoCfg(CWnd* pParent /*=NULL*/)
	: CDialog(CSubDlgVideoCfg::IDD, pParent)
{
	m_hLPRClient = NULL;
	m_nBoardType = 0;
}

CSubDlgVideoCfg::~CSubDlgVideoCfg()
{
}

void CSubDlgVideoCfg::DoDataExchange(CDataExchange* pDX)
{
	CDialog::DoDataExchange(pDX);
	DDX_Control(pDX, IDC_CMB_FRAME_SIZE, m_cmbFrameSize);
	DDX_Control(pDX, IDC_CMB_FRAME_RATE, m_cmbFrameRate);
	DDX_Control(pDX, IDC_CMB_ENCODE_TYPE, m_cmbEncodeType);
	DDX_Control(pDX, IDC_CMB_COMPRESS_MODE, m_cmbCompressMode);
	DDX_Control(pDX, IDC_CMB_IMG_QUALITY, m_cmbImgQuality);
	DDX_Control(pDX, IDC_EDIT_RATEVAL, m_editRateval);
}


BEGIN_MESSAGE_MAP(CSubDlgVideoCfg, CDialog)
	ON_BN_CLICKED(IDOK, &CSubDlgVideoCfg::OnBnClickedOk)
	ON_CBN_SELCHANGE(IDC_CMB_ENCODE_TYPE, &CSubDlgVideoCfg::OnCbnSelchangeCmbEncodeType)
	ON_CBN_SELCHANGE(IDC_CMB_COMPRESS_MODE, &CSubDlgVideoCfg::OnCbnSelchangeCmbCompressMode)
	ON_CBN_SELCHANGE(IDC_CMB_FRAME_SIZE, &CSubDlgVideoCfg::OnCbnSelchangeCmbFrameSize)
END_MESSAGE_MAP()


// CSubDlgVideoCfg message handlers
void CSubDlgVideoCfg::SetLPRHandle( VzLPRClientHandle hLPRClient )
{
	m_hLPRClient = hLPRClient;
}

BOOL CSubDlgVideoCfg::OnInitDialog()
{
	CDialog::OnInitDialog();

	int board_type = 0;
	int ret_board = VzLPRClient_GetHwBoardType(m_hLPRClient, &board_type);
	if (board_type == 2)
	{
		m_nBoardType = 2;
		m_cmbFrameSize.AddString("640x360");
		m_cmbFrameSize.AddString("720x576");
		m_cmbFrameSize.AddString("1280x720");
		m_cmbFrameSize.AddString("1920x1080");

		m_cmbEncodeType.AddString("H264");
	}
	else
	{
		m_nBoardType = 1;

		m_cmbFrameSize.AddString("352x288");
		m_cmbFrameSize.AddString("704x576");
		m_cmbFrameSize.AddString("1280x720");
		m_cmbFrameSize.AddString("1920x1080");

		m_cmbEncodeType.AddString("H264");
		m_cmbEncodeType.AddString("JPEG");
	}

	CString strRate;
	for( int i = 1; i <= 25; i++ )
	{
		strRate.Format("%d", i);
		m_cmbFrameRate.AddString( strRate );
	}

	m_cmbCompressMode.AddString("定码流");
	m_cmbCompressMode.AddString("变码流");

	m_cmbImgQuality.AddString("最流畅");
	m_cmbImgQuality.AddString("较流畅");
	m_cmbImgQuality.AddString("流畅");
	m_cmbImgQuality.AddString("中等");
	m_cmbImgQuality.AddString("清晰");
	m_cmbImgQuality.AddString("较清晰");
	m_cmbImgQuality.AddString("最清晰");

	Load( );

	return TRUE;
}

void CSubDlgVideoCfg::Load( )
{
	if ( m_hLPRClient != NULL )
	{
		//3730
		if (m_nBoardType == 1)
		{
			Get3730Param();
			
		}//8127
		else if (m_nBoardType == 2)
		{
			Get8127Param();

		}
		int nRateval = 0,  modeval = 0, bitval = 0, ratelist = 0, levelval = 0 ;

		//帧率
		int ret = VzLPRClient_GetVideoFrameRate(m_hLPRClient, &nRateval);
		if( ret == 0 && nRateval > 0 )
		{
			m_cmbFrameRate.SetCurSel(nRateval -1 );
		}

		//码流控制
		ret = VzLPRClient_GetVideoCompressMode(m_hLPRClient, &modeval );
		if ( ret == 0 )
		{
			m_cmbCompressMode.SetCurSel(modeval);

			m_editRateval.EnableWindow(modeval == 0?TRUE:FALSE);
			m_cmbImgQuality.EnableWindow(modeval == 0? FALSE:TRUE);	
		}

		//图像质量
		ret = VzLPRClient_GetVideoVBR(m_hLPRClient, &levelval);
		if ( ret == 0 )
		{
			m_cmbImgQuality.SetCurSel(levelval);
		}

		//码流上限
		CString strRateval ;
		ret = VzLPRClient_GetVideoCBR(m_hLPRClient, &bitval, &ratelist );
		if( ret == 0 )
		{
			strRateval.Format("%d", bitval);
		}
		m_editRateval.SetWindowText( strRateval );
	}
}

void CSubDlgVideoCfg::Get3730Param()
{
	//分辨率
	int nSizeVal = 0;
	int ret = VzLPRClient_GetVideoFrameSizeIndex(m_hLPRClient, &nSizeVal);
	if( ret == 0 )
	{
		if( nSizeVal == 0 ) 
		{
			m_cmbFrameSize.SetCurSel(1);
		}
		else if( nSizeVal == 1 )
		{
			m_cmbFrameSize.SetCurSel(0);
		}
		else
		{
			m_cmbFrameSize.SetCurSel(nSizeVal);
		}
		
	}

	//编码方式
	int nEncodeType = 0;
	ret = VzLPRClient_GetVideoEncodeType(m_hLPRClient, &nEncodeType);
	if ( ret == 0 )
	{
		if( nEncodeType == 0 ) {
			m_cmbEncodeType.SetCurSel(0);
			m_cmbCompressMode.EnableWindow(TRUE);
		}
		else 
		{
			m_cmbEncodeType.SetCurSel(1);
			m_cmbCompressMode.EnableWindow(FALSE);
		}
	}
}

void CSubDlgVideoCfg::Get8127Param()
{
	//分辨率
	int nSizeVal = 0;
	int ret = VzLPRClient_GetVideoFrameSizeIndexEx(m_hLPRClient, &nSizeVal);
	if( ret == 0 )
	{
		if( nSizeVal == 41943400 ) 
		{
			m_cmbFrameSize.SetCurSel(0);
		}
		else if( nSizeVal == 47186496 )
		{
			m_cmbFrameSize.SetCurSel(1);
		}
		else if(nSizeVal == 83886800)
		{
			m_cmbFrameSize.SetCurSel(2);
		}
		else if (nSizeVal == 125830200)
		{
			m_cmbFrameSize.SetCurSel(3);
		}
	}

	//编码方式
	int nEncodeType = 0;
	ret = VzLPRClient_GetVideoEncodeType(m_hLPRClient, &nEncodeType);
	if ( ret == 0 )
	{
		if( nEncodeType == 0 ) {
			m_cmbEncodeType.SetCurSel(0);
			m_cmbCompressMode.EnableWindow(TRUE);
		}
	}
}

void CSubDlgVideoCfg::OnBnClickedOk()
{
	CString strRateval;
	m_editRateval.GetWindowText(strRateval);

	int nRate = atoi(strRateval.GetBuffer(0));
	if (m_nBoardType == 1)
	{
		if( nRate <= 99 || nRate > 5000 )
		{
			MessageBox("码流范围为100-5000，请重新输入！");
			return;
		}

		//分辨率
		int nSizeVal = m_cmbFrameSize.GetCurSel();
		if ( nSizeVal == 0 )
		{
			nSizeVal = 1;
		}
		else if( nSizeVal == 1 )
		{
			nSizeVal = 0;
		}

		int ret = VzLPRClient_SetVideoFrameSizeIndex(m_hLPRClient, nSizeVal);
		if ( ret != 0 )
		{
			MessageBox("设置分辨率失败，请重试！");
			return;
		}

		//编码方式
		int nEncodeType = (m_cmbEncodeType.GetCurSel() == 0) ? 0 : 2;
		ret = VzLPRClient_SetVideoEncodeType(m_hLPRClient, nEncodeType);
		if ( ret != 0 )
		{
			MessageBox("设置编码方式失败，请重试！");
			return;
		}
	}
	else if (m_nBoardType == 2)
	{
		if( nRate < 512 || nRate > 5000 )
		{
			MessageBox("码流范围为512-5000，请重新输入！");
			return;
		}

		int nSizeVal = m_cmbFrameSize.GetCurSel();
		switch(nSizeVal)
		{
		case 0:
			nSizeVal = 41943400;break;
		case 1:
			nSizeVal = 47186496;break;
		case 2:
			nSizeVal = 83886800;break;
		case 3:
			nSizeVal = 125830200;break;
		default:
			break;
		}
		
		int ret = VzLPRClient_SetVideoFrameSizeIndexEx(m_hLPRClient, nSizeVal);
		if ( ret != 0 )
		{
			MessageBox("设置分辨率失败，请重试！");
			return;
		}

		//编码方式
		int nEncodeType = m_cmbEncodeType.GetCurSel();
		ret = VzLPRClient_SetVideoEncodeType(m_hLPRClient, nEncodeType);
		if ( ret != 0 )
		{
			MessageBox("设置编码方式失败，请重试！");
			return;
		}
	}

	//帧率
	int nRateval = m_cmbFrameRate.GetCurSel() + 1;
	int ret = VzLPRClient_SetVideoFrameRate(m_hLPRClient, nRateval);
	if ( ret != 0 )
	{
		MessageBox("设置帧率失败，请重试！");
		return;
	}

	//码流控制
	if ( m_cmbCompressMode.IsWindowEnabled() )
	{
		int modeval = m_cmbCompressMode.GetCurSel( );
		ret = VzLPRClient_SetVideoCompressMode(m_hLPRClient, modeval);
		if ( ret != 0 )
		{
			MessageBox("设置码流控制失败，请重试！");
			return;
		}
	}

	//图像质量
	if (m_cmbImgQuality.IsWindowEnabled())
	{
		int level = m_cmbImgQuality.GetCurSel();
		ret = VzLPRClient_SetVideoVBR(m_hLPRClient, level);
		if ( ret != 0 )
		{
			MessageBox("设置图像质量失败，请重试！");
			return;
		}
	}

	//码流上限
	if ( m_editRateval.IsWindowEnabled() )
	{
		ret = VzLPRClient_SetVideoCBR(m_hLPRClient, nRate);
		if ( ret != 0 )
		{
			MessageBox("设置码流上限失败，请重试！");
			return;
		}
	}

	MessageBox("设置成功！");

}

void CSubDlgVideoCfg::OnCbnSelchangeCmbEncodeType()
{
	int nCurSel = m_cmbEncodeType.GetCurSel();
	if ( nCurSel == 0 )
	{
		m_cmbCompressMode.EnableWindow(TRUE);
		int compressSel = m_cmbCompressMode.GetCurSel();
		m_cmbImgQuality.EnableWindow(compressSel != 0);
		m_editRateval.EnableWindow(compressSel == 0);
	}
	else 
	{
		m_cmbCompressMode.EnableWindow(FALSE);
		m_cmbImgQuality.EnableWindow(TRUE);
		m_editRateval.EnableWindow(FALSE);
	}
}

void CSubDlgVideoCfg::OnCbnSelchangeCmbCompressMode()
{
	if( m_cmbCompressMode.IsWindowEnabled() )
	{
		int nCurSel = m_cmbCompressMode.GetCurSel();
		if ( nCurSel == 0 )
		{
			m_editRateval.EnableWindow(TRUE);
			m_cmbImgQuality.EnableWindow(FALSE);
		}
		else
		{
			m_editRateval.EnableWindow(FALSE);
			m_cmbImgQuality.EnableWindow(TRUE);
		}
	}
	else
	{
		m_editRateval.EnableWindow(FALSE);
	}
}

void CSubDlgVideoCfg::OnCbnSelchangeCmbFrameSize()
{
	// TODO: Add your control notification handler code here
}

void CSubDlgVideoCfg::SetBoardType(int type)
{
	//m_nBoardType = type;
}

