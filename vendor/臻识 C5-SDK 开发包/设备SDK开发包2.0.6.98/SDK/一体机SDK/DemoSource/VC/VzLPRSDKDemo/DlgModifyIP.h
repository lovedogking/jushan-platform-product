#pragma once
#include "afxcmn.h"


// CDlgModifyIP dialog

class CDlgModifyIP : public CDialog
{
	DECLARE_DYNAMIC(CDlgModifyIP)

public:
	CDlgModifyIP(CWnd* pParent = NULL);   // standard constructor
	virtual ~CDlgModifyIP();

// Dialog Data
	enum { IDD = IDD_DLG_MODIFY_IP };

protected:
	virtual void DoDataExchange(CDataExchange* pDX);    // DDX/DDV support
	afx_msg void OnBnClickedOk();
	DECLARE_MESSAGE_MAP()

public:
	void SetNetParam(LPCTSTR strIP, unsigned int SL, unsigned int SH, LPCTSTR netmask, LPCTSTR gateway);
	BOOL SaveNetworkParam( );

private:
	unsigned int m_nSL;
	unsigned int m_nSH;

	CString m_strIP;
	CString m_strNetmask;
	CString m_strGateway;


	CIPAddressCtrl m_ipaddrIP;
	CIPAddressCtrl m_ipaddrMask;
	CIPAddressCtrl m_ipaddrGateway;
public:
	virtual BOOL OnInitDialog();
};
