#pragma once


// DlgPdnsLoginInfo dialog

class DlgPdnsLoginInfo : public CDialog
{
	DECLARE_DYNAMIC(DlgPdnsLoginInfo)

public:
	DlgPdnsLoginInfo(CWnd* pParent = NULL);   // standard constructor
	virtual ~DlgPdnsLoginInfo();

// Dialog Data
	enum { IDD = IDD_DLG_LOGIN_EXT };

protected:
	virtual void DoDataExchange(CDataExchange* pDX);    // DDX/DDV support
	virtual BOOL OnInitDialog();
	afx_msg void OnBnClickedOk();
	DECLARE_MESSAGE_MAP()

public:
	CString *pdns_url;
	CString *pdns_user_id;
	CString *pdns_user_pwd;
};
