#pragma once
#include "DlgSubVideoCfgR1.h"
#include "DlgSubVideoCfgR2.h"
#include "afxcmn.h"

// CDlgRVideoCfg dialog

class CDlgRVideoCfg : public CDialog
{
	DECLARE_DYNAMIC(CDlgRVideoCfg)

public:
	CDlgRVideoCfg(CWnd* pParent = NULL);   // standard constructor
	virtual ~CDlgRVideoCfg();

// Dialog Data
	enum { IDD = IDD_DLG_VIDEO_CFG_R };

protected:
	virtual void DoDataExchange(CDataExchange* pDX);    // DDX/DDV support
	afx_msg void OnTcnSelchangeTabMain(NMHDR *pNMHDR, LRESULT *pResult);
	DECLARE_MESSAGE_MAP()

public:
	virtual BOOL OnInitDialog();
	void SetLPRHandle( VzLPRClientHandle hLPRClient );

private:
	static const int MAX_NUM_TAB_COUNT = 2;

	CDlgSubVideoCfgR1 m_dlgSubVideoCfg1;
	CDlgSubVideoCfgR2 m_dlgSubVideoCfg2;

	CDialog* m_subDlgs[MAX_NUM_TAB_COUNT];
	int m_curSel;
	CTabCtrl m_tabMain;
	VzLPRClientHandle m_hLPRClient;
};
