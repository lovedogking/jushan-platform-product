// DlgMaintain.cpp : implementation file
//

#include "stdafx.h"
#include "VzLPRSDKDemo.h"
#include "DlgMaintain.h"

enum
{
	TIMER_UPDATE_STAT     = 2011,
	TIMER_UPDATE_Flish     = 2012
};

const int max_times = 60 * 5;
// CDlgMaintain dialog

IMPLEMENT_DYNAMIC(CDlgMaintain, CDialog)

CDlgMaintain::CDlgMaintain(CWnd* pParent /*=NULL*/)
	: CDialog(CDlgMaintain::IDD, pParent)
{
	m_hLPRClient	= NULL;
	m_nGetStatTimes	= 0;
}

CDlgMaintain::~CDlgMaintain()
{
}

void CDlgMaintain::DoDataExchange(CDataExchange* pDX)
{
	CDialog::DoDataExchange(pDX);
	DDX_Control(pDX, IDC_EDIT_PATH, m_editPath);
}


BEGIN_MESSAGE_MAP(CDlgMaintain, CDialog)
	ON_BN_CLICKED(IDC_BTN_RECOVERY, &CDlgMaintain::OnBnClickedBtnRecovery)
	ON_BN_CLICKED(IDC_BTN_BROWSE, &CDlgMaintain::OnBnClickedBtnBrowse)
	ON_BN_CLICKED(IDC_BTN_UPDATE, &CDlgMaintain::OnBnClickedBtnUpdate)
	ON_WM_TIMER()
	ON_BN_CLICKED(IDC_BTN_PartRECOVERY, &CDlgMaintain::OnBnClickedBtnPartrecovery)
	ON_BN_CLICKED(IDC_BTN_CANCEL_UPDATE, &CDlgMaintain::OnBnClickedBtnCancelUpdate)
	ON_WM_PAINT()
	ON_WM_CLOSE()
END_MESSAGE_MAP()


// CDlgMaintain message handlers

void CDlgMaintain::OnBnClickedBtnRecovery()
{
	if ( m_hLPRClient != NULL )
	{
		int ret = VzLPRClient_RestoreConfig( m_hLPRClient );
		if ( ret == 0 )
		{
			MessageBox("恢复出厂配置成功，设置正重启..", "提示", MB_OK );
			OnOK( );
		}
		else
		{
			MessageBox("恢复出厂配置失败，请重试", "提示", MB_OK );
		}
	}
}

void CDlgMaintain::OnBnClickedBtnBrowse()
{
	m_strFilePath = "";
	m_editPath.SetWindowText( "" );

	CFileDialog FileDlg (TRUE,NULL,NULL,OFN_HIDEREADONLY|OFN_OVERWRITEPROMPT,"bin文件|*.bin||");
	if (FileDlg.DoModal () == IDOK)
	{
		m_strFilePath = FileDlg.GetPathName( );
	}

	m_editPath.SetWindowText( m_strFilePath );
}

void CDlgMaintain::OnBnClickedBtnUpdate()
{
	KillTimer( TIMER_UPDATE_STAT );
	m_nGetStatTimes = 0;

	if( m_strFilePath != "" )
	{
		int ret = VzLPRClient_Update( m_hLPRClient, m_strFilePath.GetBuffer(0) );
		if( ret == 0 )
		{
			//MessageBox("正在升级设备，可能需要五分钟，确认后开始升级，请不要进行其他操作！");
			SetDlgItemText(IDC_STATIC_HIT, "正在升级设备，可能需要五分钟，确认后开始升级，请不要进行其他操作！");
			SetTimer(TIMER_UPDATE_STAT, 2000, NULL);
 

			SetUpdateStatus(US_UPDATE);
			
			SetProgressNum(0.0f);
		}
		else
		{
			MessageBox( "升级失败，请重试。", "提示", MB_OK );
		}
	}
	else
	{
		MessageBox( "请选择升级文件。", "提示", MB_OK );
	}
}

