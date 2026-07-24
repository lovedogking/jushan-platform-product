#pragma once
#include <VzLPRClientSDK.h>
#include "afxcmn.h"
#include "afxwin.h"

// DlgDevMate dialog

class DlgDevMate : public CDialog
{
	DECLARE_DYNAMIC(DlgDevMate)

public:
	DlgDevMate(CWnd* pParent = NULL);   // standard constructor
	virtual ~DlgDevMate();

// Dialog Data
	enum { IDD = IDD_DLG_MATE };

protected:
	virtual void DoDataExchange(CDataExchange* pDX);    // DDX/DDV support

	DECLARE_MESSAGE_MAP()
public:
	virtual BOOL OnInitDialog();
	int iListCtrlGetCurSel(CListCtrl *pList);

	VzLPRClientHandle lpr_handle_;
	CListCtrl rg_dev_list_;

	int play_handle_;
	afx_msg void OnBnClickedBtnPlay();
	CStatic play_wnd_;
	CButton play_btn_;
	CButton talk_button_;
	afx_msg void OnBnClickedBtnTalk();
	afx_msg void OnClose();
};
