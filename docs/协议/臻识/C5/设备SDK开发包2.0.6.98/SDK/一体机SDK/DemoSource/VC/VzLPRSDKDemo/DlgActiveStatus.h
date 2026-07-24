#pragma once
#include <VzLPRClientSDK.h>
#include <VzLPRFaceClientSDK.h>

#include "afxdtctl.h"


// CDlgActiveStatus dialog

class CDlgActiveStatus : public CDialog
{
	DECLARE_DYNAMIC(CDlgActiveStatus)

public:
	CDlgActiveStatus(CWnd* pParent = NULL);   // standard constructor
	virtual ~CDlgActiveStatus();

// Dialog Data
	enum { IDD = IDD_DLG_ACTIVE_STATUS };

protected:
	virtual void DoDataExchange(CDataExchange* pDX);    // DDX/DDV support

	DECLARE_MESSAGE_MAP()
public:
	afx_msg void OnBnClickedOk();
	virtual BOOL OnInitDialog();

	void SetLPRHandle(VzLPRClientHandle hLPRClient);

private:
	VzLPRClientHandle m_hLPRClient;
public:
	BOOL m_bUseTime;
	CString m_strUserPwd;
	COleDateTime m_dtDate;
	COleDateTime m_dtTime;
	afx_msg void OnBnClickedChkTime();
	CString m_strLeaveTime;
};
