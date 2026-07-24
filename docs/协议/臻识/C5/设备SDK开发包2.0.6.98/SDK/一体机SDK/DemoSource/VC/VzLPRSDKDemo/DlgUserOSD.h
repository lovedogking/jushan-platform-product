#pragma once
#include "afxwin.h"
#include <VzLPRClientSDK.h>


// CDlgUserOSD dialog

class CDlgUserOSD : public CDialog
{
	DECLARE_DYNAMIC(CDlgUserOSD)

public:
	CDlgUserOSD(CWnd* pParent = NULL);   // standard constructor
	virtual ~CDlgUserOSD();

// Dialog Data
	enum { IDD = IDD_DLG_USER_OSD };

protected:
	virtual void DoDataExchange(CDataExchange* pDX);    // DDX/DDV support
	virtual BOOL OnInitDialog();
	DECLARE_MESSAGE_MAP()

public:
	void SetLPRHandle(VzLPRClientHandle hLPRClient);

private:
	VzLPRClientHandle m_hLPRClient;

	CString m_strRow1;
	CString m_strRow2;
	CString m_strRow3;
	CString m_strRow4;
	CComboBox m_cmbColor1;
	CComboBox m_cmbColor2;
	CComboBox m_cmbColor3;
	CComboBox m_cmbColor4;
	CComboBox m_cmbFont1;
	CComboBox m_cmbFont2;
	CComboBox m_cmbFont3;
	CComboBox m_cmbFont4;

	CString m_strXPos;
	CString m_strYPos;
public:
	afx_msg void OnBnClickedOk();
};
