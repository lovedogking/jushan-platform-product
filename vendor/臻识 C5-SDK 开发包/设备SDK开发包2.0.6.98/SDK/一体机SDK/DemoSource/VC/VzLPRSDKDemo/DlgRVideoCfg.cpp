// DlgRVideoCfg.cpp : implementation file
//

#include "stdafx.h"
#include "VzLPRSDKDemo.h"
#include "DlgRVideoCfg.h"


// CDlgRVideoCfg dialog

IMPLEMENT_DYNAMIC(CDlgRVideoCfg, CDialog)

CDlgRVideoCfg::CDlgRVideoCfg(CWnd* pParent /*=NULL*/)
	: CDialog(CDlgRVideoCfg::IDD, pParent)
{
	m_hLPRClient = NULL;
}

CDlgRVideoCfg::~CDlgRVideoCfg()
{
}

void CDlgRVideoCfg::DoDataExchange(CDataExchange* pDX)
{
	CDialog::DoDataExchange(pDX);
	DDX_Control(pDX, IDC_TAB_MAIN, m_tabMain);
}


BEGIN_MESSAGE_MAP(CDlgRVideoCfg, CDialog)
	ON_NOTIFY(TCN_SELCHANGE, IDC_TAB_MAIN, &CDlgRVideoCfg::OnTcnSelchangeTabMain)
END_MESSAGE_MAP()


// CDlgRVideoCfg message handlers

BOOL CDlgRVideoCfg::OnInitDialog()
{
	CDialog::OnInitDialog();

	// TODO:  Add extra initialization here
	m_tabMain.InsertItem(0,"编码参数");
	m_tabMain.InsertItem(1,"图像参数");

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

void CDlgRVideoCfg::OnTcnSelchangeTabMain(NMHDR *pNMHDR, LRESULT *pResult)
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
		}
		else
		{
			m_dlgSubVideoCfg2.Load( );
		}
	}

	*pResult = 0;
}

void CDlgRVideoCfg::SetLPRHandle( VzLPRClientHandle hLPRClient )
{
	m_hLPRClient = hLPRClient;
}