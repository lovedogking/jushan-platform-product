#pragma once
#include "vzlprclientctrlctrl1.h"
#include "afxwin.h"

// CDlgUserPwd dialog

class CDlgUserPwd : public CDialog
{
	DECLARE_DYNAMIC(CDlgUserPwd)

public:
	CDlgUserPwd(CWnd* pParent = NULL);   // standard constructor
	virtual ~CDlgUserPwd();

// Dialog Data
	enum { IDD = IDD_DLG_USER_PWD };

public:
	void SetVzlprclientctrl( CVzlprclientctrlctrl1 *pVzLPRClientCtrl, long lprHandle );

protected:
	virtual void DoDataExchange(CDataExchange* pDX);    // DDX/DDV support

	DECLARE_MESSAGE_MAP()

private:
	CVzlprclientctrlctrl1 *m_pVzLPRClientCtrl;
	long m_lprHandle;
public:
	afx_msg void OnBnClickedOk();
	CEdit m_editPwd;
};