void CDlgMaintain::SetLPRHandle( VzLPRClientHandle hLPRClient )
{
	m_hLPRClient = hLPRClient;
}
void CDlgMaintain::OnTimer(UINT_PTR nIDEvent)
{
	// TODO: Add your message handler code here and/or call default
	if( nIDEvent == TIMER_UPDATE_STAT )
	{
		bool stopTimer = false;
		m_nGetStatTimes++;
		bool bReboot = false;

		int ret = VzLPRClient_GetUpdateState(m_hLPRClient);
		if (ret == 1 || ret == 4 || ( m_nGetStatTimes >= max_times ))
		{
			bReboot = true;	
			stopTimer = true;
		}
		else if( ret == -1 )
		{
			// SetDlgItemText(IDC_STATIC_HIT, "升级失败");
			SetUpdateStatus(US_INIT);
			// stopTimer = true;
		}
		else if( 3 == ret )
		{
           CWnd * pWnd = GetDlgItem(IDC_BTN_CANCEL_UPDATE);
		    pWnd->EnableWindow(FALSE);
		}

		if( stopTimer )
		{
			KillTimer(TIMER_UPDATE_STAT);
		}


		int iProgress = VzLPRClient_GetUpdateProgress(m_hLPRClient);

		if (bReboot)
		{
			KillTimer( TIMER_UPDATE_STAT);			
			VzLPRClient_RebootDVR(m_hLPRClient);

			Sleep(3500);

			SetTimer(TIMER_UPDATE_Flish, 5000, NULL);
			/*
			Sleep(10000);
			BYTE byteState = 0;
			while (true)
			{
				VzLPRClient_IsConnected(m_hLPRClient, &byteState);
				if (byteState == 1)
				{
					Sleep(30000);
					SetUpdateStatus(US_INIT);

					SetDlgItemText(IDC_STATIC_HIT, "设备已重启成功");

					iProgress = 100.0;
					break;
				}
			}
			*/
		}

		SetProgressNum(iProgress);
	}
	else if(nIDEvent == TIMER_UPDATE_Flish)
	{
		BYTE byteState = 0;

		VzLPRClient_IsConnected(m_hLPRClient, &byteState);
		if (byteState == 1)
		{
			SetUpdateStatus(US_INIT);

			SetDlgItemText(IDC_STATIC_HIT, "设备已重启成功");

			SetProgressNum(100.0);
			
			KillTimer(TIMER_UPDATE_Flish);
		}	
	}

	CDialog::OnTimer(nIDEvent);
}

void CDlgMaintain::OnBnClickedBtnPartrecovery()
{
	// TODO: Add your control notification handler code here
	if (m_hLPRClient == NULL)
		return;

	int nRet = VzLPRClient_RestoreConfigPartly(m_hLPRClient);
	if ( nRet == 0 )
	{
		MessageBox("恢复出厂配置成功，设置正重启..", "提示", MB_OK );
		OnOK( );
	}
	else
	{
		MessageBox("恢复出厂配置失败，请重试", "提示", MB_OK );
	}
}

void CDlgMaintain::OnBnClickedBtnCancelUpdate()
{
	// TODO: Add your control notification handler code here
	if (m_hLPRClient == NULL)
		return;

	if (VzLPRClient_StopUpdate(m_hLPRClient) != 0 )
	{
		MessageBox("取消升级失败", "提示", MB_OK);
	}
	else
	{
		KillTimer(TIMER_UPDATE_STAT);

		SetUpdateStatus(US_INIT);
		SetDlgItemText(IDC_STATIC_HIT, "取消升级成功");
	}

}


BOOL CDlgMaintain::OnInitDialog()
{
	CDialog::OnInitDialog();

	// TODO:  Add extra initialization here



	SetUpdateStatus(US_INIT);

	SetProgressNum(0);

	
	
	return TRUE;  // return TRUE unless you set the focus to a control
	// EXCEPTION: OCX Property Pages should return FALSE
}


