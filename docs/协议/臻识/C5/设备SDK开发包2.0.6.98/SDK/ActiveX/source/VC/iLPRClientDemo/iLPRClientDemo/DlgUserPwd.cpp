// DlgUserPwd.cpp : implementation file
//

#include "stdafx.h"
#include "Resource.h"
#include "DlgUserPwd.h"


// CDlgUserPwd dialog

IMPLEMENT_DYNAMIC(CDlgUserPwd, CDialog)

CDlgUserPwd::CDlgUserPwd(CWnd* pParent /*=NULL*/)
	: CDialog(CDlgUserPwd::IDD, pParent)
{
	m_pVzLPRClientCtrl  = NULL;
	m_lprHandle			= NULL;
}

CDlgUserPwd::~CDlgUserPwd()
{
}

void CDlgUserPwd::DoDataExchange(CDataExchange* pDX)
{
	CDialog::DoDataExchange(pDX);
	DDX_Control(pDX, IDC_EDIT_PWD, m_editPwd);
}


BEGIN_MESSAGE_MAP(CDlgUserPwd, CDialog)
	ON_BN_CLICKED(IDOK, &CDlgUserPwd::OnBnClickedOk)
END_MESSAGE_MAP()


// CDlgUserPwd message handlers
void CDlgUserPwd::SetVzlprclientctrl(CVzlprclientctrlctrl1 *pVzLPRClientCtrl, long lprHandle)
{
	m_pVzLPRClientCtrl	= pVzLPRClientCtrl;
	m_lprHandle			= lprHandle;
}

void CDlgUserPwd::OnBnClickedOk()
{
	if ( m_pVzLPRClientCtrl != NULL )
	{
		CString strPwd;
		m_editPwd.GetWindowText(strPwd);

		int ret = m_pVzLPRClientCtrl->VzLPRSetUserPwd(m_lprHandle, strPwd.GetBuffer(0));
		if( ret == 0 )
		{
			MessageBox("设置用户密码成功! ");
			OnOK();
		}
		else
		{
			MessageBox("设置用户密码失败，请重试! ");
		}
	}
}
