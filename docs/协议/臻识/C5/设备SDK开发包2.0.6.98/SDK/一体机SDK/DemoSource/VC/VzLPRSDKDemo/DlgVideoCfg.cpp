// DlgVideoCfg.cpp : implementation file
//

#include "stdafx.h"
#include "VzLPRSDKDemo.h"
#include "DlgVideoCfg.h"


// CDlgVideoCfg dialog

IMPLEMENT_DYNAMIC(CDlgVideoCfg, CDialog)

CDlgVideoCfg::CDlgVideoCfg(CWnd* pParent /*=NULL*/)
	: CDialog(CDlgVideoCfg::IDD, pParent)
{
	m_hLPRClient = NULL;
	m_nBoardType = 0;
}

CDlgVideoCfg::~CDlgVideoCfg()
{
}

void CDlgVideoCfg::DoDataExchange(CDataExchange* pDX)
{
	CDialog::DoDataExchange(pDX);
	DDX_Control(pDX, IDC_TAB_MAIN, m_tabMain);
}


BEGIN_MESSAGE_MAP(CDlgVideoCfg, CDialog)
	ON_NOTIFY(TCN_SELCHANGE, IDC_TAB_MAIN, &CDlgVideoCfg::OnTcnSelchangeTabMain)
END_MESSAGE_MAP()


// CDlgVideoCfg message handlers

BOOL CDlgVideoCfg::OnInitDialog()
{
	CDialog::OnInitDialog();

	// TODO:  Add extra initialization here

	m_tabMain.InsertItem(0,"主码流");
	m_tabMain.InsertItem(1,"视频源");

	m_dlgSubVideoCfg1.SetLPRHandle(m_hLPRClient);
	m_dlgSubVideoCfg2.SetLPRHandle(m_hLPRClient);

	m_dlgSubVideoCfg1.Create(m_dlgSubVideoCfg1.IDD,&m_tabMain);
	m_dlgSubVideoCfg2.Create(m_dlgSubVideoCfg2.IDD,&m_tabMain);

	m_subDlgs[0]=&m_dlgSubVideoCfg1;
	m_subDlgs[1]=&m_dlgSubVideoCfg2;

	CRect rc;
	m_tabMain.GetClientRect(&rc);
	m_tabMain.AdjustRect(FALSE, &rc);
	for(int i=0;i<MAX_NUM_TAB_COUNT;i++)
	{
		if (m_subDlgs[i]) 
		{
			m_subDlgs[i]->MoveWindow(rc,0);
		}
	}

	m_curSel = 0;
	m_subDlgs[m_curSel]->ShowWindow(SW_SHOW);

	return TRUE;
}

void CDlgVideoCfg::OnTcnSelchangeTabMain(NMHDR *pNMHDR, LRESULT *pResult)
{
	if(m_subDlgs[m_curSel]!=NULL){
		m_subDlgs[m_curSel]->ShowWindow(SW_HIDE);
	}

	m_curSel = m_tabMain.GetCurSel();
	if ( m_subDlgs[m_curSel] != NULL )
	{
		m_subDlgs[m_curSel]->ShowWindow(SW_SHOW);

		if( m_curSel == 0 )
		{
			m_dlgSubVideoCfg1.Load( );
			m_dlgSubVideoCfg1.SetBoardType(m_nBoardType);
		}
		else
		{
			m_dlgSubVideoCfg2.Load( );
		}
	}

	*pResult = 0;
}

void CDlgVideoCfg::SetLPRHandle( VzLPRClientHandle hLPRClient )
{
	m_hLPRClient = hLPRClient;
}

void CDlgVideoCfg::SetBoardType(int type)
{
	m_nBoardType = type;
}


