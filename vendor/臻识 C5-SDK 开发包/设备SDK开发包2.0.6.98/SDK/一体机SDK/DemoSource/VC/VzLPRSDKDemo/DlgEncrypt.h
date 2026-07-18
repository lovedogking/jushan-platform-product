#pragma once
#include <VzLPRClientSDK.h>
#include <VzLPRFaceClientSDK.h>
#include "VzLPRSDKDemoDlg.h"
#include "afxwin.h"

// CDlgEncrypt dialog

class CDlgEncrypt : public CDialog
{
	DECLARE_DYNAMIC(CDlgEncrypt)

public:
	CDlgEncrypt(CWnd* pParent = NULL);   // standard constructor
	virtual ~CDlgEncrypt();

// Dialog Data
	enum { IDD = IDD_DLG_ENCRYPT };

protected:
	virtual void DoDataExchange(CDataExchange* pDX);    // DDX/DDV support
	virtual BOOL OnInitDialog();
	DECLARE_MESSAGE_MAP()

public:
	void SetLPRHandle( VzLPRClientHandle hLPRClient );
	void SetDeviceInfo(DeviceLPR *pDev);

private:
	CString m_strUserPwd;
	CString m_strUserPwd2;
	CString m_strNewPwd;
	CString m_strNewrPwd2;

	VzLPRClientHandle m_hLPRClient;
	CComboBox m_cmbType;

	DeviceLPR *m_pDev;

public:
	afx_msg void OnBnClickedBtnSave();
	afx_msg void OnBnClickedRadioReset();
	afx_msg void OnBnClickedRadioModify();
	CString m_strPwdText;
	afx_msg void OnBnClickedOk();
	afx_msg void OnCbnSelchangeCmbType();
};
