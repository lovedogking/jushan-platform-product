// ExtDlg.cpp : implementation file
//

#include "stdafx.h"
#include "ExtDlg.h"


// CExtDlg dialog

IMPLEMENT_DYNAMIC(CExtDlg, CDialog)

CExtDlg::CExtDlg(CWnd* pParent /*=NULL*/)
	: CDialog(CExtDlg::IDD, pParent)
	, m_sSetUserData(_T(""))
	, m_sGetUserData(_T(""))
{

}

CExtDlg::~CExtDlg()
{
}

void CExtDlg::SetVzlprclientctrl(CVzlprclientctrlctrl1 *pVzLPRClientCtrl, long lprHandle)
{
	m_pVzLPRClientCtrl	= pVzLPRClientCtrl;
	m_lprHandle			= lprHandle;
}

void CExtDlg::DoDataExchange(CDataExchange* pDX)
{
	CDialog::DoDataExchange(pDX);
	DDX_Text(pDX, IDC_EDIT1, m_sSetUserData);
	DDX_Text(pDX, IDC_EDIT2, m_sGetUserData);
}


BEGIN_MESSAGE_MAP(CExtDlg, CDialog)
	ON_BN_CLICKED(IDC_BUTTON1, &CExtDlg::OnBnClickedButton1)
	ON_BN_CLICKED(IDC_BUTTON2, &CExtDlg::OnBnClickedButton2)
END_MESSAGE_MAP()


// CExtDlg message handlers

void CExtDlg::OnBnClickedButton1()
{
	UpdateData(TRUE);
	m_pVzLPRClientCtrl->VzLPRWriteUserData(m_lprHandle, m_sSetUserData);
}

void CExtDlg::OnBnClickedButton2()
{
	UpdateData(TRUE);
	m_sGetUserData = m_pVzLPRClientCtrl->VzLPRReadUserData(m_lprHandle);
	UpdateData(FALSE);
}
