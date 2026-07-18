#pragma once
#include "Resource.h"
#include "vzlprclientctrlctrl1.h"


// CExtDlg dialog

class CExtDlg : public CDialog
{
	DECLARE_DYNAMIC(CExtDlg)

public:
	CExtDlg(CWnd* pParent = NULL);   // standard constructor
	virtual ~CExtDlg();

// Dialog Data
	enum { IDD = IDD_DLG_EXT };

public:
	void SetVzlprclientctrl( CVzlprclientctrlctrl1 *pVzLPRClientCtrl, long lprHandle );

protected:
	virtual void DoDataExchange(CDataExchange* pDX);    // DDX/DDV support

	DECLARE_MESSAGE_MAP()
public:
	afx_msg void OnBnClickedButton1();

private:
	CString m_sSetUserData;
	CString m_sGetUserData;

	CVzlprclientctrlctrl1 *m_pVzLPRClientCtrl;
	long m_lprHandle;
public:
	afx_msg void OnBnClickedButton2();
};
