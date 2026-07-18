// DlgPdnsLoginInfo.cpp : implementation file
//

#include "stdafx.h"
#include "VzLPRSDKDemo.h"
#include "DlgPdnsLoginInfo.h"


// DlgPdnsLoginInfo dialog

IMPLEMENT_DYNAMIC(DlgPdnsLoginInfo, CDialog)

DlgPdnsLoginInfo::DlgPdnsLoginInfo(CWnd* pParent /*=NULL*/)
	: CDialog(DlgPdnsLoginInfo::IDD, pParent)
{
	pdns_url = NULL;
	pdns_user_id = NULL;
	pdns_user_pwd = NULL;
}

DlgPdnsLoginInfo::~DlgPdnsLoginInfo()
{
}

void DlgPdnsLoginInfo::DoDataExchange(CDataExchange* pDX)
{
	CDialog::DoDataExchange(pDX);
}


BEGIN_MESSAGE_MAP(DlgPdnsLoginInfo, CDialog)
	ON_BN_CLICKED(IDOK, &DlgPdnsLoginInfo::OnBnClickedOk)
END_MESSAGE_MAP()


// DlgPdnsLoginInfo message handlers

void DlgPdnsLoginInfo::OnBnClickedOk()
{
	CString url;
	CString user_id;
	CString user_pwd;

	GetDlgItemText(IDC_EDIT_URL, url);
	GetDlgItemText(IDC_EDIT_PDNS_ID, user_id);
	GetDlgItemText(IDC_EDIT_PDNS_PWD, user_pwd);

	if (url == "" || user_id == "" || user_pwd == "") {
		MessageBox("云平台账户信息不能为空! ");
		return;
	}

	*pdns_url = url;
	*pdns_user_id = user_id;
	*pdns_user_pwd = user_pwd;

	OnOK();
}

BOOL DlgPdnsLoginInfo::OnInitDialog()
{
	CDialog::OnInitDialog();

	SetDlgItemText(IDC_EDIT_URL, "open.vzicloud.com");
	SetDlgItemText(IDC_EDIT_PDNS_ID, "8dpaiHjN21151FfM8LgyMYKfWqIlGwBC");
	SetDlgItemText(IDC_EDIT_PDNS_PWD, "QdHq06TNb5A3KNFSPSBFwi1qgiMvcL5X");

	return TRUE;
}
