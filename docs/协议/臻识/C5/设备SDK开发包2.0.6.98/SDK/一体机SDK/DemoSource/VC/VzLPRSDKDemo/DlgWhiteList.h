#pragma once
#include "afxcmn.h"
#include <VzLPRClientSDK_WhiteList.h>
#include "SubDlgConfigWhiteList.h"
#include "SubDlgConfigWhiteList2.h"

// DlgWhiteList dialog

class DlgWhiteList : public CDialog
{
	DECLARE_DYNAMIC(DlgWhiteList)

public:
	DlgWhiteList(CWnd* pParent = NULL);   // standard constructor
	virtual ~DlgWhiteList();

// Dialog Data
	enum { IDD = IDD_DIALOG_SET_WLIST };

	void SetHandle(VzLPRClientHandle handle);
protected:
	virtual void DoDataExchange(CDataExchange* pDX);    // DDX/DDV support
	enum{
		MAX_NUM_TAB_DLG = 1
	};

	DECLARE_MESSAGE_MAP()
public:
	CTabCtrl m_tabWlMain;
	SubDlgConfigWhiteList m_dlgSubWhiteList1;
	SubDlgConfigWhiteList2 m_dlgSubWhiteList2;

	CDialog* m_subDlgs[MAX_NUM_TAB_DLG];
	int m_curSel;
	afx_msg void OnTcnSelchangeTab1(NMHDR *pNMHDR, LRESULT *pResult);
	virtual BOOL OnInitDialog();

	VzLPRClientHandle m_hLPRC;
	virtual BOOL PreTranslateMessage(MSG* pMsg);
};
