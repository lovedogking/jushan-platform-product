#pragma once
#include "vzlprclientctrlctrl1.h"
#include "afxcmn.h"
#include "afxwin.h"

// CDlgLedCtrl dialog

class CDlgLedCtrl : public CDialog
{
	DECLARE_DYNAMIC(CDlgLedCtrl)

public:
	CDlgLedCtrl(CWnd* pParent = NULL);   // standard constructor
	virtual ~CDlgLedCtrl();

// Dialog Data
	enum { IDD = IDD_DLG_LED_CTRL };

protected:
	virtual void DoDataExchange(CDataExchange* pDX);    // DDX/DDV support
	virtual BOOL OnInitDialog();
	DECLARE_MESSAGE_MAP()

public:
	void SetVzlprclientctrl( CVzlprclientctrlctrl1 *pVzLPRClientCtrl, long lprHandle );

public:
	CVzlprclientctrlctrl1 *m_pVzLPRClientCtrl;
	long m_lprHandle;
	CSliderCtrl m_sliderLevel;
	CStatic m_lblLevel;
	afx_msg void OnBnClickedRdoAuto();
	afx_msg void OnBnClickedRdoOpen();
	afx_msg void OnBnClickedRdoClose();
	afx_msg void OnHScroll(UINT nSBCode, UINT nPos, CScrollBar* pScrollBar);
};