void CDlgMaintain::OnPaint()
{
	CPaintDC dc(this); // device context for painting
	// TODO: Add your message handler code here
	// Do not call CDialog::OnPaint() for painting messages


	 
 
}

//设置进度
void  CDlgMaintain::SetProgressNum(float iProgress)
{
	if (iProgress > 100.0)
	{
		iProgress = 100.0;
	}
	if( iProgress < 0.0f )
	{
        iProgress = 0.0;
	}

	HWND hwnd;
	CWnd  *pProgressNumWd = GetDlgItem(IDC_STATIC_PROGRESS_NUM);


	
	if (pProgressNumWd && pProgressNumWd->GetSafeHwnd())
	{
		CDC * dc = pProgressNumWd->GetDC();

		RECT rc;

		pProgressNumWd->GetClientRect(&rc);

		CBrush brush;
	
		CRgn rgn;

		float prcent = iProgress / 100.0f;
		int FrontWidth =  (rc.right - rc.left) *prcent;
		int BackWidth = (rc.right - rc.left) - FrontWidth;

		if (FrontWidth)
		{
			brush.CreateSolidBrush(RGB(255, 20, 50));
			rgn.CreateRectRgn(rc.left, rc.top, rc.left + FrontWidth, rc.bottom);
			dc->SelectClipRgn(&rgn);
			dc->FillRect(&rc, &brush);
			dc->SelectClipRgn(NULL);
		}
		
		if (BackWidth)
		{
			brush.DeleteObject();
			brush.Detach();
			brush.CreateSolidBrush(RGB(50, 20, 255));

			rgn.DeleteObject();
			rgn.Detach();
			rgn.CreateRectRgn(rc.left + FrontWidth, rc.top, rc.right, rc.bottom);
			dc->SelectClipRgn(&rgn);
			dc->FillRect(&rc, &brush);
			dc->SelectClipRgn(NULL);
		}
		


		char str[50];
		memset(str,0,50);
		sprintf_s(str, 50, "%.1f%%", iProgress);

		dc->SetBkMode(TRANSPARENT);
		dc->DrawText(str, strlen(str), &rc, DT_CENTER|DT_VCENTER);


		pProgressNumWd->ReleaseDC(dc);

	}


}

//设置更新状态
void CDlgMaintain::SetUpdateStatus(UpdateStatus us)
{
	m_UpdateStatus = us;


	if (US_INIT == m_UpdateStatus)
	{
		CWnd  *pWnd = GetDlgItem(IDC_BTN_UPDATE);
		pWnd->EnableWindow(TRUE);
		pWnd = GetDlgItem(IDC_BTN_CANCEL_UPDATE);
		pWnd->EnableWindow(FALSE);

		pWnd = GetDlgItem(IDC_BTN_RECOVERY);
		pWnd->EnableWindow(TRUE);

		pWnd = GetDlgItem(IDC_BTN_PartRECOVERY);
		pWnd->EnableWindow(TRUE);
	} 
	else if ( US_UPDATE == m_UpdateStatus)
	{
		CWnd  *pWnd = GetDlgItem(IDC_BTN_UPDATE);
		pWnd->EnableWindow(FALSE);
		pWnd = GetDlgItem(IDC_BTN_CANCEL_UPDATE);
		pWnd->EnableWindow(TRUE);

		pWnd = GetDlgItem(IDC_BTN_RECOVERY);
		pWnd->EnableWindow(FALSE);

		pWnd = GetDlgItem(IDC_BTN_PartRECOVERY);
		pWnd->EnableWindow(FALSE);
	}

}

void CDlgMaintain::OnOK()
{
	// TODO: Add your specialized code here and/or call the base class

	CDialog::OnOK();
}


void CDlgMaintain::OnClose()
{
	// TODO: Add your message handler code here and/or call default

	KillTimer(TIMER_UPDATE_Flish);

	if (m_UpdateStatus == US_INIT )
	{
		CDialog::OnClose();
	}
	
}